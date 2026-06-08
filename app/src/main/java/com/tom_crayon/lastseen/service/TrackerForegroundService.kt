package com.tom_crayon.lastseen.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tom_crayon.lastseen.LastSeenApplication
import com.tom_crayon.lastseen.MainActivity
import com.tom_crayon.lastseen.R
import com.tom_crayon.lastseen.data.DisconnectRecord
import com.tom_crayon.lastseen.data.WhitelistManager
import com.tom_crayon.lastseen.tracker.AMapTracker
import com.tom_crayon.lastseen.tracker.WifiSniffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * DEBUG BUILD — Foreground service with unconditional notifications.
 *
 * All notifications use the unified channel [LastSeenApplication.CHANNEL_ID]
 * with IMPORTANCE_HIGH + PRIORITY_MAX to break through Xiaomi silent blocking.
 */
class TrackerForegroundService : Service() {

    companion object {
        const val TAG = "LastSeen_Debug"
        const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 2001

        private const val RECONNECT_BUFFER_MS = 0L
        private const val IDLE_SHUTDOWN_MS = 300_000L

        fun start(context: Context) {
            Log.wtf(TAG, "【服务启动】TrackerForegroundService.start() 被调用")
            val intent = Intent(context, TrackerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ──────────────────────────────────────────────
    // ACL Broadcast Receivers
    // ──────────────────────────────────────────────

    private val bluetoothEventRouter = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = extractDevice(intent)
            val mac = device?.address ?: "未知MAC"
            val name = device?.name ?: "未知设备"

            when (intent.action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.wtf(TAG, "【服务内部广播】ACL_DISCONNECTED! 设备=$name [$mac]")
                    Log.wtf(TAG, "【调试模式】无条件触发断连处理流程！")
                    handleDisconnectConfirmed(mac, name)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.wtf(TAG, "【服务内部广播】ACL_CONNECTED! 设备=$name [$mac]")
                    handler.removeCallbacksAndMessages(SHUTDOWN_TOKEN)
                }
            }
        }
    }

    private fun extractDevice(intent: Intent): BluetoothDevice? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取 BluetoothDevice 失败", e)
            e.printStackTrace()
            null
        }
    }

    // ──────────────────────────────────────────────
    // Service lifecycle
    // ──────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.wtf(TAG, "【服务雷达】onCreate — 前台保活服务正式创建！")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.wtf(TAG, "【服务雷达】onStartCommand — 服务拉起运行！")

        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
            Log.wtf(TAG, "【服务雷达】startForeground() 成功")
        } catch (e: Exception) {
            Log.e(TAG, "【服务雷达】startForeground() 失败！", e)
            e.printStackTrace()
        }

        try {
            registerBluetoothReceivers()
            Log.wtf(TAG, "【服务雷达】蓝牙广播接收器动态注册完成")
        } catch (e: Exception) {
            Log.e(TAG, "【服务雷达】蓝牙广播注册失败！", e)
            e.printStackTrace()
        }

        // DEBUG: fire test notification to confirm channel works.
        fireTestNotification()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.wtf(TAG, "【服务雷达】onDestroy — 服务被销毁！")
        serviceScope.cancel()
        try { unregisterReceiver(bluetoothEventRouter) } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ──────────────────────────────────────────────
    // Disconnect handling — full pipeline
    // ──────────────────────────────────────────────

    private fun handleDisconnectConfirmed(mac: String, deviceName: String) {
        serviceScope.launch {
            try {
                Log.wtf(TAG, "===== 【断连已确认】启动定位存库流程 =====")

                // --- Step 1: Dual-location ---
                var location: com.tom_crayon.lastseen.tracker.LocationResult? = null
                try {
                    location = AMapTracker.resolve(this@TrackerForegroundService)
                    Log.wtf(TAG, "【定位汇总】结果: ${location ?: "全部失败"}")
                } catch (e: Exception) {
                    Log.e(TAG, "定位异常", e)
                    e.printStackTrace()
                }

                // --- Step 2: WiFi scene ---
                var wifi = WifiSniffer.WifiScene(null, null, null)
                try {
                    wifi = WifiSniffer.sniff(this@TrackerForegroundService)
                    Log.wtf(TAG, "【WiFi】ssid=${wifi.ssid}, label=${wifi.sceneLabel}")
                } catch (e: Exception) {
                    Log.e(TAG, "WiFi 嗅探异常", e)
                    e.printStackTrace()
                }

                // --- Step 3: Room write ---
                var insertedId = -1L
                if (location != null) {
                    try {
                        val record = DisconnectRecord(
                            deviceName = deviceName,
                            deviceMac = mac,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            locationType = location.locationType,
                            isCached = location.isCached,
                            wifiSsid = wifi.ssid,
                            wifiBssid = wifi.bssid,
                            sceneLabel = wifi.sceneLabel,
                            timestamp = System.currentTimeMillis()
                        )
                        val app = application as LastSeenApplication
                        insertedId = app.dao.insert(record)
                        Log.wtf(TAG, "【Room】写入成功: id=$insertedId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Room 写入异常！", e)
                        e.printStackTrace()
                    }
                }

                // --- Step 4: ALERT NOTIFICATION ---
                val positionDesc = if (location != null) {
                    wifi.sceneLabel
                        ?: "${location.locationType}定位(${String.format("%.0f", location.accuracy)}m)"
                } else {
                    "位置获取失败"
                }
                showAlertNotification(deviceName, "位置已记录 · $positionDesc", insertedId)

            } catch (e: Exception) {
                Log.e(TAG, "断连处理顶层异常！", e)
                e.printStackTrace()
                try {
                    showAlertNotification(deviceName, "处理异常: ${e.message}", -1L)
                } catch (e2: Exception) {
                    Log.e(TAG, "兜底通知也失败", e2)
                    e2.printStackTrace()
                }
            }
        }
    }

    // ──────────────────────────────────────────────
    // Notifications — UNIFIED CHANNEL, MAX PRIORITY
    // ──────────────────────────────────────────────

    /**
     * Alert notification — fires on confirmed disconnect.
     * Uses the unified channel with PRIORITY_MAX + DEFAULT_ALL.
     */
    private fun showAlertNotification(deviceName: String, detail: String, recordId: Long) {
        Log.wtf(TAG, "【通知发送】准备弹出: $deviceName — $detail")

        val deepLinkIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("record_id", recordId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            recordId.toInt(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, LastSeenApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("⚠️ $deviceName 断开连接")
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        Log.wtf(TAG, "【通知发送】notify()已执行，ChannelID=${LastSeenApplication.CHANNEL_ID}")
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
        Log.wtf(TAG, "【通知发送】完成! ID=$ALERT_NOTIFICATION_ID")
    }

    /**
     * Test notification — fires immediately on service start.
     * Confirms channel configuration works on this device.
     */
    private fun fireTestNotification() {
        try {
            val n = NotificationCompat.Builder(this, LastSeenApplication.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("🟢 LastSeen 调试")
                .setContentText("前台服务已启动！Channel=${LastSeenApplication.CHANNEL_ID}")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()
            Log.wtf(TAG, "【测试通知】notify()已执行，ChannelID=${LastSeenApplication.CHANNEL_ID}")
            getSystemService(NotificationManager::class.java).notify(9999, n)
        } catch (e: Exception) {
            Log.e(TAG, "测试通知发送失败！", e)
            e.printStackTrace()
        }
    }

    /**
     * Foreground service persistent notification — silent, ongoing.
     * Uses the SAME unified channel (required by Android 14+).
     */
    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, LastSeenApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("LastSeen")
            .setContentText("正在守护你的设备连接")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ──────────────────────────────────────────────
    // Idle shutdown
    // ──────────────────────────────────────────────

    private val SHUTDOWN_TOKEN = Object()

    private fun scheduleIdleShutdown() {
        handler.removeCallbacksAndMessages(SHUTDOWN_TOKEN)
        handler.postDelayed({
            if (!hasAnyWhitelistDeviceConnected()) {
                Log.wtf(TAG, "【服务雷达】5 分钟空闲 → stopSelf()")
                stopSelf()
            }
        }, SHUTDOWN_TOKEN, IDLE_SHUTDOWN_MS)
    }

    // ──────────────────────────────────────────────
    // Bluetooth helpers
    // ──────────────────────────────────────────────

    private fun registerBluetoothReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        registerReceiver(bluetoothEventRouter, filter)
    }

    private fun hasAnyWhitelistDeviceConnected(): Boolean {
        val whitelist = WhitelistManager.get(this)
        if (whitelist.isEmpty()) return false
        val bm = getSystemService(BluetoothManager::class.java) ?: return false
        val connectedMacs = mutableSetOf<String>()
        listOf(BluetoothProfile.GATT, BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
            try {
                bm.getConnectedDevices(profile).forEach { connectedMacs.add(it.address) }
            } catch (e: Exception) {
                Log.e(TAG, "查询已连接设备异常", e)
                e.printStackTrace()
            }
        }
        return whitelist.any { it in connectedMacs }
    }
}
