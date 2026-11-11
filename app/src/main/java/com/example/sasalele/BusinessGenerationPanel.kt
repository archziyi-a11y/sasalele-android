package com.example.sasalele

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.sasalele.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URLEncoder

internal fun generationError(code:String)=when(code) {
 "chat_insufficient"->"该客户本次范围内没有足够的文本聊天，未生成画像"
 "model_not_configured"->"业务服务尚未配置模型，请联系管理员"
 "model_rate_or_quota_limited"->"模型服务限流或额度受限，请稍后重试或联系管理员"
 "model_quota_exceeded"->"模型账户额度不足，请联系管理员核实账户额度；反复重试无效"
 "model_rate_limited"->"模型服务请求限流，请稍后再试"
 "model_access_invalid"->"模型服务授权无效，请联系管理员"
 "model_output_invalid"->"模型返回内容不符合格式，请重试"
 "customer_deleted"->"客户已删除或身份失效，请重新搜索"
 "generation_interrupted"->"服务重启中断了生成，请重试；已有画像保留"
 else->"生成未完成，请稍后重试；已有画像保留"
}

internal suspend fun awaitGeneration(context:Context,key:ClientKey,path:String,body:JSONObject,
                                     onJob:(String)->Unit={}, progress:(String)->Unit):JSONObject=withTimeout(240000) {
 var job=BusinessApi.call(context,path,body)
 val id=job.getString("job_id")
 onJob(id)
 val query="jobs/$id?account_id="+URLEncoder.encode(key.workAccountId,"UTF-8")+"&friend_id="+URLEncoder.encode(key.clientId,"UTF-8")
 while(true) {
  check(job.getString("account_id")==key.workAccountId && job.getString("friend_id")==key.clientId) {"生成目标双标识不一致"}
  when(job.optString("state")) {
   "succeeded"->return@withTimeout JSONObject(job.getJSONObject("result").toString()).put("_client_job_id",id)
   "failed","interrupted"->throw BusinessFailure(generationError(job.optString("error")),FailureKind.DATA)
   "queued"->progress("已排队，正在等待生成…")
   else->progress("正在读取目标聊天并生成，已有画像会保留…")
  }
  delay(1500)
  job=BusinessApi.call(context,query)
 }
 @Suppress("UNREACHABLE_CODE") JSONObject()
}

private val eventScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
private val eventMutex=kotlinx.coroutines.sync.Mutex()
private val flushMutex=kotlinx.coroutines.sync.Mutex()
private fun flushGenerationEvents(context:Context) {
 val app=context.applicationContext
 eventScope.launch {
  if(!flushMutex.tryLock()) return@launch
  try {
   val prefs=app.getSharedPreferences("generation_events_v1",Context.MODE_PRIVATE)
   while(true) {
    eventMutex.lock()
    val entry=try {
     val q=runCatching {org.json.JSONArray(prefs.getString("queue","[]"))}.getOrDefault(org.json.JSONArray())
     val now=System.currentTimeMillis();val kept=org.json.JSONArray()
     for(i in 0 until q.length()) {val item=q.optJSONObject(i) ?: continue;if(now-item.optLong("queued_at")<=7L*24*60*60*1000) kept.put(item)}
     prefs.edit().putString("queue",kept.toString()).commit()
     kept.optJSONObject(0)
    } finally {eventMutex.unlock()}
    if(entry==null) break
    var discard=false
    try {BusinessApi.call(app,"generation-events",entry.getJSONObject("body"))}
    catch(e:CancellationException){throw e}
    catch(e:BusinessFailure){if(e.diagnosticCode in setOf("generation_job_not_found","generation_job_expired","generation_event_target_unavailable")) discard=true else break}
    catch(_:Exception){break}
    val id=entry.getJSONObject("body").getString("event_id")
    eventMutex.lock();try {
     val current=runCatching {org.json.JSONArray(prefs.getString("queue","[]"))}.getOrDefault(org.json.JSONArray());val keep=org.json.JSONArray()
     for(i in 0 until current.length()) {val item=current.optJSONObject(i) ?: continue;if(item.optJSONObject("body")?.optString("event_id")!=id)keep.put(item)}
     val edit=prefs.edit().putString("queue",keep.toString());if(discard)edit.putLong("discarded_missing_job",prefs.getLong("discarded_missing_job",0)+1);edit.commit()
    } finally {eventMutex.unlock()}
   }
  } finally {flushMutex.unlock()}
 }
}
private fun recordGenerationEvent(context:Context,key:ClientKey,jobId:String,event:String,ratio:Double?=null,elapsed:Long?=null) {
 if(jobId.isBlank()) return
 val app=context.applicationContext
 val body=BusinessApi.target(key).put("job_id",jobId).put("event_id",java.util.UUID.randomUUID().toString()).put("event",event)
 if(ratio!=null) body.put("edit_ratio",ratio)
 if(elapsed!=null) body.put("elapsed_ms",elapsed.coerceIn(0,3600000))
 eventScope.launch {eventMutex.lock();try {
  val prefs=app.getSharedPreferences("generation_events_v1",Context.MODE_PRIVATE)
  val old=runCatching {org.json.JSONArray(prefs.getString("queue","[]"))}.getOrDefault(org.json.JSONArray())
  val next=org.json.JSONArray();for(i in maxOf(0,old.length()-199) until old.length()) next.put(old.getJSONObject(i))
  next.put(JSONObject().put("queued_at",System.currentTimeMillis()).put("body",body))
  prefs.edit().putString("queue",next.toString()).commit()
 } finally {eventMutex.unlock()};flushGenerationEvents(app) }
}
internal fun editRatio(original:String,edited:String):Double {
 val a=original.take(4000);val b=edited.take(4000)
 if(a==b) return 0.0
 val length=maxOf(a.length,b.length);if(length==0)return 0.0
 var previous=IntArray(b.length+1){it};var current=IntArray(b.length+1)
 for(i in 1..a.length) {current[0]=i;for(j in 1..b.length) current[j]=minOf(current[j-1]+1,previous[j]+1,previous[j-1]+if(a[i-1]==b[j-1])0 else 1);val t=previous;previous=current;current=t}
 return previous[b.length].toDouble()/length
}

private fun recordEditedEvent(context:Context,key:ClientKey,jobId:String,event:String,original:String,edited:String) {
 eventScope.launch {val ratio=withContext(Dispatchers.Default){editRatio(original,edited)};recordGenerationEvent(context,key,jobId,event,ratio)}
}

internal class GenerationDraft {
 var taskId=java.util.UUID.randomUUID().toString()
 var jobId=""
 var resultTaskId=""
 var runningJobId=""
 var pendingTaskId=""
 var pendingStarted=0L
 var copied=false
 var latest by mutableStateOf("")
 var original by mutableStateOf("")
 var result by mutableStateOf<JSONObject?>(null)
 var edited by mutableStateOf("")
 var pending by mutableStateOf<JSONObject?>(null)
 var pendingInputs by mutableStateOf<Pair<String,String>?>(null)
 var resultInputs by mutableStateOf<Pair<String,String>?>(null)
 var feedback by mutableStateOf("")
 var revision by mutableIntStateOf(0)
}

class GenerationDraftStore {
 internal val drafts = mutableStateMapOf<Pair<ClientKey,String>,GenerationDraft>()
 private var last:Pair<ClientKey,String>?=null
 internal fun activate(key:ClientKey,type:String) {val next=key to type;if(last!=null && last!=next) get(key,type).taskId=java.util.UUID.randomUUID().toString();last=next}
 internal fun get(key:ClientKey,type:String)=drafts.getOrPut(key to type){GenerationDraft()}
}

@Composable
fun BusinessGenerationPanel(context:Context,guidanceOnly:Boolean=false,store:GenerationDraftStore?=null) {
 val client by ClientRepository.currentBoundClient.collectAsState();val key=client?.key
 val localStore=remember {GenerationDraftStore()};val drafts=store ?: localStore
 var type by remember(key,guidanceOnly){mutableStateOf(if(guidanceOnly) "销售指导" else "帮我回")}
 LaunchedEffect(key,type) {if(key!=null) drafts.activate(key,type)}
 val draft=if(key!=null) drafts.get(key,type) else null
 var busy by remember(key,type){mutableStateOf(false)}
 var details by remember(key,type){mutableStateOf(false)}
 var showInput by remember(key,type){mutableStateOf(false)}
 var job by remember {mutableStateOf<Job?>(null)};val scope=rememberCoroutineScope()
 val gate=remember {RequestEpoch()};val clipboard=LocalClipboardManager.current
 LaunchedEffect(Unit) {flushGenerationEvents(context)}
 DisposableEffect(key,type) { onDispose {
  if(key!=null && draft!=null) {val id=if(busy) draft.runningJobId else draft.jobId;if(id.isNotBlank()) recordGenerationEvent(context,key,id,"panel_closed")}
  if(busy) draft?.feedback="已停止等待，服务端任务可能仍会完成；已有内容保留"
  gate.next();job?.cancel()
 } }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("当前客户："+(client?.name ?: "尚未确认"),style=MaterialTheme.typography.titleMedium)
  if(key==null) {Text("请先识别并确认客户，再生成建议。");return@Column}
  val state=draft ?: return@Column
  if(!guidanceOnly) Row {for(option in listOf("帮我回","润色")) TextButton(onClick={type=option}){Text(if(type==option) "✓ $option" else option)}}
  state.result?.let { result ->
   Text(if(guidanceOnly) "本次跟进建议" else "回复草稿",style=MaterialTheme.typography.titleMedium)
   OutlinedTextField(state.edited,{state.edited=it;state.revision++},label={Text(if(guidanceOnly) "销售指导（内部参考）" else "审阅与编辑草稿")},modifier=Modifier.fillMaxWidth(),minLines=4)
   Button(enabled=state.edited.isNotBlank(),onClick={clipboard.setText(AnnotatedString(state.edited));state.copied=true;recordEditedEvent(context,key,state.jobId,"manual_copy",result.optString("draft"),state.edited);state.feedback=if(guidanceOnly) "已复制指导，仅供内部参考" else "已复制编辑后的草稿，请自行核对和发送"},modifier=Modifier.fillMaxWidth()){Text(if(guidanceOnly) "复制指导" else "复制草稿")}
   if(state.resultInputs!=Pair(state.latest,state.original)) Text("输入已修改，当前结果对应上一次输入。",style=MaterialTheme.typography.bodySmall)
   state.pending?.let { pending ->
    Text("生成期间的编辑已保留，新建议可选择采用。")
    Text(pending.optString("draft"),style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={state.result=pending;state.jobId=pending.optString("_client_job_id");state.resultTaskId=state.pendingTaskId;state.copied=false;state.resultInputs=state.pendingInputs;state.edited=pending.optString("draft");state.revision++;state.pending=null;state.pendingInputs=null}){Text("采用新建议")}
   }
   val warnings=result.optJSONArray("warnings")
   if(warnings!=null) for(i in 0 until warnings.length()) Text("• "+warnings.optString(i),style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={details=!details}){Text(if(details) "收起生成详情" else "生成详情与记录范围")}
   if(details) {
    Text("生成时间（服务端）：${result.optString("generated_at")}；画像版本：${result.optString("profile_revision").take(8)}")
    val coverage=result.optJSONObject("coverage")
    Text(if(coverage?.optBoolean("chat_fetched")==false) "本次未读取聊天，仅使用原文及可选缓存画像" else "聊天范围（服务端时间）：${coverage?.visibleText("window_start")} 至 ${coverage?.visibleText("window_end")}；非完整历史")
   }
   TextButton(onClick={showInput=!showInput}){Text(if(showInput) "收起补充内容" else "查看或修改补充内容")}
  }
  if(state.result==null || showInput) {
   if(type=="润色") OutlinedTextField(state.original,{state.original=it.take(4000)},label={Text("待润色原文（必须填写）")},modifier=Modifier.fillMaxWidth())
   OutlinedTextField(state.latest,{state.latest=it.take(4000)},label={Text(if(guidanceOnly) "补充局面或问题（可选）" else "补充最新局面（可选）")},modifier=Modifier.fillMaxWidth())
  }
  Button(enabled=!busy && (type!="润色" || state.original.isNotBlank()),onClick={
   val started=android.os.SystemClock.elapsedRealtime();val target=key;val requestedInputs=Pair(state.latest,state.original);val requestedType=type;val requestedTaskId=state.taskId;val revision=state.revision;val epoch=gate.next()
   if(state.jobId.isNotBlank() && state.resultTaskId==state.taskId) recordEditedEvent(context,target,state.jobId,"regenerate",state.result?.optString("draft").orEmpty(),state.edited)
   job=scope.launch {
    busy=true;state.feedback="正在提交生成请求…"
    try {
     val body=BusinessApi.target(target).put("request_type",requestedType).put("latest_situation",requestedInputs.first).put("original_text",requestedInputs.second).put("request_id",java.util.UUID.randomUUID().toString()).put("experiment_task_id",requestedTaskId)
     state.runningJobId=""
     val result=awaitGeneration(context,target,"draft",body,onJob={state.runningJobId=it}){if(gate.accepts(epoch)) state.feedback=it}
     if(gate.accepts(epoch) && ClientRepository.currentBoundClient.value?.key==target){
      if(state.revision==revision){state.resultInputs=requestedInputs;state.result=result;state.edited=result.optString("draft");state.pending=null;state.jobId=result.optString("_client_job_id");state.resultTaskId=requestedTaskId;state.copied=false;recordGenerationEvent(context,target,state.jobId,"displayed",elapsed=android.os.SystemClock.elapsedRealtime()-started)}
      else {state.pending=result;state.pendingInputs=requestedInputs;state.pendingTaskId=requestedTaskId;state.pendingStarted=started;recordGenerationEvent(context,target,result.optString("_client_job_id"),"displayed",elapsed=android.os.SystemClock.elapsedRealtime()-started)}
      state.feedback=if(state.revision!=revision) "编辑已保留，可选择采用新建议" else if(guidanceOnly) "销售指导已生成，请结合实际情况核对" else "草稿已生成，请审阅、编辑后复制"
     }
    } catch(e:TimeoutCancellationException){if(gate.accepts(epoch)) state.feedback="等待超时，服务端可能仍在处理；可稍后重试"}
    catch(e:CancellationException){throw e}
    catch(e:Exception){if(gate.accepts(epoch)) state.feedback=businessFeedback(e)}
    finally{if(gate.accepts(epoch)) busy=false}
   }
  },modifier=Modifier.fillMaxWidth()){Text(if(busy) "生成中…" else if(state.result!=null) "重新生成" else "生成$type")}
  if(busy) {LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick={recordGenerationEvent(context,key,state.runningJobId,"wait_cancelled");gate.next();job?.cancel();busy=false;state.feedback="已停止等待，服务端任务可能仍会完成"}){Text("停止等待")}}
  if(state.feedback.isNotBlank()) Text(state.feedback,style=MaterialTheme.typography.bodySmall)
  TextButton(enabled=!busy,onClick={if(state.jobId.isNotBlank() && !state.copied) recordGenerationEvent(context,key,state.jobId,"panel_closed");drafts.drafts[key to type]=GenerationDraft()}) {Text("开始新任务")}
  Text(if(guidanceOnly) "内部参考；价格、政策和安排请以确认信息为准。" else "不会自动发送；价格、优惠、名额和安排仍需核实。",style=MaterialTheme.typography.bodySmall)
 }
}
