package com.example.sasalele

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.MaterialKind
import com.example.sasalele.data.db.*
import com.example.sasalele.data.repository.MaterialRepository
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*

class MaterialFilterTest {
 @get:Rule val compose=createComposeRule()
 private val context=ApplicationProvider.getApplicationContext<Context>()
 @Test fun legacyLinksAndMixedTextAreClassifiedWithoutChangingRecords() {
  Assert.assertEquals(MaterialKind.LINK,MaterialKind.of(MaterialEntity(title="url",content=" https://example.com/path?q=1#part ",type="TEXT")))
  Assert.assertEquals(MaterialKind.TEXT,MaterialKind.of(MaterialEntity(title="mixed",content="介绍 https://example.com/path",type="TEXT")))
  Assert.assertEquals(MaterialKind.IMAGE,MaterialKind.of(MaterialEntity(title="image",content="https://example.com/image.png",type="IMAGE")))
  Assert.assertEquals(MaterialKind.TEXT,MaterialKind.of(MaterialEntity(title="bad",content="https://",type="TEXT")))
 }
 @Test fun funnelCombinesTypeWithSearchAndCanClear() {
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try {
   val repo=MaterialRepository(db.materialDao())
   runBlocking{repo.insertAll(listOf(
    MaterialEntity(title="合成图片",content="/missing-synthetic-image.png",type="IMAGE"),
    MaterialEntity(title="合成网址",content="https://example.com",type="TEXT"),
    MaterialEntity(title="合成话术",content="欢迎了解课程",type="TEXT"),
    MaterialEntity(title="合成说明",content="详细介绍 https://example.com",type="TEXT")))}
   compose.setContent{SASALELETheme{Surface{MaterialsPanel(context,{},repo)}}}
   compose.onNodeWithContentDescription("筛选物料类型").performClick()
   compose.onNodeWithText("图片").performClick()
   compose.onNodeWithText("合成图片").assertExists();compose.onNodeWithText("合成网址").assertDoesNotExist()
   compose.onNodeWithText("当前筛选：图片").assertExists()
   compose.onNode(hasSetTextAction()).performTextInput("不存在")
   compose.onNodeWithText("没有符合条件的物料").assertExists()
   compose.onNodeWithText("清除筛选和搜索").performClick()
   compose.onNodeWithText("链接").performClick()
   compose.onNodeWithText("合成网址").assertExists();compose.onNodeWithText("合成说明").assertDoesNotExist()
   compose.onNodeWithText("复制链接").assertExists()
   compose.onNodeWithText("纯文本").performClick()
   compose.onNodeWithText("合成话术").assertExists();compose.onNodeWithText("合成说明").assertExists()
   compose.onNodeWithText("合成网址").assertDoesNotExist()
   compose.onNode(hasSetTextAction()).performTextInput("合成说明")
   compose.onNode(hasText("合成说明") and !hasSetTextAction()).assertExists();compose.onNodeWithText("合成话术").assertDoesNotExist()
   androidx.test.espresso.Espresso.closeSoftKeyboard()
   Assert.assertEquals(4,runBlocking{repo.allMaterials.first().size})
   val image=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   java.io.File(context.getExternalFilesDir(null),"material-filter.png").outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
   image.recycle()
  }finally{db.close()}
 }
}
