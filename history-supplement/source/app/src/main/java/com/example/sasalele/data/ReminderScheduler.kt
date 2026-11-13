package com.example.sasalele.data

import android.content.Context
import com.example.sasalele.data.db.AppDatabase
import com.example.sasalele.data.db.ReminderEntity
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import android.util.Log
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

data class MessageEvent(
    val id: String,
    val workAccountId: String,
    val clientId: String,
    val content: String,
    val timestamp: Long,
    val firstObservedAt: Long = 0L,
    val resolved: Boolean = false
)

enum class ReminderAction {
    REPLIED, LATER, IGNORE
}

data class ClientReminderState(
    val workAccountId: String,
    val clientId: String,
    val messages: List<MessageEvent>,
    val retryCount: Int = 0,
    val nextScheduleTime: Long = 0L,
    val isMuted: Boolean = false,
    val status: String = "ACTIVE",
    val lastDailyReminderTime: Long = 0L,
    val closedAt: Long = 0L
)

object ReminderScheduler {
    private val _pendingReminders = MutableStateFlow<Map<String, ClientReminderState>>(emptyMap())
    val pendingReminders: StateFlow<Map<String, ClientReminderState>> = _pendingReminders.asStateFlow()

    private val _pendingReminderCount = MutableStateFlow(0)
    val pendingReminderCount: StateFlow<Int> = _pendingReminderCount.asStateFlow()

    private val mutex = Mutex()
    private var ownedScope: CoroutineScope? = null
    private var schedulerJob: Job? = null
    private var bindingJob: Job? = null
    var timeProvider: TimeProvider = DefaultTimeProvider
    private var dbInitJob: Job? = null
    var notifier: ReminderNotifier? = null
    
    fun start(context: Context, coroutineScope: CoroutineScope) {
        if (schedulerJob?.isActive == true) return
        val owner = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))
        ownedScope = owner

        val settingsDataStore = SettingsDataStore(context)
        val dao = AppDatabase.getDatabase(context).reminderDao()
        if (notifier == null) {
            notifier = AndroidReminderNotifier(context, settingsDataStore)
        }
        
        dbInitJob = owner.launch {
            UnbindScheduler.awaitReady()
            mutex.withLock {
            val entities = dao.getAllReminders()
            val map = mutableMapOf<String, ClientReminderState>()
            entities.forEach { entity ->
                val messages = mutableListOf<MessageEvent>()
                try {
                    val array = JSONArray(entity.messagesJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        messages.add(
                            MessageEvent(
                                id = obj.getString("id"),
                                workAccountId = obj.getString("workAccountId"),
                                clientId = obj.getString("clientId"),
                                content = obj.getString("content"),
                                timestamp = obj.getLong("timestamp"),
                                firstObservedAt = obj.optLong("firstObservedAt", 0L),
                                resolved = obj.optBoolean("resolved", entity.status == "CLOSED")
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("ReminderScheduler", "Unable to decode persisted reminder ${entity.id}", e)
                }
                val boundClient = ClientRepository.currentBoundClient.value
                val isMuted = boundClient != null && boundClient.id == entity.clientId && boundClient.workAccountId == entity.workAccountId
                map[ReminderRules.key(entity.workAccountId, entity.clientId)] = ClientReminderState(
                    workAccountId = entity.workAccountId,
                    clientId = entity.clientId,
                    messages = messages,
                    retryCount = entity.retryCount,
                    nextScheduleTime = entity.nextScheduleTime,
                    isMuted = isMuted,
                    status = entity.status,
                    lastDailyReminderTime = entity.lastDailyReminderTime,
                    closedAt = entity.closedAt
                )
            }
            updateReminders(map)
        } }

        bindingJob = owner.launch {
            ClientRepository.currentBoundClient.collect { boundClient ->
                dbInitJob?.join()
                mutex.withLock {
                    val map = _pendingReminders.value.mapValues { (_, state) ->
                        state.copy(isMuted = boundClient?.key == ClientKey(state.workAccountId, state.clientId))
                    }
                    updateReminders(map)
                }
                evaluate(context)

            }
        }

        schedulerJob = owner.launch {
            dbInitJob?.join()
            while (true) {
                delay(1000)
                evaluate(context)
            }
        }
    }

    fun stop() {
        ownedScope?.coroutineContext?.get(Job)?.cancel()
        ownedScope = null
        bindingJob?.cancel()
        bindingJob = null
        schedulerJob?.cancel()
        schedulerJob = null
        dbInitJob?.cancel()
        dbInitJob = null
    }

    private suspend fun saveToDb(dao: com.example.sasalele.data.db.ReminderDao, id: String, state: ClientReminderState) {
        val array = JSONArray()
        state.messages.forEach { msg ->
            val obj = JSONObject()
            obj.put("id", msg.id)
            obj.put("workAccountId", msg.workAccountId)
            obj.put("clientId", msg.clientId)
            obj.put("content", msg.content)
            obj.put("timestamp", msg.timestamp)
            obj.put("firstObservedAt", msg.firstObservedAt)
            obj.put("resolved", msg.resolved)
            array.put(obj)
        }
        val entity = ReminderEntity(
            id = id,
            workAccountId = state.workAccountId,
            clientId = state.clientId,
            retryCount = state.retryCount,
            nextScheduleTime = state.nextScheduleTime,
            status = state.status,
            messagesJson = array.toString(),
            lastDailyReminderTime = state.lastDailyReminderTime,
            closedAt = state.closedAt
        )
        dao.insertOrUpdate(entity)
    }

    suspend fun onNewMessage(context: Context, event: MessageEvent) {
        dbInitJob?.join()
        mutex.withLock {
        val dao = AppDatabase.getDatabase(context).reminderDao()
        val settingsDataStore = SettingsDataStore(context)
        val firstWaitTime = settingsDataStore.firstWaitTimeFlow.first()
        
        val id = ReminderRules.key(event.workAccountId, event.clientId)
        val currentMap = _pendingReminders.value.toMutableMap()
        val existingState = currentMap[id]
        val boundClient = ClientRepository.currentBoundClient.value
        val isCurrentlyBound = boundClient != null && boundClient.id == event.clientId && boundClient.workAccountId == event.workAccountId
        
        if (existingState != null) {
            if (existingState.messages.any { it.id == event.id }) {
                // Deduplicate by messageId
                return@withLock
            }
            if (existingState.status == "CLOSED") {
                val newMessages = existingState.messages + event.copy(firstObservedAt = timeProvider.currentTimeMillis())
                val newState = existingState.copy(
                    messages = newMessages,
                    retryCount = 0,
                    nextScheduleTime = timeProvider.currentTimeMillis() + firstWaitTime,
                    isMuted = isCurrentlyBound,
                    status = "ACTIVE"
                )
                currentMap[id] = newState
                saveToDb(dao, id, newState)
            } else {
                val newMessages = existingState.messages + event.copy(firstObservedAt = timeProvider.currentTimeMillis())
                val newState = existingState.copy(
                    messages = newMessages,
                    isMuted = isCurrentlyBound
                    // Do not reset waits or counts for identical messages, and here we just append without resetting
                )
                currentMap[id] = newState
                saveToDb(dao, id, newState)
            }
        } else {
            val newState = ClientReminderState(
                workAccountId = event.workAccountId,
                clientId = event.clientId,
                messages = listOf(event.copy(firstObservedAt = timeProvider.currentTimeMillis())),
                retryCount = 0,
                nextScheduleTime = timeProvider.currentTimeMillis() + firstWaitTime,
                isMuted = isCurrentlyBound,
                status = "ACTIVE"
            )
            currentMap[id] = newState
            saveToDb(dao, id, newState)
        }
        updateReminders(currentMap)
        }
    }
    
    fun handleAction(workAccountId: String, clientId: String, action: ReminderAction, context: Context? = null) {
        if (context == null) {
            val id = ReminderRules.key(workAccountId, clientId)
            val state = _pendingReminders.value[id] ?: return
            if (action != ReminderAction.LATER) updateReminders(_pendingReminders.value +
                (id to state.copy(status = "CLOSED", nextScheduleTime = 0L, closedAt = timeProvider.currentTimeMillis(),
                    messages = state.messages.map { it.copy(resolved = true) })))
            return
        }
        // UI starts the service before actions; operations share its cancellable owner.
        ownedScope?.launch { applyAction(context, workAccountId, clientId, action) }
    }

    suspend fun applyAction(context: Context, workAccountId: String, clientId: String, action: ReminderAction) {
        dbInitJob?.join()
        mutex.withLock {
            val id = ReminderRules.key(workAccountId, clientId)
            val state = _pendingReminders.value[id] ?: return@withLock
            val updated = if (action == ReminderAction.LATER) state.copy(
                nextScheduleTime = timeProvider.currentTimeMillis() + SettingsDataStore(context).reminderIntervalFlow.first()
            ) else state.copy(status = "CLOSED", nextScheduleTime = 0L, closedAt = timeProvider.currentTimeMillis(),
                    messages = state.messages.map { it.copy(resolved = true) })
            saveToDb(AppDatabase.getDatabase(context).reminderDao(), id, updated)
            updateReminders(_pendingReminders.value + (id to updated))
        }
    }

    suspend fun evaluate(context: Context) {
        dbInitJob?.join()
        mutex.withLock {
            val now = timeProvider.currentTimeMillis()
            val settings = SettingsDataStore(context)
            val limit = settings.maxRetryCountFlow.first().coerceAtLeast(0).toLong() + 1L
            val interval = settings.reminderIntervalFlow.first()
            if (notifier is AndroidReminderNotifier && !settings.enableRemindersFlow.first()) return@withLock
            val map = _pendingReminders.value.toMutableMap()
            val updates = mutableMapOf<String, ClientReminderState>()
            val clients = mutableListOf<ClientKey>()
            map.forEach { (id, state) ->
                if (state.status == "ACTIVE" && !state.isMuted) {
                    val regular = state.nextScheduleTime > 0 && now >= state.nextScheduleTime && state.retryCount < limit
                    val daily = ReminderRules.dailyDue(now, state.lastDailyReminderTime,
                        state.messages.filter { !it.resolved }.map { it.timestamp }, 0L)
                    if (regular || daily) {
                        val count = state.retryCount + if (regular) 1 else 0
                        updates[id] = state.copy(retryCount = count,
                            nextScheduleTime = if (!regular) state.nextScheduleTime else if (count >= limit) 0L else now + interval,
                            lastDailyReminderTime = if (daily) now else state.lastDailyReminderTime)
                        clients.add(ClientKey(state.workAccountId, state.clientId))
                    }
                }
            }
            if (clients.isEmpty()) return@withLock
            try {
                val output = notifier ?: return@withLock
                output.notify(clients)
                // At-least-once recovery: output first, persist only after success.
                val database = AppDatabase.getDatabase(context)
                database.withTransaction { updates.forEach { (id, state) -> saveToDb(database.reminderDao(), id, state) } }
                map.putAll(updates)
                updateReminders(map)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e("ReminderScheduler", "Reminder output/persistence failed; retry next evaluation", e) }
        }
    }

    fun clearMemoryForRecovery() {
        check(schedulerJob == null) { "Stop scheduler before clearing memory" }
        updateReminders(emptyMap())
    }

    private fun updateReminders(newMap: Map<String, ClientReminderState>) {
        _pendingReminders.value = newMap
        _pendingReminderCount.value = newMap.values.count { it.status == "ACTIVE" && !it.isMuted }
    }
}
