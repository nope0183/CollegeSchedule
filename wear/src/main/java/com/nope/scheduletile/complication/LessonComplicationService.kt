package com.nope.scheduletile.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicFloat
import androidx.wear.protolayout.expression.DynamicBuilders.DynamicInstant
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.nope.scheduletile.R
import com.nope.scheduletile.SettingsActivity
import com.nope.scheduletile.data.Fmt
import com.nope.scheduletile.data.Lesson
import com.nope.scheduletile.data.Prefs
import com.nope.scheduletile.data.ScheduleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Комплекция для циферблата.
 *  - SHORT_TEXT:   «23м» + кабинет (в слоте заголовка)
 *  - RANGED_VALUE: кольцо-прогресс + «23м» + кабинет
 *  - LONG_TEXT:    название пары, «каб. 47 · до 10:05» с живым отсчётом
 *
 * Данные отдаются ТАЙМЛАЙНОМ на ближайшие пары: «идёт пара: кабинет, сколько до конца» ->
 * «перемена: кабинет следующей, сколько до начала» -> «идёт следующая» и т.д.
 * Переключает записи сам циферблат по времени, поэтому смена пары не зависит от того,
 * успела ли система перезапросить данные (раньше после конца пары показывалось старое).
 */
class LessonComplicationService : SuspendingTimelineComplicationDataSourceService() {

    private enum class Mode { CURRENT, NEXT, EMPTY }

    /** Отрезок времени [start, end), в котором показывается одно и то же состояние. */
    private class Segment(
        val start: Long,
        val end: Long,
        val mode: Mode,
        val lesson: Lesson?,
        /** Для NEXT: когда закончилась предыдущая пара (начало перемены), если известно. */
        val breakStart: Long? = null,
    )

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline? {
        val type = request.complicationType
        if (!supported(type)) return ComplicationDataTimeline(NoDataComplicationData(), emptyList())

        val now = System.currentTimeMillis()
        val group = Prefs.group(this)
        val ready = group != null && ScheduleRepository.hasCache(this)
        val lessons = if (ready) ScheduleRepository.lessons(this, group!!) else emptyList()
        val segments = segments(lessons, now)

        // Что показывать, когда ни одна запись не действует (после последней известной пары / нет данных).
        val default = build(type, Segment(now, now + 1, Mode.EMPTY, null), now, preview = false)

        val entries = segments.mapNotNull { seg ->
            // Начало первой записи чуть в прошлом: вдруг циферблат нарисует кадр раньше, чем мы успели
            val from = Instant.ofEpochMilli(if (seg.start <= now) now - 60_000L else seg.start)
            val to = Instant.ofEpochMilli(seg.end)
            if (!from.isBefore(to)) null
            else TimelineEntry(TimeInterval(from, to), build(type, seg, now, preview = false))
        }
        return ComplicationDataTimeline(default, entries)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (!supported(type)) return NoDataComplicationData()
        val now = System.currentTimeMillis()
        val demo = Lesson(2, "Математика", "", "47", now - 35 * 60_000L, now + 45 * 60_000L)
        return build(type, Segment(demo.start, demo.end, Mode.CURRENT, demo), now, preview = true)
    }

    private fun supported(t: ComplicationType) =
        t == ComplicationType.RANGED_VALUE || t == ComplicationType.SHORT_TEXT || t == ComplicationType.LONG_TEXT

    /**
     * Цепочка состояний на ближайшее время: для каждой из ближайших пар
     * «перемена/ожидание до неё» и «сама пара».
     */
    private fun segments(lessons: List<Lesson>, now: Long): List<Segment> {
        val upcoming = lessons.filter { it.end > now }.take(MAX_LESSONS)
        val result = mutableListOf<Segment>()
        var prevEnd: Long? = lessons.lastOrNull { it.end <= now }?.end
        for (l in upcoming) {
            if (l.start > now) {
                val from = maxOf(prevEnd ?: now, now)
                if (from < l.start) result += Segment(from, l.start, Mode.NEXT, l, breakStart = prevEnd)
            }
            result += Segment(maxOf(l.start, now), l.end, Mode.CURRENT, l)
            prevEnd = l.end
        }
        return result
    }

    private fun build(type: ComplicationType, seg: Segment, now: Long, preview: Boolean): ComplicationData {
        val tap = if (preview) null else PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val l = seg.lesson
        val room = l?.let { roomShort(it) }.orEmpty()

        val description = when (seg.mode) {
            Mode.CURRENT -> "${l!!.subject}, ${Fmt.room(l).ifEmpty { "кабинет не указан" }}, до ${Fmt.time(l.end)}"
            Mode.NEXT -> "Следующая пара ${l!!.subject}, ${Fmt.room(l).ifEmpty { "кабинет не указан" }}, " +
                "${Fmt.dayPrefix(l.start, now)}${Fmt.time(l.start)}"
            Mode.EMPTY -> "Нет пар"
        }
        val cd = plain(description)

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val b = ShortTextComplicationData.Builder(shortText(seg), cd).setTapAction(tap)
                // Иконку НЕ ставим: циферблат показывает либо иконку, либо заголовок, а нам нужен кабинет.
                titleFor(seg, room, now)?.let { b.setTitle(it) }
                if (seg.mode == Mode.EMPTY) b.setMonochromaticImage(icon())
                b.build()
            }

            ComplicationType.LONG_TEXT -> {
                val text = when (seg.mode) {
                    Mode.CURRENT, Mode.NEXT -> l!!.subject
                    Mode.EMPTY -> "Пар нет"
                }
                val b = LongTextComplicationData.Builder(plain(text), cd)
                    .setMonochromaticImage(icon())
                    .setTapAction(tap)
                longTitle(seg, now)?.let { b.setTitle(it) }
                b.build()
            }

            else -> rangedValue(seg, room, cd, tap, now)
        }
    }

    /** Основной текст: живой отсчёт до конца идущей пары / до начала следующей. */
    private fun shortText(seg: Segment): ComplicationText = when (seg.mode) {
        Mode.CURRENT -> countdown(seg.lesson!!.end)
        Mode.NEXT -> countdown(seg.lesson!!.start)
        Mode.EMPTY -> plain("—")
    }

    /** Заголовок под/над временем: кабинет. Для ожидающей пары со стрелкой «→». */
    private fun titleFor(seg: Segment, room: String, now: Long): ComplicationText? = when (seg.mode) {
        Mode.CURRENT -> room.takeIf { it.isNotEmpty() }?.let { plain(it) }
        Mode.NEXT -> {
            val l = seg.lesson!!
            val label = room.ifEmpty { Fmt.dayShort(l.start, now).ifEmpty { "далее" } }
            plain("→$label")
        }
        Mode.EMPTY -> null
    }

    private fun longTitle(seg: Segment, now: Long): ComplicationText? {
        val l = seg.lesson ?: return null
        val room = Fmt.room(l)
        return when (seg.mode) {
            Mode.CURRENT -> {
                // «каб. 47 · 23м» с живым отсчётом до конца пары
                val prefix = if (room.isEmpty()) "" else "$room · "
                countdown(l.end, surrounding = "${prefix}до конца ^1")
            }
            Mode.NEXT -> {
                val prefix = if (room.isEmpty()) "" else "$room · "
                countdown(l.start, surrounding = "${prefix}через ^1")
            }
            Mode.EMPTY -> null
        }
    }

    private fun rangedValue(
        seg: Segment,
        room: String,
        cd: ComplicationText,
        tap: PendingIntent?,
        now: Long,
    ): ComplicationData {
        // Кольцо: за пару, а на перемене до 90 минут (на длинных промежутках, например ночью, оно пустое)
        val ringStart: Long?
        val ringEnd: Long?
        when {
            seg.mode == Mode.CURRENT -> { ringStart = seg.lesson!!.start; ringEnd = seg.lesson.end }
            seg.mode == Mode.NEXT && seg.breakStart != null &&
                seg.lesson!!.start - seg.breakStart <= 90 * 60_000L -> {
                ringStart = seg.breakStart; ringEnd = seg.lesson.start
            }
            else -> { ringStart = null; ringEnd = null }
        }

        val builder: RangedValueComplicationData.Builder = if (ringStart != null && ringEnd != null) {
            val totalSec = ((ringEnd - ringStart) / 1000f).coerceAtLeast(1f)
            // Для записей из будущего запасное значение 0, для действующей записи реальное
            val elapsedSec = ((now - ringStart) / 1000f).coerceIn(0f, totalSec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Кольцо идёт само, без перезапроса данных: считает циферблат.
                val dynamic: DynamicFloat = DynamicInstant
                    .withSecondsPrecision(Instant.ofEpochMilli(ringStart))
                    .durationUntil(DynamicInstant.platformTimeWithSecondsPrecision())
                    .toIntSeconds()
                    .asFloat()
                RangedValueComplicationData.Builder(
                    dynamicValue = dynamic,
                    fallbackValue = elapsedSec,
                    min = 0f,
                    max = totalSec,
                    contentDescription = cd,
                )
            } else {
                RangedValueComplicationData.Builder(
                    value = elapsedSec, min = 0f, max = totalSec, contentDescription = cd,
                )
            }
        } else {
            RangedValueComplicationData.Builder(value = 0f, min = 0f, max = 1f, contentDescription = cd)
        }

        builder.setText(shortText(seg)).setTapAction(tap)
        titleFor(seg, room, now)?.let { builder.setTitle(it) }
        // Иконка вытесняет заголовок (кабинет), поэтому только когда показывать нечего
        if (seg.mode == Mode.EMPTY) builder.setMonochromaticImage(icon())
        return builder.build()
    }

    private fun icon(): MonochromaticImage =
        MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_complication)).build()

    /** Короткая запись кабинета для узкого слота: «каб.47», нечисловые («спортзал») как есть. */
    private fun roomShort(l: Lesson): String {
        val r = l.room.trim()
        if (r.isEmpty()) return ""
        return if (r.first().isDigit()) "каб.$r" else r
    }

    private fun plain(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

    /**
     * Живой обратный отсчёт («23м», «1ч 20м», «2д 12ч»): обновляет сам циферблат.
     * Двойная единица, чтобы 80 минут не превращались в «1ч».
     */
    private fun countdown(targetMs: Long, surrounding: String? = null): ComplicationText =
        TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_DUAL_UNIT,
            CountDownTimeReference(Instant.ofEpochMilli(targetMs)),
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .apply { if (surrounding != null) setText(surrounding) }
            .build()

    private companion object {
        /** Сколько ближайших пар класть в таймлайн (2 записи на пару). */
        const val MAX_LESSONS = 6
    }
}
