package com.tom_crayon.lastseen.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single Bluetooth device disconnect event.
 * Captures the phone's GPS/network location at the moment of disconnection,
 * along with WiFi context for indoor scene recognition.
 */
@Entity(tableName = "disconnect_records")
data class DisconnectRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Bluetooth device display name, e.g. "OPPO Enco X3" */
    val deviceName: String,

    /** Bluetooth device MAC address for whitelist matching */
    val deviceMac: String,

    /** Latitude from Amap location SDK */
    val latitude: Double,

    /** Longitude from Amap location SDK */
    val longitude: Double,

    /** Location accuracy in meters (from Amap) */
    val accuracy: Float,

    /** Location provider type: "GPS", "WiFi", "基站" */
    val locationType: String,

    /** Whether this location came from getLastKnownLocation (cached) */
    val isCached: Boolean,

    /** Connected WiFi SSID at disconnect time (null if not available) */
    val wifiSsid: String? = null,

    /** Connected WiFi router BSSID at disconnect time (null if not available) */
    val wifiBssid: String? = null,

    /** User-defined scene label, e.g. "在家里附近断连" (null if unrecognized) */
    val sceneLabel: String? = null,

    /** Unix timestamp in milliseconds when the disconnect occurred */
    val timestamp: Long = System.currentTimeMillis()
)
