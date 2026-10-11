package com.dasyvpn.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.dasyvpn.app.core.AssetsManager
import com.dasyvpn.app.data.ConfigStore

class DasyApp : Application() {

    lateinit var store: ConfigStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = ConfigStore(this)
        createNotificationChannel()
        AssetsManager.ensureGeoAssetsAsync(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_VPN,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_VPN = "dasy_vpn_channel"
        lateinit var instance: DasyApp
            private set
    }
}
