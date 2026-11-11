package com.example.sasalele

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.Client
import com.example.sasalele.data.ClientRepository
import com.example.sasalele.ui.theme.SASALELETheme
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Synthetic state only: this test never starts a generation request. */
class GenerationPanelRetentionTest {
 @get:Rule val compose=createComposeRule()
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val a=Client("synthetic-a","synthetic-account","合成客户甲","","")
 private val b=Client("synthetic-b","synthetic-account","合成客户乙","","")
 private val store=GenerationDraftStore()
 private val shown=mutableStateOf(true)
 private var previous:Client?=null

 @Before fun setup(){
  previous=ClientRepository.currentBoundClient.value
  ClientRepository.restoreClient(a)
  store.get(a.key,"帮我回").apply {result=JSONObject().put("draft","甲原稿");edited="甲编辑稿"}
  store.get(b.key,"帮我回").apply {result=JSONObject().put("draft","乙原稿");edited="乙编辑稿"}
 }
 @After fun cleanup(){if(previous==null) ClientRepository.unbindClient() else ClientRepository.restoreClient(previous!!)}

 @Test fun draftSurvivesDisposalAndStaysWithItsCustomer(){
  compose.setContent {SASALELETheme {Surface {if(shown.value) BusinessGenerationPanel(context,store=store)}}}
  compose.onNodeWithText("甲编辑稿").assertExists().performTextReplacement("甲再次编辑")
  compose.runOnIdle {shown.value=false}
  compose.onNodeWithText("甲再次编辑").assertDoesNotExist()
  compose.runOnIdle {shown.value=true}
  compose.onNodeWithText("甲再次编辑").assertExists()
  compose.runOnIdle {ClientRepository.restoreClient(b)}
  compose.onNodeWithText("乙编辑稿").assertExists()
  compose.onNodeWithText("甲再次编辑").assertDoesNotExist()
  compose.runOnIdle {ClientRepository.restoreClient(a)}
  compose.onNodeWithText("甲再次编辑").assertExists()
 }
}
