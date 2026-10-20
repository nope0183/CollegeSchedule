package com.nope.scheduletile

import android.app.Application
import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.nope.scheduletile.complication.LessonComplicationService
import com.nope.scheduletile.data.ScheduleSync
import com.nope.scheduletile.data.Surfaces
import com.nope.scheduletile.tile.ScheduleTileService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // После обновления данных или смены группы перерисовываем виджет и комплекции.
        Surfaces.register { ctx ->
            TileService.getUpdater(ctx).requestUpdate(ScheduleTileService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(ctx, ComponentName(ctx, LessonComplicationService::class.java))
                .requestUpdateAll()
        }
        // Обновление расписания ТОЛЬКО на зарядке (+ первая загрузка, пока кэша нет).
        ScheduleSync.schedulePeriodic(this, requiresCharging = true, intervalHours = 1)
        ScheduleSync.fetchOnceIfNoCache(this)
    }
}
