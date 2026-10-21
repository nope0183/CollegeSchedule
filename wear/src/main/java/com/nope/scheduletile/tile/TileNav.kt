package com.nope.scheduletile.tile

import android.content.Context

/**
 * Состояние листалки виджета. Хранится в SharedPreferences, потому что Tile перерисовывается
 * «с нуля» на каждый запрос системы.
 *
 * Нажатие на кнопку приходит как `lastClickableId` в следующем запросе. Система может прислать
 * его повторно и при обычном обновлении, поэтому id делаются уникальными (`nav|<время>|<цель>`),
 * а уже обработанный id запоминается и повторно не применяется.
 */
object TileNav {
    /** Цель «вернуться к текущей паре». */
    const val LIVE = "live"

    /** Через сколько без нажатий листалка сама возвращается к «сейчас». */
    const val BROWSE_TTL_MS = 90_000L

    private const val PREFIX = "nav"

    private fun sp(ctx: Context) = ctx.getSharedPreferences("tile_nav", Context.MODE_PRIVATE)

    fun clickId(target: String, now: Long): String = "$PREFIX|$now|$target"

    /** Применяет нажатие, если оно новое. */
    fun handleClick(ctx: Context, clickableId: String?, now: Long) {
        if (clickableId.isNullOrEmpty() || !clickableId.startsWith("$PREFIX|")) return
        val p = sp(ctx)
        if (p.getString("handled", null) == clickableId) return
        val target = clickableId.split("|", limit = 3).getOrNull(2) ?: return
        p.edit()
            .putString("handled", clickableId)
            .putString("key", target.takeIf { it != LIVE })
            .putLong("ts", now)
            .apply()
    }

    /** Ключ текущей страницы листалки или null, если показываем «сейчас». */
    fun currentKey(ctx: Context, now: Long): String? {
        val p = sp(ctx)
        val key = p.getString("key", null) ?: return null
        if (now - p.getLong("ts", 0L) > BROWSE_TTL_MS) {
            p.edit().remove("key").apply()
            return null
        }
        return key
    }
}
