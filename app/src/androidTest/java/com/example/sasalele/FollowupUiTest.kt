package com.example.sasalele

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.*
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class FollowupUiTest {
 @get:Rule val compose=createComposeRule()
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private val folder=File(base.cacheDir,"synthetic-followups")
 private val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
 private var oldLan=false
 @Before fun prepare()=runBlocking {
  folder.mkdirs();oldLan=BusinessApi.lanDiagnosticsEnabled(base)
  BusinessApi.save(context,BusinessConnection("http://10.0.2.2:18878","synthetic-ui-credential-123456"))
  WorkAccountSelection.select(context,SelectedWorkAccount("account-a","合成A"))
 }
 @After fun clean()=runBlocking {
  FollowupRepository.stop();WorkAccountSelection.select(context,null);BusinessApi.enableLanDiagnostics(base,oldLan)
  folder.listFiles()?.forEach{it.delete()};folder.delete();Unit
 }
 @Test fun initialImportSilentAndActionPersistsOnReopen() {
  runBlocking{FollowupRepository.refresh(context)}
  Assert.assertFalse(ReminderScheduler.pendingReminders.value.containsKey(ReminderRules.key("account-a","followup-customer")))
  compose.setContent{SASALELETheme{Surface(Modifier.systemBarsPadding().padding(16.dp)){CompositionLocalProvider(LocalContext provides context){FollowupsPanel({})}}}}
  compose.onNodeWithText("合成待查看客户").assertExists()
  compose.onNodeWithText("合成待查看客户").performClick()
  compose.runOnIdle {
   val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
   Assert.assertEquals("合成待查看客户",clipboard.primaryClip?.getItemAt(0)?.text?.toString())
   Assert.assertEquals("needs_review",FollowupRepository.state.value.contacts.first().status)
  }
  val picture=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(base.getExternalFilesDir(null),"followups-ui.png").outputStream().use{picture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  picture.recycle()
  compose.onNodeWithText("忽略此联系人").assertDoesNotExist()
  compose.onNodeWithText("更多").performClick()
  compose.onNodeWithText("忽略此联系人").performClick()
  compose.waitUntil(5000){FollowupRepository.state.value.ignoredContacts.isNotEmpty()}
  compose.onNodeWithText("合成待查看客户").assertDoesNotExist()
  compose.onNodeWithText("忽略列表（1）").performClick()
  compose.onNodeWithText("合成待查看客户").assertExists()
  compose.onNodeWithText("移回提醒列表").performClick()
  compose.waitUntil(5000){FollowupRepository.state.value.ignoredContacts.isEmpty()}
  compose.onNodeWithText("返回提醒列表").performClick()
  compose.onNodeWithText("已处理").performClick()
  compose.waitUntil(5000){FollowupRepository.state.value.contacts.firstOrNull()?.status=="done"}
  compose.onNodeWithText("全部活跃").performClick()
  compose.onNodeWithText("已处理").assertExists()
  runBlocking{FollowupRepository.refresh(context)}
  Assert.assertEquals("done",FollowupRepository.state.value.contacts.first().status)
  runBlocking{WorkAccountSelection.select(context,SelectedWorkAccount("account-b","合成B"))}
  compose.onNodeWithText("合成待查看客户").assertDoesNotExist()
 }
}
