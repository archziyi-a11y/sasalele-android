package com.example.sasalele
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProfileSearchTest {
 private fun json()="""{"schema_version":1,"customers":[{"account_id":"test-a","friend_id":"1","display_name":"测试同名","aliases":["别名"],"profile_summary":"合成画像","profile_source":"合成测试"},{"account_id":"test-b","friend_id":"2","display_name":"测试同名","aliases":["别名"]},{"account_id":"test-a","friend_id":"3","display_name":"测试同名"}]}"""
 @Test fun defaultAllAccounts() { assertEquals(3,IdentitySamples.match(IdentitySamples.parse(json()),null,listOf("测试同名")).size) }
 @Test fun optionalAccountFilter() { assertEquals(2,IdentitySamples.match(IdentitySamples.parse(json()),"test-a",listOf("测试同名")).size) }
 @Test fun sameNameNoMerge() { assertEquals(3,IdentitySamples.parse(json()).map { it.client.key }.distinct().size) }
 @Test fun aliasMultipleCandidates() { assertEquals(2,IdentitySamples.match(IdentitySamples.parse(json()),null,listOf("别名")).size) }
 @Test fun noMatch() { assertTrue(IdentitySamples.match(IdentitySamples.parse(json()),null,listOf("没有的人")).isEmpty()) }
 @Test fun bindKeepsBothIds() { val sample=IdentitySamples.parse(json())[1]; val before=ClientRepository.currentBoundClient.value; try { ClientRepository.bindClient(sample.client); assertEquals(ClientKey("test-b","2"),ClientRepository.currentBoundClient.value?.key) } finally { if(before==null) ClientRepository.unbindClient() else ClientRepository.restoreClient(before) } }
 @Test fun missingFieldsHonest() { val sample=IdentitySamples.parse(json())[1]; assertNull(sample.client.avatarUrl); assertEquals("暂无画像摘要",sample.summary); assertEquals("来源未提供",sample.source) }
 @Test fun invalidMissingId() { assertTrue(runCatching { IdentitySamples.parse(json().replace("friend_id","wrong_id")) }.isFailure) }
 @Test fun duplicateCompositeRejected() { assertTrue(runCatching { IdentitySamples.parse(json().replace("test-b","test-a").replace("\"2\"","\"1\"")) }.isFailure) }
 @Test fun invalidSchemaRejected() { assertTrue(runCatching { IdentitySamples.parse(json().replace("schema_version\":1","schema_version\":9")) }.isFailure) }
 @Test fun importAndFailurePreservesOriginal():Unit=runBlocking { withTimeout(10000) {
  val original=ApplicationProvider.getApplicationContext<Context>(); val dir=File(original.cacheDir,"profile-tests-${System.nanoTime()}").apply { mkdirs() }
  val context=object:ContextWrapper(original) { override fun getFilesDir()=dir }
  val input=File(dir,"test.json")
  try { input.writeText(json()); val result=IdentitySamples.importJson(context,Uri.fromFile(input)); assertTrue(result.contains("导入成功")); assertEquals(3,IdentitySamples.match(IdentitySamples.load(context),null,listOf("测试同名")).size)
   input.writeText("bad json"); assertTrue(runCatching { IdentitySamples.importJson(context,Uri.fromFile(input)) }.isFailure); assertEquals(3,IdentitySamples.match(IdentitySamples.load(context),null,listOf("测试同名")).size)
  } finally { dir.deleteRecursively() }
 } }
 @Test fun staleCaptureCannotOverwriteNew() { val before=CaptureSession.state.value; try { CaptureSession.state.value=CaptureStatus("new",busy=true,hidden=true); assertFalse(CaptureSession.finish("old","wrong")); assertEquals("new",CaptureSession.state.value.id); assertTrue(CaptureSession.state.value.busy); assertTrue(CaptureSession.finish("new","cancel")); assertFalse(CaptureSession.state.value.hidden) } finally { CaptureSession.state.value=before } }
}
