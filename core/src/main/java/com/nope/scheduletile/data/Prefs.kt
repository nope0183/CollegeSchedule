package com.nope.scheduletile.data

import android.content.Context

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("schedule", Context.MODE_PRIVATE)

    fun group(ctx: Context): String? = sp(ctx).getString("group", null)?.takeIf { it.isNotBlank() }
    fun setGroup(ctx: Context, g: String) = sp(ctx).edit().putString("group", g).apply()

    fun lastSync(ctx: Context): Long = sp(ctx).getLong("last_sync", 0L)
    fun setLastSync(ctx: Context, ms: Long) = sp(ctx).edit().putLong("last_sync", ms).apply()
}
