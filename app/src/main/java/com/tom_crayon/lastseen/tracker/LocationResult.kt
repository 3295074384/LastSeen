package com.tom_crayon.lastseen.tracker

/**
 * Unified location result from either system LocationManager or Amap SDK.
 * Used as the common currency between the two location sources before
 * assembling the final DisconnectRecord.
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    /** "GPS", "WiFi", "基站", or "缓存" (for getLastKnownLocation) */
    val locationType: String,
    /** Whether this came from getLastKnownLocation (zero-delay cached) */
    val isCached: Boolean
)
