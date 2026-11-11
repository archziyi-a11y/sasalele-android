package com.example.sasalele
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class BusinessLanTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 @Test fun actualPrivateLanHealth():Unit=runBlocking {withTimeout(30000) {val result=BusinessApi.call(context,"health");assertTrue(result.optBoolean("read_only"));assertTrue(result.optInt("accounts")>0)}}
 @Test fun actualSearchConfirmAndContext():Unit=runBlocking {withTimeout(60000) {
  val fixture=JSONObject(File(context.noBackupFilesDir,"lan-test-target.json").readText())
  val search=BusinessApi.call(context,"search",JSONObject().put("q",fixture.getString("query")))
  assertTrue(search.getJSONArray("candidates").length()>0)
  val key=ClientKey(fixture.getString("account_id"),fixture.getString("friend_id"))
  val confirmation=BusinessApi.call(context,"confirm",BusinessApi.target(key));val client=BusinessApi.client(confirmation.getJSONObject("identity"));assertTrue(client.key==key)
  val response=BusinessApi.call(context,"context",BusinessApi.target(key).put("request_type","帮我回").put("latest_situation","只读测试，未调用模型"))
  assertTrue(response.getJSONObject("target").getString("account_id")==key.workAccountId);assertTrue(response.getJSONObject("target").getString("friend_id")==key.clientId)
  assertFalse(response.getJSONObject("trust_boundary").optBoolean("model_called"));assertFalse(response.getJSONObject("coverage").optBoolean("complete_history"))
 }}
 @Test fun networkFailureFeedback():Unit=runBlocking {withTimeout(12000) {
  val folder=File(context.cacheDir,"connection-test-${System.nanoTime()}").apply {mkdirs()};val isolated=object:ContextWrapper(context) {override fun getNoBackupFilesDir()=folder}
  try {BusinessApi.save(isolated,BusinessConnection("http://192.168.70.43:1","synthetic-token-1234567890"));val error=runCatching {BusinessApi.call(isolated,"health")}.exceptionOrNull();assertNotNull(error);assertTrue(error!!.message.orEmpty().contains("网络连接失败"))}finally {folder.deleteRecursively()}
 }}
}
