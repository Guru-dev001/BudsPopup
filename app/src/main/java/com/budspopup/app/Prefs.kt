package com.budspopup.app

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences("buds_popup", Context.MODE_PRIVATE)

    /** Part of the Bluetooth device name to match. Empty = any device. */
    fun keyword(ctx: Context): String = sp(ctx).getString("keyword", "Nord Buds") ?: ""

    fun setKeyword(ctx: Context, value: String) {
        sp(ctx).edit().putString("keyword", value.trim()).apply()
    }

    fun enabled(ctx: Context): Boolean = sp(ctx).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean("enabled", value).apply()
    }
}
