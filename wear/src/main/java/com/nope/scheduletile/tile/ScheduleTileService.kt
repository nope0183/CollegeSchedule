package com.nope.scheduletile.tile

import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.nope.scheduletile.data.BrowsePages
import com.nope.scheduletile.data.ScheduleRepository
import com.nope.scheduletile.data.ScheduleState
import kotlin.time.Duration.Companion.milliseconds

/**
 * Виджет «Пары» на Material 3 Expressive.
 * Цвета берутся из системной динамической темы (Material You, от циферблата),
 * если она недоступна — из стандартной палитры.
 *
 * Два вида: «сейчас» (идущая и следующая пара) и листалка по дням с кабинетами.
 */
class ScheduleTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val ctx = this@ScheduleTileService
        val now = System.currentTimeMillis()

        // Нажатие ‹ / › / «Расписание» / «Сейчас» приходит как id последней нажатой кнопки
        TileNav.handleClick(ctx, requestParams.currentState.lastClickableId, now)

        val state = ScheduleState.compute(ctx, now)
        val all = state.group?.let { ScheduleRepository.lessons(ctx, it) }.orEmpty()
        val pages = BrowsePages.build(all, now)
        val browsePage = TileNav.currentKey(ctx, now)?.let { k -> pages.firstOrNull { it.key == k } }

        val layout = tileLayout(ctx, state, pages, browsePage)
        return tile(
            timeline = Timeline.fromLayoutElement(layout),
            // В листалке вернёмся к «сейчас» сами, если пользователь отвлёкся
            freshness = if (browsePage != null) TileNav.BROWSE_TTL_MS.milliseconds else state.freshness,
        )
    }
}
