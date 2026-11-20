/*
 * Copyright (C) 2026 InterTune
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.dd3boh.outertune.utils.xray

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/**
 * Parses common Xray / V2Ray share links (`vless://`, `vmess://`, `trojan://`, `ss://`)
 * or full Xray JSON configs and produces a ready-to-run Xray JSON configuration
 * exposing local SOCKS5 (`socksPort`) and HTTP CONNECT (`httpPort`) inbounds on `127.0.0.1`.
 */
object XrayConfigParser {

    data class ParsedSummary(
        val protocol: String,
        val server: String,
        val remark: String
    )

    fun summarize(rawInput: String): ParsedSummary? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when {
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    val outbounds = root.optJSONArray("outbounds")
                    val first = outbounds?.optJSONObject(0)
                    val proto = first?.optString("protocol")?.ifBlank { "json" } ?: "json"
                    ParsedSummary(proto.uppercase(), "Custom JSON", "")
                }
                trimmed.startsWith("vless://", ignoreCase = true) -> {
                    val uri = URI(trimmed)
                    val remark = uri.rawFragment?.let(::urlDecode).orEmpty()
                    ParsedSummary("VLESS", "${uri.host}:${uri.port}", remark)
                }
                trimmed.startsWith("trojan://", ignoreCase = true) -> {
                    val uri = URI(trimmed)
                    val remark = uri.rawFragment?.let(::urlDecode).orEmpty()
                    ParsedSummary("TROJAN", "${uri.host}:${uri.port}", remark)
                }
                trimmed.startsWith("vmess://", ignoreCase = true) -> {
                    val json = JSONObject(decodeBase64(trimmed.substringAfter("://")))
                    ParsedSummary(
                        "VMESS",
                        "${json.optString("add")}:${json.optString("port")}",
                        json.optString("ps")
                    )
                }
                trimmed.startsWith("ss://", ignoreCase = true) -> {
                    val remark = trimmed.substringAfter("#", "").let(::urlDecode)
                    val withoutFrag = trimmed.substringAfter("://").substringBefore("#")
                    val hostPort = if ("@" in withoutFrag) {
                        withoutFrag.substringAfter("@").substringBefore("?")
                    } else {
                        decodeBase64(withoutFrag).substringAfter("@")
                    }
                    ParsedSummary("SHADOWSOCKS", hostPort, remark)
                }
                else -> null
            }
        }.getOrNull()
    }

    fun buildXrayJson(
        rawInput: String,
        socksPort: Int,
        httpPort: Int
    ): Result<String> = runCatching {
        val trimmed = rawInput.trim()
        require(trimmed.isNotEmpty()) { "Xray config is empty" }

        if (trimmed.startsWith("{")) {
            val root = JSONObject(trimmed)
            root.put("inbounds", buildLocalInbounds(socksPort, httpPort))
            if (!root.has("log")) {
                root.put("log", JSONObject().put("loglevel", "warning"))
            }
            return@runCatching root.toString()
        }

        val outbound = when {
            trimmed.startsWith("vless://", ignoreCase = true) -> parseVless(trimmed)
            trimmed.startsWith("vmess://", ignoreCase = true) -> parseVmess(trimmed)
            trimmed.startsWith("trojan://", ignoreCase = true) -> parseTrojan(trimmed)
            trimmed.startsWith("ss://", ignoreCase = true) -> parseShadowsocks(trimmed)
            else -> error("Unsupported config format. Paste vless://, vmess://, trojan://, ss://, or Xray JSON.")
        }

        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("inbounds", buildLocalInbounds(socksPort, httpPort))
        root.put(
            "outbounds",
            JSONArray()
                .put(outbound)
                .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
        )
        root.toString()
    }

    private fun buildLocalInbounds(socksPort: Int, httpPort: Int): JSONArray {
        val socks = JSONObject()
            .put("tag", "socks")
            .put("port", socksPort)
            .put("listen", "127.0.0.1")
            .put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))

        val http = JSONObject()
            .put("tag", "http")
            .put("port", httpPort)
            .put("listen", "127.0.0.1")
            .put("protocol", "http")
            .put("sniffing", JSONObject().put("enabled", true).put("destOverride", JSONArray().put("http").put("tls")))

        return JSONArray().put(socks).put(http)
    }

    private fun parseVless(link: String): JSONObject {
        val uri = URI(link)
        val uuid = urlDecode(uri.rawUserInfo ?: error("Missing VLESS UUID"))
        val host = uri.host ?: error("Missing VLESS host")
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery)

        val user = JSONObject()
            .put("id", uuid)
            .put("encryption", q["encryption"]?.ifBlank { "none" } ?: "none")
        q["flow"]?.takeIf { it.isNotBlank() }?.let { user.put("flow", it) }

        val vnext = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", buildStreamSettings(q, defaultHost = host))
    }

    private fun parseTrojan(link: String): JSONObject {
        val uri = URI(link)
        val password = urlDecode(uri.rawUserInfo ?: error("Missing Trojan password"))
        val host = uri.host ?: error("Missing Trojan host")
        val port = if (uri.port > 0) uri.port else 443
        val q = parseQuery(uri.rawQuery).toMutableMap()
        if (!q.containsKey("security")) q["security"] = "tls"

        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("password", password)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "trojan")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
            .put("streamSettings", buildStreamSettings(q, defaultHost = host))
    }

    private fun parseVmess(link: String): JSONObject {
        val payload = decodeBase64(link.substringAfter("://").trim())
        val j = JSONObject(payload)
        val host = j.optString("add").ifBlank { error("Missing VMess address") }
        val port = j.optString("port").toIntOrNull() ?: error("Missing VMess port")
        val id = j.optString("id").ifBlank { error("Missing VMess UUID") }
        val aid = j.optString("aid").toIntOrNull() ?: 0
        val scy = j.optString("scy").ifBlank { "auto" }

        val user = JSONObject()
            .put("id", id)
            .put("alterId", aid)
            .put("security", scy)

        val vnext = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("users", JSONArray().put(user))

        val q = mutableMapOf<String, String>()
        q["type"] = j.optString("net").ifBlank { "tcp" }
        q["headerType"] = j.optString("type")
        q["host"] = j.optString("host")
        q["path"] = j.optString("path")
        q["security"] = if (j.optString("tls").equals("tls", ignoreCase = true)) "tls" else "none"
        q["sni"] = j.optString("sni").ifBlank { j.optString("host") }
        q["alpn"] = j.optString("alpn")
        q["fp"] = j.optString("fp")

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vmess")
            .put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            .put("streamSettings", buildStreamSettings(q, defaultHost = host))
    }

    private fun parseShadowsocks(link: String): JSONObject {
        val withoutScheme = link.substringAfter("://").substringBefore("#")
        val beforeQuery = withoutScheme.substringBefore("?")
        val (method, password, host, port) = if ("@" in beforeQuery) {
            val userInfoRaw = beforeQuery.substringBefore("@")
            val hostPort = beforeQuery.substringAfter("@")
            val decoded = if (":" in userInfoRaw) urlDecode(userInfoRaw) else decodeBase64(userInfoRaw)
            val m = decoded.substringBefore(":")
            val p = decoded.substringAfter(":")
            val (h, pt) = splitHostPort(hostPort)
            Quad(m, p, h, pt)
        } else {
            val decoded = decodeBase64(beforeQuery)
            val creds = decoded.substringBefore("@")
            val hostPort = decoded.substringAfter("@")
            val m = creds.substringBefore(":")
            val p = creds.substringAfter(":")
            val (h, pt) = splitHostPort(hostPort)
            Quad(m, p, h, pt)
        }

        val server = JSONObject()
            .put("address", host)
            .put("port", port)
            .put("method", method)
            .put("password", password)

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "shadowsocks")
            .put("settings", JSONObject().put("servers", JSONArray().put(server)))
    }

    private fun buildStreamSettings(q: Map<String, String>, defaultHost: String): JSONObject {
        val rawNetwork = q["type"]?.lowercase()?.ifBlank { "tcp" } ?: "tcp"
        val network = when (rawNetwork) {
            "splithttp" -> "xhttp"
            else -> rawNetwork
        }
        val security = q["security"]?.lowercase()?.ifBlank { "none" } ?: "none"

        val ss = JSONObject()
            .put("network", network)
            .put("security", security)

        val hostHeader = q["host"]?.takeIf { it.isNotBlank() }
        val path = q["path"]?.takeIf { it.isNotBlank() } ?: "/"

        when (network) {
            "ws" -> {
                val ws = JSONObject().put("path", path)
                if (hostHeader != null) {
                    ws.put("headers", JSONObject().put("Host", hostHeader))
                }
                ss.put("wsSettings", ws)
            }
            "grpc" -> {
                val grpc = JSONObject()
                    .put("serviceName", q["serviceName"] ?: q["path"] ?: "")
                    .put("multiMode", q["mode"] == "multi")
                if (q["authority"]?.isNotBlank() == true) {
                    grpc.put("authority", q["authority"])
                }
                ss.put("grpcSettings", grpc)
            }
            "httpupgrade" -> {
                val hu = JSONObject().put("path", path)
                if (hostHeader != null) hu.put("host", hostHeader)
                ss.put("httpupgradeSettings", hu)
            }
            "xhttp" -> {
                val xh = JSONObject().put("path", path)
                if (hostHeader != null) xh.put("host", hostHeader)
                q["mode"]?.takeIf { it.isNotBlank() }?.let { xh.put("mode", it) }
                ss.put("xhttpSettings", xh)
            }
            "h2", "http" -> {
                val h2 = JSONObject().put("path", path)
                if (hostHeader != null) {
                    h2.put("host", JSONArray().put(hostHeader))
                }
                ss.put("httpSettings", h2)
            }
            "tcp" -> {
                if (q["headerType"] == "http") {
                    val req = JSONObject()
                        .put("path", JSONArray().put(path))
                    if (hostHeader != null) {
                        req.put("headers", JSONObject().put("Host", JSONArray().put(hostHeader)))
                    }
                    ss.put(
                        "tcpSettings",
                        JSONObject().put("header", JSONObject().put("type", "http").put("request", req))
                    )
                }
            }
        }

        val sni = q["sni"]?.takeIf { it.isNotBlank() } ?: hostHeader ?: defaultHost
        val fp = q["fp"]?.takeIf { it.isNotBlank() } ?: "chrome"

        when (security) {
            "tls" -> {
                val tls = JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", fp)
                    .put("allowInsecure", q["allowInsecure"] == "1" || q["allowInsecure"] == "true")
                q["alpn"]?.takeIf { it.isNotBlank() }?.let { alpnStr ->
                    val arr = JSONArray()
                    alpnStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it) }
                    if (arr.length() > 0) tls.put("alpn", arr)
                }
                ss.put("tlsSettings", tls)
            }
            "reality" -> {
                val reality = JSONObject()
                    .put("serverName", sni)
                    .put("fingerprint", fp)
                    .put("publicKey", q["pbk"] ?: "")
                    .put("shortId", q["sid"] ?: "")
                    .put("spiderX", q["spx"] ?: "/")
                ss.put("realitySettings", reality)
            }
        }

        return ss
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        return rawQuery.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else urlDecode(pair.substring(0, idx)) to urlDecode(pair.substring(idx + 1))
        }.toMap()
    }

    private fun urlDecode(s: String): String =
        runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun decodeBase64(s: String): String {
        val clean = s.trim().replace('-', '+').replace('_', '/')
        val padded = when (clean.length % 4) {
            2 -> "$clean=="
            3 -> "$clean="
            else -> clean
        }
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }

    private fun splitHostPort(hostPort: String): Pair<String, Int> {
        return if (hostPort.startsWith("[")) {
            val end = hostPort.indexOf(']')
            val h = hostPort.substring(1, end)
            val p = hostPort.substring(end + 2).toInt()
            h to p
        } else {
            val idx = hostPort.lastIndexOf(':')
            hostPort.substring(0, idx) to hostPort.substring(idx + 1).toInt()
        }
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: Int)
}
