package com.example.sasalele

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sasalele.data.*
import com.example.sasalele.data.db.AppDatabase
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ReminderLogicTest {

    private lateinit var testScope: kotlinx.coroutines.CoroutineScope
    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var context: Context
    private lateinit var fakeNotifier: FakeNotifier

    class MockTimeProvider : TimeProvider {
        var currentTime = 0L
        override fun currentTimeMillis(): Long = currentTime
    }

    class FakeNotifier : ReminderNotifier {
        val notifiedClients = mutableListOf<ClientKey>()
        override suspend fun notify(clients: List<ClientKey>) {
            notifiedClients.addAll(clients)
        }
    }

    @Before
    fun setup() {
        // Device tests use the real Android main dispatcher.
        testScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "datastore").deleteRecursively()

        mockTimeProvider = MockTimeProvider()
        UnbindScheduler.timeProvider = mockTimeProvider
        ReminderScheduler.timeProvider = mockTimeProvider

        ClientRepository.unbindClient()
        AppDatabase.getDatabase(context).clearAllTables()

        fakeNotifier = FakeNotifier()
        ReminderScheduler.notifier = fakeNotifier
        
        ReminderScheduler.stop()
        val p = ReminderScheduler::class.java.getDeclaredField("_pendingReminders")
        p.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (p.get(ReminderScheduler) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, ClientReminderState>>).value = emptyMap()
    }

    @After
    fun tearDown() {
        ReminderScheduler.stop()
        UnbindScheduler.stop()
        testScope.cancel()
    }

    @Test(timeout = 20000)
    fun testDuplicateMessageIgnoresWaitCountReset() = runBlocking {
        val event1 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        ReminderScheduler.onNewMessage(context, event1)
        var state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertNotNull(state)
        val initialNextScheduleTime = state!!.nextScheduleTime

        // Duplicate message
        val event2 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        ReminderScheduler.onNewMessage(context, event2)
        state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals(1, state!!.messages.size) // deduplicated
        assertEquals(initialNextScheduleTime, state.nextScheduleTime)

        // New message for same client
        val event3 = MessageEvent("msg_2", "wa_1", "client_A", "World", 0L)
        ReminderScheduler.onNewMessage(context, event3)
        state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals(2, state!!.messages.size)
        // should NOT reset nextScheduleTime or counts because it's already active
        assertEquals(initialNextScheduleTime, state.nextScheduleTime)
    }

    @Test(timeout = 20000)
    fun testActiveClientPausedUnbindResumes() = runBlocking {
        val event1 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        ReminderScheduler.onNewMessage(context, event1)

        val clientA = Client("client_A", "wa_1", "A", "Co_A", "H")
        ClientRepository.bindClient(clientA)

        ReminderScheduler.start(context, testScope)
        delay(1500L)

        var state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertTrue(state!!.isMuted)

        ClientRepository.unbindClient()
        delay(1500L)

        state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertFalse(state!!.isMuted)
    }

    @Test(timeout = 20000)
    fun testRepliedOrIgnoreIgnoresOldReplayedMessages() = runBlocking {
        val event1 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        ReminderScheduler.onNewMessage(context, event1)
        ReminderScheduler.applyAction(context, "wa_1", "client_A", ReminderAction.REPLIED)

        var state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals("CLOSED", state!!.status)

        // Replayed old message shouldn't wake it up if same ID
        val event2 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        ReminderScheduler.onNewMessage(context, event2)
        state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals("CLOSED", state!!.status)

        // But new message wakes it up
        val event3 = MessageEvent("msg_2", "wa_1", "client_A", "World", 0L)
        ReminderScheduler.onNewMessage(context, event3)
        state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals("ACTIVE", state!!.status)
    }

    @Test(timeout = 20000)
    fun testLaterDelaysExecution() = runBlocking {
        val event1 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        ReminderScheduler.onNewMessage(context, event1)

        mockTimeProvider.currentTime = 1000L
        ReminderScheduler.applyAction(context, "wa_1", "client_A", ReminderAction.LATER)
        
        delay(500L)

        val state = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals("ACTIVE", state!!.status)
        assertTrue(state.nextScheduleTime > 1000L)
    }

    @Test(timeout = 20000)
    fun testFirstWaitIntervalAndMaxRetryLimitsLogic() = runBlocking {
        val event1 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", 0L)
        mockTimeProvider.currentTime = 0L
        ReminderScheduler.onNewMessage(context, event1)

        ReminderScheduler.start(context, testScope)

        mockTimeProvider.currentTime = 6000L // Exceeds default firstWaitTime of 5000L
        delay(1500L)

        val stateAfterFirst = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals(1, stateAfterFirst!!.retryCount)
        assertEquals(1, fakeNotifier.notifiedClients.size)

        mockTimeProvider.currentTime = 6000L + 16000L // Exceeds default reminderInterval of 15000L
        delay(1500L)
        
        val stateAfterSecond = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals(2, stateAfterSecond!!.retryCount)
        assertEquals(2, fakeNotifier.notifiedClients.size)

        mockTimeProvider.currentTime = 22000L + 16000L
        delay(1500L)

        val stateAfterThird = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals(3, stateAfterThird!!.retryCount)
        assertEquals(3, fakeNotifier.notifiedClients.size)
        assertTrue(stateAfterThird.nextScheduleTime > 0L)

        mockTimeProvider.currentTime = 54000L
        delay(1500L)
        val stateAfterFourth = ReminderScheduler.pendingReminders.value[ReminderRules.key("wa_1", "client_A")]
        assertEquals(4, stateAfterFourth!!.retryCount) // First notification plus three additional notifications.
        assertEquals(0L, stateAfterFourth.nextScheduleTime)
        assertEquals(4, fakeNotifier.notifiedClients.size)
        assertEquals("ACTIVE", stateAfterFourth.status)
        mockTimeProvider.currentTime = 100000L
        delay(1500L)
        assertEquals(4, fakeNotifier.notifiedClients.size)
    }

    @Test(timeout = 20000)
    fun testUnbindCatchUpLogic() = runBlocking {
        val settingsDataStore = SettingsDataStore(context)
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        
        val pastUnbindCal = Calendar.getInstance(timeZone).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val pastUnbindTime = pastUnbindCal.timeInMillis
        settingsDataStore.setLastUnbindTime(pastUnbindTime)
        
        val currentCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = pastUnbindTime
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }
        mockTimeProvider.currentTime = currentCal.timeInMillis
        
        val client = Client("client_B", "wa_1", "B", "Co_B", "H", boundTimestamp = pastUnbindCal.timeInMillis + 1000)
        ClientRepository.restoreClient(client)
        
        assertEquals("client_B", ClientRepository.currentBoundClient.value?.id)
        
        UnbindScheduler.start(context, testScope)
        delay(2000L)
        
        assertNull(ClientRepository.currentBoundClient.value)
    }
}