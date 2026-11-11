package com.example.sasalele

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sasalele.data.*
import com.example.sasalele.data.db.MaterialEntity
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.*
import java.io.File
import java.util.UUID
import androidx.room.Room
import com.example.sasalele.data.db.AppDatabase
import com.example.sasalele.data.repository.MaterialRepository
import kotlinx.coroutines.flow.first

class TeamMaterialsTest {
 @get:Rule val compose=createComposeRule()
 private val base=ApplicationProvider.getApplicationContext<Context>()
 private fun device(name:String)=object:ContextWrapper(base){
  override fun getNoBackupFilesDir()=File(base.cacheDir,"synthetic-team-$name").apply{mkdirs()}
  override fun getSharedPreferences(key:String,mode:Int)=base.getSharedPreferences("synthetic-team-$name-$key",mode)
 }
 private val a=device("a");private val b=device("b")
 private fun fixture(path:String){OkHttpClient().newCall(Request.Builder().url("http://10.0.2.2:18881/test/$path").post(ByteArray(0).toRequestBody()).build()).execute().use{Assert.assertTrue(it.isSuccessful)}}
 @Before fun before(){fixture("reset");listOf(a,b).forEach{BusinessApi.save(it,BusinessConnection("http://10.0.2.2:18881","synthetic-team-materials-credential"))}}
 @Test fun twoClientsShareImageTextFavoritesAndOfflineCache()=runBlocking {
  val request=UUID.randomUUID().toString()
  val text=TeamMaterials.upload(a,MaterialEntity(title="合成共享文字",content="https://example.com",type="TEXT"),request)
  Assert.assertEquals(text.id,TeamMaterials.upload(a,MaterialEntity(title="合成共享文字",content="https://example.com",type="TEXT"),request).id)
  val file=File(a.cacheDir,"team-synthetic.png")
  val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
  file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val image=TeamMaterials.upload(a,MaterialEntity(title="合成共享图片",content=file.absolutePath,type="IMAGE"),UUID.randomUUID().toString())
  val other=TeamMaterials.sync(b);Assert.assertEquals(setOf(text.id,image.id),other.map{it.id}.toSet())
  val received=TeamMaterials.imageFile(b,image)
  val decoded=BitmapFactory.decodeFile(received.absolutePath);Assert.assertEquals(8,decoded.width);decoded.recycle()
  TeamMaterials.favorite(a,text.id,true);TeamMaterials.favorite(b,text.id,false)
  Assert.assertTrue(TeamMaterials.favorite(a,text.id));Assert.assertFalse(TeamMaterials.favorite(b,text.id))
  fixture("offline")
  try {TeamMaterials.sync(b);Assert.fail("must fail")}catch(e:BusinessFailure){}
  Assert.assertEquals(2,TeamMaterials.cached(b).size)
  Assert.assertEquals(received.absolutePath,TeamMaterials.imageFile(b,image).absolutePath)
 }
 @Test fun publishAndFilterTeamMaterialWithoutPublicDelete() {
  compose.setContent{SASALELETheme{Surface(Modifier.systemBarsPadding()){TeamMaterialsPanel(a,{}, {})}}}
  compose.waitUntil(10000){compose.onAllNodesWithText("已同步团队物料").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithContentDescription("新增团队文本或链接").performClick()
  compose.onNodeWithText("发布到团队库").assertIsNotEnabled()
  compose.onNodeWithText("物料标题").performTextInput("合成团队问候")
  compose.onNodeWithText("文本内容或完整链接").performTextInput("欢迎咨询课程")
  androidx.test.espresso.Espresso.closeSoftKeyboard()
  compose.onNodeWithText("发布到团队库").performScrollTo().performClick()
  compose.waitUntil(10000){compose.onAllNodesWithText("合成团队问候").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("删除").assertDoesNotExist()
  compose.onNodeWithContentDescription("筛选团队物料类型").performClick()
  compose.onNodeWithText("链接").performClick()
  compose.onNodeWithText("合成团队问候").assertDoesNotExist()
  compose.onNodeWithText("清除筛选和搜索").performClick()
  compose.onNodeWithText("合成团队问候").assertExists()
  val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
  File(base.getExternalFilesDir(null),"team-materials.png").outputStream().use{screenshot.compress(Bitmap.CompressFormat.PNG,100,it)};screenshot.recycle()
 }
 @Test fun oldLocalMaterialsAreNeverAutomaticallyUploaded() {
  val db=Room.inMemoryDatabaseBuilder(base,AppDatabase::class.java).build()
  try {
   val repo=MaterialRepository(db.materialDao())
   runBlocking{repo.insert(MaterialEntity(title="合成本机草稿",content="本机正文",type="TEXT"))}
   var uploads=0
   compose.setContent{SASALELETheme{Surface(Modifier.systemBarsPadding()){LocalMaterialsPanel(a,{},repo,onUpload={material,request->TeamMaterials.upload(a,material,request);uploads++})}}}
   compose.waitUntil(5000){compose.onAllNodesWithText("合成本机草稿").fetchSemanticsNodes().isNotEmpty()}
   Assert.assertEquals(0,uploads)
   compose.onNodeWithText("上传到团队库").performClick()
   compose.onNodeWithText("取消上传").performClick();Assert.assertEquals(0,uploads)
   compose.onNodeWithText("上传到团队库").performClick();compose.onNodeWithText("确认上传").performClick()
   compose.waitUntil(10000){uploads==1}
   Assert.assertEquals(1,runBlocking{repo.allMaterials.first().size})
   Assert.assertEquals("合成本机草稿",runBlocking{TeamMaterials.sync(b).single().title})
  }finally{db.close()}
 }
}
