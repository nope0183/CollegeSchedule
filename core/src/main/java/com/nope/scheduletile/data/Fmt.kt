package com.nope.scheduletile.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

/** Форматирование текстов для виджета и комплекции. */
object Fmt {
    private val zone get() = ZoneId.systemDefault()
    private val ru = Locale.forLanguageTag("ru")
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
    private val dayFmt = DateTimeFormatter.ofPattern("EE d MMM", ru)
    private val dowFmt = DateTimeFormatter.ofPattern("EE", ru)

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(hhmm)

    /** Мини-отсчёт: «23 мин», «3 ч 20 мин», «2 д 12 ч». */
    fun minutes(ms: Long): String {
        val m = ceil(ms / 60_000.0).toInt().coerceAtLeast(1)
        return when {
            m >= 1440 -> "${m / 1440} д ${(m % 1440) / 60} ч"
            m >= 60 -> "${m / 60} ч ${m % 60} мин"
            else -> "$m мин"
        }
    }

    /** Совсем короткий отсчёт для заголовка: «23 мин», «3 ч 20 м», «2 д 12 ч». */
    fun minutesShort(ms: Long): String {
        val m = ceil(ms / 60_000.0).toInt().coerceAtLeast(1)
        return when {
            m >= 1440 -> "${m / 1440} д ${(m % 1440) / 60} ч"
            m >= 60 -> "${m / 60} ч ${m % 60} м"
            else -> "$m мин"
        }
    }

    /** "" для сегодня, "завтра, " или "пн 26 окт, " для других дней. */
    fun dayPrefix(ms: Long, now: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (d) {
            today -> ""
            today.plusDays(1) -> "завтра, "
            else -> d.format(dayFmt) + ", "
        }
    }

    /** "Сегодня" / "Завтра" / "Пн 26 окт" — для заголовков. */
    fun dayLabel(ms: Long, now: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (d) {
            today -> "Сегодня"
            today.plusDays(1) -> "Завтра"
            else -> d.format(dayFmt).replaceFirstChar { it.uppercase() }
        }
    }

    /** "" для сегодня, "завтра" или "пн" — коротко, для комплекций. */
    fun dayShort(ms: Long, now: Long): String {
        val d = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when (d) {
            today -> ""
            today.plusDays(1) -> "завтра"
            else -> d.format(dowFmt)
        }
    }

    /** "1 пара", "3 пары", "5 пар". */
    fun pairs(n: Int): String {
        val word = when {
            n % 100 in 11..14 -> "пар"
            n % 10 == 1 -> "пара"
            n % 10 in 2..4 -> "пары"
            else -> "пар"
        }
        return "$n $word"
    }

    fun room(l: Lesson): String = if (l.room.isBlank()) "" else "каб. ${l.room}"

    /** Кабинет для отдельной строки карточки; если не указан, то преподаватель или заглушка. */
    fun roomLine(l: Lesson): String =
        room(l).ifEmpty { l.teacher.ifBlank { "кабинет не указан" } }
}
