package com.example.sasalele.data

import android.content.Context
import com.example.sasalele.data.preferences.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

interface TimeProvider {
    fun currentTimeMillis(): Long
}

object DefaultTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

object UnbindScheduler {
    var timeProvider: TimeProvider = DefaultTimeProvider
    private var job: Job? = null
    private var ready = kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
    suspend fun awaitReady() { ready.await() }
    private var syncJob: Job? = null

    fun start(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.Default)) {
        job?.cancel()
        syncJob?.cancel()
        
        ready = kotlinx.coroutines.CompletableDeferred()
        job = scope.launch {
            val settingsDataStore = SettingsDataStore(context)
            
            // Restore from persistence
            val boundClientJson = settingsDataStore.boundClientJsonFlow.first()
            if (boundClientJson != null) {
                try {
                    val obj = JSONObject(boundClientJson)
                    val restoredClient = Client(
                        id = obj.getString("id"),
                        workAccountId = obj.getString("workAccountId"),
                        name = obj.getString("name"),
                        company = obj.getString("company"),
                        intentLevel = obj.getString("intentLevel"),
                        avatarUrl = if (obj.has("avatarUrl")) obj.getString("avatarUrl") else null,
                        boundTimestamp = obj.getLong("boundTimestamp")
                    )
                    // We only set it if we don't already have one in memory
                    if (ClientRepository.currentBoundClient.value == null && WorkAccountSelection.allows(restoredClient.workAccountId)) {
                        ClientRepository.restoreClient(restoredClient)
                    }
                } catch (e: Exception) {}
            }

            // Sync flow to datastore
            syncJob = launch {
                ClientRepository.currentBoundClient.collect { client ->
                    if (client == null) {
                        settingsDataStore.setBoundClientJson(null)
                    } else {
                        val obj = JSONObject()
                        obj.put("id", client.id)
                        obj.put("workAccountId", client.workAccountId)
                        obj.put("name", client.name)
                        obj.put("company", client.company)
                        obj.put("intentLevel", client.intentLevel)
                        client.avatarUrl?.let { obj.put("avatarUrl", it) }
                        obj.put("boundTimestamp", client.boundTimestamp)
                        settingsDataStore.setBoundClientJson(obj.toString())
                    }
                }
            }
            
            while (true) {
                val currentTime = timeProvider.currentTimeMillis()
                val last = settingsDataStore.lastUnbindTimeFlow.first()
                val point = ReminderRules.latestUnbindPoint(currentTime)
                if (point > last) {
                    val client = ClientRepository.currentBoundClient.value
                    if (client != null && client.boundTimestamp < point) {
                        ClientRepository.unbindClient()
                        settingsDataStore.setBoundClientJson(null)
                    }
                    settingsDataStore.setLastUnbindTime(point)
                }
                ready.complete(Unit)
                delay(1000L)
            }

        }
    }

    fun stop() {
        job?.cancel()
        ready.cancel()
        ready = kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
        syncJob?.cancel()
        job = null
        syncJob = null
    }

}
