package com.nope.scheduletile.phone.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.nope.scheduletile.data.ScheduleSync

/** Тап по пустому виджету: скачать расписание заново. */
class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ScheduleSync.fetchAll(context) // внутри перерисует все виджеты
        ScheduleWidget().update(context, glanceId)
    }
}
