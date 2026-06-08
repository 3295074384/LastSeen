package com.tom_crayon.lastseen.tracker

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

/**
 * Lightweight WiFi scene recognizer.
 *
 * On disconnect, sniffs the currently connected WiFi's SSID and BSSID,
 * then checks if the BSSID matches a user-labeled "known place" (home,
 * office, etc.). Returns a [WifiScene] for inclusion in the DisconnectRecord.
 */
object WifiSniffer {

    private const val TAG = "LastSeen_WifiSniffer"

    data class WifiScene(
        val ssid: String?,
        val bssid: String?,
        /** e.g. "在家里附近断连" or null if the network is unknown */
        val sceneLabel: String?
    )

    /**
     * Sniff the current WiFi connection and resolve the scene label.
     * Returns WifiScene with nulls if WiFi is disabled or not connected.
     */
    fun sniff(context: Context): WifiScene {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "WiFi 已关闭")
            return WifiScene(null, null, null)
        }

        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo

        val ssid = wifiInfo.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }
        val bssid = wifiInfo.bssid?.takeIf { it != "02:00:00:00:00:00" }

        if (ssid == null || bssid == null) {
            Log.d(TAG, "WiFi 未连接或信息不可用")
            return WifiScene(null, null, null)
        }

        Log.d(TAG, "WiFi 嗅探: SSID=$ssid, BSSID=$bssid")

        // Check against user-labeled known places.
        val label = SceneLabelManager.resolve(context, bssid)

        return WifiScene(
            ssid = ssid,
            bssid = bssid,
            sceneLabel = label?.let { "在[$it]附近断连" }
        )
    }
}
