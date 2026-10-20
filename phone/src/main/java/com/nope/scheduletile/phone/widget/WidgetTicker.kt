package com.nope.scheduletile.phone.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.nope.scheduletile.data.ScheduleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Виджеты не тикают сами, поэтому будим их будильником: пока идёт пара, каждые 5 минут
 * (обновляются прогресс и «ещё N мин»), иначе к началу следующей пары, но не реже чем раз в 30 минут.
 * Будильник неточный (без разрешения на точные), на границах пар возможна задержка в пару минут.
 */
object WidgetTicker {
    private const val FIVE_MIN = 5 * 60_000L
    private const val HALF_HOUR = 30 * 60_000L

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun scheduleNext(ctx: Context, s: ScheduleState) {
        val current = s.current
        val next = s.next
        val delay = when {
            current != null -> minOf(current.end - s.now + 1_000, FIVE_MIN)
            next != null -> minOf(next.start - s.now + 1_000, HALF_HOUR)
            else -> HALF_HOUR
        }.coerceAtLeast(60_000L)
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC, s.now + delay, pending(ctx))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx))
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // updateAll → provideGlance → scheduleNext: цикл продолжается, пока есть виджеты
                ScheduleWidget().updateAll(context)
            } finally {
                result.finish()
            }
        }
    }
}
