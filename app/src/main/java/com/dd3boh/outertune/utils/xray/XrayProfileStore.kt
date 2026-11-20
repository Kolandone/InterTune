/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils.xray

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Persistent store for Xray subscriptions and server profiles (with real-delay ping results).
 * Saved as JSON under `filesDir/xray_profiles.json`.
 */
object XrayProfileStore {
    private const val FILE_NAME = "xray_profiles.json"

    data class Subscription(
        val id: String,
        val name: String,
        val url: String,
        val lastUpdatedMs: Long = System.currentTimeMillis()
    )

    data class Profile(
        val id: String,
        val name: String,
        val protocol: String,
        val server: String,
        val rawConfig: String,
        val subscriptionId: String? = null,
        val delayMs: Long? = null // null = untested, -1 = failed/timeout, >0 = real delay ms
    )

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            _selectedProfileId.value = root.optString("selectedProfileId").takeIf { it.isNotBlank() }

            val subsArr = root.optJSONArray("subscriptions") ?: JSONArray()
            val subs = mutableListOf<Subscription>()
            for (i in 0 until subsArr.length()) {
                val o = subsArr.optJSONObject(i) ?: continue
                subs += Subscription(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    url = o.optString("url"),
                    lastUpdatedMs = o.optLong("lastUpdatedMs", 0L)
                )
            }
            _subscriptions.value = subs

            val profArr = root.optJSONArray("profiles") ?: JSONArray()
            val profs = mutableListOf<Profile>()
            for (i in 0 until profArr.length()) {
                val o = profArr.optJSONObject(i) ?: continue
                val delay = if (o.has("delayMs") && !o.isNull("delayMs")) o.optLong("delayMs") else null
                profs += Profile(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    protocol = o.optString("protocol"),
                    server = o.optString("server"),
                    rawConfig = o.optString("rawConfig"),
                    subscriptionId = o.optString("subscriptionId").takeIf { it.isNotBlank() },
                    delayMs = delay
                )
            }
            _profiles.value = profs
        }
    }

    @Synchronized
    private fun persist(context: Context) {
        runCatching {
            val root = JSONObject()
            _selectedProfileId.value?.let { root.put("selectedProfileId", it) }

            val subsArr = JSONArray()
            _subscriptions.value.forEach { s ->
                subsArr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("name", s.name)
                        .put("url", s.url)
                        .put("lastUpdatedMs", s.lastUpdatedMs)
                )
            }
            root.put("subscriptions", subsArr)

            val profArr = JSONArray()
            _profiles.value.forEach { p ->
                val o = JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("protocol", p.protocol)
                    .put("server", p.server)
                    .put("rawConfig", p.rawConfig)
                p.subscriptionId?.let { o.put("subscriptionId", it) }
                p.delayMs?.let { o.put("delayMs", it) }
                profArr.put(o)
            }
            root.put("profiles", profArr)

            File(context.filesDir, FILE_NAME).writeText(root.toString())
        }
    }

    fun selectProfile(context: Context, profileId: String?) {
        ensureLoaded(context)
        _selectedProfileId.value = profileId
        persist(context)
    }

    fun getSelectedProfile(context: Context): Profile? {
        ensureLoaded(context)
        val id = _selectedProfileId.value ?: return _profiles.value.firstOrNull()
        return _profiles.value.firstOrNull { it.id == id } ?: _profiles.value.firstOrNull()
    }

    fun updateDelay(context: Context, profileId: String, delayMs: Long) {
        ensureLoaded(context)
        _profiles.value = _profiles.value.map {
            if (it.id == profileId) it.copy(delayMs = delayMs) else it
        }
        persist(context)
    }

    fun sortByDelay(context: Context) {
        ensureLoaded(context)
        _profiles.value = _profiles.value.sortedWith(
            compareBy<Profile>(
                {
                    when {
                        it.delayMs != null && it.delayMs > 0 -> 0
                        it.delayMs == null -> 1
                        else -> 2
                    }
                },
                { it.delayMs?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE }
            )
        )
        persist(context)
    }

    /**
     * Import one or multiple configs (share links or JSON) from pasted text.
     * Returns number of imported profiles.
     */
    fun importRawText(
        context: Context,
        rawText: String,
        subscriptionId: String? = null
    ): List<Profile> {
        ensureLoaded(context)
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        val extracted = extractConfigEntries(trimmed)
        if (extracted.isEmpty()) return emptyList()

        val newProfiles = extracted.mapNotNull { entry ->
            val summary = XrayConfigParser.summarize(entry) ?: return@mapNotNull null
            val displayName = summary.remark.ifBlank { "${summary.protocol} • ${summary.server}" }
            Profile(
                id = UUID.randomUUID().toString(),
                name = displayName,
                protocol = summary.protocol,
                server = summary.server,
                rawConfig = entry,
                subscriptionId = subscriptionId,
                delayMs = null
            )
        }
        if (newProfiles.isEmpty()) return emptyList()

        val base = if (subscriptionId != null) {
            _profiles.value.filterNot { it.subscriptionId == subscriptionId }
        } else {
            _profiles.value
        }
        _profiles.value = base + newProfiles
        if (_selectedProfileId.value == null || _profiles.value.none { it.id == _selectedProfileId.value }) {
            _selectedProfileId.value = newProfiles.first().id
        }
        persist(context)
        return newProfiles
    }

    fun deleteProfile(context: Context, profileId: String) {
        ensureLoaded(context)
        _profiles.value = _profiles.value.filterNot { it.id == profileId }
        if (_selectedProfileId.value == profileId) {
            _selectedProfileId.value = _profiles.value.firstOrNull()?.id
        }
        persist(context)
    }

    fun clearAllProfiles(context: Context) {
        ensureLoaded(context)
        _profiles.value = emptyList()
        _subscriptions.value = emptyList()
        _selectedProfileId.value = null
        persist(context)
    }

    fun deleteSubscription(context: Context, subId: String) {
        ensureLoaded(context)
        _subscriptions.value = _subscriptions.value.filterNot { it.id == subId }
        _profiles.value = _profiles.value.filterNot { it.subscriptionId == subId }
        if (_profiles.value.none { it.id == _selectedProfileId.value }) {
            _selectedProfileId.value = _profiles.value.firstOrNull()?.id
        }
        persist(context)
    }

    /**
     * Fetch a subscription URL (supports Base64-encoded or plain URI lists),
     * save/update the subscription record, and import its configs.
     */
    suspend fun fetchAndImportSubscription(
        context: Context,
        url: String,
        name: String = "",
        existingSubId: String? = null
    ): Result<Pair<Subscription, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            ensureLoaded(context)
            val cleanUrl = url.trim()
            require(cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) {
                "Subscription URL must start with http:// or https://"
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "v2rayNG/1.9.0")
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string().orEmpty()
            }
            require(body.isNotBlank()) { "Empty subscription response" }

            val subId = existingSubId ?: UUID.randomUUID().toString()
            val imported = importRawText(context, body, subscriptionId = subId)
            require(imported.isNotEmpty()) { "No valid VLESS/VMess/Trojan/SS configs found in subscription" }

            val subName = name.trim().ifBlank {
                runCatching { java.net.URI(cleanUrl).host ?: "Subscription" }.getOrDefault("Subscription")
            }
            val sub = Subscription(
                id = subId,
                name = subName,
                url = cleanUrl,
                lastUpdatedMs = System.currentTimeMillis()
            )
            val updatedSubs = _subscriptions.value.filterNot { it.id == subId } + sub
            _subscriptions.value = updatedSubs
            persist(context)
            sub to imported.size
        }
    }

    /**
     * Extracts individual config links (or a single JSON object) from raw or Base64-encoded text.
     */
    private fun extractConfigEntries(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return listOf(trimmed)
        }
        val directLines = parseSupportedLines(trimmed)
        if (directLines.isNotEmpty()) return directLines

        // Try Base64 decoding (standard v2ray/Xray subscription format)
        val decoded = tryDecodeBase64(trimmed)
        if (!decoded.isNullOrBlank()) {
            if (decoded.trim().startsWith("{")) return listOf(decoded.trim())
            return parseSupportedLines(decoded)
        }
        return emptyList()
    }

    private fun parseSupportedLines(text: String): List<String> =
        text.lines()
            .map { it.trim() }
            .filter { line ->
                line.startsWith("vless://", ignoreCase = true) ||
                    line.startsWith("vmess://", ignoreCase = true) ||
                    line.startsWith("trojan://", ignoreCase = true) ||
                    line.startsWith("ss://", ignoreCase = true)
            }

    private fun tryDecodeBase64(input: String): String? = runCatching {
        val clean = input.replace("\\s".toRegex(), "").replace('-', '+').replace('_', '/')
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }.getOrNull()
}
