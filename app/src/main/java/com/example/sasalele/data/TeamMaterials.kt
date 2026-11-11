package com.example.sasalele.data

import android.content.Context
import android.util.AtomicFile
import com.example.sasalele.data.db.MaterialEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class MaterialAlias(val id:String,val title:String)
data class TeamMaterial(val id:String,val title:String,val type:String,val content:String,val sha:String,val size:Long,val aliases:List<MaterialAlias> = emptyList()) {
 fun json()=JSONObject().put("id",id).put("title",title).put("type",type).put("content",content).put("sha256",sha).put("size",size).put("aliases",JSONArray().apply{aliases.forEach{put(JSONObject().put("id",it.id).put("title",it.title))}})
 companion object {fun parse(j:JSONObject):TeamMaterial {
  val a=j.optJSONArray("aliases") ?: JSONArray()
  return TeamMaterial(j.getString("id"),j.getString("title"),j.getString("type"),j.optString("content"),j.optString("sha256"),j.optLong("size"),(0 until a.length()).map{val v=a.getJSONObject(it);MaterialAlias(v.getString("id"),v.getString("title"))})
 }}
 fun local(file:File?=null)=MaterialEntity(title=title,content=file?.absolutePath ?: content,type=type)
}

object TeamMaterials {
 private val http=OkHttpClient.Builder().connectTimeout(8,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()
 private suspend fun receive(request:Request):Response=suspendCancellableCoroutine {continuation->
  val call=http.newCall(request);continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback {
   override fun onFailure(call:Call,e:java.io.IOException){if(continuation.isActive)continuation.resumeWithException(BusinessFailure("物料网络请求失败，请检查网络后重试"))}
   override fun onResponse(call:Call,response:Response){continuation.resume(response,onCancellation={_,value,_->value.close()})}
  })
 }
 private fun root(context:Context):File {
  val origin=BusinessApi.connection(context).url.trimEnd('/')
  val key=hash(origin.toByteArray()).take(20)
  return File(context.noBackupFilesDir,"team-materials/$key").apply{mkdirs()}
 }
 private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 fun cached(context:Context):List<TeamMaterial> = runCatching {
  val rows=JSONArray(AtomicFile(File(root(context),"index.json")).readFully().toString(Charsets.UTF_8))
  (0 until rows.length()).map{TeamMaterial.parse(rows.getJSONObject(it))}
 }.getOrDefault(emptyList())
 suspend fun sync(context:Context):List<TeamMaterial> {
  MaterialNaming.flush(context)
  val result=mutableListOf<TeamMaterial>();var offset=0;var revision:String?=null
  while(true) {
   val j=BusinessApi.call(context,"materials?offset=$offset");val rows=j.getJSONArray("materials")
   val current=j.getString("revision")
   if(revision!=null && revision!=current)throw BusinessFailure("团队库正在变化，请重新刷新；原列表保留")
   revision=current
   for(i in 0 until rows.length())result.add(TeamMaterial.parse(rows.getJSONObject(i)))
   if(j.isNull("next_offset"))break
   val next=j.getInt("next_offset");check(next>offset && next<=5000);offset=next
  }
  val unique=result.distinctBy{it.id}
  withContext(Dispatchers.IO) {
   val file=AtomicFile(File(root(context),"index.json"));val out=file.startWrite()
   try {out.write(JSONArray().apply{unique.forEach{put(it.json())}}.toString().toByteArray());file.finishWrite(out)}
   catch(e:Exception){file.failWrite(out);throw e}
  }
  return unique
 }
 suspend fun upload(context:Context,material:MaterialEntity,requestId:String):TeamMaterial {
  if(material.type!="IMAGE") return TeamMaterial.parse(BusinessApi.call(context,"materials",JSONObject().put("title",material.title).put("content",material.content).put("request_id",requestId)))
  val prepared=MaterialImages.prepare(context,material.content)
  return withContext(Dispatchers.IO) {
   val bytes=try{context.contentResolver.openInputStream(prepared.uri)!!.use{it.readBytes()}}finally{context.contentResolver.delete(prepared.uri,null,null)}
   val c=BusinessApi.connection(context)
   val url=(c.url.trimEnd('/')+"/sales/materials/images").toHttpUrl().newBuilder().addQueryParameter("title",material.title).addQueryParameter("request_id",requestId).build()
   val request=Request.Builder().url(url).header("Authorization","Bearer "+c.accessCode).post(bytes.toRequestBody(prepared.mime.toMediaType())).build()
   receive(request).use {response->
    if(!response.isSuccessful) throw BusinessFailure(if(response.code==413) "图片超过上传限制" else "图片上传失败，请检查网络、授权或服务版本后重试")
    TeamMaterial.parse(JSONObject(response.body!!.string()))
   }
  }
 }
 suspend fun imageFile(context:Context,item:TeamMaterial):File=withContext(Dispatchers.IO) {
  require(item.sha.matches(Regex("[a-f0-9]{64}")) && item.size in 1..(25L*1024*1024))
  val file=File(root(context),item.sha)
  if(file.isFile && file.length()==item.size && hash(file.readBytes())==item.sha)return@withContext file
  val c=BusinessApi.connection(context)
  val request=Request.Builder().url(c.url.trimEnd('/')+"/sales/materials/"+item.id+"/image").header("Authorization","Bearer "+c.accessCode).build()
  receive(request).use {response->
   if(!response.isSuccessful)throw BusinessFailure("图片读取失败，素材可能已下架或授权已失效")
   val temp=File(root(context),java.util.UUID.randomUUID().toString()+".tmp")
   try {
    response.body!!.byteStream().use{input->temp.outputStream().use{out->val buffer=ByteArray(8192);var count=0L;while(true){val n=input.read(buffer);if(n<0)break;count+=n;require(count<=item.size);out.write(buffer,0,n)}}}
    check(temp.length()==item.size && hash(temp.readBytes())==item.sha){"图片校验失败"}
    check(temp.renameTo(file)){"图片保存失败"}
    // Bound only the download cache; never remove the index or local materials.
    var remaining=root(context).listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{64}"))}.sumOf{it.length()}
    root(context).listFiles().orEmpty().filter{it.name.matches(Regex("[a-f0-9]{64}")) && it.name!=file.name}.sortedBy{it.lastModified()}.forEach{old->
     if(remaining>150L*1024*1024){val length=old.length();if(old.delete())remaining-=length}
    }
   }finally{temp.delete()}
  }
  file
 }
 fun favorite(context:Context,id:String)=context.getSharedPreferences("team-material-favorites",0).getBoolean(id,false)
 fun favorite(context:Context,id:String,value:Boolean){context.getSharedPreferences("team-material-favorites",0).edit().putBoolean(id,value).apply()}
}
