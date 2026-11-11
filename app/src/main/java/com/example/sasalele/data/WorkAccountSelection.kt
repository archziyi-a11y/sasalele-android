package com.example.sasalele.data

import android.content.Context
import android.util.AtomicFile
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

data class SelectedWorkAccount(val id:String,val name:String)

object WorkAccountSelection {
 private val state=MutableStateFlow<SelectedWorkAccount?>(null)
 val current=state.asStateFlow()
 private var initializedPath:String?=null
 private fun file(context:Context)=File(context.noBackupFilesDir,"selected-work-account.json")
 @Synchronized fun initialize(context:Context) {
  val path=file(context)
  if(initializedPath==path.absolutePath) return
  state.value=runCatching {
   val row=JSONObject(AtomicFile(path).readFully().toString(Charsets.UTF_8))
   SelectedWorkAccount(row.getString("id"),row.getString("name")).also {require(it.id.isNotBlank())}
  }.getOrNull()
  initializedPath=path.absolutePath
  if(ClientRepository.currentBoundClient.value?.workAccountId!=state.value?.id) ClientRepository.unbindClient()
 }
 suspend fun select(context:Context,account:SelectedWorkAccount?) {
  initialize(context)
  require(account==null || account.id.isNotBlank())
  if(state.value?.id!=account?.id) {
   ClientRepository.unbindClient();ClientRepository.viewProfile(null)
   SettingsDataStore(context).setBoundClientJson(null)
  }
  val atomic=AtomicFile(file(context))
  if(account==null) atomic.delete() else {
   val stream=atomic.startWrite()
   try {stream.write(JSONObject().put("id",account.id).put("name",account.name).toString().toByteArray());atomic.finishWrite(stream)}
   catch(e:Exception){atomic.failWrite(stream);throw e}
  }
  state.value=account
  if(ClientRepository.currentBoundClient.value?.workAccountId!=account?.id) {
   ClientRepository.unbindClient();ClientRepository.viewProfile(null)
  }
 }
 fun allows(accountId:String)=state.value?.id==accountId
}
