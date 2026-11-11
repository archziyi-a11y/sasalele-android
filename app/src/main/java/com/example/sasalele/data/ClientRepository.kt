package com.example.sasalele.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ClientKey(val workAccountId: String, val clientId: String)

data class Client(
    val id: String,
    val workAccountId: String,
    val name: String,
    val company: String,
    val intentLevel: String,
    val avatarUrl: String? = null,
    val boundTimestamp: Long = 0L
) {
    val key: ClientKey
        get() = ClientKey(workAccountId, id)
}

object ClientRepository {
    private val _currentBoundClient = MutableStateFlow<Client?>(null)
    val currentBoundClient: StateFlow<Client?> = _currentBoundClient.asStateFlow()

    private val _profileViewingTarget = MutableStateFlow<Client?>(null)
    val profileViewingTarget: StateFlow<Client?> = _profileViewingTarget.asStateFlow()

    fun bindClient(client: Client) {
        check(WorkAccountSelection.current.value==null || WorkAccountSelection.allows(client.workAccountId)) {"客户不属于当前工作号"}
        _currentBoundClient.value = client.copy(boundTimestamp = System.currentTimeMillis())
    }

    fun restoreClient(client: Client) {
        _currentBoundClient.value = client
    }

    fun unbindClient() {
        _currentBoundClient.value = null
    }

    fun viewProfile(client: Client?) {
        _profileViewingTarget.value = client
    }
}
