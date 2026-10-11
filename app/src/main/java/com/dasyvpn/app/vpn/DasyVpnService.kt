package com.dasyvpn.app.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.dasyvpn.app.DasyApp
import com.dasyvpn.app.MainActivity
import com.dasyvpn.app.R
import com.dasyvpn.app.core.XrayConfigBuilder
import com.dasyvpn.app.core.XrayCoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DasyVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunInterface: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isSystemRestart =
            intent == null || intent.action == SERVICE_INTERFACE

        startForegroundNotification()

        // اگر سیستم خودش سرویس را بالا آورد (Always-on) و کاربر قبلاً وصل بوده، دوباره وصل شو
        if (isSystemRestart && !DasyApp.instance.store.wantConnected) {
            stopEverything()
            return START_NOT_STICKY
        }

        if (XrayCoreManager.isRunning()) return START_STICKY

        scope.launch { startVpn() }
        return START_STICKY
    }

    private suspend fun startVpn() {
        val store = DasyApp.instance.store
        val profile = store.selectedServer()
        if (profile == null) {
            XrayCoreManager.appendLog("سروری انتخاب نشده است")
            stopEverything()
            return
        }

        try {
            val config = XrayConfigBuilder.build(profile.raw, store.bypassIran)

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(1500)
                .addAddress("172.19.0.1", 30)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            // ترافیک خودِ برنامه از تونل رد نشود
            builder.addDisallowedApplication(packageName)

            if (store.perAppProxy) {
                for (pkg in store.perAppPackages) {
                    try {
                        if (store.perAppBypassMode) {
                            builder.addDisallowedApplication(pkg)
                        } else {
                            builder.addAllowedApplication(pkg)
                        }
                    } catch (_: PackageManager.NameNotFoundException) {
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            tunInterface?.close()
            tunInterface = builder.establish()
            if (tunInterface == null) {
                throw IllegalStateException("establish() returned null")
            }

            XrayCoreManager.start(config, tunInterface!!.fd)
            store.wantConnected = true
        } catch (e: Exception) {
            XrayCoreManager.appendLog("اتصال ناموفق: ${e.message}")
            stopEverything()
        }
    }

    override fun onRevoke() {
        XrayCoreManager.appendLog("دسترسی VPN توسط سیستم لغو شد")
        stopEverything()
        super.onRevoke()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    private fun stopEverything() {
        try {
            XrayCoreManager.stop()
        } catch (_: Exception) {
        }
        try {
            tunInterface?.close()
        } catch (_: Exception) {
        }
        tunInterface = null
        DasyApp.instance.store.wantConnected = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, DasyApp.CHANNEL_VPN)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val NOTIF_ID = 43
        var instance: DasyVpnService? = null
            private set

        fun connect() {
            val ctx = DasyApp.instance
            val intent = Intent(ctx, DasyVpnService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }

        fun disconnect() {
            instance?.stopEverything() ?: run {
                // سرویس در حال اجرا نیست؛ فقط پرچم را پاک کن
                DasyApp.instance.store.wantConnected = false
            }
        }
    }
}
