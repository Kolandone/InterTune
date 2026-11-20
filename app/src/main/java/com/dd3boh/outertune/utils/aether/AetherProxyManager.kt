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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket

/**
 * Manages the embedded Aether censorship-circumvention engine.
 *
 * Aether runs as a local subprocess that establishes an encrypted MASQUE / WireGuard
 * tunnel and exposes a local SOCKS5 proxy (default 127.0.0.1:1819).
 * All YouTube Music requests, streaming chunks, album covers, and lyrics
 * are routed through this internal proxy, bypassing internet censorship
 * without needing an external VPN application.
 */
object AetherProxyManager {
    private const val TAG = "AetherProxyManager"
    const val DEFAULT_PORT = 1819
    const val DEFAULT_HOST = "127.0.0.1"

    enum class State {
        STOPPED,
        STARTING,
        CONNECTED,
        ERROR
    }

    private val _state = MutableStateFlow(State.STOPPED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var process: Process? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private var healthJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Start the internal Aether SOCKS5 proxy.
     */
    @Synchronized
    fun start(
        context: Context,
        protocol: String = "masque",
        scanMode: String = "turbo",
        port: Int = DEFAULT_PORT
    ) {
        if (_state.value == State.STARTING || _state.value == State.CONNECTED) {
            if (isRunning()) return
        }

        val binary = resolveBinary(context)
        if (binary == null || !binary.exists()) {
            val errorMsg = "Aether binary not found for architecture: ${Build.SUPPORTED_ABIS.firstOrNull()}"
            Log.e(TAG, errorMsg)
            _lastError.value = errorMsg
            _state.value = State.ERROR
            return
        }

        if (!binary.canExecute()) {
            binary.setExecutable(true, false)
        }

        stopProcess()
        _lastError.value = null
        _state.value = State.STARTING

        val protoFlag = when (protocol.lowercase()) {
            "wg", "wireguard" -> "--wg"
            "gool" -> "--gool"
            else -> "--masque"
        }

        val args = mutableListOf(
            binary.absolutePath,
            protoFlag,
            "-4",
            "--scan", scanMode.lowercase(),
            "--bind", "$DEFAULT_HOST:$port"
        )

        Log.i(TAG, "Starting Aether: ${args.joinToString(" ")}")

        try {
            val pb = ProcessBuilder(args)
            pb.directory(context.filesDir)
            pb.redirectErrorStream(true)
            process = pb.start()

            acquireWakeLock(context)

            // Stream output to logs
            process?.inputStream?.bufferedReader()?.let { reader ->
                scope.launch {
                    try {
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            Log.d(TAG, "[Aether] $line")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error reading Aether output: ${e.message}")
                    }
                }
            }

            // Monitor process termination
            monitorJob = scope.launch {
                val exitCode = process?.waitFor() ?: -1
                Log.w(TAG, "Aether process terminated with code $exitCode")
                releaseWakeLock()
                if (_state.value != State.STOPPED) {
                    _lastError.value = "Process exited with code $exitCode"
                    _state.value = State.ERROR
                }
            }

            // Health check: wait until SOCKS5 port accepts connections
            healthJob = scope.launch {
                for (attempt in 1..60) {
                    delay(500)
                    if (!isRunning()) break
                    if (isPortReachable(DEFAULT_HOST, port)) {
                        Log.i(TAG, "Aether SOCKS5 is ready on $DEFAULT_HOST:$port")
                        applyProxyToApp(DEFAULT_HOST, port)
                        _state.value = State.CONNECTED
                        return@launch
                    }
                }
                if (_state.value == State.STARTING) {
                    Log.w(TAG, "Timed out waiting for Aether SOCKS5 port")
                    _lastError.value = "Connection timeout"
                    _state.value = State.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Aether", e)
            _lastError.value = e.localizedMessage ?: "Failed to start"
            _state.value = State.ERROR
            releaseWakeLock()
        }
    }

    /**
     * Stop the internal Aether SOCKS5 proxy and reset app proxy routing if needed.
     */
    @Synchronized
    fun stop() {
        stopProcess()
        _state.value = State.STOPPED
        _lastError.value = null
        // Revert proxy to direct
        YouTube.proxy = null
        ProxySelector.setDefault(ProxySelector.getDefault())
        Log.i(TAG, "Aether proxy stopped")
    }

    private fun stopProcess() {
        healthJob?.cancel()
        healthJob = null
        monitorJob?.cancel()
        monitorJob = null

        process?.let { proc ->
            try {
                proc.outputStream.close()
                proc.inputStream.close()
                proc.errorStream.close()
                proc.destroy()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {}
        }
        process = null
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

    private fun isPortReachable(host: String, port: Int): Boolean = try {
        Socket().use {
            it.connect(InetSocketAddress(host, port), 400)
            true
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Apply SOCKS5 proxy to YouTube client and application HTTP stack.
     */
    fun applyProxyToApp(host: String = DEFAULT_HOST, port: Int = DEFAULT_PORT) {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(host, port))
        YouTube.proxy = proxy
        ProxySelector.setDefault(AppProxySelector(proxy))
    }

    /**
     * Locate the Aether binary across supported locations:
     * 1. Native library directory (libaether.so extracted by Android package manager)
     * 2. App internal filesDir (libaether.so)
     * 3. Assets extraction fallback (if bundled in assets/aether/<abi>)
     */
    fun resolveBinary(context: Context): File? {
        // 1. Native library dir (standard Android NDK packaging)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeBin = File(nativeDir, "libaether.so")
        if (nativeBin.exists() && nativeBin.length() > 0) {
            return nativeBin
        }

        // 2. Internal app files directory
        val internalBin = File(context.filesDir, "libaether.so")
        if (internalBin.exists() && internalBin.length() > 0) {
            return internalBin
        }

        // 3. Fallback: extract from assets if present
        for (abi in Build.SUPPORTED_ABIS) {
            val assetPath = "aether/$abi/libaether.so"
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(internalBin).use { output ->
                        input.copyTo(output)
                    }
                    internalBin.setExecutable(true, false)
                    Log.i(TAG, "Extracted Aether binary from asset: $assetPath")
                    return internalBin
                }
            } catch (_: Exception) {
                // Asset not found for this ABI, continue
            }
        }

        return null
    }

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
