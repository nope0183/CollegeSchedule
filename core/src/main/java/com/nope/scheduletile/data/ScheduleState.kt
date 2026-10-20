package com.nope.scheduletile.data

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Что показывать прямо сейчас. Общая модель для виджета и комплекции. */
data class ScheduleState(
    val group: String?,
    val hasData: Boolean,
    val now: Long,
    val current: Lesson?,
    val next: Lesson?,
    val hadEarlierToday: Boolean,
    /** Все пары того дня, на который выпадает [next] (для режима «пары кончились → показываем след. день»). */
    val nextDayLessons: List<Lesson> = emptyList(),
    /** Ближайшие пары по порядку (сегодня и дальше), до 8 штук. */
    val upcoming: List<Lesson> = emptyList(),
) {
    /** Как скоро системе стоит перерисовать виджет. */
    val freshness: Duration
        get() = when {
            current != null -> 60.seconds
            next != null -> (next.start - now).milliseconds.coerceIn(30.seconds, 5.minutes)
            else -> 30.minutes
        }

    companion object {
        fun compute(ctx: Context, now: Long = System.currentTimeMillis()): ScheduleState {
            val group = Prefs.group(ctx)
            if (group == null) return ScheduleState(null, false, now, null, null, false)
            val all = ScheduleRepository.lessons(ctx, group)
            val zone = ZoneId.systemDefault()
            fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            val today = dateOf(now)
            val next = all.firstOrNull { it.start > now }
            val nextDate = next?.let { dateOf(it.start) }
            return ScheduleState(
                group = group,
                hasData = ScheduleRepository.hasCache(ctx),
                now = now,
                current = all.firstOrNull { now >= it.start && now < it.end },
                next = next,
                hadEarlierToday = all.any { it.end <= now && dateOf(it.end) == today },
                nextDayLessons = if (nextDate == null) emptyList() else all.filter { dateOf(it.start) == nextDate },
                upcoming = all.filter { it.start > now }.take(8),
            )
        }
    }
}
