package com.example.sasalele

import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.view.Gravity
import android.graphics.PixelFormat
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.*
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import java.io.File

class WorkAccountSelectionTest {
 @get:Rule val compose=createComposeRule()
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private val folder=File(base.cacheDir,"synthetic-account-selection")
 private val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
 private var oldLan=false
 @Before fun prepare()=runBlocking {
  folder.mkdirs();oldLan=BusinessApi.lanDiagnosticsEnabled(base)
  BusinessApi.save(context,BusinessConnection("http://10.0.2.2:18878","synthetic-ui-credential-123456"))
  WorkAccountSelection.select(context,null)
 }
 @After fun clean()=runBlocking {
  WorkAccountSelection.select(context,null);BusinessApi.enableLanDiagnostics(base,oldLan)
  ClientRepository.unbindClient();folder.listFiles()?.forEach {it.delete()};folder.delete();Unit
 }
 @Test fun cachedDirectoryOpensWithoutNetworkAndSurvivesReopen() {
  WorkAccountDirectoryCache.save(context,org.json.JSONArray().put(org.json.JSONObject().put("account_id","cached").put("display_name","Cached account")))
  BusinessApi.save(context,BusinessConnection("http://10.0.2.2:18879","synthetic-ui-credential-123456"))
  compose.setContent{SASALELETheme{Surface{WorkAccountBindingPanel(context)}}}
  compose.onNodeWithText("选择工作号").performClick()
  compose.waitUntil(3000){compose.onAllNodesWithText("Cached account · 尾号 cached").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("收起工作号列表").performClick()
  compose.onNodeWithText("选择工作号").performClick()
  compose.onNodeWithText("Cached account · 尾号 cached").assertExists()
  compose.onNodeWithText("刷新工作号列表").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("仍可选择已缓存的工作号",substring=true).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("Cached account · 尾号 cached").assertExists()
  Assert.assertTrue(WorkAccountDirectoryCache.load(context)!!.fresh)
  Assert.assertFalse(WorkAccountDirectoryCache.load(context,System.currentTimeMillis()+25*60*60*1000L)!!.fresh)
 }
 @Test fun choosingAccountFromServicePersistsAndSwitchClearsClient() {
  compose.setContent{SASALELETheme{Surface{WorkAccountBindingPanel(context)}}}
  compose.onNodeWithText("选择工作号").performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("合成A · 尾号 ount-a").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("合成A · 尾号 ount-a").performClick()
  compose.onNodeWithText("使用此工作号").performClick()
  compose.waitUntil(10000){WorkAccountSelection.current.value?.id=="account-a"}
  ClientRepository.bindClient(Client("customer-a","account-a","合成客户","",""))
  runBlocking{WorkAccountSelection.select(context,SelectedWorkAccount("account-b","合成B"))}
  Assert.assertNull(ClientRepository.currentBoundClient.value)
  Assert.assertFalse(WorkAccountSelection.allows("account-a"))
  val alternate=object:ContextWrapper(base){override fun getNoBackupFilesDir()=File(folder,"alternate").apply{mkdirs()}}
  WorkAccountSelection.initialize(alternate)
  WorkAccountSelection.initialize(context)
  Assert.assertEquals("account-b",WorkAccountSelection.current.value?.id)
 }
 @Test fun accountConfirmationWorksInRealOverlayWithoutActivityToken() {
  val instrumentation=InstrumentationRegistry.getInstrumentation()
  instrumentation.uiAutomation.executeShellCommand("appops set ${base.packageName} SYSTEM_ALERT_WINDOW allow").close()
  val wm=base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
  lateinit var owner:ServiceLifecycleOwner
  lateinit var overlay:ComposeView
  compose.runOnIdle {
   owner=ServiceLifecycleOwner().apply {onCreate();onStart();onResume()}
   overlay=ComposeView(base.applicationContext).apply {
    setViewTreeLifecycleOwner(owner);setViewTreeViewModelStoreOwner(owner);setViewTreeSavedStateRegistryOwner(owner)
    setContent {SASALELETheme{Surface{WorkAccountBindingPanel(this@WorkAccountSelectionTest.context)}}}
   }
   val metrics=base.resources.displayMetrics
   wm.addView(overlay,WindowManager.LayoutParams(metrics.widthPixels,(metrics.heightPixels*0.85f).toInt(),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL})
  }
  try {
   compose.onNodeWithText("选择工作号").performClick()
   compose.waitUntil(10000){compose.onAllNodesWithText("合成A · 尾号 ount-a").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("合成A · 尾号 ount-a").performClick()
   compose.onNodeWithText("使用此工作号？").assertExists()
   compose.onNodeWithText("使用此工作号").performClick()
   compose.waitUntil(10000){WorkAccountSelection.current.value?.id=="account-a"}
   compose.onNodeWithText("已选择工作号").assertExists()
  } finally {
   compose.runOnIdle {wm.removeViewImmediate(overlay);overlay.disposeComposition();owner.onPause();owner.onStop();owner.onDestroy()}
  }
 }
 @Test fun settingsLongListSelectionRemainsAndSaves(){
  compose.setContent{SASALELETheme{Surface{SettingsPanel(context,{})}}}
  compose.onNodeWithText("选择工作号").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("合成15 · 尾号 unt-15").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("合成15 · 尾号 unt-15").performScrollTo().performClick()
  compose.onNodeWithText("使用此工作号").performScrollTo().performClick()
  compose.waitUntil(10000){WorkAccountSelection.current.value?.id=="account-15"}
  compose.onNodeWithText("切换工作号").performScrollTo().assertExists()
 }
 @Test fun noAccountPromptsSettingsAndHidesContent() {
  var navigated=false
  compose.setContent{SASALELETheme{Surface{WorkAccountScope(context,{navigated=true}){androidx.compose.material3.Text("private-content")}}}}
  compose.onNodeWithText("private-content").assertDoesNotExist()
  compose.onNodeWithText("前往助手设置").performClick()
  compose.runOnIdle{Assert.assertTrue(navigated)}
 }
 @Test fun foreignTargetBlockedBeforeRequest()=runBlocking {
  WorkAccountSelection.select(context,SelectedWorkAccount("account-a","合成A"))
  try {
   BusinessApi.call(context,"detail",BusinessApi.target(ClientKey("account-b","foreign")))
   Assert.fail("foreign account must be blocked")
  } catch(e:BusinessFailure){Assert.assertTrue(e.message.orEmpty().contains("当前工作号"))}
 }
 @Test fun reminderPageLinksSettingsWithoutAccount() {
  var opened=false
  compose.setContent{SASALELETheme{Surface{RemindersPanel({}, {opened=true})}}}
  compose.onNodeWithText("前往设置选择工作号").performClick()
  compose.runOnIdle{Assert.assertTrue(opened)}
 }
}
