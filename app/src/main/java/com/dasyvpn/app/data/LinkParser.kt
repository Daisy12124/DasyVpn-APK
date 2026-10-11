package com.dasyvpn.app.data

import android.util.Base64
import com.dasyvpn.app.model.ServerProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLDecoder
import java.util.UUID

/**
 * پارسر لینک‌های پرکاربرد: vless / vmess / trojan / shadowsocks
 * و لیست اشتراک (subscription) که می‌تواند base64 یا متن ساده باشد.
 */
object LinkParser {

    private val SUPPORTED = setOf("vless://", "vmess://", "trojan://", "ss://")

    fun isSupportedLink(link: String): Boolean {
        val l = link.trim().lowercase()
        return SUPPORTED.any { l.startsWith(it) }
    }

    /** اشتراک را به لیست پروفایل تبدیل می‌کند */
    fun parseSubscription(content: String): List<ServerProfile> {
        val decoded = tryDecodeBase64(content) ?: content
        return decoded
            .lineSequence()
            .map { it.trim() }
            .filter { isSupportedLink(it) }
            .mapNotNull { parseLink(it) }
            .toList()
    }

    fun parseLink(link: String): ServerProfile? {
        val raw = link.trim()
        return try {
            when {
                raw.startsWith("vless://", true) -> parseVless(raw)
                raw.startsWith("vmess://", true) -> parseVmess(raw)
                raw.startsWith("trojan://", true) -> parseTrojan(raw)
                raw.startsWith("ss://", true) -> parseShadowsocks(raw)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun fragmentName(raw: String, fallback: String): String {
        val idx = raw.indexOf('#')
        if (idx < 0) return fallback
        return try {
            URLDecoder.decode(raw.substring(idx + 1), "UTF-8")
                .ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun parseVless(raw: String): ServerProfile? {
        // vless://uuid@host:port?params#name
        val body = raw.substring("vless://".length)
        val withoutFragment = body.substringBefore('#')
        val authHostPort = withoutFragment.substringBefore('?')
        val user = authHostPort.substringBefore('@')
        val hostPort = authHostPort.substringAfter('@')
        if (user.isEmpty() || hostPort.isEmpty()) return null

        val host = hostPort.substringBeforeLast(':')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
        val name = fragmentName(raw, "$host:$port")
        return ServerProfile(
            id = newId(), name = name, raw = raw, protocol = "vless",
            host = host, port = port
        )
    }

    private fun parseVmess(raw: String): ServerProfile? {
        val b64 = raw.substring("vmess://".length)
        val json = tryDecodeBase64(b64) ?: return null
        val obj = Json.parseToJsonElement(json).jsonObject
        fun s(k: String) = obj[k]?.jsonPrimitive?.contentOrNull
        val host = s("add") ?: return null
        val port = s("port")?.trim()?.toIntOrNull() ?: return null
        val name = s("ps")?.ifBlank { null } ?: "$host:$port"
        return ServerProfile(
            id = newId(), name = name, raw = raw, protocol = "vmess",
            host = host, port = port
        )
    }

    private fun parseTrojan(raw: String): ServerProfile? {
        val body = raw.substring("trojan://".length)
        val withoutFragment = body.substringBefore('#')
        val hostPort = withoutFragment.substringAfter('@').substringBefore('?')
        if (hostPort.isEmpty()) return null
        val host = hostPort.substringBeforeLast(':')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
        val name = fragmentName(raw, "$host:$port")
        return ServerProfile(
            id = newId(), name = name, raw = raw, protocol = "trojan",
            host = host, port = port
        )
    }

    private fun parseShadowsocks(raw: String): ServerProfile? {
        // ss://base64(method:password)@host:port#name
        // یا ss://base64(method:password@host:port)#name
        val body = raw.substring("ss://".length)
        val main = body.substringBefore('#')
        val name = fragmentName(raw, "Shadowsocks")

        val userInfo: String
        val hostPort: String
        if (main.contains('@')) {
            userInfo = main.substringBefore('@')
            hostPort = main.substringAfter('@')
        } else {
            val decoded = tryDecodeBase64(main) ?: return null
            if (!decoded.contains('@')) return null
            userInfo = decoded.substringBefore('@')
            hostPort = decoded.substringAfter('@')
        }
        if (hostPort.isEmpty() || userInfo.isEmpty()) return null

        val host = hostPort.substringBeforeLast(':').removePrefix("[")
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: return null
        return ServerProfile(
            id = newId(), name = name, raw = raw, protocol = "ss",
            host = host, port = port
        )
    }

    private fun tryDecodeBase64(input: String): String? {
        return try {
            var s = input.trim().replace("\n", "").replace("\r", "")
            s = s.replace('-', '+').replace('_', '/')
            val pad = (4 - s.length % 4) % 4
            s += "=".repeat(pad)
            val bytes = Base64.decode(s, Base64.DEFAULT)
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}
