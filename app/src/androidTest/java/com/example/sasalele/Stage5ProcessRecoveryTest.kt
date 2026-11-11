package com.example.sasalele
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import com.example.sasalele.data.db.AppDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class Stage5ProcessRecoveryTest {
 private val context:Context=ApplicationProvider.getApplicationContext()
 private val clock=object:TimeProvider { override fun currentTimeMillis()=1791765000000L }
 @Test fun seed()=runBlocking<Unit> { withTimeout(10000) { ReminderScheduler.timeProvider=clock; ReminderScheduler.onNewMessage(context,MessageEvent("process-fixture","stage5-process","X","synthetic",1791678600000L)); context.getSharedPreferences("stage5-process",0).edit().putInt("seedPid",Process.myPid()).commit(); assertNotNull(AppDatabase.getDatabase(context).reminderDao().getAllReminders().find { it.workAccountId=="stage5-process" }) } }
 @Test fun restore()=runBlocking<Unit> { withTimeout(10000) { val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default); try { val seedPid=context.getSharedPreferences("stage5-process",0).getInt("seedPid",-1); assertTrue(seedPid>0); assertNotEquals(seedPid,Process.myPid()); assertTrue(ReminderScheduler.pendingReminders.value.isEmpty()); ReminderScheduler.timeProvider=clock; ReminderScheduler.notifier=object:ReminderNotifier { override suspend fun notify(clients:List<ClientKey>) {} }; ReminderScheduler.start(context,scope); delay(300); assertNotNull(ReminderScheduler.pendingReminders.value[ReminderRules.key("stage5-process","X")]); android.util.Log.i("Stage5Process","restored seedPid=$seedPid restorePid=${Process.myPid()}") } finally { ReminderScheduler.stop(); scope.cancel(); AppDatabase.getDatabase(context).reminderDao().deleteReminder(ReminderRules.key("stage5-process","X")); context.getSharedPreferences("stage5-process",0).edit().clear().commit() } } }
}
