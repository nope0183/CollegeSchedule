package com.nope.scheduletile.data

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * «Поверхности», которые надо перерисовать после обновления данных или смены группы:
 * на часах это виджет (Tile) и комплекции, на телефоне это виджет на рабочем столе.
 * Каждое приложение регистрирует свой обработчик в Application.onCreate().
 */
object Surfaces {
    private val hooks = CopyOnWriteArrayList<(Context) -> Unit>()

    fun register(hook: (Context) -> Unit) {
        hooks += hook
    }

    fun refreshAll(ctx: Context) {
        hooks.forEach { runCatching { it(ctx) } }
    }
}
