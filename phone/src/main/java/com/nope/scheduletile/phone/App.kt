package com.nope.scheduletile.phone

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.nope.scheduletile.data.ScheduleSync
import com.nope.scheduletile.data.Surfaces
import com.nope.scheduletile.phone.widget.ScheduleWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // После загрузки данных или смены группы перерисовываем все виджеты.
        Surfaces.register { ctx ->
            CoroutineScope(Dispatchers.Default).launch { ScheduleWidget().updateAll(ctx) }
        }
        // На телефоне обновляем раз в 3 часа при наличии сети (зарядка не нужна).
        ScheduleSync.schedulePeriodic(this, requiresCharging = false, intervalHours = 3)
        ScheduleSync.fetchOnceIfNoCache(this)
    }
}
