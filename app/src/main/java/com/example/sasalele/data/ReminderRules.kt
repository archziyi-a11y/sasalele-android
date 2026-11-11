package com.example.sasalele.data

import java.util.Calendar
import java.util.TimeZone

object ReminderRules {
    fun key(account: String, client: String) = "${account.length}:$account$client"

    fun dayStart(now: Long): Long = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun dailyDue(now: Long, lastDaily: Long, timestamps: List<Long>, closedAt: Long): Boolean {
        val midnight = dayStart(now)
        return now >= midnight + (8 * 60 + 30) * 60_000L && lastDaily < midnight &&
            timestamps.any { it > 0 && it > closedAt && it < midnight }
    }

    fun latestUnbindPoint(now: Long): Long {
        val midnight = dayStart(now)
        val evening = midnight + (18 * 60 + 30) * 60_000L
        val night = midnight + 22 * 60 * 60_000L
        return when {
            now >= night -> night
            now >= evening -> evening
            else -> midnight - 2 * 60 * 60_000L
        }
    }
}
