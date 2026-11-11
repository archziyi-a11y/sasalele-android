package com.example.sasalele
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import com.example.sasalele.data.db.*
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.TimeZone
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase

@RunWith(AndroidJUnit4::class)
class Stage5IntegrationTest {
 private val context: Context = ApplicationProvider.getApplicationContext()
 private var now = t("2026-10-12 08:29")
 private val clock = object: TimeProvider { override fun currentTimeMillis() = now }
 private val outputs = mutableListOf<List<ClientKey>>()
 private fun t(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.parse(s)!!.time
 private fun state(account: String="A",client: String="X") = ReminderScheduler.pendingReminders.value[ReminderRules.key(account,client)]!!
 private fun test(body: suspend CoroutineScope.() -> Unit) = runBlocking {
  withTimeout(20000) {
   val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
   try {
    ReminderScheduler.stop(); UnbindScheduler.stop(); delay(100)
    ReminderScheduler.clearMemoryForRecovery(); ClientRepository.unbindClient()
    AppDatabase.getDatabase(context).clearAllTables()
    val settings = SettingsDataStore(context)
    settings.setMaxRetryCount(0); settings.setFirstWaitTime(5000); settings.setReminderInterval(15000)
    settings.setLastUnbindTime(0); settings.setBoundClientJson(null)
    ReminderScheduler.timeProvider=clock; UnbindScheduler.timeProvider=clock
    outputs.clear(); ReminderScheduler.notifier=object: ReminderNotifier { override suspend fun notify(clients: List<ClientKey>) { outputs.add(clients) } }
    body(scope)
   } finally { ReminderScheduler.stop(); UnbindScheduler.stop(); scope.cancel(); delay(100); ReminderScheduler.timeProvider=DefaultTimeProvider; UnbindScheduler.timeProvider=DefaultTimeProvider; ReminderScheduler.notifier=null }
  }
 }
 private suspend fun message(account: String="A",client: String="X",id: String="m",time: Long=t("2026-10-11 12:00")) = ReminderScheduler.onNewMessage(context,MessageEvent(id,account,client,"synthetic",time))
 @Test fun retry0() = retry(0)
 @Test fun retry1() = retry(1)
 @Test fun retry3() = retry(3)
 private fun retry(n: Int) = test { SettingsDataStore(context).setMaxRetryCount(n); message(time=now); repeat(n+1) { now+=16000; ReminderScheduler.evaluate(context) }; assertEquals(n+1,state().retryCount); assertEquals(0,state().nextScheduleTime); now+=16000; ReminderScheduler.evaluate(context); assertEquals(n+1,outputs.size) }
 @Test fun mergedDailyAndRegularAndPersistence() = test {
  message(); now=t("2026-10-12 08:30"); ReminderScheduler.evaluate(context)
  assertEquals(1,outputs.size); assertEquals(1,state().retryCount); val daily=state().lastDailyReminderTime
  ReminderScheduler.start(context,this); delay(200); ReminderScheduler.stop(); delay(100); ReminderScheduler.clearMemoryForRecovery()
  ReminderScheduler.start(context,this); delay(200); ReminderScheduler.evaluate(context)
  assertEquals(daily,state().lastDailyReminderTime); assertEquals(1,outputs.size)
  now=t("2026-10-15 10:00"); ReminderScheduler.evaluate(context); assertEquals(2,outputs.size); assertEquals(1,state().retryCount)
 }
 @Test fun boundPauseUnbindDailyCatchupAndAccountIsolation() = test {
  ClientRepository.restoreClient(Client("X","A","A","Co","H",boundTimestamp=now)); message(); message("B")
  ReminderScheduler.start(context,this); delay(200); now=t("2026-10-12 08:30"); ReminderScheduler.evaluate(context)
  assertEquals(listOf(ClientKey("B","X")),outputs.flatten()); assertEquals(0,state().lastDailyReminderTime)
  ClientRepository.unbindClient(); delay(300); assertTrue(outputs.flatten().contains(ClientKey("A","X"))); assertTrue(state().lastDailyReminderTime>0)
 }
 @Test fun repliedReplay() = replay(ReminderAction.REPLIED)
 @Test fun ignoredReplay() = replay(ReminderAction.IGNORE)
 private fun replay(action: ReminderAction) = test { message(); ReminderScheduler.applyAction(context,"A","X",action); message(); assertEquals("CLOSED",state().status); now=t("2026-10-12 10:00"); ReminderScheduler.evaluate(context); assertTrue(outputs.isEmpty()); message(id="new",time=now); assertEquals("ACTIVE",state().status); ReminderScheduler.evaluate(context); assertTrue(outputs.isEmpty()) }
 @Test fun outputFailureRetried() = test { message(); now=t("2026-10-12 08:30"); ReminderScheduler.notifier=object:ReminderNotifier { override suspend fun notify(clients:List<ClientKey>) { error("synthetic failure") } }; ReminderScheduler.evaluate(context); assertEquals(0,state().lastDailyReminderTime); assertEquals(0,state().retryCount); ReminderScheduler.notifier=object:ReminderNotifier { override suspend fun notify(clients:List<ClientKey>) { outputs.add(clients) } }; ReminderScheduler.evaluate(context); assertEquals(1,outputs.size) }
 @Test fun stopStartNoDuplicateSubscription() = test { message(time=now); repeat(3) { ReminderScheduler.start(context,this); ReminderScheduler.start(context,this); delay(100); ReminderScheduler.stop(); delay(100) }; now+=6000; ReminderScheduler.start(context,this); delay(300); assertEquals(1,outputs.size); ReminderScheduler.stop(); now+=20000; ClientRepository.unbindClient(); delay(1200); assertEquals(1,outputs.size) }
 @Test fun old1830() = unbind("2026-10-12 18:00","2026-10-12 18:35",true)
 @Test fun new1830() = unbind("2026-10-12 18:31","2026-10-12 18:35",false)
 @Test fun old2200() = unbind("2026-10-12 21:00","2026-10-12 22:05",true)
 @Test fun new2200() = unbind("2026-10-12 22:01","2026-10-12 22:05",false)
 @Test fun crossDayRecovery() = unbind("2026-10-12 21:00","2026-10-13 08:00",true)
 private fun unbind(bound: String,current: String,removed: Boolean) = test { now=t(current); ClientRepository.restoreClient(Client("X","A","A","Co","H",boundTimestamp=t(bound))); UnbindScheduler.start(context,this); delay(300); assertEquals(removed,ClientRepository.currentBoundClient.value==null) }
 @Test fun dailyDoesNotChangeRegularSchedule() = test { message(); now=t("2026-10-12 08:29"); now+=6000; ReminderScheduler.evaluate(context); val before=state(); now=t("2026-10-12 08:30"); ReminderScheduler.evaluate(context); assertEquals(before.retryCount,state().retryCount); assertEquals(before.nextScheduleTime,state().nextScheduleTime); assertEquals(2,outputs.size) }
 @Test fun consecutiveDailyPersistence() = test { message(); for(day in 12..14) { now=t("2026-10-$day 08:30"); ReminderScheduler.evaluate(context); ReminderScheduler.evaluate(context) }; assertEquals(3,outputs.size); assertEquals(1,state().retryCount) }
 @Test fun persistedBindingFirstRecovery() = test { now=t("2026-10-12 18:35"); SettingsDataStore(context).setBoundClientJson("""{"id":"X","workAccountId":"A","name":"A","company":"Co","intentLevel":"H","boundTimestamp":${t("2026-10-12 18:00")}}"""); ClientRepository.unbindClient(); UnbindScheduler.start(context,this); delay(300); assertNull(ClientRepository.currentBoundClient.value) }
 @Test fun guidanceAccountIsolation() = test { val dao=AppDatabase.getDatabase(context).guidanceMessageDao(); dao.insertMessage(GuidanceMessageEntity(clientId="X",workAccountId="A",content="synthetic A",isFromUser=false,timestamp=now)); dao.insertMessage(GuidanceMessageEntity(clientId="X",workAccountId="B",content="synthetic B",isFromUser=false,timestamp=now)); val a=dao.getMessagesForClient("X","A").first(); assertEquals(1,a.size); assertEquals("A",a.single().workAccountId) }
 @Test fun newTodayDoesNotReviveResolvedYesterdayDaily() = test { message(); ReminderScheduler.applyAction(context,"A","X",ReminderAction.REPLIED); message(id="today",time=now); now=t("2026-10-12 08:30"); ReminderScheduler.evaluate(context); assertEquals(0,state().lastDailyReminderTime); assertEquals(1,state().retryCount) }
 @Test fun migrationPreservesRows() {
  val helper=FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(null).callback(object:SupportSQLiteOpenHelper.Callback(3) {
   override fun onCreate(db:SupportSQLiteDatabase) { MIGRATION_2_3.migrate(db); db.execSQL("CREATE TABLE materials (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, content TEXT NOT NULL, type TEXT NOT NULL, isFavorite INTEGER NOT NULL)"); db.execSQL("CREATE TABLE guidance_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, clientId TEXT NOT NULL, workAccountId TEXT NOT NULL, content TEXT NOT NULL, isFromUser INTEGER NOT NULL, timestamp INTEGER NOT NULL)"); db.execSQL("INSERT INTO materials VALUES (1,'title','material','TEXT',1)"); db.execSQL("INSERT INTO guidance_messages VALUES (1,'X','A','guidance',0,123)"); db.execSQL("INSERT INTO reminders VALUES ('A_X','A','X',4,0,'ACTIVE','[]')") }
   override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {}
  }).build())
  try { val db=helper.writableDatabase; MIGRATION_3_4.migrate(db); db.query("SELECT * FROM reminders").use { assertTrue(it.moveToFirst()); assertEquals(ReminderRules.key("A","X"),it.getString(it.getColumnIndexOrThrow("id"))); assertEquals(4,it.getInt(it.getColumnIndexOrThrow("retryCount"))); assertEquals(0,it.getLong(it.getColumnIndexOrThrow("lastDailyReminderTime"))) }; for(table in listOf("materials","guidance_messages")) db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); assertEquals(1,it.getInt(0)) } } finally { helper.close() }
 }
}
