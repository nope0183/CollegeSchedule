package com.nope.scheduletile.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class Lesson(
    val num: Int,
    val subject: String,
    val teacher: String,
    val room: String,
    val start: Long,
    val end: Long,
)

/** Читает кэш schedule.json + history.json (та же логика, что в app.js сайта). */
object ScheduleRepository {

    // Расписание звонков (из app.js). В понедельник первая строка — классный час.
    private val MONDAY = listOf(
        "08:45" to "09:15", "09:20" to "10:40", "10:50" to "12:10",
        "12:25" to "13:45", "13:50" to "15:10", "15:15" to "16:35", "16:40" to "18:00",
    )
    private val OTHER = listOf(
        "08:45" to "10:05", "10:15" to "11:35", "11:45" to "13:05",
        "13:20" to "14:40", "14:45" to "16:05", "16:10" to "17:30",
    )

    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )

    private class Day(val iso: String, val groups: List<String>, val schedule: JSONObject)

    fun hasCache(ctx: Context) = File(ctx.filesDir, "schedule.json").exists()

    private fun readFile(ctx: Context, name: String): JSONObject? = try {
        File(ctx.filesDir, name).takeIf { it.exists() }?.readText()?.let { JSONObject(it) }
    } catch (e: Exception) {
        null
    }

    private fun parseRuDate(text: String): String? {
        val m = Regex("""(\d{1,2})\s+([а-яё]+)\s+(\d{4})""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val month = MONTHS.indexOf(m.groupValues[2].lowercase()) + 1
        if (month == 0) return null
        return LocalDate.of(m.groupValues[3].toInt(), month, m.groupValues[1].toInt()).toString()
    }

    private fun groupsOf(o: JSONObject, schedule: JSONObject?): List<String> {
        val res = linkedSetOf<String>()
        o.optJSONArray("groups")?.let { a -> for (i in 0 until a.length()) res += a.optString(i) }
        schedule?.keys()?.forEach { res += it }
        return res.filter { it.isNotBlank() }
    }

    private fun readDays(ctx: Context): List<Day> {
        val result = linkedMapOf<String, Day>()
        readFile(ctx, "history.json")?.optJSONObject("days")?.let { days ->
            days.keys().forEach { iso ->
                val d = days.optJSONObject(iso) ?: return@forEach
                val sch = d.optJSONObject("schedule") ?: return@forEach
                result[iso] = Day(iso, groupsOf(d, sch), sch)
            }
        }
        // schedule.json — свежая выгрузка, главнее истории
        readFile(ctx, "schedule.json")?.let { cur ->
            val iso = cur.optString("date_iso").ifBlank { parseRuDate(cur.optString("date")) ?: "" }
            val sch = cur.optJSONObject("schedule")
            if (iso.isNotBlank() && sch != null) result[iso] = Day(iso, groupsOf(cur, sch), sch)
        }
        return result.values.toList()
    }

    fun groups(ctx: Context): List<String> =
        readDays(ctx).flatMap { it.groups }.distinct().sorted()

    private fun times(num: Int, monday: Boolean): Pair<LocalTime, LocalTime>? {
        val list = if (monday) MONDAY else OTHER
        val idx = if (monday) num else num - 1
        return list.getOrNull(idx)?.let { LocalTime.parse(it.first) to LocalTime.parse(it.second) }
    }

    /** Все пары группы по всем дням из кэша, по возрастанию времени. «Окна» (пустые строки) пропускаются. */
    fun lessons(ctx: Context, group: String): List<Lesson> {
        val zone = ZoneId.systemDefault()
        return readDays(ctx).flatMap { day ->
            val date = try {
                LocalDate.parse(day.iso)
            } catch (e: Exception) {
                return@flatMap emptyList<Lesson>()
            }
            val arr = day.schedule.optJSONArray(group) ?: return@flatMap emptyList<Lesson>()
            val isMonday = date.dayOfWeek == DayOfWeek.MONDAY
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val subject = o.optString("subject").trim()
                val teacher = o.optString("teacher").trim()
                val room = o.optString("room").trim()
                if (subject.isEmpty() && teacher.isEmpty() && room.isEmpty()) return@mapNotNull null
                val num = o.optString("num").trim().toIntOrNull() ?: return@mapNotNull null
                val (s, e) = times(num, isMonday) ?: return@mapNotNull null
                Lesson(
                    num = num,
                    subject = subject.ifEmpty { "Пара" },
                    teacher = teacher,
                    room = room,
                    start = date.atTime(s).atZone(zone).toInstant().toEpochMilli(),
                    end = date.atTime(e).atZone(zone).toInstant().toEpochMilli(),
                )
            }
        }.sortedBy { it.start }
    }
}
