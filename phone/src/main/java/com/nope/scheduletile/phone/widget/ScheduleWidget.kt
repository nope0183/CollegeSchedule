package com.nope.scheduletile.phone.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nope.scheduletile.data.Fmt
import com.nope.scheduletile.data.Lesson
import com.nope.scheduletile.data.ScheduleState
import com.nope.scheduletile.phone.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Виджет «Пары» на Jetpack Glance. Цвета берутся из динамической темы Material You (Android 12+),
 * на старых версиях используется стандартная палитра.
 *
 * Размеры:
 *  - маленький (2x2): главная карточка;
 *  - средний/широкий: карточка + одна следующая пара;
 *  - высокий (от ~4x3): карточка + список ближайших пар.
 */
class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = withContext(Dispatchers.IO) { ScheduleState.compute(context) }
        WidgetTicker.scheduleNext(context, state)
        provideContent {
            GlanceTheme { WidgetContent(state) }
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val TALL = DpSize(250.dp, 250.dp)
    }
}

// ---------- стили ----------

private fun style(color: ColorProvider, size: Float, bold: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

private fun isNextDayMode(s: ScheduleState, current: Lesson?, next: Lesson?): Boolean =
    current == null && next != null && Fmt.dayPrefix(next.start, s.now).isNotEmpty()

// ---------- содержимое ----------

@Composable
private fun WidgetContent(s: ScheduleState) {
    val size = LocalSize.current
    val wide = size.width >= 200.dp
    val medium = size.height >= 140.dp
    val tall = size.height >= 200.dp

    val current = s.current
    val next = s.next
    val openSettings = actionStartActivity(Intent(LocalContext.current, SettingsActivity::class.java))
    val refresh = actionRunCallback<RefreshCallback>()

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Header(s, current, next, openSettings)
        Spacer(GlanceModifier.height(8.dp))
        when {
            s.group == null -> Message("Выберите группу", "Нажмите, чтобы открыть настройки", openSettings)
            !s.hasData -> Message("Нет данных", "Нажмите, чтобы обновить", refresh)
            current != null -> {
                val fraction = ((s.now - current.start).toFloat() / (current.end - current.start).toFloat())
                    .coerceIn(0f, 1f)
                HeroCard(
                    label = "Сейчас · ${current.num} пара",
                    lesson = current,
                    detail = listOf(Fmt.roomLine(current), "до ${Fmt.time(current.end)}").joinToString(" · "),
                    progress = fraction,
                    wide = wide,
                )
                RestList(s.upcoming, s.now, maxRows = if (tall) 3 else if (medium) 1 else 0, title = "Дальше")
            }
            next != null -> {
                val dayMode = isNextDayMode(s, current, next)
                val label = if (dayMode) "Первая пара · ${next.num}" else "Дальше · ${next.num} пара"
                HeroCard(
                    label = label,
                    lesson = next,
                    detail = listOf(Fmt.roomLine(next), Fmt.time(next.start)).joinToString(" · "),
                    progress = null,
                    wide = wide,
                )
                val rest = if (dayMode) s.nextDayLessons.drop(1) else s.upcoming.drop(1)
                RestList(rest, s.now, maxRows = if (tall) 4 else if (medium) 2 else 0, title = null)
            }
            else -> Message("Занятий нет", "Ждём новое расписание", refresh)
        }
    }
}

@Composable
private fun Header(s: ScheduleState, current: Lesson?, next: Lesson?, openSettings: Action) {
    val right = when {
        s.group == null || !s.hasData -> ""
        current != null -> "ещё ${Fmt.minutesShort(current.end - s.now)}"
        next != null && isNextDayMode(s, current, next) ->
            "${Fmt.dayLabel(next.start, s.now)} · через ${Fmt.minutesShort(next.start - s.now)}"
        next != null && s.hadEarlierToday -> "перемена · ${Fmt.minutesShort(next.start - s.now)}"
        next != null -> "начало через ${Fmt.minutesShort(next.start - s.now)}"
        else -> ""
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(openSettings),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = s.group?.let { "Группа $it" } ?: "Расписание",
            style = style(GlanceTheme.colors.primary, 14f, bold = true),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (right.isNotEmpty()) {
            Text(
                text = right,
                style = style(GlanceTheme.colors.onSurfaceVariant, 12f),
                maxLines = 1,
            )
        }
    }
}

/** Главная акцентная карточка: идущая пара (с прогрессом) или ближайшая. */
@Composable
private fun HeroCard(label: String, lesson: Lesson, detail: String, progress: Float?, wide: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Text(
            text = label,
            style = style(GlanceTheme.colors.onPrimaryContainer, 11f),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = lesson.subject,
            style = style(GlanceTheme.colors.onPrimaryContainer, if (wide) 18f else 15f, bold = true),
            maxLines = 2,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = detail,
            style = style(GlanceTheme.colors.onPrimaryContainer, 14f),
            maxLines = 2,
        )
        if (progress != null) {
            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = GlanceModifier.fillMaxWidth(),
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surface,
            )
        }
    }
}

@Composable
private fun RestList(lessons: List<Lesson>, now: Long, maxRows: Int, title: String?) {
    val rows = lessons.take(maxRows)
    if (rows.isEmpty()) return
    Spacer(GlanceModifier.height(8.dp))
    if (title != null) {
        Text(
            text = title,
            style = style(GlanceTheme.colors.onSurfaceVariant, 12f),
            maxLines = 1,
        )
    }
    rows.forEach { LessonRow(it, now) }
}

@Composable
private fun LessonRow(l: Lesson, now: Long) {
    val sub = listOf(Fmt.dayShort(l.start, now), Fmt.roomLine(l)).filter { it.isNotEmpty() }.joinToString(" · ")
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Fmt.time(l.start),
            modifier = GlanceModifier.width(48.dp),
            style = style(GlanceTheme.colors.primary, 14f, bold = true),
            maxLines = 1,
        )
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = l.subject,
                style = style(GlanceTheme.colors.onSurface, 14f, bold = true),
                maxLines = 1,
            )
            Text(
                text = sub,
                style = style(GlanceTheme.colors.onSurfaceVariant, 12f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Message(title: String, hint: String, onClick: Action) {
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(title, style = style(GlanceTheme.colors.onSurface, 16f, bold = true), maxLines = 2)
            Spacer(GlanceModifier.height(4.dp))
            Text(hint, style = style(GlanceTheme.colors.onSurfaceVariant, 12f), maxLines = 2)
        }
    }
}
