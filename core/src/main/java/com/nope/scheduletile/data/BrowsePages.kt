package com.nope.scheduletile.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Одна «страница» листалки: до [BrowsePages.PER_PAGE] пар одного дня. */
data class BrowsePage(
    /** Стабильный ключ вида «2026-10-27#1» (дата и номер части дня). */
    val key: String,
    val date: LocalDate,
    val lessons: List<Lesson>,
    /** Номер части внутри дня, с нуля. */
    val part: Int,
    val parts: Int,
)

/** Нарезка расписания на страницы для листания в виджете. Не зависит от Android. */
object BrowsePages {
    const val PER_PAGE = 2
    private const val MAX_DAYS = 14

    fun build(all: List<Lesson>, now: Long): List<BrowsePage> {
        val zone = ZoneId.systemDefault()
        fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = dateOf(now)
        return all.groupBy { dateOf(it.start) }
            .filterKeys { !it.isBefore(today) }
            .entries
            .sortedBy { it.key }
            .take(MAX_DAYS)
            .flatMap { (date, lessons) ->
                val sorted = lessons.sortedBy { it.start }
                val chunks = sorted.chunked(PER_PAGE)
                chunks.mapIndexed { i, part ->
                    BrowsePage("$date#$i", date, part, i, chunks.size)
                }
            }
    }

    /** Страница, с которой удобно начать: первая, где ещё есть не закончившаяся пара. */
    fun entryKey(pages: List<BrowsePage>, now: Long): String? =
        (pages.firstOrNull { p -> p.lessons.any { it.end > now } } ?: pages.firstOrNull())?.key
}
