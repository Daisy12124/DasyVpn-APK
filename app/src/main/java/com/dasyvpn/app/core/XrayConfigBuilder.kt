package com.dasyvpn.app.core

import android.net.Uri
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * ساخت JSON کانفیگ Xray از لینک خام سرور.
 * خروجی با الگوی رسمی v2rayNG (با TUN داخلی) سازگار است.
 */
object XrayConfigBuilder {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private const val TAG_PROXY = "proxy"
    private const val TAG_DIRECT = "direct"
    private const val TAG_BLOCK = "block"

    fun build(rawLink: String, bypassIran: Boolean): String {
        val link = rawLink.trim()
        val outbound = when {
            link.startsWith("vless://", true) -> vlessOutbound(link)
            link.startsWith("vmess://", true) -> vmessOutbound(link)
            link.startsWith("trojan://", true) -> trojanOutbound(link)
            link.startsWith("ss://", true) -> ssOutbound(link)
            else -> throw IllegalArgumentException("protocol not supported")
        }

        val rules = buildJsonArray {
            // ترافیک داخلی شبکه مستقیم
            addJsonObjectRule(
                listOf("geoip:private"),
                TAG_DIRECT
            )
            if (bypassIran) {
                addJsonObjectRule(
                    listOf("geosite:category-ir", "geoip:ir"),
                    TAG_DIRECT
                )
            }
        }

        val config = buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonObject("stats") {}
            putJsonObject("policy") {
                putJsonObject("system") {
                    put("statsOutboundUplink", true)
                    put("statsOutboundDownlink", true)
                }
            }
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("tag", "socks")
                    put("port", 10808)
                    put("listen", "127.0.0.1")
                    put("protocol", "socks")
                    putJsonObject("settings") {
                        put("auth", "noauth")
                        put("udp", true)
                    }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        put("destOverride", buildJsonArray {
                            add(JsonPrimitive("http")); add(JsonPrimitive("tls")); add(JsonPrimitive("quic"))
                        })
                    }
                })
                add(buildJsonObject {
                    put("tag", "tun")
                    put("protocol", "tun")
                    putJsonObject("settings") {
                        put("name", "dasy0")
                        put("MTU", 1500)
                    }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        put("destOverride", buildJsonArray {
                            add(JsonPrimitive("http")); add(JsonPrimitive("tls")); add(JsonPrimitive("quic"))
                        })
                    }
                })
            })
            put("outbounds", buildJsonArray {
                add(outbound)
                add(buildJsonObject {
                    put("tag", TAG_DIRECT)
                    put("protocol", "freedom")
                    putJsonObject("streamSettings") {
                        putJsonObject("sockopt") {
                            put("domainStrategy", "UseIP")
                        }
                    }
                })
                add(buildJsonObject {
                    put("tag", TAG_BLOCK)
                    put("protocol", "blackhole")
                })
            })
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                put("rules", rules)
            }
            putJsonObject("dns") {
                put("servers", buildJsonArray {
                    // DoH از داخل تونل → دور زدن DNS poisoning
                    add(buildJsonObject {
                        put("address", "https://dns.google/dns-query")
                        put("domains", buildJsonArray { add(JsonPrimitive("geosite:geolocation-!ir")) })
                    })
                    add(JsonPrimitive("1.1.1.1"))
                    add(JsonPrimitive("8.8.8.8"))
                })
            }
        }
        return json.encodeToString(JsonObject.serializer(), config)
    }

    private fun kotlinx.serialization.json.JsonArrayBuilder.addJsonObjectRule(
        ip: List<String>,
        outbound: String
    ) {
        add(buildJsonObject {
            put("type", "field")
            put("ip", buildJsonArray { ip.forEach { add(JsonPrimitive(it)) } })
            put("outboundTag", outbound)
        })
    }

    // ---------- هر پروتکل ----------

    private fun vlessOutbound(link: String): JsonObject {
        val uri = Uri.parse(link)
        val uuid = uri.userInfo ?: throw IllegalArgumentException("missing uuid")
        val host = uri.host ?: throw IllegalArgumentException("missing host")
        val port = uri.port.takeIf { it > 0 } ?: 443
        val q = uri.queryParams

        val security = q("security") ?: "none"
        val network = q("type") ?: "tcp"
        val sni = q("sni") ?: q("host")
        val flow = q("flow") ?: ""

        val user = buildJsonObject {
            put("id", uuid)
            put("encryption", "none")
            if (flow.isNotEmpty()) put("flow", flow)
        }

        return buildJsonObject {
            put("tag", TAG_PROXY)
            put("protocol", "vless")
            putJsonObject("settings") {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", host)
                        put("port", port)
                        put("users", buildJsonArray { add(user) })
                    })
                })
            }
            put("streamSettings", streamSettings(security, network, sni, q))
        }
    }

    private fun vmessOutbound(link: String): JsonObject {
        val b64 = link.substring("vmess://".length)
        val obj = json.parseToJsonElement(b64Decode(b64)).jsonObject
        fun s(k: String) = obj[k]?.jsonPrimitive?.contentOrNull.orEmpty()

        val host = s("add")
        val port = s("port").toIntOrNull() ?: 443
        val uuid = s("id")
        val aid = s("aid").ifEmpty { "0" }
        val security = s("scy").ifEmpty { "auto" }
        val network = s("net").ifEmpty { "tcp" }
        val tls = s("tls")
        val sni = s("sni").ifEmpty { s("host") }

        return buildJsonObject {
            put("tag", TAG_PROXY)
            put("protocol", "vmess")
            putJsonObject("settings") {
                put("vnext", buildJsonArray {
                    add(buildJsonObject {
                        put("address", host)
                        put("port", port)
                        put("users", buildJsonArray {
                            add(buildJsonObject {
                                put("id", uuid)
                                put("alterId", aid.toIntOrNull() ?: 0)
                                put("security", security)
                            })
                        })
                    })
                })
            }
            put("streamSettings", streamSettings(
                security = if (tls.isNotEmpty()) "tls" else "none",
                network = network,
                sni = sni,
                query = { key -> when (key) {
                    "host" -> s("host")
                    "path" -> s("path")
                    "serviceName" -> s("path")
                    "headerType" -> s("type")
                    "alpn" -> s("alpn")
                    "fp" -> s("fp")
                    else -> null
                } }
            ))
        }
    }

    private fun trojanOutbound(link: String): JsonObject {
        val uri = Uri.parse(link)
        val password = uri.userInfo ?: throw IllegalArgumentException("missing password")
        val host = uri.host ?: throw IllegalArgumentException("missing host")
        val port = uri.port.takeIf { it > 0 } ?: 443
        val q = uri.queryParams

        val network = q("type") ?: "tcp"
        val sni = q("sni") ?: q("peer")

        return buildJsonObject {
            put("tag", TAG_PROXY)
            put("protocol", "trojan")
            putJsonObject("settings") {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("address", host)
                        put("port", port)
                        put("password", password)
                    })
                })
            }
            put("streamSettings", streamSettings("tls", network, sni, q))
        }
    }

    private fun ssOutbound(link: String): JsonObject {
        val body = link.substring("ss://".length).substringBefore('#')
        val method: String
        val password: String
        val host: String
        val port: Int

        if (body.contains('@')) {
            val userInfoB64 = body.substringBefore('@')
            val hostPort = body.substringAfter('@').substringBefore('?')
            val decoded = b64Decode(userInfoB64).ifEmpty { userInfoB64 }
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':')
            host = hostPort.substringBeforeLast(':')
            port = hostPort.substringAfterLast(':').toIntOrNull() ?: 8388
        } else {
            val decoded = b64Decode(body)
            val userInfo = decoded.substringBefore('@')
            val hostPort = decoded.substringAfter('@')
            method = userInfo.substringBefore(':')
            password = userInfo.substringAfter(':')
            host = hostPort.substringBeforeLast(':')
            port = hostPort.substringAfterLast(':').toIntOrNull() ?: 8388
        }

        return buildJsonObject {
            put("tag", TAG_PROXY)
            put("protocol", "shadowsocks")
            putJsonObject("settings") {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("address", host)
                        put("port", port)
                        put("method", method)
                        put("password", password)
                    })
                })
            }
        }
    }

    // ---------- streamSettings مشترک ----------

    private fun streamSettings(
        security: String,
        network: String,
        sni: String?,
        query: (String) -> String?
    ): JsonObject = buildJsonObject {
        put("network", network)

        when (network) {
            "ws" -> putJsonObject("wsSettings") {
                val path = query("path")?.ifEmpty { "/" } ?: "/"
                put("path", path)
                val host = query("host")
                if (!host.isNullOrBlank()) {
                    putJsonObject("headers") { put("Host", host) }
                }
            }
            "grpc" -> putJsonObject("grpcSettings") {
                put("serviceName", query("serviceName") ?: query("path") ?: "")
            }
            "h2" -> putJsonObject("httpSettings") {
                val path = query("path") ?: "/"
                put("path", path)
                val host = query("host")
                if (!host.isNullOrBlank()) {
                    put("host", buildJsonArray { add(JsonPrimitive(host)) })
                }
            }
        }

        when (security) {
            "tls" -> {
                put("security", "tls")
                putJsonObject("tlsSettings") {
                    if (!sni.isNullOrBlank()) put("serverName", sni)
                    val fp = query("fp")
                    if (!fp.isNullOrBlank()) put("fingerprint", fp)
                    val alpn = query("alpn")
                    if (!alpn.isNullOrBlank()) {
                        put("alpn", buildJsonArray {
                            alpn.split(",").forEach { add(JsonPrimitive(it.trim())) }
                        })
                    }
                    if (query("allowInsecure") == "1" || query("allowInsecure") == "true") {
                        put("allowInsecure", true)
                    }
                }
            }
            "reality" -> {
                put("security", "reality")
                putJsonObject("realitySettings") {
                    val serverName = query("sni") ?: query("host") ?: ""
                    put("serverName", serverName)
                    put("fingerprint", query("fp") ?: "chrome")
                    put("publicKey", query("pbk") ?: "")
                    put("shortId", query("sid") ?: "")
                    put("spiderX", query("spx") ?: "/")
                }
            }
            else -> put("security", "none")
        }
    }

    // ---------- helpers ----------

    private val Uri.queryParams: (String) -> String?
        get() = { key ->
            try {
                getQueryParameter(key)
            } catch (_: Exception) {
                null
            }
        }

    private fun b64Decode(s: String): String {
        var t = s.trim().replace('-', '+').replace('_', '/')
        val pad = (4 - t.length % 4) % 4
        t += "=".repeat(pad)
        return String(Base64.decode(t, Base64.DEFAULT), Charsets.UTF_8)
    }
}
