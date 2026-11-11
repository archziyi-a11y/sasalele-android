package com.example.sasalele.data

import kotlinx.coroutines.delay

enum class OcrResultState {
    SUCCESS,
    MULTI_CANDIDATE,
    NO_MATCH,
    NO_CLUE,
    NON_PRIVATE_CHAT
}

sealed class OcrResult {
    data class Success(val client: Client) : OcrResult()
    data class MultiCandidate(val candidates: List<Client>) : OcrResult()
    object NoMatch : OcrResult()
    object NoClue : OcrResult()
    object NonPrivateChat : OcrResult()
}

interface MockOcrEngine {
    var currentState: OcrResultState
    suspend fun recognize(imageUri: String): OcrResult
}

class MockOcrEngineImpl : MockOcrEngine {
    override var currentState: OcrResultState = OcrResultState.SUCCESS

    override suspend fun recognize(imageUri: String): OcrResult {
        delay(1500) // Simulate processing time
        return when (currentState) {
            OcrResultState.SUCCESS -> OcrResult.Success(
                Client("c_1", "wa_1", "张总", "某某科技有限公司", "高意向")
            )
            OcrResultState.MULTI_CANDIDATE -> OcrResult.MultiCandidate(
                listOf(
                    Client("c_1", "wa_1", "张总", "某某科技有限公司", "高意向"),
                    Client("c_2", "wa_1", "张经理", "另一家科技有限公司", "中意向")
                )
            )
            OcrResultState.NO_MATCH -> OcrResult.NoMatch
            OcrResultState.NO_CLUE -> OcrResult.NoClue
            OcrResultState.NON_PRIVATE_CHAT -> OcrResult.NonPrivateChat
        }
    }
}
