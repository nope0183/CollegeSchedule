package com.nope.scheduletile.phone.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.nope.scheduletile.data.ScheduleSync

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        ScheduleSync.enqueueNow(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetTicker.cancel(context)
    }
}
