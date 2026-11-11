package com.example.sasalele

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.*
import com.example.sasalele.data.db.*
import com.example.sasalele.data.repository.MaterialRepository
import com.example.sasalele.ui.theme.SASALELETheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.junit.*
import java.io.File
import java.util.UUID

class MaterialNamingTest {
 @get:Rule val compose = createComposeRule()
 private val base = ApplicationProvider.getApplicationContext<Context>()
 private val tag = UUID.randomUUID().toString()
 private fun device(name:String):Context = object:ContextWrapper(base) {
  private val root=File(base.filesDir,"naming-$tag-$name").apply{mkdirs()}
  override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
  override fun getFilesDir()=File(root,"files").apply{mkdirs()}
  // FileProvider uses the application's configured material-share cache root.
  override fun getCacheDir()=base.cacheDir
  override fun getSharedPreferences(key:String,mode:Int)=base.getSharedPreferences("naming-$tag-$name-$key",mode)
 }
 private val a by lazy { device("a") }; private val b by lazy { device("b") }
 private lateinit var dbA:AppDatabase; private lateinit var dbB:AppDatabase
 private lateinit var repoA:MaterialRepository; private lateinit var repoB:MaterialRepository
 private val http=OkHttpClient()
 private fun fixture(path:String,post:Boolean=true):String {
  val builder=Request.Builder().url("http://10.0.2.2:18881/test/$path")
  if(post) builder.post(ByteArray(0).toRequestBody())
  return http.newCall(builder.build()).execute().use { Assert.assertTrue("fixture $path: ${it.code}",it.isSuccessful);it.body!!.string() }
 }
 private fun report():JSONArray=JSONArray(fixture("report",false))
 private fun reportFor(title:String)=report().let { rows -> (0 until rows.length()).map{rows.getJSONObject(it)}.first{it.getString("alias_title")==title} }
 private suspend fun account(context:Context,id:String)=WorkAccountSelection.select(context,SelectedWorkAccount(id,id))
 private suspend fun publish(title:String="公共名称")=TeamMaterials.upload(a,MaterialEntity(title=title,content="合成内容",type="TEXT"),UUID.randomUUID().toString())
 @Before fun setup() {
  fixture("reset")
  listOf(a,b).forEach{BusinessApi.save(it,BusinessConnection("http://10.0.2.2:18881","synthetic-team-materials-credential"))}
  dbA=Room.inMemoryDatabaseBuilder(base,AppDatabase::class.java).build(); dbB=Room.inMemoryDatabaseBuilder(base,AppDatabase::class.java).build()
  repoA=MaterialRepository(dbA.materialDao()); repoB=MaterialRepository(dbB.materialDao())
 }
 @After fun teardown(){runCatching{fixture("online")};dbA.close();dbB.close()}

 @Test fun sameSourcePreservesIdentityAndFavoriteAndAccountBAdopts()=runBlocking {
  account(a,"synthetic-a");val item=publish()
  MaterialNaming.saveLocal(a,item,"我的别名",null,"explicit",repoA)
  val first=repoA.allMaterials.first().single();repoA.update(first.copy(isFavorite=true))
  MaterialNaming.saveLocal(a,item,"我的别名",null,"explicit",repoA)
  val saved=repoA.allMaterials.first().single()
  Assert.assertEquals(first.id,saved.id);Assert.assertTrue(saved.isFavorite)
  MaterialNaming.flush(a,repoA);Assert.assertEquals(0,MaterialNaming.pending(a,repoA))
  val alias=TeamMaterials.sync(b).single().aliases.single{it.title=="我的别名"}
  account(b,"synthetic-b")
  MaterialNaming.saveLocal(b,item,alias.title,alias,"adopt",repoB);MaterialNaming.flush(b,repoB)
  val row=reportFor("我的别名")
  Assert.assertEquals(1,row.getInt("explicit_accounts"));Assert.assertEquals(1,row.getInt("adopt_accounts"))
  Assert.assertEquals("公共名称",TeamMaterials.sync(b).single().title)
 }
 @Test fun offlineSaveSurvivesAndFlushesWhenOnline()=runBlocking {
  account(a,"synthetic-a");val item=publish();fixture("offline")
  MaterialNaming.saveLocal(a,item,"离线别名",null,"explicit",repoA)
  MaterialNaming.flush(a,repoA)
  Assert.assertEquals("离线别名",repoA.allMaterials.first().single().title)
  Assert.assertEquals(1,MaterialNaming.pending(a,repoA))
  fixture("online");MaterialNaming.flush(a,repoA)
  Assert.assertEquals(0,MaterialNaming.pending(a,repoA));Assert.assertEquals(1,reportFor("离线别名").getInt("explicit_accounts"))
  Assert.assertEquals("公共名称",TeamMaterials.sync(a).single().title)
 }
 @Test fun downloadedImageIsCopiedToPersistentLocalFiles()=runBlocking {
  account(a,"synthetic-a")
  val file=File(a.cacheDir,"naming-$tag.png")
  val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
  file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  val image=TeamMaterials.upload(a,MaterialEntity(title="公共图片",content=file.absolutePath,type="IMAGE"),UUID.randomUUID().toString())
  account(b,"synthetic-b");MaterialNaming.saveLocal(b,image,"本机图片",null,"explicit",repoB)
  val local=File(repoB.allMaterials.first().single().content)
  Assert.assertTrue(local.isFile);Assert.assertTrue(local.canonicalPath.startsWith(b.filesDir.canonicalPath+File.separator))
  Assert.assertFalse(local.canonicalPath.startsWith(b.cacheDir.canonicalPath+File.separator))
  TeamMaterials.imageFile(b,image).delete();Assert.assertTrue(local.isFile)
 }
 @Test fun aliasSearchShowsOneCardAndSavingUnchangedRecordsAdoption() {
  runBlocking {account(a,"synthetic-a");val item=publish();MaterialNaming.saveLocal(a,item,"独特搜索词",null,"explicit",repoA);MaterialNaming.flush(a,repoA);account(b,"synthetic-b")}
  compose.setContent { SASALELETheme { Surface(Modifier.systemBarsPadding()){TeamMaterialsPanel(b,{}, {},repoB)} } }
  compose.waitUntil(10000){compose.onAllNodesWithText("已同步团队物料").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("搜索团队标题或员工别名").performTextInput("独特搜索词")
  compose.onAllNodesWithText("公共名称").assertCountEquals(1)
  androidx.test.espresso.Espresso.closeSoftKeyboard()
  compose.onNodeWithText("复制").performScrollTo().performClick()
  compose.waitUntil(5000){runBlocking{MaterialNaming.pending(b,repoB)==1}}
  runBlocking{MaterialNaming.flush(b,repoB)}
  Assert.assertEquals(1,reportFor("独特搜索词").getInt("search_use_accounts"))
  compose.onNode(hasText("独特搜索词") and hasClickAction() and !hasSetTextAction()).performScrollTo().performClick()
  compose.onNodeWithText("本机物料标题").assertTextContains("独特搜索词")
  Assert.assertTrue(runBlocking{repoB.allMaterials.first().isEmpty()})
  Assert.assertEquals(0,runBlocking{MaterialNaming.pending(b,repoB)})
  Assert.assertEquals(0,reportFor("独特搜索词").getInt("adopt_accounts"))
  compose.onNodeWithText("保存到本机").performScrollTo().performClick()
  compose.waitUntil(10000){runBlocking{repoB.allMaterials.first().isNotEmpty() && MaterialNaming.pending(b,repoB)==0}}
  Assert.assertEquals("独特搜索词",runBlocking{repoB.allMaterials.first().single().title})
  Assert.assertEquals(1,reportFor("独特搜索词").getInt("adopt_accounts"))
 }
 @Test fun ambiguousAliasSearchDoesNotChooseFirstAutomatically() {
  runBlocking {account(a,"synthetic-a");val item=publish();for(title in listOf("共同词甲","共同词乙")){MaterialNaming.saveLocal(a,item,title,null,"explicit",repoA)};MaterialNaming.flush(a,repoA);account(b,"synthetic-b")}
  compose.setContent { SASALELETheme { Surface(Modifier.systemBarsPadding()){TeamMaterialsPanel(b,{}, {},repoB)} } }
  compose.waitUntil(10000){compose.onAllNodesWithText("已同步团队物料").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("搜索团队标题或员工别名").performTextInput("共同词")
  compose.onAllNodesWithText("公共名称").assertCountEquals(1)
  androidx.test.espresso.Espresso.closeSoftKeyboard()
  compose.onNodeWithText("复制").performScrollTo().performClick()
  compose.waitForIdle()
  Assert.assertEquals(0,runBlocking{MaterialNaming.pending(b,repoB)})
  compose.onNodeWithText("保存到本机").performClick()
  compose.onNodeWithText("本机物料标题").assertTextContains("公共名称")
  compose.onNodeWithText("保存到本机").performScrollTo().performClick()
  compose.waitUntil(10000){runBlocking{repoB.allMaterials.first().isNotEmpty() && MaterialNaming.pending(b,repoB)==0}}
  Assert.assertEquals(0,reportFor("共同词甲").getInt("adopt_accounts"));Assert.assertEquals(0,reportFor("共同词乙").getInt("adopt_accounts"))
 }
 @Test fun clickingLocalTitleOpensRenameAndUpdatesSameRecord() {
  runBlocking { repoA.insert(MaterialEntity(title="原本机标题",content="测试内容",type="TEXT",isFavorite=true)) }
  val id=runBlocking{repoA.allMaterials.first().single().id}
  compose.setContent { SASALELETheme { Surface(Modifier.systemBarsPadding()){LocalMaterialsPanel(a,{},repoA)} } }
  compose.waitUntil(5000){compose.onAllNodesWithText("原本机标题").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("原本机标题").performClick()
  compose.onNodeWithText("本机物料标题").assertTextContains("原本机标题").performTextClearance()
  compose.onNodeWithText("本机物料标题").performTextInput("新本机标题")
  androidx.test.espresso.Espresso.closeSoftKeyboard()
  compose.onNodeWithText("保存标题").performClick()
  compose.waitUntil(5000){runBlocking{repoA.byId(id)?.title=="新本机标题"}}
  val saved=runBlocking{repoA.allMaterials.first().single()}
  Assert.assertEquals(id,saved.id);Assert.assertTrue(saved.isFavorite)
 }
 @Test fun clickingTeamTitleRenamesLocalCopyWithoutChangingPublicTitle() {
  runBlocking {account(a,"synthetic-a");publish();account(b,"synthetic-b")}
  compose.setContent { SASALELETheme { Surface(Modifier.systemBarsPadding()){TeamMaterialsPanel(b,{}, {},repoB)} } }
  compose.waitUntil(10000){compose.onAllNodesWithText("已同步团队物料").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithText("公共名称").performClick()
  compose.onNodeWithText("本机物料标题").assertTextContains("公共名称").performTextClearance()
  compose.onNodeWithText("本机物料标题").performTextInput("个人名称")
  compose.onNodeWithText("本机物料标题").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("个人名称")))
  androidx.test.espresso.Espresso.closeSoftKeyboard()
  compose.onNodeWithText("保存到本机").performScrollTo().performClick()
  try {compose.waitUntil(10000){runBlocking{repoB.allMaterials.first().singleOrNull()?.title=="个人名称" && MaterialNaming.pending(b,repoB)==0}}}
  catch(e:Throwable){
   val shot=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
   File(base.getExternalFilesDir(null),"title-failure.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()
   throw AssertionError("Synthetic title result: "+runBlocking{repoB.allMaterials.first().map{it.title}}+" pending="+runBlocking{MaterialNaming.pending(b,repoB)},e)
  }
  Assert.assertEquals("公共名称",runBlocking{TeamMaterials.sync(b).single().title})
 }
 private fun imageFile(context:Context):File {
  val file=File(context.cacheDir,"interaction-$tag.png")
  val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
  file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  return file
 }
 private fun assertPreviewOpensAndReturns(description:String) {
  val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
  val monitor=instrumentation.addMonitor(MaterialImagePreviewActivity::class.java.name,null,false)
  try {
   compose.onNodeWithContentDescription(description).performScrollTo().performClick()
   val activity=instrumentation.waitForMonitorWithTimeout(monitor,10000)
   Assert.assertNotNull("image preview did not open",activity)
   Assert.assertTrue(activity!!.intent.getStringExtra("image_uri").orEmpty().startsWith("content://"))
   Assert.assertTrue(AcquisitionUi.hidden.value)
   fun blueArea():Int {
    val shot=instrumentation.uiAutomation.takeScreenshot()
    val pixels=IntArray(shot.width*shot.height);shot.getPixels(pixels,0,shot.width,0,0,shot.width,shot.height);shot.recycle()
    return pixels.count{(it and 255)>240 && ((it shr 8) and 255)<10 && ((it shr 16) and 255)<10}
   }
   var before=0
   compose.waitUntil(10000){before=blueArea();before>10000}
   compose.onNodeWithContentDescription("放大的素材图片").performTouchInput{doubleClick(center)}
   compose.waitUntil(10000){blueArea()>before+10000}
   androidx.test.espresso.Espresso.pressBack()
   compose.waitUntil(10000){!AcquisitionUi.hidden.value}
   compose.onNodeWithContentDescription(description).assertExists()
  } finally {instrumentation.removeMonitor(monitor)}
 }
 @Test fun localImageOpensPreviewAndReturns() {
  val file=imageFile(a)
  runBlocking{repoA.insert(MaterialEntity(title="本机图片",content=file.absolutePath,type="IMAGE"))}
  compose.setContent { SASALELETheme { Surface(Modifier.systemBarsPadding()){LocalMaterialsPanel(a,{},repoA)} } }
  compose.waitUntil(5000){compose.onAllNodesWithContentDescription("素材图片").fetchSemanticsNodes().isNotEmpty()}
  assertPreviewOpensAndReturns("素材图片")
 }
 @Test fun teamImageOpensPreviewAndReturns() {
  runBlocking {account(a,"synthetic-a");TeamMaterials.upload(a,MaterialEntity(title="团队图片标题",content=imageFile(a).absolutePath,type="IMAGE"),UUID.randomUUID().toString());account(b,"synthetic-b")}
  compose.setContent { SASALELETheme { Surface(Modifier.systemBarsPadding()){TeamMaterialsPanel(b,{}, {},repoB)} } }
  compose.waitUntil(15000){compose.onAllNodesWithContentDescription("团队图片").fetchSemanticsNodes().isNotEmpty()}
  assertPreviewOpensAndReturns("团队图片")
 }
 @Test fun materialPickerRestoresOverlayWhenClosed() {
  AcquisitionUi.hidden.value=false
  var launched:android.content.Intent?=null
  val recordingContext=object:ContextWrapper(base) {
   override fun startActivity(intent:android.content.Intent) { launched=intent }
  }
  AcquisitionUi.open(recordingContext,PickMaterialImageActivity::class.java)
  Assert.assertTrue(AcquisitionUi.hidden.value)
  val intent=checkNotNull(launched)
  Assert.assertEquals(PickMaterialImageActivity::class.java.name,intent.component?.className)
  val scenario=androidx.test.core.app.ActivityScenario.launch<PickMaterialImageActivity>(intent)
  try {Assert.assertTrue(AcquisitionUi.hidden.value)} finally {scenario.close()}
  compose.waitUntil(10000){!AcquisitionUi.hidden.value}
 }
 @Test fun linkingUploadedMaterialKeepsOriginalLocalTitle()=runBlocking {
  account(a,"synthetic-a")
  val repo=MaterialNaming.repository(a)
  val unique="上传前本机标题-$tag"
  repo.insert(MaterialEntity(title=unique,content="上传测试正文",type="TEXT"))
  val local=repo.unlinked().single{it.title==unique}
  try {
   val team=TeamMaterials.upload(a,local,UUID.randomUUID().toString())
   MaterialNaming.linkUploaded(a,local,team.copy(title="服务端公共标题"))
   val linked=repo.byId(local.id)!!
   Assert.assertEquals(unique,linked.title)
   Assert.assertEquals(MaterialNaming.source(a,team.id),linked.teamSourceId)
  } finally {repo.byId(local.id)?.let{repo.delete(it)}}
 }
 @Test fun migrationPreservesV4Rows() {
  val name="naming-migration-$tag.db"
  val config=SupportSQLiteOpenHelper.Configuration.builder(base).name(name).callback(object:SupportSQLiteOpenHelper.Callback(4){
   override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("CREATE TABLE materials (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL, type TEXT NOT NULL, isFavorite INTEGER NOT NULL)");db.execSQL("INSERT INTO materials VALUES (7, '旧标题', '旧内容', 'TEXT', 1)")}
   override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase,oldVersion:Int,newVersion:Int){}
  }).build()
  val helper=FrameworkSQLiteOpenHelperFactory().create(config)
  try {
   val sql=helper.writableDatabase;MIGRATION_4_5.migrate(sql)
   sql.query("SELECT id,title,content,isFavorite,teamSourceId,teamAliasId,titleOrigin FROM materials").use { c ->
    Assert.assertTrue(c.moveToFirst());Assert.assertEquals(7,c.getInt(0));Assert.assertEquals("旧标题",c.getString(1));Assert.assertEquals("旧内容",c.getString(2));Assert.assertEquals(1,c.getInt(3));Assert.assertTrue(c.isNull(4));Assert.assertTrue(c.isNull(5));Assert.assertTrue(c.isNull(6));Assert.assertFalse(c.moveToNext())
   }
   sql.query("SELECT COUNT(*) FROM material_naming_outbox").use{c->Assert.assertTrue(c.moveToFirst());Assert.assertEquals(0,c.getInt(0))}
  } finally {helper.close();base.deleteDatabase(name)}
 }

 @Test fun equalContentDoesNotMergeUntilUserSelectsExistingRecord()=runBlocking {
  account(a,"synthetic-a")
  repoA.insert(MaterialEntity(title="私人名称",content="合成内容",type="TEXT",isFavorite=true))
  val personal=repoA.allMaterials.first().single()
  val first=publish()
  Assert.assertEquals(listOf(personal.id),MaterialNaming.legacyCandidates(first,repoA).map{it.id})
  MaterialNaming.saveLocal(a,first,"独立副本",null,"explicit",repoA)
  Assert.assertEquals(2,repoA.allMaterials.first().size)
  Assert.assertEquals("私人名称",repoA.byId(personal.id)!!.title)
  val second=publish("另一团队记录")
  MaterialNaming.saveLocal(a,second,"确认关联后的名称",null,"explicit",repoA,legacy=personal)
  Assert.assertEquals(2,repoA.allMaterials.first().size)
  val linked=repoA.byTeamSource(MaterialNaming.source(a,second.id))!!
  Assert.assertEquals(personal.id,linked.id);Assert.assertTrue(linked.isFavorite)
 }

 @Test fun failedEvidenceInsertRollsBackLocalTitleInSameTransaction()=runBlocking {
  val source=MaterialNaming.source(a,UUID.randomUUID().toString())
  repoA.saveTeam(MaterialEntity(title="事务前标题",content="合成内容",type="TEXT",teamSourceId=source))
  val old=repoA.byTeamSource(source)!!
  val duplicate=MaterialNamingEvent(id=42,source=source,path="materials/test/aliases",body="{}")
  repoA.addNaming(duplicate)
  try {repoA.saveNamed(old.copy(title="不应保存"),duplicate);Assert.fail("duplicate evidence must fail")}
  catch(_:android.database.sqlite.SQLiteConstraintException){}
  Assert.assertEquals("事务前标题",repoA.byTeamSource(source)!!.title)
  Assert.assertEquals(1,repoA.pendingNaming(source).size)
 }
}
