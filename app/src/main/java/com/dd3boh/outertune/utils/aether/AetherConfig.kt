/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils.aether

import androidx.datastore.preferences.core.Preferences
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
import com.dd3boh.outertune.constants.AetherUpstreamKey
import java.io.File

/**
 * Full configuration for the embedded Aether censorship-circumvention engine (v2.4.0+).
 * Supports all native Aether protocols (MASQUE, WireGuard, Gool, Gool Classic, MiM),
 * transports (HTTP/3 QUIC, HTTP/2 TLS), scan modes, Noize obfuscation, TLS fragmentation,
 * ECH, custom SNI, custom peers, DNS, upstream proxy, and Zero Trust.
 */
data class AetherConfig(
    val protocol: String = "masque",               // masque | wg | gool | gool-classic | mim
    val masqueTransport: String = "h3",            // h3 | h2
    val scanMode: String = "turbo",                // turbo | balanced | thorough | verified | ironclad
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
    val customPeer: String = "",                   // ip:port
    val outerPeer: String = "",                    // ip:port for gool-classic / mim
    val innerPeer: String = "",                    // ip:port for gool / gool-classic / mim
    val dns: String = "",                          // e.g. 1.1.1.1,1.0.0.1
    val upstream: String = "",                     // e.g. socks5://127.0.0.1:1080
    val team: String = "",                         // Zero Trust team name
    val extraArgs: String = "",                    // Power-user additional CLI args
    val port: Int = AetherProxyManager.DEFAULT_PORT
) {
    val usesMasque: Boolean
        get() = protocol == "masque" || protocol == "gool" || protocol == "mim"

    fun effectiveSocksPort(): Int = port

    fun effectiveHttpPort(): Int = port + 1

    /**
     * Build the exact CLI argument list for `aether` v2.4.0.
     */
    fun buildCliArgs(
        binaryPath: String,
        configDir: File
    ): List<String> {
        val args = mutableListOf<String>()
        args += binaryPath
        args += listOf("--config", File(configDir, "aether.toml").absolutePath)
        args += listOf("--bind", "${AetherProxyManager.DEFAULT_HOST}:$port")
        args += listOf("--http-proxy", "${AetherProxyManager.DEFAULT_HOST}:${effectiveHttpPort()}")

        val effectiveProtocol = if (protocol in VALID_PROTOCOLS) protocol else "masque"

        when (effectiveProtocol) {
            "wg" -> args += "--wg"
            "gool" -> args += "--gool"
            "gool-classic" -> args += "--gool-classic"
            "mim" -> args += "--mim"
            else -> args += "--masque"
        }

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
            if (masqueTransport == "h2") {
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
                    val flag = if (masqueTransport == "h2") "--h2-peer" else "--peer"
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
                    val flag = if (masqueTransport == "h2") "--h2-peer" else "--peer"
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
        val VALID_PROTOCOLS = setOf("masque", "wg", "gool", "gool-classic", "mim")

        fun fromPreferences(prefs: Preferences): AetherConfig {
            val savedProto = prefs[AetherProtocolKey]
            val protocol = if (savedProto in VALID_PROTOCOLS) savedProto!! else "masque"
            return AetherConfig(
                protocol = protocol,
                masqueTransport = prefs[AetherMasqueTransportKey] ?: "h3",
                scanMode = prefs[AetherScanModeKey] ?: "turbo",
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
