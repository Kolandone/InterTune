/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils.aether

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.dd3boh.outertune.utils.AppProxySelector
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Manages the embedded Aether censorship-circumvention engine (v2.4.0+).
 *
 * Aether runs as a local native subprocess (`libaether.so` in `nativeLibraryDir`,
 * extracted via `extractNativeLibs="true"` + `useLegacyPackaging = true`),
 * establishes an encrypted MASQUE / WireGuard / Gool / MiM / Tor / Psiphon tunnel,
 * and exposes a local SOCKS5 proxy.
 *
 * All YouTube Music API requests, audio streaming (ExoPlayer / SABR), cover art (Coil),
 * lyrics, and downloads are routed dynamically through [AppProxySelector.dynamic] and
 * [YouTube.proxy] without requiring an external VPN or app restart.
 */
object AetherProxyManager {
    private const val TAG = "AetherProxyManager"
    private const val MAX_LOG_LINES = 300
    const val DEFAULT_PORT = 1819
    const val DEFAULT_HOST = "127.0.0.1"

    enum class State {
        STOPPED,
        STARTING,
        CONNECTED,
        ERROR
    }

    data class ConnectionTestResult(
        val ip: String,
        val country: String,
        val warp: String,
        val latencyMs: Long
    )

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _statusDetail = MutableStateFlow<String?>(null)
    val statusDetail: StateFlow<String?> = _statusDetail.asStateFlow()

    private val _activePort = MutableStateFlow(DEFAULT_PORT)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var healthJob: Job? = null
    private var readerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var fallbackProxy: Proxy? = null

    /**
     * Save the user's manual proxy (if any) so stopping Aether can cleanly restore it.
     */
    fun setManualFallbackProxy(proxy: Proxy?) {
        fallbackProxy = proxy
    }

    /**
     * Start (or restart) the internal Aether SOCKS5 proxy with the given [AetherConfig].
     */
    @Synchronized
    fun start(
        context: Context,
        config: AetherConfig,
        forceRestart: Boolean = true
    ) {
        if (!forceRestart && (_state.value == State.STARTING || _state.value == State.CONNECTED) && isRunning()) {
            return
        }

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val binary = resolveBinary(context)
        if (binary == null || !binary.exists()) {
            val foundFiles = nativeDir.listFiles()?.joinToString(", ") { it.name } ?: "none"
            val errorMsg = "Aether binary not found for ABI ${Build.SUPPORTED_ABIS.firstOrNull()} (nativeDir: $foundFiles)"
            Log.e(TAG, errorMsg)
            appendLog("[ERROR] $errorMsg")
            _lastError.value = errorMsg
            _state.value = State.ERROR
            return
        }

        if (!binary.canExecute()) {
            binary.setExecutable(true, false)
        }

        stopProcess()
        _lastError.value = null
        _statusDetail.value = "Launching Aether..."
        _state.value = State.STARTING

        val configDir = File(context.filesDir, "aether").apply { mkdirs() }
        val cacheDir = File(context.cacheDir, "aether_tmp").apply { mkdirs() }
        val targetPort = config.effectiveSocksPort()
        _activePort.value = targetPort

        val args = config.buildCliArgs(
            binaryPath = binary.absolutePath,
            nativeDir = nativeDir,
            configDir = configDir
        )

        val cmdLine = args.joinToString(" ")
        Log.i(TAG, "Starting Aether: $cmdLine")
        appendLog(">>> Starting Aether (target SOCKS5: $DEFAULT_HOST:$targetPort)")
        appendLog(">>> $cmdLine")

        try {
            val pb = ProcessBuilder(args)
            pb.directory(configDir)
            pb.redirectErrorStream(true)
            pb.environment().apply {
                put("HOME", configDir.absolutePath)
                put("TMPDIR", cacheDir.absolutePath)
                put("TEMP", cacheDir.absolutePath)
                put("TMP", cacheDir.absolutePath)
                put("XDG_CONFIG_HOME", configDir.absolutePath)
                put("XDG_CACHE_HOME", cacheDir.absolutePath)
                put("XDG_DATA_HOME", configDir.absolutePath)
            }

            val proc = pb.start()
            process = proc

            // Close stdin immediately so Aether can never block waiting for interactive input
            runCatching { proc.outputStream.close() }

            acquireWakeLock(context)

            // Stream stdout/stderr into live logs and parse status milestones
            readerJob = scope.launch {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            val clean = stripAnsi(line).trim()
                            if (clean.isNotEmpty()) {
                                Log.d(TAG, "[Aether] $clean")
                                appendLog(clean)
                                updateStatusFromLog(clean)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.w(TAG, "Error reading Aether output: ${e.message}")
                    }
                }
            }

            // Monitor process termination
            monitorJob = scope.launch {
                val exitCode = try {
                    proc.waitFor()
                } catch (_: InterruptedException) {
                    -1
                }
                Log.w(TAG, "Aether process terminated with code $exitCode")
                appendLog(">>> Process exited with code $exitCode")
                releaseWakeLock()
                if (process === proc && _state.value != State.STOPPED) {
                    val lastLine = _logs.value.lastOrNull {
                        it.contains("error", ignoreCase = true) ||
                            it.contains("failed", ignoreCase = true) ||
                            it.contains("refused", ignoreCase = true)
                    }
                    _lastError.value = lastLine ?: "Process exited with code $exitCode"
                    _statusDetail.value = null
                    _state.value = State.ERROR
                }
            }

            // Health check: wait up to 180s (thorough/ironclad/Tor/Psiphon can take time)
            healthJob = scope.launch {
                for (attempt in 1..360) {
                    delay(500)
                    if (!isRunning() || process !== proc) break
                    if (isPortReachable(DEFAULT_HOST, targetPort)) {
                        Log.i(TAG, "Aether SOCKS5 is ready on $DEFAULT_HOST:$targetPort")
                        appendLog(">>> SOCKS5 proxy ready on $DEFAULT_HOST:$targetPort")
                        applyProxyToApp(DEFAULT_HOST, targetPort)
                        _statusDetail.value = "SOCKS5 $DEFAULT_HOST:$targetPort"
                        _state.value = State.CONNECTED
                        return@launch
                    }
                }
                if (process === proc && _state.value == State.STARTING) {
                    Log.w(TAG, "Timed out waiting for Aether SOCKS5 port $targetPort")
                    appendLog(">>> Timed out waiting for port $targetPort")
                    _lastError.value = "Connection timeout (180s) — check Logs for details"
                    _state.value = State.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Aether", e)
            appendLog("[ERROR] Failed to launch: ${e.localizedMessage}")
            _lastError.value = e.localizedMessage ?: "Failed to start"
            _statusDetail.value = null
            _state.value = State.ERROR
            releaseWakeLock()
        }
    }

    /**
     * Stop the internal Aether SOCKS5 proxy and restore direct (or manual fallback) routing.
     */
    @Synchronized
    fun stop() {
        stopProcess()
        _state.value = State.STOPPED
        _lastError.value = null
        _statusDetail.value = null
        val restore = fallbackProxy
        YouTube.proxy = restore
        AppProxySelector.dynamic.activeProxy = restore
        appendLog(">>> Aether proxy stopped")
        Log.i(TAG, "Aether proxy stopped")
    }

    private fun stopProcess() {
        healthJob?.cancel()
        healthJob = null
        monitorJob?.cancel()
        monitorJob = null
        readerJob?.cancel()
        readerJob = null

        val proc = process
        process = null
        proc?.let {
            try {
                it.outputStream.close()
            } catch (_: Exception) {}
            try {
                it.inputStream.close()
            } catch (_: Exception) {}
            try {
                it.errorStream.close()
            } catch (_: Exception) {}
            try {
                it.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.destroyForcibly()
                }
            } catch (_: Exception) {}
        }
        releaseWakeLock()
    }

    fun isRunning(): Boolean = try {
        process?.exitValue()
        false
    } catch (_: IllegalThreadStateException) {
        true
    } catch (_: Exception) {
        false
    }

    /**
     * Suspends while Aether is in [State.STARTING] up to [timeoutMs] until it becomes ready
     * or terminates. Returns `true` if connected, `false` otherwise.
     */
    suspend fun awaitReady(timeoutMs: Long = 20_000L): Boolean {
        val current = _state.value
        if (current == State.CONNECTED) return true
        if (current != State.STARTING) return false
        return withTimeoutOrNull(timeoutMs) {
            _state.first { it != State.STARTING }
        } == State.CONNECTED
    }

    private fun isPortReachable(host: String, port: Int): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress(host, port), 400)
            true
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Apply SOCKS5 proxy dynamically to YouTube client and all application OkHttp stacks.
     */
    fun applyProxyToApp(host: String = DEFAULT_HOST, port: Int = _activePort.value) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
        YouTube.proxy = proxy
        AppProxySelector.dynamic.activeProxy = proxy
    }

    /**
     * Test the live Aether SOCKS5 tunnel by requesting Cloudflare's trace endpoint through it.
     */
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
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
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
                        warp = map["warp"] ?: "off",
                        latencyMs = latency
                    )
                }
            }
        }

    /**
     * Delete saved WARP identities, Tor/Psiphon caches, and last-connection state.
     */
    fun resetIdentity(context: Context) {
        val wasRunning = isRunning()
        if (wasRunning) stop()
        val configDir = File(context.filesDir, "aether")
        runCatching { configDir.deleteRecursively() }
        appendLog(">>> Cleared Aether identity and cache directory")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun resolveBinary(context: Context): File? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeBin = File(nativeDir, "libaether.so")
        if (nativeBin.exists() && nativeBin.length() > 0) {
            return nativeBin
        }
        return null
    }

    fun hasPsiphonBinary(context: Context): Boolean {
        val f = File(context.applicationInfo.nativeLibraryDir, "libpsiphon.so")
        return f.exists() && f.length() > 0
    }

    fun hasLyrebirdBinary(context: Context): Boolean {
        val f = File(context.applicationInfo.nativeLibraryDir, "liblyrebird.so")
        return f.exists() && f.length() > 0
    }

    private fun appendLog(line: String) {
        val current = _logs.value
        val next = if (current.size >= MAX_LOG_LINES) {
            current.drop(current.size - MAX_LOG_LINES + 1) + line
        } else {
            current + line
        }
        _logs.value = next
    }

    private fun updateStatusFromLog(line: String) {
        val lower = line.lowercase()
        when {
            "socks5" in lower && ("listening" in lower || "ready" in lower || "serving" in lower) ->
                _statusDetail.value = line
            "scanning" in lower || "register" in lower || "validat" in lower ||
                "connect" in lower || "tunnel" in lower || "tor" in lower ||
                "psiphon" in lower || "gateway" in lower || "exit" in lower ->
                _statusDetail.value = line.take(120)
        }
    }

    private val ansiRegex = Regex("\u001B\\[[;\\d]*[ -/]*[@-~]")
    private fun stripAnsi(input: String): String = ansiRegex.replace(input, "")

    private fun acquireWakeLock(context: Context) {
        releaseWakeLock()
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "outertune:aether_proxy")
            wakeLock?.acquire(60 * 60 * 1000L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire wake lock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {}
        wakeLock = null
    }
}
