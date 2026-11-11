package com.example.sasalele

import android.content.Context
import com.example.sasalele.BuildConfig
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import coil.compose.AsyncImage
import com.example.sasalele.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.drop
import org.json.JSONObject
private fun JSONObject.visible(key:String,fallback:String="未知")=optString(key).takeIf {it.isNotBlank() && it!="null"} ?: fallback
private fun JSONObject.profileState()=when(optString("status")){"available"->"已有历史摘要";"missing"->"摘要缺失";"unavailable"->"画像服务暂不可用";"conflict"->"历史记录冲突";else->"状态不确定"}

private fun JSONObject.hasSummary()=optString("summary").let { it.isNotBlank() && it!="null" }
private fun JSONObject.freshnessText()=if(!hasSummary()) "时效不适用（无可用摘要）" else if(optBoolean("stale")) "陈旧或时间未知" else "时间范围内"

@Composable
fun BusinessConnectionPanel(context:Context) {
 LaunchedEffect(Unit) { CaptureSession.initialize(context) }
 val deviceRevision by WorkDeviceConfig.updates.collectAsState()
 var feedback by remember {mutableStateOf("")};var diagnostic by remember {mutableStateOf(false)};val scope=rememberCoroutineScope()
 Column {
  Text("工作机业务服务")
  val captureStatus by CaptureSession.state.collectAsState()
  if(captureStatus.message.isNotBlank()) Text("最近截图状态："+captureStatus.message)
  TextButton(onClick={
   val clipboard=context.getSystemService(android.content.ClipboardManager::class.java)
   clipboard.setPrimaryClip(android.content.ClipData.newPlainText("截图诊断",CaptureSession.diagnosticText(context)))
   Toast.makeText(context,"已复制截图阶段记录，不含图片或聊天内容",Toast.LENGTH_SHORT).show()
  }) { Text("复制截图诊断") }
  Text("正常WiFi或移动网络均可使用。由管理员发机时配置一次，销售无需连接电脑或登录。")
  Text(if(remember(deviceRevision,feedback){BusinessApi.isConfigured(context)}) "工作机配置已保存，请检查服务状态" else if(BuildConfig.SALES_SERVICE_URL.isBlank()) "此构建尚未配置正式服务，不能作为上线验收通过" else "尚未完成管理员发机配置")
  Button(onClick={scope.launch {feedback="正在检查业务服务…";try {val result=BusinessApi.call(context,"health");feedback="业务服务可用：${result.optInt("accounts")}个授权工作号；工作机授权有效至${result.optString("access_expires_at")}"}catch(e:CancellationException){throw e}catch(e:Exception){feedback=businessFeedback(e)}}}){Text("检查服务／重试")}
  Text(feedback)
  TextButton(onClick={AcquisitionUi.open(context,ProvisionWorkDeviceActivity::class.java)}){Text("管理员发机配置")}
  if(BuildConfig.DEBUG) {
   TextButton(onClick={diagnostic=!diagnostic}){Text("开发诊断（非日常使用）")}
   if(diagnostic){LanDiagnosticConnectionPanel(context);TextButton(onClick={BusinessApi.enableLanDiagnostics(context,false);feedback="已退出局域网诊断模式"}){Text("退出局域网诊断，使用正式服务")}}
  }
 }
}

@Composable
fun LanDiagnosticConnectionPanel(context:Context) {
 val saved=remember { BusinessApi.load(context) };var url by remember { mutableStateOf(saved.url) };var code by remember { mutableStateOf(saved.accessCode) }
 var feedback by remember { mutableStateOf("") }; val scope=rememberCoroutineScope()
 Column {
  Text("局域网只读服务连接")
  Text("只允许已授权测试设备访问PC上的只读业务服务；访问码覆盖服务可见的全部工作号，8小时有效。方舟/飞书凭据留在PC")
  OutlinedTextField(url,{url=it},label={Text("PC局域网地址，例如 http://192.168.1.20:8876")})
  OutlinedTextField(code,{code=it},label={Text("PC提供的短期访问码")},visualTransformation=PasswordVisualTransformation())
  Button(onClick={ scope.launch { feedback="正在检查连接…";try { BusinessApi.save(context,BusinessConnection(url.trim(),code.trim())); val health=BusinessApi.call(context,"health");feedback="连接成功：${health.optInt("accounts")}个已授权工作号，服务只读；授权至${health.optString("access_expires_at")}" } catch(e:Exception) { if(e is CancellationException) throw e;feedback=businessFeedback(e) } } }) { Text("保存并测试连接") }
  Text(feedback)
 }
}

internal fun headerNameChoices(header:HeaderRecognition?):List<String> {
 fun clean(text:String)=text.trim().replace(Regex("^[<‹〈←]+\\s*\\d*\\s*"), "").replace(Regex("[>›〉→]+$"), "").trim()
 fun valid(text:String)=text.isNotBlank() && text.any { it.isLetter() } && text !in listOf("微信","WeChat","Weixin","5G","4G","LTE","HD")
 val all=mutableListOf<String>()
 val title=clean(header?.title.orEmpty())
 if(valid(title)) all.add(title)
 header?.lines.orEmpty().forEach { raw ->
  val full=clean(raw)
  if(valid(full)) all.add(full)
  full.split(Regex("\\s+|[|｜/／]+" )).map(::clean).filter(::valid).forEach { all.add(it) }
  Regex("[A-Za-z]{2,}").findAll(full).map { it.value }.filter(::valid).forEach { all.add(it) }
 }
 return all.distinct()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BusinessSearchPanel(context:Context,uri:Uri?,header:HeaderRecognition?,error:String?,onBound:()->Unit,onRead:(HeaderCrop)->Unit) {
 val deviceRevision by WorkDeviceConfig.updates.collectAsState()
 var maintenance by remember { mutableStateOf(false) }
 var accountPanel by remember { mutableStateOf(false) }
 var menu by remember { mutableStateOf(false) }
 if(maintenance) { RealRecognitionPanel(context,uri,header,error,{maintenance=false},onRead);return }
 if(accountPanel) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {TextButton(onClick={accountPanel=false}) {Text("返回客户查找")};WorkAccountBindingPanel(context)};return }
 var name by remember(uri) { mutableStateOf("") };var nameEdited by remember(uri) {mutableStateOf(false)}
 var editing by remember(uri) {mutableStateOf(false)}
 val focus=LocalFocusManager.current
 LaunchedEffect(uri,header) {if(!nameEdited) name=header?.title.orEmpty()}
 val selectedAccount by WorkAccountSelection.current.collectAsState()
 val account=selectedAccount?.id;var page by remember(uri,name,account) { mutableIntStateOf(1) };var refresh by remember { mutableIntStateOf(0) }
 var result by remember(uri,name,account,page) { mutableStateOf<JSONObject?>(null) };var feedback by remember(uri,name,account) { mutableStateOf("") };var loading by remember { mutableStateOf(false) }
 val scope=rememberCoroutineScope()
 val gate=remember { RequestEpoch() };var confirmBusy by remember { mutableStateOf(false) }
 DisposableEffect(uri) { onDispose { gate.next();BusinessUi.detailOpened.value=false } }
 LaunchedEffect(uri,header,name,account,page,refresh,deviceRevision) {
  val epoch=gate.next();result=null;loading=false
  if(header?.rejected==true || name.isBlank() || account==null) return@LaunchedEffect
  if(!BusinessApi.isConfigured(context)) {feedback="工作机尚未准备好，管理员需完成发机配置并检查服务；没有发起业务查询";return@LaunchedEffect}
  loading=true;feedback="正在查询真实业务客户…"
  delay(350)
  try {
   val body=JSONObject().put("q",name).put("page",page).put("page_size",50).put("refresh",refresh>0)
   account?.let { body.put("account_id",it) }
   val response=BusinessApi.call(context,"search",body)
   if(gate.accepts(epoch)) {result=response;feedback=if(response.optString("status")=="degraded") "暂时显示已缓存客户，可稍后刷新" else ""}
  } catch(e:CancellationException) {throw e}
  catch(e:Exception) { if(gate.accepts(epoch)) feedback=businessFeedback(e) }
  finally { if(gate.accepts(epoch)) loading=false }
 }
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
   Text("查找客户",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
   Box { IconButton(onClick={menu=true}) {Icon(Icons.Default.MoreVert,contentDescription="更多")}
    DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
     DropdownMenuItem(text={Text("当前工作号："+(selectedAccount?.name ?: "尚未选择"))},onClick={menu=false;accountPanel=true})
     DropdownMenuItem(text={Text("重新截图")},onClick={menu=false;CaptureSession.request(context)})
     DropdownMenuItem(text={Text("选择已有图片")},onClick={menu=false;AcquisitionUi.open(context,PickRecognitionImageActivity::class.java)})
     DropdownMenuItem(text={Text("诊断／维护")},onClick={menu=false;maintenance=true})
    }
   }
  }
  Text("核对聊天名称，选择对应客户查看画像。",style=MaterialTheme.typography.bodySmall)
  if(error!=null) Text(error)
  if(uri!=null && header==null && error==null) Text("正在识别聊天标题…")
  if(header?.rejected==true) {Text("检测到群聊、公众号或列表，已跳过客户搜索。");TextButton(onClick={maintenance=true}) {Text("诊断／维护")};return@Column}
  if(uri!=null && header!=null && header.height>0 && header.cropBottom>header.cropTop) HeaderCropPreview(context,uri,header)
  val choices=remember(header) {headerNameChoices(header)}
  if(header!=null && name.isBlank()) Text(if(choices.isNotEmpty()) "请选择识别到的名称，或手动编辑。" else "未能可靠定位聊天名称，请手动填写。")
  if(choices.isNotEmpty()) {
   Text("识别到的名称",style=MaterialTheme.typography.labelMedium)
   androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
    choices.forEach { choice -> FilterChip(selected=name==choice,onClick={nameEdited=true;name=choice;editing=false;focus.clearFocus()},label={Text(choice)}) }
   }
  }
  Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
   if(editing) OutlinedTextField(name,{nameEdited=true;name=it.take(120)},label={Text("姓名／备注")},modifier=Modifier.weight(1f),singleLine=true)
   else Text(name.ifBlank { "填写客户姓名或备注" },Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
   if(editing) TextButton(onClick={editing=false;focus.clearFocus()}) {Text("完成")} else IconButton(onClick={editing=true}) {Icon(Icons.Default.Edit,contentDescription="编辑名称")}
  }
  if(loading || confirmBusy) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
  if(feedback.isNotBlank()) Text(feedback,style=MaterialTheme.typography.bodySmall)
  TextButton(onClick={refresh++}) {Text("刷新／重试")}
  val coverage=result?.optJSONObject("coverage")
  val candidates=result?.optJSONArray("candidates")
  if(candidates!=null && candidates.length()==0) Text("没有找到匹配客户。请调整名称或核对工作号。")
  if(candidates!=null) for(i in 0 until candidates.length()) {
   val candidate=candidates.getJSONObject(i)
   Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
     val avatar=candidate.optString("avatar");if(avatar.isNotBlank() && avatar!="null") AsyncImage(model=avatar,contentDescription="客户头像",modifier=Modifier.size(48.dp))
     Column {Text(candidate.optString("name"),style=MaterialTheme.typography.titleMedium)
      listOf("remark" to "备注","nickname" to "昵称").forEach { (field,label)->candidate.optString(field).takeIf {it.isNotBlank() && it!="null" && it!=candidate.optString("name")}?.let {Text("$label：$it",style=MaterialTheme.typography.bodySmall)} }
     }
    }
    val profile=candidate.optJSONObject("profile_hint");Text(profile?.optString("summary")?.takeIf {it!="null" && it.isNotBlank()}?.take(120) ?: "暂无可用画像摘要",style=MaterialTheme.typography.bodySmall)
    Button(enabled=!confirmBusy,onClick={scope.launch {
     confirmBusy=true;feedback="正在核对客户…"
     val key=ClientKey(candidate.getString("account_id"),candidate.getString("friend_id"));val requestedName=name;val epoch=gate.next()
     try {val response=BusinessApi.call(context,"confirm",BusinessApi.target(key).put("recognized_name",requestedName));val client=BusinessApi.client(response.getJSONObject("identity"));check(client.key==key)
      if(gate.accepts(epoch) && name==requestedName && WorkAccountSelection.current.value?.id==key.workAccountId){ClientRepository.bindClient(client);onBound()}
     } catch(e:CancellationException){throw e} catch(e:Exception){if(gate.accepts(epoch)) feedback=businessFeedback(e)} finally {confirmBusy=false}
    }}) {Text(if(confirmBusy) "正在核对…" else "查看画像")}
   }}
  }
  if(coverage?.optBoolean("has_more")==true) TextButton(onClick={page++}) {Text("下一页候选")}
  if(page>1) TextButton(onClick={page--}) {Text("上一页")}
 }
}

@Composable
fun BusinessProfilePanel(context:Context,onSearch:()->Unit,onGuidance:()->Unit={}) {
 var busy by remember {mutableStateOf(false)}
 val client by ClientRepository.currentBoundClient.collectAsState();var data by remember(client?.key) {mutableStateOf<JSONObject?>(null)};var message by remember(client?.key){mutableStateOf("")};var refresh by remember(client?.key){mutableIntStateOf(0)};var reload by remember(client?.key){mutableIntStateOf(0)}
 LaunchedEffect(client?.key,refresh,reload) {
  val key=client?.key ?: return@LaunchedEffect
  busy=true
  message="读取资料中…"
  try {
   if(refresh==0 || data==null) {
    val result=BusinessApi.call(context,"detail",BusinessApi.target(key,false))
    if(ClientRepository.currentBoundClient.value?.key==key) {data=result;message=""}
   } else {
    val previous=data ?: return@LaunchedEffect
    val generated=awaitGeneration(context,key,"profile/ensure",BusinessApi.target(key)){message=it}
    if(ClientRepository.currentBoundClient.value?.key==key) {data=JSONObject(previous.toString()).put("profile",generated.getJSONObject("profile"));message="画像已更新"}
   }
  }
  catch(e:TimeoutCancellationException){message="等待画像超时，已有资料保留；可稍后重试"}
  catch(e:CancellationException){throw e} catch(e:Exception){message=businessFeedback(e)}
  finally{busy=false}
 }
 var identityDetails by remember(client?.key){mutableStateOf(false)}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
   client?.avatarUrl?.takeIf {it.isNotBlank()}?.let { AsyncImage(model=it,contentDescription="客户头像",modifier=Modifier.size(48.dp)) }
   Column {Text(client?.name ?: "尚未确认客户",style=MaterialTheme.typography.titleLarge)
    data?.optJSONObject("identity")?.optString("nickname")?.takeIf {it.isNotBlank() && it!="null" && it!=client?.name}?.let {Text("昵称：$it",style=MaterialTheme.typography.bodySmall)}
   }
  }
  if(client==null) {Text("识别当前聊天，选择对应客户后查看画像。");Button(onClick=onSearch){Text("识别当前客户")};return@Column}
  if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
  if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
  data?.let {
   val profile=it.getJSONObject("profile")
   when(profile.optString("status")) {
    "missing" -> Text("还没有画像，可根据聊天记录生成。")
    "unavailable", "conflict" -> {Text("画像暂时无法读取，请重试。");TextButton(enabled=!busy,onClick={refresh=0;reload++}) {Text("重新加载")}}
    else -> ProfilePresentation(profile)
   }
  }
  if(data==null && !busy) Button(onClick={refresh=0;reload++},modifier=Modifier.fillMaxWidth()) {Text("重新加载")}
  if(data?.optJSONObject("profile")?.optString("status") in listOf("available","missing")) Button(enabled=!busy,onClick={refresh++},modifier=Modifier.fillMaxWidth()) {Text(if(busy) "生成中…" else if(data?.optJSONObject("profile")?.hasSummary()==true) "更新画像" else "生成画像")}
  Button(onClick=onGuidance,modifier=Modifier.fillMaxWidth()) {Text("查看销售指导")}
  Row {TextButton(onClick=onSearch){Text("切换客户")};TextButton(onClick={identityDetails=!identityDetails}){Text(if(identityDetails) "收起更多" else "更多")}}
  if(identityDetails) {
   TextButton(onClick={ClientRepository.unbindClient()}) {Text("解除绑定")}
  }
 }
}

@Composable
private fun HeaderCropPreview(context:Context,uri:Uri,header:HeaderRecognition) {
 val image by produceState<android.graphics.Bitmap?>(null,uri,header.cropTop,header.cropBottom) {
  var owned:android.graphics.Bitmap?=null
  try {
   withContext(Dispatchers.IO) {
    val options=android.graphics.BitmapFactory.Options().apply { inSampleSize=2 }
    val source=context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it,null,options) }
    if(source!=null) {
     try {
      val top=(header.cropTop.toFloat()/header.height*source.height).toInt().coerceIn(0,source.height-1)
      val bottom=(header.cropBottom.toFloat()/header.height*source.height).toInt().coerceIn(top+1,source.height)
      owned=android.graphics.Bitmap.createBitmap(source,0,top,source.width,bottom-top)
     } finally {if(owned !== source) source.recycle()}
    }
   }
   value=owned
   // Compose may still draw the preceding frame after disposal; let GC release the displayed bitmap.
   awaitDispose { owned=null }
  } finally {owned=null}
 }
 image?.let {Image(it.asImageBitmap(),contentDescription="识别的聊天标题区域",modifier=Modifier.fillMaxWidth().heightIn(max=100.dp),contentScale=ContentScale.Fit)}
}
