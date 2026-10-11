package com.dasyvpn.app.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val raw: String,
    val protocol: String,
    val host: String = "",
    val port: Int = 0,
    val subscriptionId: String? = null
)

@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String
)

enum class VpnState {
    DISCONNECTED, CONNECTING, CONNECTED, STOPPING, ERROR
}
