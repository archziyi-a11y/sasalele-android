package com.example.sasalele.data

import android.content.Context
import com.example.sasalele.data.db.*
import com.example.sasalele.data.repository.MaterialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object MaterialNaming {
 private val mutex=Mutex()
 fun normalize(title:String)=title.trim().replace(Regex("\\s+")," ")
 private fun namespace(context:Context)=MessageDigest.getInstance("SHA-256").digest(BusinessApi.connection(context).url.trimEnd('/').toByteArray()).joinToString(""){"%02x".format(it)}.take(20)+"|"
 fun source(context:Context,id:String)=namespace(context)+id
 fun repository(context:Context)=MaterialRepository(AppDatabase.getDatabase(context).materialDao())
 suspend fun pending(context:Context,repo:MaterialRepository=repository(context))=repo.pendingNaming(namespace(context)).size
 suspend fun rejected(context:Context,repo:MaterialRepository=repository(context))=repo.rejectedNaming(namespace(context))
 private fun account(context:Context):String? {
  WorkAccountSelection.initialize(context)
  return WorkAccountSelection.current.value?.id
 }
 private fun titleEvent(context:Context,id:String,title:String,origin:String,aliasId:String?):MaterialNamingEvent? {
  val account=account(context) ?: return null
  val body=JSONObject().put("title",title).put("work_account_id",account).put("origin",origin)
   .put("candidate_id",aliasId ?: JSONObject.NULL).put("request_id",UUID.randomUUID().toString())
  return MaterialNamingEvent(source=source(context,id),path="materials/$id/aliases",body=body.toString())
 }
 suspend fun flush(context:Context,repo:MaterialRepository=repository(context))=withContext(Dispatchers.IO) {
  mutex.withLock {
   val rows=repo.pendingNaming(namespace(context))
   if(rows.isEmpty())return@withLock
   try{BusinessApi.call(context,"accounts")}
   catch(e:CancellationException){throw e}catch(_:Exception){return@withLock}
   val blocked=mutableSetOf<String>();var attempts=0
   for(row in rows) {
    val body=JSONObject(row.body)
    val group=body.optString("work_account_id")+":"+row.source
    if(group in blocked || attempts>=20)continue
    attempts++
    try{BusinessApi.call(context,row.path,body);repo.removeNaming(row.id)}
    catch(e:CancellationException){throw e}
    catch(e:Exception){
     val permanent=e is BusinessFailure && e.diagnosticCode in setOf("material_not_found","material_alias_stale","invalid_material","invalid_material_alias","invalid_material_event","material_alias_limit")
     if(permanent)repo.rejectNaming(row.id) else blocked.add(group)
    }
   }
  }
 }
 private suspend fun digest(file:File)=withContext(Dispatchers.IO) {
  val hash=MessageDigest.getInstance("SHA-256")
  file.inputStream().use{input->val bytes=ByteArray(8192);while(true){val n=input.read(bytes);if(n<0)break;hash.update(bytes,0,n)}}
  hash.digest().joinToString(""){"%02x".format(it)}
 }
 suspend fun legacyCandidates(item:TeamMaterial,repo:MaterialRepository):List<MaterialEntity> = repo.unlinked().filter{record->
  if(item.type=="IMAGE")record.type=="IMAGE" && try{digest(File(record.content))==item.sha}catch(e:CancellationException){throw e}catch(_:Exception){false}
  else record.type!="IMAGE" && record.content==item.content
 }
 suspend fun saveLocal(context:Context,item:TeamMaterial,title:String,alias:MaterialAlias?,origin:String,repo:MaterialRepository=repository(context),legacy:MaterialEntity?=null,recordEvidence:Boolean=true):String {
  val clean=normalize(title);require(clean.isNotEmpty() && clean.length<=120)
  val key=source(context,item.id)
  val old=repo.byTeamSource(key)
  // Content equality only offers a choice; never silently merges an unrelated personal record.
  if(old==null && legacy!=null)require(legacyCandidates(item,repo).any{it.id==legacy.id})
  val record=old ?: legacy
  val content=if(record!=null && (item.type!="IMAGE" || File(record.content).isFile))record.content else if(item.type=="IMAGE")withContext(Dispatchers.IO) {
   val downloaded=TeamMaterials.imageFile(context,item)
   val dir=File(context.filesDir,"materials").apply{mkdirs()}
   val target=File(dir,"team-${namespace(context).dropLast(1)}-${item.id}")
   downloaded.copyTo(target,overwrite=true);target.absolutePath
  } else item.content
  val event=if(recordEvidence)titleEvent(context,item.id,clean,origin,alias?.id) else null
  val material=MaterialEntity(id=record?.id ?: 0,title=clean,content=content,type=item.type,isFavorite=record?.isFavorite ?: false,
   teamSourceId=key,teamAliasId=alias?.id,titleOrigin=if(recordEvidence)origin else record?.titleOrigin)
  // The local copy and its evidence enter SQLite in one transaction; a process stop cannot split them.
  repo.saveNamed(material,event)
  return if(recordEvidence && event==null)"已保存本机；未选择工作号，本次不参与标题统计" else "已保存本机；团队公共标题保持不变"
 }
 suspend fun renameLocal(context:Context,material:MaterialEntity,title:String,repo:MaterialRepository):String {
  val clean=normalize(title);require(clean.isNotEmpty() && clean.length<=120)
  if(clean==normalize(material.title))return "本机标题未变化"
  val renamed=material.copy(title=clean,teamAliasId=null,titleOrigin=if(material.teamSourceId!=null)"explicit" else null)
  val key=material.teamSourceId
  if(key==null || !key.startsWith(runCatching{namespace(context)}.getOrDefault("!")))repo.update(renamed)
  else repo.saveNamed(renamed,titleEvent(context,key.substringAfter('|'),clean,"explicit",null))
  return "本机标题已保存；团队公共标题保持不变"
 }
 suspend fun usedAfterAliasSearch(context:Context,item:TeamMaterial,alias:MaterialAlias?,event:String,repo:MaterialRepository=repository(context)) {
  if(alias==null)return
  val account=account(context) ?: return
  repo.addNaming(MaterialNamingEvent(source=source(context,item.id),path="materials/${item.id}/events",
   body=JSONObject().put("alias_id",alias.id).put("work_account_id",account).put("event",event).toString()))
 }
 suspend fun linkUploaded(context:Context,local:MaterialEntity,team:TeamMaterial) {
  if(local.id==0)return
  val repo=repository(context)
  val original=repo.byId(local.id) ?: return
  if(repo.byTeamSource(source(context,team.id))==null)repo.update(original.copy(teamSourceId=source(context,team.id),titleOrigin="upload"))
 }
}
