package com.tom_crayon.lastseen.tracker

import android.content.Context
import android.content.SharedPreferences

/**
 * Maps known WiFi BSSIDs to human-readable location labels.
 * Users configure these via the Settings screen (e.g. "家里", "公司").
 *
 * Storage format (SharedPreferences):
 *   key = "scene_labels"
 *   value = "AA:BB:CC:DD:EE:FF=家里,11:22:33:44:55:66=公司"
 */
object SceneLabelManager {

    private const val PREFS_NAME = "lastseen_scenes"
    private const val KEY_LABELS = "scene_labels"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Resolve a BSSID to a user-defined label. Returns null if unknown. */
    fun resolve(context: Context, bssid: String): String? {
        return getAll(context)[bssid.uppercase()]
    }

    /** Get all BSSID→label mappings. */
    fun getAll(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_LABELS, null) ?: return emptyMap()
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2) parts[0].uppercase() to parts[1] else null
        }.toMap()
    }

    /** Add or update a BSSID→label mapping. */
    fun put(context: Context, bssid: String, label: String) {
        val current = getAll(context).toMutableMap()
        current[bssid.uppercase()] = label
        saveAll(context, current)
    }

    /** Remove a BSSID mapping. */
    fun remove(context: Context, bssid: String) {
        val current = getAll(context).toMutableMap()
        current.remove(bssid.uppercase())
        saveAll(context, current)
    }

    private fun saveAll(context: Context, map: Map<String, String>) {
        val serialized = map.entries.joinToString(",") { "${it.key}=${it.value}" }
        prefs(context).edit().putString(KEY_LABELS, serialized).apply()
    }
}
