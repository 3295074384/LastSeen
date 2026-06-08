package com.tom_crayon.lastseen.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dual-location fallback manager for LastSeen.
 *
 * Strategy:
 *   1. Zero-delay cached: getLastKnownLocation() → instant approximate position.
 *   2. High-precision fresh: Amap AMapLocationClient with onceLocation=true
 *      → GPS/WiFiconstellation/基站 fused position within 15 s.
 */
object AMapTracker {

    private const val TAG = "LastSeen_Bugfix"
    private const val AMAP_TIMEOUT_MS = 15_000L   // 15 seconds for cold GPS fix

    // ──────────────────────────────────────────────
    // Layer 1: Zero-delay cached location
    // ──────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun getCachedLocation(context: Context): LocationResult? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        for (provider in providers) {
            try {
                val location = lm.getLastKnownLocation(provider) ?: continue
                Log.wtf(TAG, "【缓存定位】命中: provider=$provider, " +
                        "lat=${location.latitude}, lng=${location.longitude}, " +
                        "accuracy=${location.accuracy}m")
                return LocationResult(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    locationType = "缓存",
                    isCached = true
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "缓存定位权限不足: $provider", e)
                e.printStackTrace()
            } catch (e: Exception) {
                Log.w(TAG, "缓存定位异常: $provider", e)
                e.printStackTrace()
            }
        }
        Log.wtf(TAG, "【缓存定位】所有 provider 均无结果")
        return null
    }

    // ──────────────────────────────────────────────
    // Layer 2: High-precision Amap location
    // ──────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun getHighPrecisionLocation(context: Context): LocationResult? {
        val deferred = CompletableDeferred<LocationResult?>()

        try {
            val client = AMapLocationClient(context)
            val option = AMapLocationClientOption().apply {
                // === HIGH ACCURACY: GPS + WiFi + 基站 fusion ===
                locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy

                // === SINGLE SHOT ===
                isOnceLocation = true

                // === 15 SECOND TIMEOUT for cold GPS fix ===
                httpTimeOut = AMAP_TIMEOUT_MS

                // === WiFi SCAN: critical for indoor / urban accuracy ===
                // Forces Amap to scan surrounding WiFi hotspots for triangulation.
                // Without this, indoor accuracy degrades to 200m+ (基站 only).
                isWifiScan = true

                // === NO MOCK: reject spoofed locations ===
                isMockEnable = false

                // === NO ADDRESS: we only need coordinates, saves bandwidth ===
                isNeedAddress = false

                // === CACHE: use recent Amap cache if fresh ===
                isLocationCacheEnable = true
            }
            client.setLocationOption(option)

            Log.wtf(TAG, "【高德定位】启动: mode=Hight_Accuracy, timeout=${AMAP_TIMEOUT_MS}ms, " +
                    "wifiScan=true, mockEnable=false, onceLocation=true")

            client.setLocationListener(AMapLocationListener { location ->
                client.onDestroy()

                if (location == null) {
                    Log.wtf(TAG, "【高德定位】回调返回 null")
                    deferred.complete(null)
                    return@AMapLocationListener
                }

                if (location.errorCode != 0) {
                    Log.wtf(TAG, "【高德定位】失败: code=${location.errorCode}, " +
                            "msg=${location.errorInfo}, detail=${location.locationDetail}")
                    deferred.complete(null)
                    return@AMapLocationListener
                }

                val type = mapAmapLocationType(location.locationType)
                Log.wtf(TAG, "【定位成功】经度:${location.longitude}, 纬度:${location.latitude}, " +
                        "精度:${location.accuracy}米, 类型:$type")

                deferred.complete(
                    LocationResult(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracy = location.accuracy,
                        locationType = type,
                        isCached = false
                    )
                )
            })

            client.startLocation()

            return withTimeoutOrNull(AMAP_TIMEOUT_MS + 3_000L) {
                deferred.await()
            } ?: run {
                Log.wtf(TAG, "【高德定位】超时（${AMAP_TIMEOUT_MS + 3000}ms）")
                try { client.onDestroy() } catch (_: Exception) {}
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "【高德定位】异常", e)
            e.printStackTrace()
            return null
        }
    }

    private fun mapAmapLocationType(type: Int): String = when (type) {
        1 -> "GPS"
        2, 4 -> "WiFi"
        5 -> "基站"
        6 -> "离线定位"
        else -> "未知($type)"
    }

    // ──────────────────────────────────────────────
    // Combined: Amap preferred, cached fallback
    // ──────────────────────────────────────────────

    suspend fun resolve(context: Context): LocationResult? {
        val cached = getCachedLocation(context)
        val amap = getHighPrecisionLocation(context)

        Log.wtf(TAG, "【定位决策】amap=${amap != null}, cached=${cached != null} → " +
                "使用=${if (amap != null) "高德" else if (cached != null) "缓存" else "全部失败"}")

        return amap ?: cached
    }
}
