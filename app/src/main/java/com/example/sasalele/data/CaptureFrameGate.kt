package com.example.sasalele.data

/** A bounded wait for compositor startup frames, never a protected-window bypass. */
class CaptureFrameGate(private val waitMs: Long = 3000) {
    enum class Decision { ACCEPT, WAIT, EMPTY_TIMEOUT }
    private var blankSince: Long? = null
    fun inspect(blank: Boolean, nowMs: Long): Decision {
        if (!blank) return Decision.ACCEPT
        if (blankSince == null) blankSince = nowMs
        return if (deadlineReached(nowMs)) Decision.EMPTY_TIMEOUT else Decision.WAIT
    }
    fun deadlineReached(nowMs: Long): Boolean = blankSince?.let { nowMs - it >= waitMs } ?: false
}
