/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils.aether

import androidx.datastore.preferences.core.Preferences
import com.dd3boh.outertune.constants.AetherChainModeKey
import com.dd3boh.outertune.constants.AetherCustomPeerKey
import com.dd3boh.outertune.constants.AetherDnsKey
import com.dd3boh.outertune.constants.AetherEchDnsKey
import com.dd3boh.outertune.constants.AetherEchKey
import com.dd3boh.outertune.constants.AetherExitLocKey
import com.dd3boh.outertune.constants.AetherExtraArgsKey
import com.dd3boh.outertune.constants.AetherFragmentDelayKey
import com.dd3boh.outertune.constants.AetherFragmentKey
import com.dd3boh.outertune.constants.AetherFragmentSizeKey
import com.dd3boh.outertune.constants.AetherInnerPeerKey
import com.dd3boh.outertune.constants.AetherIpVersionKey
import com.dd3boh.outertune.constants.AetherMasqueSniKey
import com.dd3boh.outertune.constants.AetherMasqueTransportKey
import com.dd3boh.outertune.constants.AetherNoizeKey
import com.dd3boh.outertune.constants.AetherOuterPeerKey
import com.dd3boh.outertune.constants.AetherPortKey
import com.dd3boh.outertune.constants.AetherProtocolKey
import com.dd3boh.outertune.constants.AetherQuicV2Key
import com.dd3boh.outertune.constants.AetherQuickReconnectKey
import com.dd3boh.outertune.constants.AetherScanModeKey
import com.dd3boh.outertune.constants.AetherTeamKey
import com.dd3boh.outertune.constants.AetherTorBridgesModeKey
import com.dd3boh.outertune.constants.AetherTorCustomBridgeKey
import com.dd3boh.outertune.constants.AetherUpstreamKey
import java.io.File

/**
 * Configuration for the embedded Aether censorship-circumvention engine (v2.4.0+) with Tor support.
 * Default profile: MASQUE over HTTP/2 (`--masque --h2`) + Tor inside the tunnel (`--tor`)
 * + Ironclad scanner (`--scan ironclad`), giving reliable non-IR exits for YouTube Music.
 */
data class AetherConfig(
    val protocol: String = "masque",               // masque | wg | gool | gool-classic | mim | tor-only
    val masqueTransport: String = "h2",            // h2 | h3
    val chainMode: String = "tor",                 // none | tor | tor-reverse
    val scanMode: String = "ironclad",             // turbo | balanced | thorough | verified | ironclad
    val ipVersion: String = "v4",                  // v4 | v6 | both
    val noize: String = "default",                 // default | off | light | firewall | balanced | gfw | aggressive
    val quickReconnect: Boolean = true,
    val fragment: Boolean = false,
    val fragmentSize: String = "",                 // e.g. 8-16
    val fragmentDelay: String = "",                // e.g. 2-10
    val quicV2: Boolean = true,
    val masqueSni: String = "",                    // e.g. www.cloudflare.com
    val ech: String = "off",                       // off | auto | <base64>
    val echDns: String = "",                       // e.g. https://1.1.1.1/dns-query
    val exitLoc: String = "",                      // e.g. !IR,RU or DE,NL
    val torBridgesMode: String = "auto",           // auto | force | off
    val torCustomBridge: String = "",
    val customPeer: String = "",                   // ip:port
    val outerPeer: String = "",                    // ip:port for gool-classic / mim
    val innerPeer: String = "",                    // ip:port for gool / gool-classic / mim
    val dns: String = "",                          // e.g. 1.1.1.1,1.0.0.1
    val upstream: String = "",                     // e.g. socks5://127.0.0.1:1080
    val team: String = "",                         // Zero Trust team name
    val extraArgs: String = "",                    // Power-user additional CLI args
    val port: Int = AetherProxyManager.DEFAULT_PORT
) {
    val isStandaloneTor: Boolean
        get() = protocol == "tor-only"

    val usesMasque: Boolean
        get() = protocol == "masque" || protocol == "gool" || protocol == "mim"

    val usesTor: Boolean
        get() = isStandaloneTor || chainMode == "tor" || chainMode == "tor-reverse"

    /**
     * Local SOCKS5 port that InterTune routes traffic through.
     * In `--tor` chain mode, Aether serves WARP on `--bind` and Tor on `--tor-bind` (`port + 1`).
     */
    fun effectiveSocksPort(): Int = when {
        isStandaloneTor -> port
        chainMode == "tor" -> port + 1
        else -> port
    }

    /**
     * Local HTTP CONNECT port used by Android WebView (LoginScreen) so sign-in also goes through Aether/Tor.
     */
    fun effectiveHttpPort(): Int = when {
        isStandaloneTor -> port + 1
        chainMode == "tor" -> port + 2
        else -> port + 1
    }

    /**
     * Build the exact CLI argument list for `aether` v2.4.0.
     */
    fun buildCliArgs(
        binaryPath: String,
        nativeDir: File,
        configDir: File
    ): List<String> {
        val args = mutableListOf<String>()
        args += binaryPath
        args += listOf("--config", File(configDir, "aether.toml").absolutePath)
        args += listOf("--bind", "${AetherProxyManager.DEFAULT_HOST}:$port")

        val sanitizedProto = if (protocol in VALID_PROTOCOLS) protocol else "masque"
        val sanitizedChain = if (chainMode in VALID_CHAINS) chainMode else "tor"

        // Reverse Tor requires TCP-capable MASQUE/Gool/MiM over HTTP/2
        val isReverseTor = !isStandaloneTor && sanitizedChain == "tor-reverse"
        val effectiveProtocol = if (isReverseTor && (sanitizedProto == "wg" || sanitizedProto == "gool-classic")) {
            "masque"
        } else {
            sanitizedProto
        }

        // HTTP proxy for WebView: in --tor mode, use --tor-http; otherwise --http-proxy
        if (isStandaloneTor || sanitizedChain != "tor") {
            args += listOf("--http-proxy", "${AetherProxyManager.DEFAULT_HOST}:${effectiveHttpPort()}")
        }

        when (effectiveProtocol) {
            "wg" -> args += "--wg"
            "gool" -> args += "--gool"
            "gool-classic" -> args += "--gool-classic"
            "mim" -> args += "--mim"
            "tor-only" -> args += "--tor-only"
            else -> args += "--masque"
        }

        if (!isStandaloneTor) {
            args += listOf("--scan", scanMode.lowercase())

            when (ipVersion.lowercase()) {
                "v6" -> args += "-6"
                "both", "dual" -> args += "--dual"
                else -> args += "-4"
            }

            args += if (quickReconnect) "--quick-reconnect" else "--no-quick-reconnect"

            if (noize.isNotBlank() && noize != "default") {
                args += listOf("--noize", noize.lowercase())
            }

            // MASQUE transport options
            val effectiveUsesMasque = effectiveProtocol == "masque" || effectiveProtocol == "gool" || effectiveProtocol == "mim"
            if (effectiveUsesMasque) {
                if (isReverseTor || masqueTransport == "h2") {
                    args += "--h2"
                } else {
                    args += "--h3"
                }
                if (!quicV2) {
                    args += "--no-quic-v2"
                }
                if (masqueSni.isNotBlank()) {
                    args += listOf("--masque-sni", masqueSni.trim())
                }
            }

            // TLS ClientHello fragmentation
            if (fragment) {
                args += "--fragment"
                if (fragmentSize.isNotBlank()) {
                    args += listOf("--fragment-size", fragmentSize.trim())
                }
                if (fragmentDelay.isNotBlank()) {
                    args += listOf("--fragment-delay", fragmentDelay.trim())
                }
            }

            // Encrypted Client Hello (ECH)
            if (ech.isNotBlank() && ech != "off") {
                args += listOf("--ech", ech.trim())
                if (echDns.isNotBlank()) {
                    args += listOf("--ech-dns", echDns.trim())
                }
            }

            // Custom peer endpoints
            when (effectiveProtocol) {
                "masque" -> {
                    if (customPeer.isNotBlank()) {
                        val flag = if (isReverseTor || masqueTransport == "h2") "--h2-peer" else "--peer"
                        args += listOf(flag, customPeer.trim())
                    }
                }
                "wg" -> {
                    if (customPeer.isNotBlank()) {
                        args += listOf("--peer", customPeer.trim())
                    }
                }
                "gool" -> {
                    if (customPeer.isNotBlank()) {
                        val flag = if (isReverseTor || masqueTransport == "h2") "--h2-peer" else "--peer"
                        args += listOf(flag, customPeer.trim())
                    }
                    if (innerPeer.isNotBlank()) {
                        args += listOf("--gool-peer", innerPeer.trim())
                    }
                }
                "gool-classic" -> {
                    if (outerPeer.isNotBlank()) {
                        args += listOf("--wiw-outer", outerPeer.trim())
                    }
                    if (innerPeer.isNotBlank()) {
                        args += listOf("--wiw-inner", innerPeer.trim())
                    }
                }
                "mim" -> {
                    if (outerPeer.isNotBlank()) {
                        args += listOf("--mim-outer", outerPeer.trim())
                    }
                    if (innerPeer.isNotBlank()) {
                        args += listOf("--mim-inner", innerPeer.trim())
                    }
                }
            }

            // Tor chain mode
            when (sanitizedChain) {
                "tor" -> {
                    args += "--tor"
                    args += listOf("--tor-bind", "${AetherProxyManager.DEFAULT_HOST}:${effectiveSocksPort()}")
                    args += listOf("--tor-http", "${AetherProxyManager.DEFAULT_HOST}:${effectiveHttpPort()}")
                }
                "tor-reverse" -> {
                    args += "--tor-reverse"
                }
            }
        }

        // Tor specific flags + bundled lyrebird pluggable transport
        if (usesTor) {
            val torDir = File(configDir, "tor").apply { mkdirs() }
            args += listOf("--tor-dir", torDir.absolutePath)

            val lyrebird = File(nativeDir, "liblyrebird.so")
            if (lyrebird.exists() && lyrebird.length() > 0) {
                args += listOf("--tor-pt", "obfs4=${lyrebird.absolutePath}")
                args += listOf("--tor-pt", "webtunnel=${lyrebird.absolutePath}")
            }

            when (torBridgesMode) {
                "force" -> args += "--tor-bridges"
                "off" -> args += "--no-tor-bridges"
            }

            if (torCustomBridge.isNotBlank()) {
                torCustomBridge.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { bridgeLine ->
                        args += listOf("--tor-bridge", bridgeLine)
                    }
            }
        }

        // Global options
        if (exitLoc.isNotBlank()) {
            args += listOf("--exit-loc", exitLoc.trim())
        }
        if (dns.isNotBlank()) {
            args += listOf("--dns", dns.trim())
        }
        if (upstream.isNotBlank()) {
            args += listOf("--upstream", upstream.trim())
        }
        if (team.isNotBlank()) {
            args += listOf("--team", team.trim())
        }

        // Periodic stats logging
        args += "--stats"

        // Extra user-specified CLI flags
        if (extraArgs.isNotBlank()) {
            args += splitArgs(extraArgs)
        }

        return args
    }

    companion object {
        val VALID_PROTOCOLS = setOf("masque", "wg", "gool", "gool-classic", "mim", "tor-only")
        val VALID_CHAINS = setOf("none", "tor", "tor-reverse")

        fun fromPreferences(prefs: Preferences): AetherConfig {
            val savedProto = prefs[AetherProtocolKey]
            val protocol = if (savedProto in VALID_PROTOCOLS) savedProto!! else "masque"
            val savedChain = prefs[AetherChainModeKey]
            val chainMode = if (savedChain in VALID_CHAINS) savedChain!! else "tor"
            return AetherConfig(
                protocol = protocol,
                masqueTransport = prefs[AetherMasqueTransportKey] ?: "h2",
                chainMode = chainMode,
                scanMode = prefs[AetherScanModeKey] ?: "ironclad",
                ipVersion = prefs[AetherIpVersionKey] ?: "v4",
                noize = prefs[AetherNoizeKey] ?: "default",
                quickReconnect = prefs[AetherQuickReconnectKey] ?: true,
                fragment = prefs[AetherFragmentKey] ?: false,
                fragmentSize = prefs[AetherFragmentSizeKey] ?: "",
                fragmentDelay = prefs[AetherFragmentDelayKey] ?: "",
                quicV2 = prefs[AetherQuicV2Key] ?: true,
                masqueSni = prefs[AetherMasqueSniKey] ?: "",
                ech = prefs[AetherEchKey] ?: "off",
                echDns = prefs[AetherEchDnsKey] ?: "",
                exitLoc = prefs[AetherExitLocKey] ?: "",
                torBridgesMode = prefs[AetherTorBridgesModeKey] ?: "auto",
                torCustomBridge = prefs[AetherTorCustomBridgeKey] ?: "",
                customPeer = prefs[AetherCustomPeerKey] ?: "",
                outerPeer = prefs[AetherOuterPeerKey] ?: "",
                innerPeer = prefs[AetherInnerPeerKey] ?: "",
                dns = prefs[AetherDnsKey] ?: "",
                upstream = prefs[AetherUpstreamKey] ?: "",
                team = prefs[AetherTeamKey] ?: "",
                extraArgs = prefs[AetherExtraArgsKey] ?: "",
                port = prefs[AetherPortKey] ?: AetherProxyManager.DEFAULT_PORT
            )
        }

        private fun splitArgs(input: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var inQuotes = false
            var quoteChar = '"'
            for (c in input) {
                when {
                    inQuotes -> {
                        if (c == quoteChar) {
                            inQuotes = false
                        } else {
                            current.append(c)
                        }
                    }
                    c == '"' || c == '\'' -> {
                        inQuotes = true
                        quoteChar = c
                    }
                    c.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            result += current.toString()
                            current.clear()
                        }
                    }
                    else -> current.append(c)
                }
            }
            if (current.isNotEmpty()) {
                result += current.toString()
            }
            return result
        }
    }
}
