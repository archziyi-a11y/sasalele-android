package com.example.sasalele

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.sasalele.data.Client
import com.example.sasalele.data.ClientRepository
import com.example.sasalele.data.MessageEvent
import com.example.sasalele.data.ReminderAction
import com.example.sasalele.data.ReminderScheduler
import com.example.sasalele.data.TimeProvider
import com.example.sasalele.data.UnbindScheduler
import com.example.sasalele.data.db.AppDatabase
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Calendar
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SasaleleTests {

    private val testDispatcher = StandardTestDispatcher()
    private class MockTimeProvider : TimeProvider {
        var currentTime = 0L
        override fun currentTimeMillis(): Long = currentTime
    }

    private lateinit var mockTimeProvider: MockTimeProvider
    private lateinit var context: Context

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "datastore").deleteRecursively()
        
        mockTimeProvider = MockTimeProvider()
        UnbindScheduler.timeProvider = mockTimeProvider
        ReminderScheduler.timeProvider = mockTimeProvider
        
        // Reset singletons for tests
        ClientRepository.unbindClient()
        AppDatabase.getDatabase(context).clearAllTables()
        
        val f = ReminderScheduler::class.java.getDeclaredField("isInitialized")
        f.isAccessible = true
        f.set(ReminderScheduler, false)
        
        val jobF = ReminderScheduler::class.java.getDeclaredField("schedulerJob")
        jobF.isAccessible = true
        (jobF.get(ReminderScheduler) as? kotlinx.coroutines.Job)?.cancel()
        jobF.set(ReminderScheduler, null)
    }

    @After
    fun tearDown() {
        UnbindScheduler.stop()
        val jobF = ReminderScheduler::class.java.getDeclaredField("schedulerJob")
        jobF.isAccessible = true
        (jobF.get(ReminderScheduler) as? kotlinx.coroutines.Job)?.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun testReminderDeduplicationAndExcludeActiveClient() { runBlocking {
        val clientA = Client("client_A", "wa_1", "A", "Co_A", "H")
        
        // 1. New message for client A
        val event1 = MessageEvent("msg_1", "wa_1", "client_A", "Hello", System.currentTimeMillis())
        ReminderScheduler.onNewMessage(context, event1)
        
        var state = ReminderScheduler.pendingReminders.value["wa_1_client_A"]
        assertNotNull(state)
        assertEquals(1, state?.messages?.size)
        
        // 2. Another message for client A (deduplication/merging)
        val event2 = MessageEvent("msg_2", "wa_1", "client_A", "World", System.currentTimeMillis())
        ReminderScheduler.onNewMessage(context, event2)
        
        // Test duplicate message id
        ReminderScheduler.onNewMessage(context, event2)
        
        state = ReminderScheduler.pendingReminders.value["wa_1_client_A"]
        assertEquals(2, state?.messages?.size)
        assertEquals(false, state?.isMuted)
        
        // 3. Bind client A -> should mute
        ClientRepository.bindClient(clientA)
        ReminderScheduler.start(context, this)
        delay(1000)
        
        state = ReminderScheduler.pendingReminders.value["wa_1_client_A"]
        assertEquals(true, state?.isMuted)
        
        // 4. Unbind -> should unmute
        ClientRepository.unbindClient()
        delay(1000)
        
        state = ReminderScheduler.pendingReminders.value["wa_1_client_A"]
        assertEquals(false, state?.isMuted)
        
        // 5. Action REPLIED sets status to CLOSED
        ReminderScheduler.handleAction("wa_1", "client_A", ReminderAction.REPLIED)
        state = ReminderScheduler.pendingReminders.value["wa_1_client_A"]
        assertEquals("CLOSED", state?.status)
        
        // Cancel the loop job so runBlocking can exit
        val jobF = ReminderScheduler::class.java.getDeclaredField("schedulerJob")
        jobF.isAccessible = true
        (jobF.get(ReminderScheduler) as? kotlinx.coroutines.Job)?.cancel()
    } }

    @Test
    fun testUnbindCatchupLogic() { runBlocking {
        val settingsDataStore = SettingsDataStore(context)
        val timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        
        // Setup past unbind time
        val pastUnbindCal = Calendar.getInstance(timeZone).apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }
        val pastUnbindTime = pastUnbindCal.timeInMillis
        settingsDataStore.setLastUnbindTime(pastUnbindTime)
        
        // Setup current time to be after 18:30
        val currentCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = pastUnbindTime
            set(Calendar.HOUR_OF_DAY, 19)
            set(Calendar.MINUTE, 0)
        }
        mockTimeProvider.currentTime = currentCal.timeInMillis
        
        // Bound client BEFORE the 18:30 threshold
        val client = Client("client_B", "wa_1", "B", "Co_B", "H", boundTimestamp = pastUnbindCal.timeInMillis + 1000)
        ClientRepository.restoreClient(client) // use restore so we don't overwrite timestamp
        
        // Verify client is bound
        assertEquals("client_B", ClientRepository.currentBoundClient.value?.id)
        
        // Start scheduler, catch up logic should trigger Unbind
        UnbindScheduler.start(context, this)
        
        // Wait for datastore first() and catch up execution
        delay(2000)
        
        // Client should be unbound
        assertNull(ClientRepository.currentBoundClient.value)
        UnbindScheduler.stop()
    } }
    
    @Test
    fun testLeaderboardViewDoesNotUnbindActiveClient() { runBlocking {
        val activeClient = Client("client_Active", "wa_1", "Active", "Co", "H")
        ClientRepository.bindClient(activeClient)
        
        assertEquals("client_Active", ClientRepository.currentBoundClient.value?.id)
        
        // User clicks leaderboard client B
        val viewingClient = Client("client_B", "wa_2", "B", "Co", "M")
        ClientRepository.viewProfile(viewingClient)
        
        // Active client should still be bound
        assertEquals("client_Active", ClientRepository.currentBoundClient.value?.id)
        
        // Profile viewing target should be client B
        assertEquals("client_B", ClientRepository.profileViewingTarget.value?.id)
    } }
}
