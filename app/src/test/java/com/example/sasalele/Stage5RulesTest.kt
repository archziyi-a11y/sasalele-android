package com.example.sasalele
import com.example.sasalele.data.ReminderRules
import org.junit.Test
import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.TimeZone
class Stage5RulesTest {
 private fun t(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.parse(s)!!.time
 @Test fun before0830() { assertFalse(ReminderRules.dailyDue(t("2026-10-12 08:29"),0,listOf(t("2026-10-11 12:00")),0)) }
 @Test fun at0830Yesterday() { assertTrue(ReminderRules.dailyDue(t("2026-10-12 08:30"),0,listOf(t("2026-10-11 12:00")),0)) }
 @Test fun todayExcluded() { for (s in listOf("08:29","08:30")) assertFalse(ReminderRules.dailyDue(t("2026-10-12 10:00"),0,listOf(t("2026-10-12 $s")),0)) }
 @Test fun unknownExcluded() { assertFalse(ReminderRules.dailyDue(t("2026-10-12 10:00"),0,listOf(0L),0)) }
 @Test fun oncePerDay() { assertFalse(ReminderRules.dailyDue(t("2026-10-12 12:00"),t("2026-10-12 08:30"),listOf(t("2026-10-11 12:00")),0)) }
 @Test fun consecutiveDays() { for (day in 12..14) assertTrue(ReminderRules.dailyDue(t("2026-10-$day 08:30"),t("2026-10-${day-1} 08:30"),listOf(t("2026-10-10 12:00")),0)) }
 @Test fun closedHistoryExcluded() { assertFalse(ReminderRules.dailyDue(t("2026-10-12 10:00"),0,listOf(t("2026-10-10 12:00")),t("2026-10-11 12:00"))) }
 @Test fun missedDaysTodayOnly() { assertTrue(ReminderRules.dailyDue(t("2026-10-15 10:00"),t("2026-10-10 08:30"),listOf(t("2026-10-09 12:00")),0)) }
 @Test fun identityCollision() { assertNotEquals(ReminderRules.key("a_b","c"),ReminderRules.key("a","b_c")) }
 @Test fun unbindPoints() { for ((now,point) in listOf("2026-10-12 18:30" to "2026-10-12 18:30", "2026-10-12 22:00" to "2026-10-12 22:00", "2026-10-13 08:00" to "2026-10-12 22:00")) assertEquals(t(point),ReminderRules.latestUnbindPoint(t(now))) }
}
