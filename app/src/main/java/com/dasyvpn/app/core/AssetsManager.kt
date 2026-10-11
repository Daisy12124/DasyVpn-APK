package com.dasyvpn.app.core

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * فایل‌های geoip.dat / geosite.dat از APK (که از AAR هسته می‌آیند) را
 * به پوشه داده برنامه کپی می‌کند تا هسته Xray بتواند از آن‌ها استفاده کند.
 */
object AssetsManager {
    private const val DIR_ASSETS = "assets"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val GEO_FILES = listOf("geoip.dat", "geosite.dat")

    fun userAssetPath(context: Context): String =
        context.getDir(DIR_ASSETS, Context.MODE_PRIVATE).absolutePath

    fun ensureGeoAssetsAsync(context: Context) {
        scope.launch { ensureGeoAssets(context) }
    }

    @Synchronized
    fun ensureGeoAssets(context: Context) {
        val dir = File(userAssetPath(context))
        if (!dir.exists()) dir.mkdirs()
        for (name in GEO_FILES) {
            val out = File(dir, name)
            if (out.exists() && out.length() > 0L) continue
            try {
                context.assets.open(name).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            } catch (_: Exception) {
                // اگر فایل در دارایی‌ها نبود (نسخه‌های کم‌حجم‌تر) بی‌خیال می‌شویم
            }
        }
    }
}
