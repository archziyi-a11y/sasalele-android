package com.example.sasalele

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sasalele.data.*
import com.example.sasalele.ui.theme.SASALELETheme
import org.json.JSONObject
import org.junit.*
import java.io.File

class UiExperienceTest {
 @get:Rule val compose=createComposeRule()
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private val folder=File(base.cacheDir,"synthetic-ui-test")
 private val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
 private var oldLan=false
 @Before fun prepare(){oldLan=BusinessApi.lanDiagnosticsEnabled(base);folder.mkdirs();BusinessApi.save(context,BusinessConnection("http://10.0.2.2:18878","synthetic-ui-credential-123456"));ClientRepository.unbindClient();kotlinx.coroutines.runBlocking{WorkAccountSelection.select(context,SelectedWorkAccount("synthetic-account","合成工作号"))}}
 @After fun restore(){kotlinx.coroutines.runBlocking{WorkAccountSelection.select(context,null)};ClientRepository.unbindClient();BusinessApi.enableLanDiagnostics(base,oldLan);folder.listFiles()?.forEach{it.delete()};folder.delete()}
 private fun screenshot(name:String){
  compose.waitForIdle()
  val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(base.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()}.resolve("$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
  bitmap.recycle()
 }
 @Test fun guidanceUsesDedicatedInternalGenerationMode(){
  compose.setContent {SASALELETheme{Surface{BusinessGenerationPanel(context,guidanceOnly=true)}}}
  compose.onNodeWithText("帮我回").assertDoesNotExist()
  compose.onNodeWithText("润色").assertDoesNotExist()
  compose.onNodeWithText("待润色原文（必须填写）").assertDoesNotExist()
  compose.onNodeWithText("生成销售指导").assertExists().assertIsNotEnabled()
  compose.onNodeWithText("补充局面或本次希望解决的问题（可选）").assertExists()
 }
 @Test fun guidanceSurvivesInputChangeFailureAndReplacesOnSuccess(){
  ClientRepository.bindClient(Client("synthetic-customer","synthetic-account","合成客户","",""))
  compose.setContent{SASALELETheme{Surface(Modifier.systemBarsPadding().imePadding().padding(16.dp)){BusinessGenerationPanel(context,guidanceOnly=true)}}}
  val input="补充局面或本次希望解决的问题（可选）"
  val result="销售指导（内部参考，可编辑备注）"
  val stale="输入已修改，以下结果对应上一次输入；重新生成成功后才会替换。"
  compose.onNodeWithText("生成销售指导").performScrollTo().performClick()
  compose.waitUntil(15000){compose.onAllNodesWithText(result).fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText(result).performScrollTo().performTextReplacement("合成已编辑指导")
  compose.onNodeWithText(input).performScrollTo().performTextReplacement("synthetic-failure")
  compose.onNodeWithText(stale).performScrollTo().assertExists()
  compose.onNodeWithText("合成已编辑指导").assertExists()
  compose.onNodeWithText("生成销售指导").performScrollTo().performClick()
  compose.waitUntil(15000){compose.onAllNodesWithText("模型返回内容不符合格式，请重试").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("合成已编辑指导").assertExists()
  compose.onNodeWithText(input).performScrollTo().performTextReplacement("synthetic-next")
  compose.onNodeWithText("生成销售指导").performScrollTo().performClick()
  compose.waitUntil(15000){compose.onAllNodesWithText("合成新指导").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("合成已编辑指导").assertDoesNotExist()
  compose.onNodeWithText(stale).assertDoesNotExist()
  screenshot("guidance-preview")
 }
 @Test fun homeShowsPreparationInsteadOfChatList(){
  compose.setContent {SASALELETheme{MainScreen({}, {})}}
  compose.onNodeWithText("准备好，再回到客户会话").assertIsDisplayed()
  compose.onNodeWithText("模拟聊天列表").assertDoesNotExist()
  screenshot("home")
 }
 @Test fun profileSeparatesFactsInferencesAndUnknowns(){
  val profile=JSONObject("""{"summary":"合成客户 · 家长明确咨询学习方向，当前报名状态未知。","structured_fields_available":true,"facts":{"learner_identity":{"value":"家长"}},"inferences":[{"field":"intent_level","value":"尚待了解","reason":"仅有一般咨询，不能判定高意向"}],"unknowns":["enrollment_status"],"source":"fangzhou_chat_generated"}""")
  compose.setContent{SASALELETheme{Surface{Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){ProfilePresentation(profile)}}}}
  compose.onNodeWithText("学员身份：家长").assertExists()
  compose.onNodeWithText("跟进判断").assertExists()
  compose.onNodeWithText("待补充信息：报名状态").assertDoesNotExist()
  screenshot("profile")
  compose.onNodeWithText("画像来源与时间").performScrollTo().performClick()
  compose.onNodeWithText("待补充信息：报名状态").assertExists()
 }
 @Test fun polishShowsWarningsAndCopiesEditedText(){
  ClientRepository.bindClient(Client("synthetic-customer","synthetic-account","合成客户","",""))
  compose.setContent{SASALELETheme{Surface(Modifier.systemBarsPadding().imePadding().padding(16.dp)){BusinessGenerationPanel(context)}}}
  compose.onNodeWithText("润色").performClick()
  compose.onNodeWithText("待润色原文（必须填写）").performTextInput("这件事我会核实后回复您。")
  compose.onNodeWithText("生成润色").performScrollTo().performClick()
  compose.waitUntil(15000){compose.onAllNodesWithText("审阅与编辑草稿").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("审阅与编辑草稿").performScrollTo().performTextReplacement("合成编辑后的草稿")
  compose.onNodeWithText("请核对以下调整").performScrollTo().assertIsDisplayed()
  compose.onNodeWithText("复制草稿").performScrollTo().performClick()
  compose.runOnIdle { Assert.assertEquals("合成编辑后的草稿",base.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()) }
  compose.onNodeWithText("复制草稿").performScrollTo().assertIsDisplayed()
  screenshot("polish-edited")
  compose.onNodeWithText("已复制编辑后的草稿，请自行核对和发送").assertExists()
 }
 @Test fun candidatesRequireExplicitConfirmation(){
  compose.setContent{SASALELETheme{Surface(Modifier.systemBarsPadding().imePadding().padding(16.dp)){BusinessSearchPanel(context,null,null,null,{}, {})}}}
  compose.onNodeWithText("OCR姓名／备注（可修正）").performTextInput("合成客户")
  compose.waitUntil(10000){compose.onAllNodesWithText("查看并人工确认").fetchSemanticsNodes().isNotEmpty()}
  Assert.assertNull(ClientRepository.currentBoundClient.value)
  compose.onNodeWithText("查看并人工确认").performScrollTo().performClick()
  compose.onNodeWithText("人工确认绑定").performScrollTo().assertIsDisplayed()
  screenshot("candidate-confirm")
  compose.onNodeWithText("人工确认绑定").performClick()
  compose.waitUntil(10000){ClientRepository.currentBoundClient.value!=null}
  Assert.assertEquals(ClientKey("synthetic-account","synthetic-customer"),ClientRepository.currentBoundClient.value?.key)
 }
}
