package com.nope.scheduletile.tile

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.layoutString
import com.nope.scheduletile.SettingsActivity
import com.nope.scheduletile.data.Fmt
import com.nope.scheduletile.data.ScheduleState

fun MaterialScope.tileLayout(ctx: Context, s: ScheduleState): LayoutElement {
    val openSettings = clickable(
        action = launchAction(ComponentName(ctx, SettingsActivity::class.java), emptyMap()),
        id = "settings",
    )
    return primaryLayout(
        titleSlot = { text(headerText(s).layoutString, typography = Typography.LABEL_SMALL) },
        mainSlot = { mainContent(s) },
        bottomSlot = {
            textEdgeButton(
                onClick = openSettings,
                labelContent = {
                    text((s.group?.let { "Группа $it" } ?: "Выбрать группу").layoutString)
                },
                colors = filledTonalButtonColors(),
            )
        },
    )
}

/** Пары на сегодня кончились (или ещё не начались), а ближайшая — в другой день. */
private fun isNextDayMode(s: ScheduleState): Boolean {
    val n = s.next
    return s.current == null && n != null && Fmt.dayPrefix(n.start, s.now).isNotEmpty()
}

private fun headerText(s: ScheduleState): String {
    val c = s.current
    val n = s.next
    return when {
        s.group == null || !s.hasData -> "Расписание"
        c != null -> "${c.num} пара · ${Fmt.minutesShort(c.end - s.now)}"
        n != null && isNextDayMode(s) ->
            "${Fmt.dayLabel(n.start, s.now)} · ${Fmt.minutesShort(n.start - s.now)}"
        else -> "Сейчас"
    }
}

private fun MaterialScope.mainContent(s: ScheduleState): LayoutElement {
    if (s.group == null) return messageCard("Выберите группу", "Нажмите кнопку ниже")
    if (!s.hasData) return messageCard("Нет данных", "Поставьте часы на зарядку")

    if (isNextDayMode(s)) return nextDayContent(s)

    val children = mutableListOf<LayoutElement>()
    children += currentCard(s)
    val c = s.current
    if (c != null) {
        val fraction = ((s.now - c.start).toFloat() / (c.end - c.start).toFloat()).coerceIn(0f, 1f)
        children += spacer(3f)
        children += progressBar(fraction)
    }
    children += spacer(3f)
    children += nextCard(s)
    return column(*children.toTypedArray(), width = expand(), height = expand())
}

private fun MaterialScope.messageCard(title: String, sub: String): LayoutElement =
    titleCard(
        onClick = clickable(id = "message"),
        height = expand(),
        title = { text(title.layoutString) },
        content = { text(sub.layoutString) },
    )

/** Большая акцентная карточка: идёт пара / перемена / пар нет. */
private fun MaterialScope.currentCard(s: ScheduleState): LayoutElement {
    val c = s.current
    val refresh = clickable(id = "refresh_current")
    if (c != null) {
        return titleCard(
            onClick = refresh,
            height = expand(),
            title = { text(c.subject.layoutString) },
            content = { text(Fmt.roomLine(c).layoutString) },
            time = { text("до ${Fmt.time(c.end)}".layoutString) },
        )
    }
    val n = s.next
    if (n == null) {
        return titleCard(
            onClick = refresh,
            height = expand(),
            title = { text("Занятий нет".layoutString) },
            content = { text("ждём новое расписание".layoutString) },
        )
    }
    val (head, sub) =
        if (s.hadEarlierToday) "Перемена" to "до следующей пары"
        else "Пары не начались" to "первая в ${Fmt.time(n.start)}"
    return titleCard(
        onClick = refresh,
        height = expand(),
        title = { text(head.layoutString) },
        content = { text(sub.layoutString) },
        time = { text(Fmt.minutes(n.start - s.now).layoutString) },
    )
}

/**
 * Пары на сегодня закончились: показываем первые пары следующего учебного дня
 * и мини-отсчёт до начала первой.
 */
private fun MaterialScope.nextDayContent(s: ScheduleState): LayoutElement {
    val first = s.next!!
    val second = s.nextDayLessons.firstOrNull { it.start > first.start }

    val firstCard = titleCard(
        onClick = clickable(id = "refresh_first"),
        height = expand(),
        title = { text(first.subject.layoutString) },
        content = { text(Fmt.roomLine(first).layoutString) },
        time = { text(Fmt.time(first.start).layoutString) },
    )

    val secondCard = if (second != null) {
        titleCard(
            onClick = clickable(id = "refresh_second"),
            height = expand(),
            colors = filledTonalCardColors(),
            title = { text(second.subject.layoutString) },
            content = { text(Fmt.roomLine(second).layoutString) },
            time = { text(Fmt.time(second.start).layoutString) },
        )
    } else {
        titleCard(
            onClick = clickable(id = "refresh_second"),
            height = expand(),
            colors = filledTonalCardColors(),
            title = { text("Единственная пара".layoutString) },
            content = { text("до ${Fmt.time(first.end)}".layoutString) },
        )
    }
    return column(firstCard, spacer(3f), secondCard, width = expand(), height = expand())
}

/** Тональная карточка: следующая пара, где и когда. */
private fun MaterialScope.nextCard(s: ScheduleState): LayoutElement {
    val n = s.next
    val refresh = clickable(id = "refresh_next")
    if (n == null) {
        return titleCard(
            onClick = refresh,
            height = expand(),
            colors = filledTonalCardColors(),
            title = { text("Пар больше нет".layoutString) },
            content = { text("ждём новое расписание".layoutString) },
        )
    }
    return titleCard(
        onClick = refresh,
        height = expand(),
        colors = filledTonalCardColors(),
        title = { text(n.subject.layoutString) },
        content = { text(Fmt.roomLine(n).layoutString) },
        time = { text(Fmt.time(n.start).layoutString) },
    )
}

private fun spacer(h: Float): LayoutElement =
    LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

/** Линейный прогресс пары в цветах динамической темы. */
private fun MaterialScope.progressBar(fraction: Float): LayoutElement {
    val f = fraction.coerceIn(0.04f, 0.96f)
    fun segment(color: LayoutColor, w: Float): LayoutElement =
        LayoutElementBuilders.Box.Builder()
            .setWidth(weight(w))
            .setHeight(dp(6f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(color.prop)
                            .setCorner(shapes.full)
                            .build()
                    )
                    .build()
            )
            .build()

    return LayoutElementBuilders.Row.Builder()
        .setWidth(expand())
        .setHeight(dp(6f))
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(14f)).setEnd(dp(14f)).build())
                .build()
        )
        .addContent(segment(colorScheme.primary, f))
        .addContent(segment(colorScheme.surfaceContainerHigh, 1f - f))
        .build()
}
