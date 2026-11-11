package com.example.sasalele.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedWorkAccounts(val rows:JSONArray,val fresh:Boolean)

object WorkAccountDirectoryCache {
 private const val TTL=24*60*60*1000L
 private fun file(context:Context)=AtomicFile(File(context.noBackupFilesDir,"work-account-directory.json"))
 fun load(context:Context,now:Long=System.currentTimeMillis()):CachedWorkAccounts?=runCatching {
  val data=JSONObject(file(context).readFully().toString(Charsets.UTF_8))
  val age=now-data.getLong("fetched_at_ms")
  CachedWorkAccounts(data.getJSONArray("accounts"),age in 0 until TTL)
 }.getOrNull()
 fun save(context:Context,rows:JSONArray,now:Long=System.currentTimeMillis()) {
  val target=file(context);val output=target.startWrite()
  try {
   output.write(JSONObject().put("accounts",rows).put("fetched_at_ms",now).toString().toByteArray(Charsets.UTF_8))
   target.finishWrite(output)
  } catch(e:Exception){target.failWrite(output);throw e}
 }
}
