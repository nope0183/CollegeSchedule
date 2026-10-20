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
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.nope.scheduletile.R
import com.nope.scheduletile.SettingsActivity
import com.nope.scheduletile.data.Fmt
import com.nope.scheduletile.data.Lesson
import com.nope.scheduletile.data.ScheduleState
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Комплекция для циферблата.
 *  - RANGED_VALUE: кольцо-прогресс пары + иконка + «сколько осталось»
 *  - SHORT_TEXT:   «23 мин» до конца пары
 *  - LONG_TEXT:    название пары, кабинет, до скольки
 * Между парами и после последней пары показывает живой отсчёт до следующей пары (в т.ч. завтрашней).
 */
class LessonComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val s = ScheduleState.compute(this)
        return build(request.complicationType, s.current, s.next, s.now, s.group != null && s.hasData)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val now = System.currentTimeMillis()
        val demo = Lesson(2, "Математика", "", "47", now - 35 * 60_000L, now + 45 * 60_000L)
        return build(type, demo, null, now, true, preview = true)
    }

    private fun build(
        type: ComplicationType,
        current: Lesson?,
        next: Lesson?,
        now: Long,
        ready: Boolean,
        preview: Boolean = false,
    ): ComplicationData? {
        if (type != ComplicationType.RANGED_VALUE && type != ComplicationType.SHORT_TEXT &&
            type != ComplicationType.LONG_TEXT
        ) return NoDataComplicationData()

        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_complication)).build()
        val tap = if (preview) null else PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Что именно показываем: идущая пара, иначе следующая, иначе «пусто»
        val mode = when {
            !ready -> Mode.EMPTY
            current != null -> Mode.CURRENT
            next != null -> Mode.NEXT
            else -> Mode.EMPTY
        }

        val shortText: ComplicationText = when (mode) {
            Mode.CURRENT -> countdown(current!!.end)
            Mode.NEXT -> countdown(next!!.start, TimeDifferenceStyle.SHORT_DUAL_UNIT)
            Mode.EMPTY -> plain("—")
        }
        val title: ComplicationText? = when (mode) {
            Mode.CURRENT -> current!!.room.takeIf { it.isNotBlank() }?.let { plain("каб. $it") }
            Mode.NEXT -> {
                val day = Fmt.dayShort(next!!.start, now)
                plain(
                    day.ifEmpty { next.room.takeIf { it.isNotBlank() }?.let { "каб. $it" } ?: "далее" }
                )
            }
            Mode.EMPTY -> null
        }
        val description = when (mode) {
            Mode.CURRENT -> "${current!!.subject}, до ${Fmt.time(current.end)}"
            Mode.NEXT -> "Следующая пара ${next!!.subject}, ${Fmt.dayPrefix(next.start, now)}${Fmt.time(next.start)}"
            Mode.EMPTY -> "Нет пар"
        }
        val cd = plain(description)

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(shortText, cd)
                    .setTitle(title)
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()

            ComplicationType.LONG_TEXT -> {
                val text = when (mode) {
                    Mode.CURRENT -> current!!.subject
                    Mode.NEXT -> next!!.subject
                    Mode.EMPTY -> "Пар нет"
                }
                val sub = when (mode) {
                    Mode.CURRENT -> listOf(Fmt.room(current!!), "до ${Fmt.time(current.end)}")
                    Mode.NEXT -> listOf(Fmt.room(next!!), Fmt.dayPrefix(next.start, now) + Fmt.time(next.start))
                    Mode.EMPTY -> emptyList()
                }.filter { it.isNotEmpty() }.joinToString(" · ")
                LongTextComplicationData.Builder(plain(text), cd)
                    .setTitle(if (sub.isEmpty()) null else plain(sub))
                    .setMonochromaticImage(icon)
                    .setTapAction(tap)
                    .build()
            }

            else -> rangedValue(mode, current, shortText, title, icon, cd, tap, now)
        }
    }

    private fun rangedValue(
        mode: Mode,
        current: Lesson?,
        text: ComplicationText,
        title: ComplicationText?,
        icon: MonochromaticImage,
        cd: ComplicationText,
        tap: PendingIntent?,
        now: Long,
    ): ComplicationData {
        val builder: RangedValueComplicationData.Builder = if (mode == Mode.CURRENT && current != null) {
            val totalSec = ((current.end - current.start) / 1000f).coerceAtLeast(1f)
            val elapsedSec = ((now - current.start) / 1000f).coerceIn(0f, totalSec)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Кольцо идёт само, без перезапроса данных: считает циферблат.
                val dynamic: DynamicFloat = DynamicInstant
                    .withSecondsPrecision(Instant.ofEpochMilli(current.start))
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
        return builder
            .setText(text)
            .setTitle(title)
            .setMonochromaticImage(icon)
            .setTapAction(tap)
            .build()
    }

    private fun plain(s: String): ComplicationText = PlainComplicationText.Builder(s).build()

    /** Живой обратный отсчёт («23 мин», «14ч 20м»): обновляет сам циферблат. */
    private fun countdown(
        targetMs: Long,
        style: TimeDifferenceStyle = TimeDifferenceStyle.SHORT_SINGLE_UNIT,
    ): ComplicationText =
        TimeDifferenceComplicationText.Builder(
            style,
            CountDownTimeReference(Instant.ofEpochMilli(targetMs)),
        )
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .build()

    private enum class Mode { CURRENT, NEXT, EMPTY }
}
