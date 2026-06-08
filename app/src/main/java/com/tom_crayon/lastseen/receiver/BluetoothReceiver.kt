package com.tom_crayon.lastseen.receiver

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tom_crayon.lastseen.data.WhitelistManager
import com.tom_crayon.lastseen.service.TrackerForegroundService

/**
 * DEBUG BUILD — lightweight seed receiver.
 *
 * Changes for debugging:
 *  - Log.wtf at entry to confirm broadcast delivery.
 *  - Triple gate REMOVED: service starts unconditionally on boot.
 *  - Also listens for ACL_DISCONNECTED / ACL_CONNECTED as a fallback
 *    in case the service's dynamic receiver was killed by HyperOS.
 *  - On ANY ACL event, immediately starts TrackerForegroundService
 *    which will fire a test notification unconditionally.
 */
class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LastSeen_Debug"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // === RADAR: unconditional top-level log ===
        Log.wtf(TAG, "【广播雷达】捕获到系统事件！Action = ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.wtf(TAG, "【广播雷达】收到 BOOT_COMPLETED，无条件拉起服务")
                TrackerForegroundService.start(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = extractDevice(intent)
                val mac = device?.address ?: "未知MAC"
                val name = device?.name ?: "未知设备"
                Log.wtf(TAG, "【广播雷达】ACL_DISCONNECTED! 设备=$name [$mac]，无条件拉起服务")
                TrackerForegroundService.start(context)
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = extractDevice(intent)
                val mac = device?.address ?: "未知MAC"
                val name = device?.name ?: "未知设备"
                Log.wtf(TAG, "【广播雷达】ACL_CONNECTED! 设备=$name [$mac]")
                // Connected event — service handles this internally.
            }
            else -> {
                Log.w(TAG, "【广播雷达】未处理的 Action: ${intent.action}")
            }
        }
    }

    private fun extractDevice(intent: Intent): BluetoothDevice? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
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
}
