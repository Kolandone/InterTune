/*
 * Copyright (C) 2026 KLtune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Persistent per-app language & RTL layout helper (System / English / Persian) for ComponentActivity.
 */
object AppLocaleHelper {
    private const val PREFS = "kltune_locale"
    private const val KEY_LANG = "app_lang"

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "").orEmpty()

    fun isRtl(context: Context): Boolean {
        val lang = getLanguage(context).ifBlank { Locale.getDefault().language }
        return lang.lowercase() in setOf("fa", "ar", "he", "ur")
    }

    fun setLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANG, lang)
            .apply()
        (context as? Activity)?.recreate()
    }

    fun wrap(base: Context): Context {
        val lang = getLanguage(base)
        if (lang.isBlank()) return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
