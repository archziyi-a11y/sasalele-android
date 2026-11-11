package com.example.sasalele

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.db.*
import com.example.sasalele.data.repository.MaterialRepository
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*

class MaterialCreateTest {
 @get:Rule val compose=createComposeRule()
 @Test fun createTextAndLinkAndCancelWithoutSaving() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).build()
  try {
   val repo=MaterialRepository(db.materialDao())
   compose.setContent{SASALELETheme{Surface{MaterialsPanel(context,{},repo)}}}
   fun open(){compose.onNodeWithContentDescription("添加文本或链接").performClick()}
   fun fill(title:String,content:String){
    compose.onNodeWithText("物料标题").performTextInput(title)
    compose.onNodeWithText("文本内容或完整链接").performTextInput(content)
    androidx.test.espresso.Espresso.closeSoftKeyboard()
   }
   open();compose.onNodeWithText("保存物料").assertIsNotEnabled()
   fill("取消的草稿","不应保存");compose.onNodeWithText("取消新增").performClick()
   Assert.assertTrue(runBlocking{repo.allMaterials.first().isEmpty()})
   open();fill("合成话术","您好，欢迎咨询。\n课程说明 https://example.com")
   compose.onNodeWithText("保存物料").performClick()
   compose.waitUntil(5000){runBlocking{repo.allMaterials.first().size==1}}
   Assert.assertEquals("TEXT",runBlocking{repo.allMaterials.first().single().type})
   open();fill("合成链接"," https://example.com/course?q=1 ")
   compose.onNodeWithText("保存物料").performClick()
   compose.waitUntil(5000){runBlocking{repo.allMaterials.first().size==2}}
   val link=runBlocking{repo.allMaterials.first().single{it.title=="合成链接"}}
   Assert.assertEquals("LINK",link.type);Assert.assertEquals("https://example.com/course?q=1",link.content)
   compose.onNodeWithContentDescription("筛选物料类型").performClick()
   compose.onNodeWithText("链接").performClick()
   compose.onNodeWithText("合成链接").assertExists();compose.onNodeWithText("合成话术").assertDoesNotExist()
  }finally{db.close()}
 }
}
