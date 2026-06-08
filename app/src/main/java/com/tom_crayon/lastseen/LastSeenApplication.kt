package com.tom_crayon.lastseen

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.room.Room
import com.amap.api.location.AMapLocationClient
import com.tom_crayon.lastseen.data.DisconnectDao
import com.tom_crayon.lastseen.data.LastSeenDatabase

/**
 * Application class for LastSeen.
 *
 * Responsibilities:
 * 1. Amap privacy compliance.
 * 2. Lazy singleton for Room database.
 * 3. Create the UNIFIED notification channel (IMPORTANCE_HIGH) at app startup.
 */
class LastSeenApplication : Application() {

    companion object {
        /** Single unified channel ID used by ALL notifications in the app. */
        const val CHANNEL_ID = "lastseen_core_channel"
    }

    val database: LastSeenDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            LastSeenDatabase::class.java,
            "lastseen_db"
        ).build()
    }

    val dao: DisconnectDao by lazy { database.disconnectDao() }

    override fun onCreate() {
        super.onCreate()

        // Amap mandatory privacy compliance.
        AMapLocationClient.updatePrivacyShow(this, true, true)
        AMapLocationClient.updatePrivacyAgree(this, true)

        // Create the unified notification channel at the earliest possible moment.
        createCoreChannel()
    }

    /**
     * Single channel for ALL notifications (foreground service + alert).
     * IMPORTANCE_HIGH ensures visibility on Xiaomi/HyperOS.
     * VISIBILITY_PUBLIC shows on lockscreen.
     */
    private fun createCoreChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "LastSeen 核心通知",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用于耳机断开时的高精度位置强提醒及后台服务保活"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
