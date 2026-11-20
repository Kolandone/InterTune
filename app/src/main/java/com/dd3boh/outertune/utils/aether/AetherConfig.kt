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
import com.dd3boh.outertune.constants.AetherPsiphonCdnSetsKey
import com.dd3boh.outertune.constants.AetherPsiphonModeKey
import com.dd3boh.outertune.constants.AetherPsiphonRegionKey
import com.dd3boh.outertune.constants.AetherQuicV2Key
import com.dd3boh.outertune.constants.AetherQuickReconnectKey
import com.dd3boh.outertune.constants.AetherScanModeKey
import com.dd3boh.outertune.constants.AetherTeamKey
import com.dd3boh.outertune.constants.AetherTorBridgesModeKey
import com.dd3boh.outertune.constants.AetherTorCustomBridgeKey
import com.dd3boh.outertune.constants.AetherUpstreamKey
import java.io.File

/**
 * Full configuration for the embedded Aether censorship-circumvention engine (v2.4.0+).
 * Mirrors every supported protocol, carrier, scan mode, obfuscation, TLS/ECH, Tor, Psiphon,
 * and custom peer option from CluvexStudio/Aether.
 */
data class AetherConfig(
    val protocol: String = "masque",               // masque | wg | gool | gool-classic | mim | tor-only | psiphon-only
    val masqueTransport: String = "h3",            // h3 | h2
    val chainMode: String = "none",                // none | tor | tor-reverse | psiphon | psiphon-reverse
    val scanMode: String = "balanced",             // turbo | balanced | thorough | verified | ironclad
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
    val psiphonMode: String = "auto",              // auto | cdn | direct
    val psiphonRegion: String = "",                // e.g. DE, NL, US
    val psiphonCdnSets: String = "",               // e.g. cloudflare,fastly
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
    val isStandaloneCarrier: Boolean
        get() = protocol == "tor-only" || protocol == "psiphon-only"

    val usesMasque: Boolean
        get() = protocol == "masque" || protocol == "gool" || protocol == "mim"

    val usesTor: Boolean
        get() = protocol == "tor-only" || (!isStandaloneCarrier && (chainMode == "tor" || chainMode == "tor-reverse"))

    val usesPsiphon: Boolean
        get() = protocol == "psiphon-only" || (!isStandaloneCarrier && (chainMode == "psiphon" || chainMode == "psiphon-reverse"))

    /**
     * The local SOCKS5 port that InterTune must route traffic through.
     * In chain modes (`--tor` / `--psiphon`), Aether serves plain WARP on `--bind` and
     * the chained exit on `--tor-bind` (`port + 1`) or `--psiphon-bind` (`port + 2`).
     */
    fun effectiveSocksPort(): Int = when {
        isStandaloneCarrier -> port
        chainMode == "tor" -> port + 1
        chainMode == "psiphon" -> port + 2
        else -> port
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

        // Determine effective protocol (Tor/Psiphon reverse modes require TCP-capable MASQUE/Gool/MiM)
        val isReverseCarrier = !isStandaloneCarrier && (chainMode == "tor-reverse" || chainMode == "psiphon-reverse")
        val effectiveProtocol = if (isReverseCarrier && (protocol == "wg" || protocol == "gool-classic")) {
            "masque"
        } else {
            protocol
        }

        // Protocol flag
        when (effectiveProtocol) {
            "wg" -> args += "--wg"
            "gool" -> args += "--gool"
            "gool-classic" -> args += "--gool-classic"
            "mim" -> args += "--mim"
            "tor-only" -> args += "--tor-only"
            "psiphon-only" -> args += "--psiphon-only"
            else -> args += "--masque"
        }

        // WARP/MASQUE tunnel options (not applicable to tor-only / psiphon-only)
        if (!isStandaloneCarrier) {
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
                if (isReverseCarrier || masqueTransport == "h2") {
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
                        val flag = if (isReverseCarrier || masqueTransport == "h2") "--h2-peer" else "--peer"
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
                        val flag = if (isReverseCarrier || masqueTransport == "h2") "--h2-peer" else "--peer"
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

            // Carrier chain mode
            when (chainMode) {
                "tor" -> {
                    args += "--tor"
                    args += listOf("--tor-bind", "${AetherProxyManager.DEFAULT_HOST}:${port + 1}")
                }
                "tor-reverse" -> {
                    args += "--tor-reverse"
                }
                "psiphon" -> {
                    args += "--psiphon"
                    args += listOf("--psiphon-bind", "${AetherProxyManager.DEFAULT_HOST}:${port + 2}")
                }
                "psiphon-reverse" -> {
                    args += "--psiphon-reverse"
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

        // Psiphon specific flags + bundled psiphon-tunnel-core binary
        if (usesPsiphon) {
            val psiphonDir = File(configDir, "psiphon").apply { mkdirs() }
            args += listOf("--psiphon-dir", psiphonDir.absolutePath)

            val psiphonBin = File(nativeDir, "libpsiphon.so")
            if (psiphonBin.exists() && psiphonBin.length() > 0) {
                args += listOf("--psiphon-bin", psiphonBin.absolutePath)
            }

            if (psiphonMode in setOf("auto", "cdn", "direct")) {
                args += listOf("--psiphon-mode", psiphonMode)
            }
            if (psiphonRegion.isNotBlank()) {
                args += listOf("--psiphon-region", psiphonRegion.trim().uppercase())
            }
            if (psiphonCdnSets.isNotBlank()) {
                args += listOf("--psiphon-cdn-sets", psiphonCdnSets.trim())
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

        // Always enable periodic stats logging for visibility
        args += "--stats"

        // Extra user-specified CLI flags
        if (extraArgs.isNotBlank()) {
            args += splitArgs(extraArgs)
        }

        return args
    }

    companion object {
        fun fromPreferences(prefs: Preferences): AetherConfig = AetherConfig(
            protocol = prefs[AetherProtocolKey] ?: "masque",
            masqueTransport = prefs[AetherMasqueTransportKey] ?: "h3",
            chainMode = prefs[AetherChainModeKey] ?: "none",
            scanMode = prefs[AetherScanModeKey] ?: "balanced",
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
            psiphonMode = prefs[AetherPsiphonModeKey] ?: "auto",
            psiphonRegion = prefs[AetherPsiphonRegionKey] ?: "",
            psiphonCdnSets = prefs[AetherPsiphonCdnSetsKey] ?: "",
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
