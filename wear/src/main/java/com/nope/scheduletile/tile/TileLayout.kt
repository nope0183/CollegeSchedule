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
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.layout.column
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.CardDefaults.filledCardColors
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.layoutString
import com.nope.scheduletile.SettingsActivity
import com.nope.scheduletile.data.BrowsePage
import com.nope.scheduletile.data.BrowsePages
import com.nope.scheduletile.data.Fmt
import com.nope.scheduletile.data.Lesson
import com.nope.scheduletile.data.ScheduleState

/**
 * @param pages страницы листалки (пары по дням); пусто, если данных нет
 * @param browsePage страница, которую листает пользователь, или null: тогда обычный вид «сейчас»
 */
fun MaterialScope.tileLayout(
    ctx: Context,
    s: ScheduleState,
    pages: List<BrowsePage>,
    browsePage: BrowsePage?,
): LayoutElement {
    val openSettings = clickable(
        action = launchAction(ComponentName(ctx, SettingsActivity::class.java), emptyMap()),
        id = "settings",
    )
    if (browsePage != null) return browseLayout(s, pages, browsePage)

    // Кнопка внизу: «Расписание» открывает листалку; пока нет группы или данных, как раньше выбор группы.
    val entry = BrowsePages.entryKey(pages, s.now)
    val canBrowse = s.group != null && s.hasData && entry != null
    return primaryLayout(
        titleSlot = { text(headerText(s).layoutString, typography = Typography.LABEL_SMALL) },
        mainSlot = { mainContent(s, openSettings) },
        bottomSlot = {
            textEdgeButton(
                onClick = if (canBrowse) clickable(id = TileNav.clickId(entry!!, s.now)) else openSettings,
                labelContent = {
                    text(
                        (if (canBrowse) "Расписание" else s.group?.let { "Группа $it" } ?: "Выбрать группу")
                            .layoutString
                    )
                },
                colors = filledTonalButtonColors(),
            )
        },
    )
}

// ───────────────────────────── Листалка ─────────────────────────────

private fun MaterialScope.browseLayout(
    s: ScheduleState,
    pages: List<BrowsePage>,
    page: BrowsePage,
): LayoutElement {
    val idx = pages.indexOf(page)
    // По кругу: перед первой страницей и после последней «сейчас», так что мёртвых кнопок нет
    val prev = pages.getOrNull(idx - 1)?.key ?: TileNav.LIVE
    val next = pages.getOrNull(idx + 1)?.key ?: TileNav.LIVE

    val day = Fmt.dayLabel(page.lessons.first().start, s.now)
    val title = if (page.parts > 1) "$day · ${page.part + 1}/${page.parts}" else day

    val cards = page.lessons.map { lessonCard(it, s) }
    val children = mutableListOf<LayoutElement>()
    cards.forEachIndexed { i, c ->
        if (i > 0) children += spacer(3f)
        children += c
    }
    children += spacer(4f)
    children += navRow(prev, next, s.now)

    return primaryLayout(
        titleSlot = { text(title.layoutString, typography = Typography.LABEL_SMALL) },
        mainSlot = { column(*children.toTypedArray(), width = expand(), height = expand()) },
    )
}

/** Карточка пары в листалке: название, кабинет, время начала. Идущая пара выделена акцентом. */
private fun MaterialScope.lessonCard(l: Lesson, s: ScheduleState): LayoutElement {
    val running = s.now >= l.start && s.now < l.end
    return titleCard(
        onClick = clickable(id = "lesson_${l.start}"),
        height = expand(),
        colors = if (running) filledCardColors() else filledTonalCardColors(),
        title = { text(l.subject.layoutString) },
        content = { text(Fmt.roomLine(l).layoutString) },
        time = { text(Fmt.time(l.start).layoutString) },
    )
}

/** Ряд кнопок ‹ Сейчас ›. */
private fun MaterialScope.navRow(prev: String, next: String, now: Long): LayoutElement =
    buttonGroup(height = dp(36f)) {
        buttonGroupItem {
            textButton(
                onClick = clickable(id = TileNav.clickId(prev, now)),
                labelContent = { text("‹".layoutString) },
                width = expand(),
                height = expand(),
                colors = filledTonalButtonColors(),
            )
        }
        buttonGroupItem {
            textButton(
                onClick = clickable(id = TileNav.clickId(TileNav.LIVE, now)),
                labelContent = { text("Сейчас".layoutString) },
                width = weight(2f),
                height = expand(),
            )
        }
        buttonGroupItem {
            textButton(
                onClick = clickable(id = TileNav.clickId(next, now)),
                labelContent = { text("›".layoutString) },
                width = expand(),
                height = expand(),
                colors = filledTonalButtonColors(),
            )
        }
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
        c != null -> listOf("${c.num} пара", Fmt.room(c), Fmt.minutesShort(c.end - s.now))
            .filter { it.isNotEmpty() }
            .joinToString(" · ")
        n != null && isNextDayMode(s) ->
            "${Fmt.dayLabel(n.start, s.now)} · ${Fmt.minutesShort(n.start - s.now)}"
        else -> "Сейчас"
    }
}

private fun MaterialScope.mainContent(s: ScheduleState, openSettings: Clickable): LayoutElement {
    if (s.group == null) return messageCard("Выберите группу", "Нажмите, чтобы выбрать", openSettings)
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

private fun MaterialScope.messageCard(
    title: String,
    sub: String,
    onClick: Clickable = clickable(id = "message"),
): LayoutElement =
    titleCard(
        onClick = onClick,
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
        else "Пары не начались" to listOf("первая в ${Fmt.time(n.start)}", Fmt.roomLine(n)).joinToString(" · ")
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
