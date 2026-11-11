package com.example.sasalele
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BusinessGenerationTest {
 private suspend fun isolated(block:suspend(Context)->Unit) {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val old=BusinessApi.load(context);val enabled=BusinessApi.lanDiagnosticsEnabled(context)
  try {
   val port=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("generationPort","18877")
   BusinessApi.save(context,BusinessConnection("http://10.0.2.2:$port","synthetic-generation-credential"))
   block(context)
  } finally {if(BusinessApi.validUrl(old.url)) BusinessApi.save(context,old);BusinessApi.enableLanDiagnostics(context,enabled)}
 }
 @Test fun jobPollRetainsTargetAndReturnsDraft()=runBlocking {isolated {context->
  val key=ClientKey("synthetic-account","synthetic-customer")
  val result=try {awaitGeneration(context,key,"draft",BusinessApi.target(key)){ }}
  catch(e:BusinessFailure){throw AssertionError("transport diagnostic="+e.diagnosticCode)}
  assertEquals("合成草稿",result.getString("draft"))
 }}
 @Test fun modelQuotaFailureHasChineseFeedback()=runBlocking {isolated {context->
  val key=ClientKey("synthetic-account","failure")
  val error=runCatching {awaitGeneration(context,key,"draft",BusinessApi.target(key)){ }}.exceptionOrNull()
  assertTrue(error is BusinessFailure);assertTrue(error!!.message.orEmpty().contains("额度"))
 }}
 @Test fun cancelPollingDiscardsOldResult()=runBlocking {isolated {context->
  val key=ClientKey("synthetic-account","slow")
  val task=async {awaitGeneration(context,key,"draft",BusinessApi.target(key)){ }}
  delay(100);task.cancelAndJoin();assertTrue(task.isCancelled)
 }}
 @Test fun repeatedCancelThenNewDraft()=runBlocking {isolated {context->
  repeat(5) {
   val old=ClientKey("synthetic-account","slow")
   val task=async {awaitGeneration(context,old,"draft",BusinessApi.target(old)){ }}
   delay(100);task.cancelAndJoin()
   val key=ClientKey("synthetic-account","synthetic-customer")
   val result=try {awaitGeneration(context,key,"draft",BusinessApi.target(key)){ }}
   catch(e:BusinessFailure){throw AssertionError("cancel cycle=$it diagnostic="+e.diagnosticCode)}
   assertEquals("合成草稿",result.getString("draft"))
  }
 }}
}
