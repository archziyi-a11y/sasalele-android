package com.example.sasalele
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sasalele.data.*
import com.example.sasalele.ui.theme.SASALELETheme
import org.junit.*
import java.io.File
class CustomerIdentificationUiTest {
 @get:Rule val compose=createComposeRule()
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private val folder=File(base.cacheDir,"customer-ui-v34")
 private val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=folder}
 @Before fun setup(){java.net.URL("http://10.0.2.2:18885/reset").readText();folder.mkdirs();BusinessApi.save(context,BusinessConnection("http://10.0.2.2:18885","synthetic-ui-credential-123456"));ClientRepository.unbindClient();kotlinx.coroutines.runBlocking{WorkAccountSelection.select(context,SelectedWorkAccount("synthetic-account","合成工作号"))}}
 @After fun clear(){kotlinx.coroutines.runBlocking{WorkAccountSelection.select(context,null)};BusinessApi.enableLanDiagnostics(base,false);ClientRepository.unbindClient()}
 private fun waitText(s:String){compose.waitUntil(15000){compose.onAllNodesWithText(s).fetchSemanticsNodes().isNotEmpty()}}
 private fun shot(name:String){compose.waitForIdle();val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot();File(base.getExternalFilesDir(null),"v34").apply{mkdirs()}.resolve("$name.png").outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
 @Test fun chipSearchConfirmAndExplicitGeneration(){
  var bound by mutableStateOf(false)
  val bitmap=android.graphics.Bitmap.createBitmap(720,1280,android.graphics.Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.WHITE)
  val paint=android.graphics.Paint().apply{color=android.graphics.Color.BLACK;textSize=26f}
  canvas.drawText("Synthetic title camelli",120f,90f,paint)
  val screenshot=File(folder,"synthetic-title.png");screenshot.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  compose.setContent{SASALELETheme{Surface(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)){if(bound) BusinessProfilePanel(context,{bound=false}) else BusinessSearchPanel(context,android.net.Uri.fromFile(screenshot),HeaderRecognition(listOf("〈2 （市场）小红书10.17camelli"),1,false,width=720,height=1280,cropTop=40,cropBottom=120),null,{bound=true},{})}}}
  compose.onNodeWithText("（市场）小红书10.17camelli").performScrollTo().performClick()
  waitText("查看画像")
  compose.onNodeWithText("synthetic-account",substring=true).assertDoesNotExist()
  compose.onNodeWithText("synthetic-customer",substring=true).assertDoesNotExist()
  shot("search")
  compose.onNodeWithText("查看画像").performScrollTo().performClick()
  waitText("还没有画像，可根据聊天记录生成。")
  compose.onNodeWithText("合成画像生成成功").assertDoesNotExist()
  shot("missing")
  compose.onNodeWithText("生成画像").performScrollTo().performClick()
  waitText("合成画像生成成功")
  compose.onNodeWithText("更新画像").assertExists()
  shot("generated")
  compose.onNodeWithText("更新画像").performScrollTo().performClick()
  waitText("业务服务或上游暂不可用，请稍后重试")
  compose.onNodeWithText("合成画像生成成功").assertExists()
  compose.runOnIdle{ClientRepository.bindClient(Client("other","synthetic-account","另一合成客户","",""))}
  waitText("还没有画像，可根据聊天记录生成。")
  val counts=org.json.JSONObject(java.net.URL("http://10.0.2.2:18885/stats").readText())
  Assert.assertEquals(0,counts.optInt("/sales/profile/ensure:other"))
 }
 @Test fun readFailureCanRetryWithoutGenerating(){
  ClientRepository.bindClient(Client("read-fail","synthetic-account","失败测试客户","",""))
  compose.setContent{SASALELETheme{Surface(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)){BusinessProfilePanel(context,{})}}}
  waitText("业务服务或上游暂不可用，请稍后重试")
  compose.onNodeWithText("还没有画像，可根据聊天记录生成。").assertDoesNotExist()
  compose.onNodeWithText("重新加载").performScrollTo().performClick()
  waitText("还没有画像，可根据聊天记录生成。")
 }
}
