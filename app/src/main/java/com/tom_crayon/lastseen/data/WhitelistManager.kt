package com.tom_crayon.lastseen.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the set of Bluetooth device MAC addresses the user wants to track.
 * Backed by SharedPreferences — survives app restarts.
 *
 * The user configures this list via the app's Settings screen.
 * For now, an empty whitelist means "nothing to track" (service stays idle).
 */
object WhitelistManager {

    private const val PREFS_NAME = "lastseen_whitelist"
    private const val KEY_MACS = "tracked_macs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the current whitelist as a mutable Set of MAC strings. */
    fun get(context: Context): MutableSet<String> {
        return prefs(context).getStringSet(KEY_MACS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
    }

    /** Add a MAC address to the whitelist. */
    fun add(context: Context, mac: String) {
        val current = get(context)
        current.add(mac.uppercase())
        prefs(context).edit().putStringSet(KEY_MACS, current).apply()
    }

    /** Remove a MAC address from the whitelist. */
    fun remove(context: Context, mac: String) {
        val current = get(context)
        current.remove(mac.uppercase())
        prefs(context).edit().putStringSet(KEY_MACS, current).apply()
    }

    /** Replace the entire whitelist (used by Settings screen bulk edit). */
    fun setAll(context: Context, macs: Set<String>) {
        prefs(context).edit().putStringSet(KEY_MACS, macs.map { it.uppercase() }.toSet()).apply()
    }

    /** Check if a specific MAC is in the whitelist. */
    fun contains(context: Context, mac: String): Boolean {
        return get(context).contains(mac.uppercase())
    }

    /** Check if the whitelist is empty (nothing to track). */
    fun isEmpty(context: Context): Boolean {
        return get(context).isEmpty()
    }
}
