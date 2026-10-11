package com.dasyvpn.app.data

import android.content.Context
import com.dasyvpn.app.model.ServerProfile
import com.dasyvpn.app.model.Subscription
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** ذخیره‌سازی ساده و سریع روی SharedPreferences */
class ConfigStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("dasy_vpn", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var servers: List<ServerProfile>
        get() = decodeList(prefs.getString(KEY_SERVERS, null))
        set(value) {
            prefs.edit().putString(KEY_SERVERS, json.encodeToString(value)).apply()
        }

    var subscriptions: List<Subscription>
        get() = decodeList(prefs.getString(KEY_SUBS, null))
        set(value) {
            prefs.edit().putString(KEY_SUBS, json.encodeToString(value)).apply()
        }

    var selectedServerId: String?
        get() = prefs.getString(KEY_SELECTED, null)
        set(value) {
            prefs.edit().putString(KEY_SELECTED, value).apply()
        }

    /** وقتی کاربر وصل بود و اپ کشته شد، خودکار دوباره وصل شویم */
    var wantConnected: Boolean
        get() = prefs.getBoolean(KEY_WANT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WANT, value).apply()
        }

    var bypassIran: Boolean
        get() = prefs.getBoolean(KEY_BYPASS_IRAN, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BYPASS_IRAN, value).apply()
        }

    var perAppProxy: Boolean
        get() = prefs.getBoolean(KEY_PER_APP, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PER_APP, value).apply()
        }

    var perAppBypassMode: Boolean
        get() = prefs.getBoolean(KEY_PER_APP_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PER_APP_MODE, value).apply()
        }

    var perAppPackages: Set<String>
        get() = prefs.getStringSet(KEY_PER_APP_PKGS, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_PER_APP_PKGS, value).apply()
        }

    fun selectedServer(): ServerProfile? {
        val id = selectedServerId ?: return null
        return servers.firstOrNull { it.id == id }
    }

    private inline fun <reified T> decodeList(raw: String?): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<T>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private companion object {
        const val KEY_SERVERS = "servers"
        const val KEY_SUBS = "subscriptions"
        const val KEY_SELECTED = "selected_server"
        const val KEY_WANT = "want_connected"
        const val KEY_BYPASS_IRAN = "bypass_iran"
        const val KEY_PER_APP = "per_app_proxy"
        const val KEY_PER_APP_MODE = "per_app_bypass"
        const val KEY_PER_APP_PKGS = "per_app_packages"
    }
}
