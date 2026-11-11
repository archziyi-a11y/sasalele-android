package com.example.sasalele
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
@RunWith(AndroidJUnit4::class)
class BusinessNetworkStateTest {
 private suspend fun isolated(block:suspend(Context)->Unit) {
  val base=ApplicationProvider.getApplicationContext<Context>();val folder=File(base.cacheDir,"network-case-${System.nanoTime()}").apply{mkdirs()}
  val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
  val old=BusinessApi.lanDiagnosticsEnabled(base)
  try {BusinessApi.save(context,BusinessConnection("http://10.0.2.2:18876","synthetic-test-credential-123456"));block(context)}finally{BusinessApi.enableLanDiagnostics(base,old);folder.deleteRecursively()}
 }
 @Test fun authorizationAndUpstreamFailuresDistinct():Unit=runBlocking {withTimeout(20000){isolated {context->
  for((query,kind,word) in listOf(Triple("revoked",FailureKind.AUTHORIZATION,"撤销"),Triple("expired",FailureKind.AUTHORIZATION,"过期"),Triple("busy",FailureKind.BUSY,"正忙"),Triple("unavailable",FailureKind.UPSTREAM,"上游"))) {
   val failure=runCatching{BusinessApi.call(context,"search",JSONObject().put("q",query))}.exceptionOrNull() as? BusinessFailure
   assertNotNull(failure);assertEquals(kind,failure!!.kind);assertTrue(failure.message.orEmpty().contains(word));assertEquals("synthetic-request",failure.requestId)
  }
 }}}
 @Test fun canceledOldRequestThenNewResult():Unit=runBlocking {withTimeout(10000){isolated{context->
  val old=async {BusinessApi.call(context,"search",JSONObject().put("q","slow"))};delay(150);old.cancelAndJoin();assertTrue(old.isCancelled)
  val new=BusinessApi.call(context,"search",JSONObject().put("q","fresh"));assertEquals("success",new.getString("status"))
 }}}
 @Test fun safeReadRecoversFromIncompleteResponseOnce():Unit=runBlocking{withTimeout(10000){isolated{context->
  val response=BusinessApi.call(context,"search",JSONObject().put("q","reset-once:"+java.util.UUID.randomUUID()))
  assertEquals("success",response.getString("status"))
 }}}
}
