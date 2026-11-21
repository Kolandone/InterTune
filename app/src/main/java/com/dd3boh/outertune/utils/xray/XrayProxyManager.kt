/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils.xray

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.dd3boh.outertune.constants.XrayConfigInputKey
import com.dd3boh.outertune.utils.AppProxySelector
import com.dd3boh.outertune.utils.aether.AetherProxyManager
import com.dd3boh.outertune.utils.dataStore
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Manages the embedded Xray-core engine (`patterniha/AndroidLibXrayLite` / `libv2ray.aar`).
 * Runs in local proxy mode (`tunFd = 0`) exposing SOCKS5 on `127.0.0.1:<port>` and
 * HTTP CONNECT on `127.0.0.1:<port + 1>` for WebView, without requiring an Android VPN permission.
 */
object XrayProxyManager {
    private const val TAG = "XrayProxyManager"
    private const val MAX_LOG_LINES = 200
    const val DEFAULT_HOST = "127.0.0.1"
    const val DEFAULT_PORT = 10808

    enum class State {
        STOPPED,
        STARTING,
        CONNECTED,
        ERROR
    }

    data class ConnectionTestResult(
        val ip: String,
        val country: String,
        val latencyMs: Long
    )

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _activePort = MutableStateFlow(DEFAULT_PORT)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _scanProgress = MutableStateFlow<String?>(null)
    val scanProgress: StateFlow<String?> = _scanProgress.asStateFlow()

    private val _activeProfileName = MutableStateFlow<String?>(null)
    val activeProfileName: StateFlow<String?> = _activeProfileName.asStateFlow()

    private const val REAL_DELAY_TEST_URL = "https://www.gstatic.com/generate_204"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var controller: CoreController? = null
    private var startJob: Job? = null
    private var healthJob: Job? = null
    private var envInitialized = false
    private var fallbackProxy: Proxy? = null

    fun setManualFallbackProxy(proxy: Proxy?) {
        fallbackProxy = proxy
    }

    fun coreVersion(): String = runCatching {
        val raw = Libv2ray.checkVersionX()
        val ver = Regex("""v\d+\.\d+\.\d+""").find(raw)?.value
        if (ver != null) "Xray $ver" else "Xray Core"
    }.getOrDefault("Xray Core")

    @Synchronized
    fun start(
        context: Context,
        rawConfig: String,
        port: Int = DEFAULT_PORT,
        forceRestart: Boolean = true
    ) {
        if (!forceRestart && (_state.value == State.STARTING || _state.value == State.CONNECTED) && isRunning()) {
            return
        }

        // Ensure Aether is stopped so both engines never fight over the app proxy
        if (AetherProxyManager.state.value != AetherProxyManager.State.STOPPED) {
            AetherProxyManager.stop()
        }

        stopInternal()
        clearProxyToFallback()
        _lastError.value = null
        _activePort.value = port

        val httpPort = port + 1
        val parsed = XrayConfigParser.buildXrayJson(rawConfig, socksPort = port, httpPort = httpPort)
        val configJson = parsed.getOrElse { err ->
            val msg = err.localizedMessage ?: "Invalid Xray config"
            appendLog("[ERROR] $msg")
            _lastError.value = msg
            _state.value = State.ERROR
            return
        }

        _state.value = State.STARTING
        _activeProfileName.value = XrayConfigParser.summarize(rawConfig)?.let { s ->
            s.remark.ifBlank { "${s.protocol} • ${s.server}" }
        }
        appendLog(">>> Starting Xray (${coreVersion()}) on $DEFAULT_HOST:$port (HTTP :$httpPort)")

        ensureCoreEnv(context)

        val callback = object : CoreCallbackHandler {
            override fun startup(): Long {
                appendLog(">>> Xray core started")
                return 0L
            }

            override fun shutdown(): Long {
                appendLog(">>> Xray core shut down")
                return 0L
            }

            override fun onEmitStatus(p0: Long, p1: String?): Long {
                if (!p1.isNullOrBlank()) {
                    appendLog("[Xray] $p1")
                }
                return 0L
            }
        }

        val ctrl = try {
            Libv2ray.newCoreController(callback)
        } catch (e: Throwable) {
            val msg = "Failed to initialize Xray core: ${e.localizedMessage}"
            Log.e(TAG, msg, e)
            appendLog("[ERROR] $msg")
            _lastError.value = msg
            _state.value = State.ERROR
            return
        }
        controller = ctrl

        startJob = scope.launch {
            try {
                ctrl.startLoop(configJson, 0)
            } catch (e: Throwable) {
                if (controller === ctrl && _state.value != State.STOPPED) {
                    val msg = e.localizedMessage ?: "Xray startLoop failed"
                    Log.e(TAG, msg, e)
                    appendLog("[ERROR] $msg")
                    clearProxyToFallback()
                    _lastError.value = msg
                    _state.value = State.ERROR
                }
            }
        }

        healthJob = scope.launch {
            for (attempt in 1..40) {
                delay(250)
                if (controller !== ctrl || _state.value == State.ERROR || _state.value == State.STOPPED) break
                if (isPortReachable(DEFAULT_HOST, port)) {
                    Log.i(TAG, "Xray SOCKS5 ready on $DEFAULT_HOST:$port (HTTP :$httpPort)")
                    appendLog(">>> SOCKS5 proxy ready on $DEFAULT_HOST:$port")
                    applyProxyToApp(DEFAULT_HOST, port, httpPort)
                    _state.value = State.CONNECTED
                    return@launch
                }
            }
            if (controller === ctrl && _state.value == State.STARTING) {
                val msg = "Xray failed to open port $port — check config & logs"
                appendLog("[ERROR] $msg")
                clearProxyToFallback()
                _lastError.value = msg
                _state.value = State.ERROR
            }
        }
    }

    @Synchronized
    fun stop() {
        stopInternal()
        _state.value = State.STOPPED
        _lastError.value = null
        clearProxyToFallback()
        appendLog(">>> Xray proxy stopped")
    }

    private fun stopInternal() {
        healthJob?.cancel()
        healthJob = null
        val ctrl = controller
        controller = null
        if (ctrl != null) {
            runCatching { ctrl.stopLoop() }
        }
        startJob?.cancel()
        startJob = null
    }

    fun isRunning(): Boolean = runCatching { controller?.isRunning == true }.getOrDefault(false)

    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        val current = _state.value
        if (current == State.CONNECTED) return true
        if (current != State.STARTING) return false
        return withTimeoutOrNull(timeoutMs) {
            _state.first { it != State.STARTING }
        } == State.CONNECTED
    }

    private fun applyProxyToApp(host: String, socksPort: Int, httpPort: Int) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, socksPort))
        YouTube.proxy = proxy
        AppProxySelector.dynamic.activeProxy = proxy
        applyWebViewProxy(host, httpPort)
    }

    private fun clearProxyToFallback() {
        val restore = fallbackProxy
        YouTube.proxy = restore
        AppProxySelector.dynamic.activeProxy = restore
        clearWebViewProxy()
    }

    private fun applyWebViewProxy(host: String, httpPort: Int) {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    val config = ProxyConfig.Builder()
                        .addProxyRule("http://$host:$httpPort")
                        .addBypassRule("localhost")
                        .addBypassRule("127.0.0.1")
                        .addBypassRule("[::1]")
                        .build()
                    ProxyController.getInstance().setProxyOverride(config, { it.run() }) {
                        Log.i(TAG, "WebView proxy routed through Xray http://$host:$httpPort")
                    }
                }
            }
        }
    }

    private fun clearWebViewProxy() {
        Handler(Looper.getMainLooper()).post {
            runCatching {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    ProxyController.getInstance().clearProxyOverride({ it.run() }) {}
                }
            }
        }
    }

    suspend fun testConnection(port: Int = _activePort.value): Result<ConnectionTestResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(DEFAULT_HOST, port))
                val client = OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build()
                val startMs = System.currentTimeMillis()
                val request = Request.Builder()
                    .url("https://www.cloudflare.com/cdn-cgi/trace")
                    .build()
                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - startMs
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    val map = body.lines()
                        .mapNotNull { line ->
                            val idx = line.indexOf('=')
                            if (idx > 0) line.substring(0, idx) to line.substring(idx + 1) else null
                        }
                        .toMap()
                    ConnectionTestResult(
                        ip = map["ip"] ?: "unknown",
                        country = map["loc"] ?: "unknown",
                        latencyMs = latency
                    )
                }
            }
        }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun ensureCoreEnv(context: Context) {
        if (envInitialized) return
        val assetDir = File(context.filesDir, "xray_assets").apply { mkdirs() }
        runCatching {
            Libv2ray.initCoreEnv(assetDir.absolutePath, "")
            envInitialized = true
        }.onFailure { e ->
            appendLog("[WARN] initCoreEnv: ${e.message}")
        }
    }

    /**
     * Measures real end-to-end HTTP delay (in ms) through Xray core without interrupting the active tunnel.
     * Returns > 0 on success, or -1L on timeout / failure.
     */
    suspend fun measureRealDelay(
        context: Context,
        rawConfig: String,
        testUrl: String = REAL_DELAY_TEST_URL
    ): Long = withContext(Dispatchers.IO) {
        ensureCoreEnv(context)
        val json = XrayConfigParser.buildXrayJson(rawConfig, socksPort = 0, httpPort = 0).getOrNull()
            ?: return@withContext -1L
        runCatching {
            withTimeoutOrNull(8_000L) {
                val delay = Libv2ray.measureOutboundDelay(json, testUrl)
                if (delay > 0L) delay else -1L
            } ?: -1L
        }.getOrDefault(-1L)
    }

    /**
     * Tests real delay of all saved profiles concurrently (max 5 at a time), updates [XrayProfileStore]
     * live, sorts by lowest real delay, and returns the fastest working profile (or null).
     */
    suspend fun testAllRealDelays(context: Context): XrayProfileStore.Profile? = withContext(Dispatchers.IO) {
        XrayProfileStore.ensureLoaded(context)
        val profiles = XrayProfileStore.profiles.value
        if (profiles.isEmpty()) return@withContext null

        val total = profiles.size
        val done = AtomicInteger(0)
        val semaphore = Semaphore(5)
        _scanProgress.value = "0 / $total"
        appendLog(">>> Testing real delay for $total Xray servers...")

        val results = coroutineScope {
            profiles.map { profile ->
                async {
                    semaphore.withPermit {
                        val ms = measureRealDelay(context, profile.rawConfig)
                        XrayProfileStore.updateDelay(context, profile.id, ms)
                        val count = done.incrementAndGet()
                        _scanProgress.value = "$count / $total"
                        profile.copy(delayMs = ms)
                    }
                }
            }.awaitAll()
        }

        XrayProfileStore.sortByDelay(context)
        _scanProgress.value = null
        val best = results.filter { (it.delayMs ?: -1L) > 0L }.minByOrNull { it.delayMs!! }
        if (best != null) {
            appendLog(">>> Best server: ${best.name} (${best.delayMs} ms)")
        } else {
            appendLog(">>> No working Xray servers responded to real-delay test")
        }
        best
    }

    /**
     * ZedSecure-style Auto-Select: tests all profiles with real-delay ping, selects the fastest
     * working server, saves it as active config, and immediately starts Xray with it.
     */
    suspend fun autoSelectAndConnect(
        context: Context,
        port: Int = DEFAULT_PORT
    ): Result<XrayProfileStore.Profile> = withContext(Dispatchers.IO) {
        runCatching {
            val best = testAllRealDelays(context)
                ?: error("No working server found — check your subscription or internet")
            XrayProfileStore.selectProfile(context, best.id)
            context.dataStore.edit { it[XrayConfigInputKey] = best.rawConfig }
            start(context, best.rawConfig, port = port, forceRestart = true)
            best
        }
    }

    private fun isPortReachable(host: String, port: Int): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress(host, port), 300)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun appendLog(line: String) {
        val current = _logs.value
        _logs.value = if (current.size >= MAX_LOG_LINES) {
            current.drop(current.size - MAX_LOG_LINES + 1) + line
        } else {
            current + line
        }
    }
}
