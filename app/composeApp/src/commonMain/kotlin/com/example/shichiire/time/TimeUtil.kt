package com.example.shichiire.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val zone: TimeZone get() = TimeZone.currentSystemDefault()

fun currentInstant(): Instant = Clock.System.now()

/** 今日から daysFromToday 日後の hour:minute（端末のタイムゾーン） */
fun atLocal(daysFromToday: Int, hour: Int, minute: Int = 0, base: Instant = currentInstant()): Instant {
    val date = base.toLocalDateTime(zone).date.plus(daysFromToday, DateTimeUnit.DAY)
    return LocalDateTime(date, LocalTime(hour, minute)).toInstant(zone)
}

private fun Int.two(): String = toString().padStart(2, '0')

/** 例：11/25 21:00 */
fun formatShort(instant: Instant): String {
    val t = instant.toLocalDateTime(zone)
    return "${t.monthNumber}/${t.dayOfMonth} ${t.hour.two()}:${t.minute.two()}"
}

/** 期限までの残り。過ぎていたら null */
fun remainingText(due: Instant, now: Instant): String? {
    val d: Duration = due - now
    if (d <= Duration.ZERO) return null
    val days = d.inWholeDays
    val hours = d.inWholeHours % 24
    val minutes = d.inWholeMinutes % 60
    val seconds = d.inWholeSeconds % 60
    return when {
        days > 0 -> "${days}日 ${hours}時間"
        hours > 0 -> "${hours}時間 ${minutes}分"
        minutes > 0 -> "${minutes}分"
        else -> "${seconds}秒"
    }
}

/** 期限の候補。プロトタイプなので日時ピッカーの代わりにチップで選ぶ */
data class DuePreset(val label: String, val resolve: (Instant) -> Instant)

val duePresets: List<DuePreset> = listOf(
    DuePreset("1分後（デモ用）") { it + 1.minutes },
    DuePreset("今日 21:00") { atLocal(0, 21, base = it) },
    DuePreset("明日 9:00") { atLocal(1, 9, base = it) },
    DuePreset("明日 21:00") { atLocal(1, 21, base = it) },
    DuePreset("3日後 21:00") { atLocal(3, 21, base = it) },
    DuePreset("1週間後 21:00") { atLocal(7, 21, base = it) },
)
