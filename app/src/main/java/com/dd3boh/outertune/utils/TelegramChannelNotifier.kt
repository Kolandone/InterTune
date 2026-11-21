/*
 * Copyright (C) 2026 KLtune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dd3boh.outertune.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Periodically checks the public Telegram channel `@kolandjs1` every 10 minutes using a persistent
 * timestamp in SharedPreferences (never resets when the app is closed and reopened).
 * Keeps a history of seen post IDs so duplicates are never sent, trims messages to a short preview,
 * and opens the Telegram channel when the notification is tapped.
 */
object TelegramChannelNotifier {
    private const val TAG = "TelegramChannelNotifier"
    private const val CHANNEL_USERNAME = "kolandjs1"
    private const val CHANNEL_PREVIEW_URL = "https://t.me/s/$CHANNEL_USERNAME"
    private const val CHANNEL_OPEN_URL = "https://t.me/$CHANNEL_USERNAME"
    private const val PREFS_NAME = "kltune_tg_notifier"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"
    private const val KEY_SEEN_IDS = "seen_post_ids"
    private const val KEY_INITIALIZED = "history_initialized"
    private const val CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
    private const val MAX_HISTORY = 50
    private const val MAX_SNIPPET_CHARS = 85
    private const val NOTIF_CHANNEL_ID = "kltune_channel_updates"
    private const val NOTIF_ID = 94101

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var loopJob: Job? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .proxySelector(AppProxySelector.dynamic)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private data class ChannelPost(val id: Long, val snippet: String)

    @Synchronized
    fun start(context: Context) {
        if (loopJob?.isActive == true) return
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        loopJob = scope.launch {
            while (isActive) {
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
                val now = System.currentTimeMillis()
                val elapsed = now - lastCheck
                val waitMs = if (lastCheck == 0L || elapsed >= CHECK_INTERVAL_MS) {
                    0L
                } else {
                    CHECK_INTERVAL_MS - elapsed
                }

                if (waitMs > 0L) {
                    delay(waitMs)
                }

                // Persist check timestamp before/after check so restarting app never resets the 10-min cycle
                prefs.edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply()
                runCatching { checkOnce(appContext) }
                    .onFailure { Log.d(TAG, "Channel check skipped: ${it.message}") }

                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private fun checkOnce(context: Context) {
        val request = Request.Builder()
            .url(CHANNEL_PREVIEW_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .build()

        val html = httpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return
            resp.body?.string().orEmpty()
        }
        if (html.isBlank()) return

        val posts = parsePosts(html)
        if (posts.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val seenIds = prefs.getString(KEY_SEEN_IDS, "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .toMutableSet()

        if (!initialized) {
            // Seed history on first install so old posts are not spammed
            posts.forEach { seenIds += it.id }
            saveHistory(prefs, seenIds)
            return
        }

        val unseen = posts.filter { it.id !in seenIds }.sortedBy { it.id }
        if (unseen.isEmpty()) return

        // Notify only for the newest unseen post (short preview) and mark all fetched posts as seen
        val latest = unseen.last()
        posts.forEach { seenIds += it.id }
        saveHistory(prefs, seenIds)

        showNotification(context, latest)
    }

    private fun saveHistory(prefs: android.content.SharedPreferences, seenIds: Set<Long>) {
        val kept = seenIds.sortedDescending().take(MAX_HISTORY).joinToString(",")
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_SEEN_IDS, kept)
            .apply()
    }

    private fun parsePosts(html: String): List<ChannelPost> {
        val postRegex = Regex(
            """data-post="$CHANNEL_USERNAME/(\d+)"[\s\S]*?(?=data-post="$CHANNEL_USERNAME/\d+"|$)""",
            RegexOption.IGNORE_CASE
        )
        val textRegex = Regex(
            """<div class="tgme_widget_message_text[^"]*"[^>]*>([\s\S]*?)</div>""",
            RegexOption.IGNORE_CASE
        )
        val result = mutableListOf<ChannelPost>()
        for (match in postRegex.findAll(html)) {
            val id = match.groupValues[1].toLongOrNull() ?: continue
            val block = match.value
            val rawText = textRegex.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val plain = if (rawText.isNotBlank()) {
                val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(rawText, Html.FROM_HTML_MODE_LEGACY).toString()
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(rawText).toString()
                }
                decoded.replace(Regex("\\s+"), " ").trim()
            } else {
                ""
            }
            val snippet = when {
                plain.isEmpty() -> "پیام جدید در کانال @$CHANNEL_USERNAME"
                plain.length <= MAX_SNIPPET_CHARS -> plain
                else -> plain.take(MAX_SNIPPET_CHARS).trimEnd() + "…"
            }
            result += ChannelPost(id, snippet)
        }
        return result
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                context.getString(R.string.tg_notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.tg_notif_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, post: ChannelPost) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$CHANNEL_OPEN_URL/${post.id}")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            post.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.small_icon)
            .setContentTitle(context.getString(R.string.tg_notif_title))
            .setContentText(post.snippet)
            .setStyle(NotificationCompat.BigTextStyle().bigText(post.snippet))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
        }
    }
}
