package com.example.sasalele.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class FollowupContact(val accountId:String,val friendId:String,val name:String,val messageId:String,val time:Long,val status:String)
data class FollowupView(val accountId:String="",val contacts:List<FollowupContact> = emptyList(),val observedAt:Long=0,val busy:Boolean=false,val feedback:String="",val ignoredContacts:List<FollowupContact> = emptyList())
object FollowupRepository {
 private val mutable=MutableStateFlow(FollowupView())
 val state=mutable.asStateFlow()
 private var job:Job?=null
 private val mutex=Mutex()
 private fun cache(context:Context,account:String)=AtomicFile(File(context.noBackupFilesDir,"followups-"+java.security.MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString(""){"%02x".format(it)}+".json"))
 private fun load(context:Context,account:String)=runCatching{JSONObject(cache(context,account).readFully().toString(Charsets.UTF_8))}.getOrNull()
 private fun view(account:String,data:JSONObject,feedback:String=""):FollowupView {
  require(data.getString("account_id")==account)
  fun parse(rows:org.json.JSONArray)=(0 until rows.length()).map {i->val r=rows.getJSONObject(i)
   require(r.getString("account_id")==account)
   FollowupContact(account,r.getString("friend_id"),r.getString("display_name"),r.getString("last_message_id"),r.getLong("last_message_at_ms"),r.getString("status"))}
  val contacts=parse(data.getJSONArray("contacts"))
  val ignored=parse(data.optJSONArray("ignored_contacts") ?: org.json.JSONArray())
  return FollowupView(account,contacts,data.optLong("observed_at_ms"),data.optBoolean("syncing"),feedback,ignored)

 }
 fun start(context:Context,scope:CoroutineScope) {
  if(job?.isActive==true)return
  job=scope.launch {
   WorkAccountSelection.current.collectLatest {account->
    mutable.value=FollowupView(account?.id.orEmpty())
    if(account!=null) {
     load(context,account.id)?.let{runCatching{mutable.value=view(account.id,it,"显示上次同步列表")}}
     while(isActive){refresh(context);delay(if(mutable.value.busy) 3000 else 60000)}
    }
   }
  }
 }
 fun stop(){job?.cancel();job=null;mutable.value=FollowupView()}
 suspend fun refresh(context:Context,force:Boolean=false)=mutex.withLock {
  val account=WorkAccountSelection.current.value?.id ?: return@withLock
  try {
   val previous=load(context,account)
   val result=BusinessApi.call(context,"followups?account_id="+android.net.Uri.encode(account)+"&refresh="+force)
   if(WorkAccountSelection.current.value?.id!=account)return@withLock
   val parsed=view(account,result,if(!result.isNull("error")) result.optString("error") else if(result.optBoolean("syncing")) "正在同步，已有列表仍可查看" else "已同步")
   if(parsed.observedAt>0 && previous!=null && previous.optLong("observed_at_ms")>0 && parsed.observedAt>previous.optLong("observed_at_ms")) {
    val old=view(account,previous).contacts.associateBy{it.friendId}
    parsed.contacts.filter{it.status=="needs_review" && old[it.friendId]?.messageId!=it.messageId}.forEach {
     ReminderScheduler.onNewMessage(context,MessageEvent(it.messageId,account,it.friendId,"",it.time))
    }
   }
   val previousRows=previous?.let{view(account,it).contacts.associateBy{row->row.friendId}} ?: emptyMap()
   (parsed.contacts+parsed.ignoredContacts).distinctBy{it.friendId}.filter{it.status!="needs_review" && (it.status!="later" || previousRows[it.friendId]?.status!="later")}.forEach {
    ReminderScheduler.applyAction(context,account,it.friendId,if(it.status=="later") ReminderAction.LATER else ReminderAction.REPLIED)
   }
   val file=cache(context,account);val output=file.startWrite()
   try{output.write(result.toString().toByteArray(Charsets.UTF_8));file.finishWrite(output)}catch(e:Exception){file.failWrite(output);throw e}
   mutable.value=parsed
  } catch(e:CancellationException){throw e}
    catch(e:Exception){if(WorkAccountSelection.current.value?.id==account) mutable.value=mutable.value.copy(accountId=account,busy=false,feedback=businessFeedback(e)+"；保留已有列表")}
 }
 suspend fun act(context:Context,row:FollowupContact,action:String) {
  if(WorkAccountSelection.current.value?.id!=row.accountId)return
  try {
   BusinessApi.call(context,"followups/action",JSONObject().put("account_id",row.accountId).put("friend_id",row.friendId).put("message_id",row.messageId).put("action",action))
   refresh(context)
  } catch(e:CancellationException){throw e}
    catch(e:Exception){if(WorkAccountSelection.current.value?.id==row.accountId)mutable.value=mutable.value.copy(feedback=businessFeedback(e))}
 }
}
