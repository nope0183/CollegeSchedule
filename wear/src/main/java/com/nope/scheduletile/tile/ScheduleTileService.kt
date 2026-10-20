package com.nope.scheduletile.tile

import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.nope.scheduletile.data.ScheduleState

/**
 * Виджет «Пары» на Material 3 Expressive.
 * Цвета берутся из системной динамической темы (Material You, от циферблата),
 * если она недоступна — из стандартной палитры.
 */
class ScheduleTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val state = ScheduleState.compute(this@ScheduleTileService)
        val layout = tileLayout(this@ScheduleTileService, state)
        return tile(
            timeline = Timeline.fromLayoutElement(layout),
            freshness = state.freshness,
        )
    }
}
