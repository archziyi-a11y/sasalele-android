package com.example.sasalele

import android.content.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.MaterialImages
import com.example.sasalele.data.db.*
import com.example.sasalele.data.repository.MaterialRepository
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import java.io.File

class MaterialActionsTest {
 @get:Rule val compose=createComposeRule()
 private val context=ApplicationProvider.getApplicationContext<Context>()
 @Test fun remoteImageIsFileContentNotLinkAndClipboardContainsImageUri() {
  compose.setContent{SASALELETheme{Surface{Text("合成图片测试")}}}
  val image=runBlocking{MaterialImages.prepare(context,"http://10.0.2.2:18880/image")}
  Assert.assertEquals("image/png",image.mime)
  Assert.assertEquals("content",image.uri.scheme)
  val intent=MaterialImages.shareIntent(context,image)
  Assert.assertEquals(Intent.ACTION_SEND,intent.action)
  Assert.assertEquals("image/png",intent.type)
  Assert.assertFalse(intent.hasExtra(Intent.EXTRA_TEXT))
  Assert.assertNotNull(intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM))
  Assert.assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  val bytes=context.contentResolver.openInputStream(image.uri)!!.use{it.readBytes()}
  Assert.assertEquals(137,bytes[0].toInt() and 255);Assert.assertEquals(80,bytes[1].toInt())
  compose.runOnIdle {
   MaterialImages.copy(context,image)
  }
  compose.waitUntil(5000) {
   (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
    .primaryClip?.getItemAt(0)?.uri == image.uri
  }
  compose.runOnIdle {
   val clip=(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
   Assert.assertEquals(image.uri,clip.getItemAt(0).uri)
   Assert.assertTrue(clip.description.hasMimeType("image/png"))
   Assert.assertNull(clip.getItemAt(0).text)
  }
 }
 @Test fun invalidDownloadedFileDoesNotBecomeTextShare()=runBlocking {
  try{MaterialImages.prepare(context,"http://10.0.2.2:18880/text");Assert.fail("invalid data must not be shared")}
  catch(e:IllegalArgumentException){Assert.assertTrue(e.message.orEmpty().contains("有效图片"))}
 }
 @Test fun deleteRequiresConfirmationAndDoesNotRecreateExamples() {
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try {
   val repo=MaterialRepository(db.materialDao());var shown by mutableStateOf(true)
   runBlocking{repo.insert(MaterialEntity(title="合成待删除物料",content="合成正文",type="TEXT"))}
   compose.setContent{SASALELETheme{Surface{if(shown)MaterialsPanel(context,{},repo)}}}
   compose.waitUntil(5000){compose.onAllNodesWithText("合成待删除物料").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("删除").performClick();compose.onNodeWithText("取消").performClick()
   Assert.assertEquals(1,runBlocking{repo.allMaterials.first().size})
   compose.onNodeWithText("删除").performClick();compose.onNodeWithText("确认删除").performClick()
   compose.waitUntil(5000){runBlocking{repo.allMaterials.first().isEmpty()}}
   compose.runOnIdle{shown=false};compose.waitForIdle();compose.runOnIdle{shown=true};compose.waitForIdle()
   Assert.assertTrue(runBlocking{repo.allMaterials.first().isEmpty()})
  }finally{db.close()}
 }
 @Test fun deletingOneReferenceDoesNotDeleteAnotherMaterialsFile()=runBlocking {
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  val dir=File(context.cacheDir,"synthetic-owned-materials").apply{mkdirs()};val file=File(dir,"fixture.jpg").apply{writeText("fixture")}
  try {
   val repo=MaterialRepository(db.materialDao())
   repo.insert(MaterialEntity(title="a",content=file.absolutePath,type="IMAGE"));repo.insert(MaterialEntity(title="b",content=file.absolutePath,type="IMAGE"))
   repo.delete(repo.allMaterials.first().first(),dir);Assert.assertTrue(file.exists())
   repo.delete(repo.allMaterials.first().first(),dir);Assert.assertFalse(file.exists())
  }finally{file.delete();dir.delete();db.close()}
 }
}
