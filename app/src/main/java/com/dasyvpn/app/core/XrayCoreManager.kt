package com.dasyvpn.app.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.dasyvpn.app.DasyApp
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.util.concurrent.atomic.AtomicBoolean

/**
 * مدیریت هسته Xray از طریق libv2ray (کتابخانه رسمی 2dust / v2rayNG).
 */
object XrayCoreManager {

    data class TrafficStats(val uplink: Long, val downlink: Long)

    private val initialized = AtomicBoolean(false)
    private var controller: CoreController? = null

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _statusEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val statusEvents: SharedFlow<String> = _statusEvents.asSharedFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        try {
            Seq.setContext(context.applicationContext)
            val assetPath = AssetsManager.userAssetPath(context)
            Libv2ray.initCoreEnv(assetPath, deviceId(context))
            appendLog("هسته Xray آماده شد (${Libv2ray.checkVersionX()})")
        } catch (e: Exception) {
            initialized.set(false)
            appendLog("خطا در راه‌اندازی هسته: ${e.message}")
            throw e
        }
    }

    private val startLock = Any()

    fun start(configJson: String, tunFd: Int) {
        synchronized(startLock) {
            if (_running.value) return
            val ctx = DasyApp.instance
            init(ctx)

            val cb = object : CoreCallbackHandler {
                override fun onEmitStatus(p0: Long, p1: String?): Long {
                    p1?.let { appendLog(it) }
                    return 0
                }

                override fun shutdown(): Long {
                    _running.value = false
                    appendLog("هسته متوقف شد")
                    return 0
                }

                override fun startup(): Long {
                    _running.value = true
                    appendLog("هسته در حال اجراست")
                    return 0
                }
            }

            val core = controller ?: Libv2ray.newCoreController(cb).also { controller = it }
            try {
                core.startLoop(configJson, tunFd)
                _running.value = true
                appendLog("اتصال برقرار شد")
                _statusEvents.tryEmit("connected")
            } catch (e: Exception) {
                _running.value = false
                appendLog("خطا در اتصال: ${e.message}")
                throw e
            }
        }
    }

    fun stop() {
        synchronized(startLock) {
            val core = controller ?: return
            try {
                core.stopLoop()
            } catch (e: Exception) {
                appendLog("خطا در توقف: ${e.message}")
            }
        }
    }

    fun isRunning(): Boolean = controller?.isRunning ?: false

    /** مجموع ترافیک همه outbounds از آخرین فراخوانی (واحد بایت) */
    fun pollTraffic(): TrafficStats {
        val core = controller ?: return TrafficStats(0, 0)
        val raw = try {
            core.queryAllOutboundTrafficStats()
        } catch (_: Exception) {
            return TrafficStats(0, 0)
        }
        if (raw.isNullOrBlank()) return TrafficStats(0, 0)
        var up = 0L
        var down = 0L
        // فرمت: tag,direction,value;tag,direction,value;
        for (entry in raw.split(';')) {
            if (entry.isBlank()) continue
            val parts = entry.split(',')
            if (parts.size < 3) continue
            val value = parts[2].trim().toLongOrNull() ?: continue
            when (parts[1].trim().lowercase()) {
                "uplink", "up" -> up += value
                "downlink", "down" -> down += value
            }
        }
        return TrafficStats(up, down)
    }

    /** تست تأخیر بدون اتصال دائمی (برای دکمه تست در لیست سرورها) */
    suspend fun measureDelay(configJson: String, url: String = "https://www.gstatic.com/generate_204"): Long =
        withContext(Dispatchers.IO) {
            try {
                Libv2ray.measureOutboundDelay(configJson, url)
            } catch (_: Exception) {
                -1L
            }
        }

    fun appendLog(line: String) {
        val stamped = line.trim()
        if (stamped.isEmpty()) return
        val cur = _logs.value
        val next = (cur + stamped).takeLast(300)
        _logs.value = next
    }

    private fun deviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )?.toByteArray(Charsets.UTF_8) ?: ByteArray(32)
        val padded = if (androidId.size >= 32) androidId.copyOf(32) else androidId + ByteArray(32 - androidId.size)
        return Base64.encodeToString(padded, Base64.NO_PADDING or Base64.URL_SAFE)
    }
}
