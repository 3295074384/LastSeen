package com.tom_crayon.lastseen

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tom_crayon.lastseen.data.DisconnectDao
import com.tom_crayon.lastseen.data.WhitelistManager
import com.tom_crayon.lastseen.service.TrackerForegroundService
import com.tom_crayon.lastseen.ui.screen.MainScreen
import com.tom_crayon.lastseen.ui.screen.MainViewModel
import com.tom_crayon.lastseen.ui.theme.LastSeenTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Main entry point. Hosts [MainScreen] (map + bottom sheet).
 *
 * Deep Link handling:
 *  - TrackerForegroundService sends an intent with extra "record_id" when
 *    the user taps an alert notification.
 *  - Both onCreate() and onNewIntent() parse this extra and call
 *    viewModel.focusOnRecord(id) to animate the map to the disconnect location.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "LastSeen_Main"
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LastSeenApplication
        viewModel = ViewModelProvider(
            this,
            MainViewModelFactory(app.dao)
        )[MainViewModel::class.java]

        setContent {
            LastSeenTheme {
                MainScreen(viewModel = viewModel)
            }
        }

        // Handle Deep Link from notification (cold start).
        handleDeepLink(intent)

        // Seed whitelist with OPPO Enco X3 dual-earbud MAC addresses.
        // These are the actual hardware addresses captured via BT scanner.
        WhitelistManager.add(this, "40:72:18:C5:9F:12")
        WhitelistManager.add(this, "40:72:18:C5:68:63")
        Log.i(TAG, "白名单已初始化: OPPO Enco X3 左耳 40:72:18:C5:9F:12, 右耳 40:72:18:C5:68:63")

        // DEBUG: ensure service is running every time app opens (fallback for
        // cases where HyperOS killed it after BOOT_COMPLETED).
        TrackerForegroundService.start(this)
        Log.i(TAG, "从 MainActivity 拉起 TrackerForegroundService")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle Deep Link from notification (warm start — activity already alive).
        handleDeepLink(intent)
    }

    /**
     * Parse incoming intent for a record_id extra.
     * If present, wait for Room data to load then focus the map on that record.
     */
    private fun handleDeepLink(intent: Intent?) {
        val recordId = intent?.getLongExtra("record_id", -1L) ?: -1L
        if (recordId <= 0) return

        Log.i(TAG, "Deep Link 收到: record_id=$recordId")

        // Room data loads asynchronously. Wait for non-empty list before
        // calling focusOnRecord so the record is actually available.
        viewModel.viewModelScope.launch {
            viewModel.recordsState
                .filter { it.isNotEmpty() }
                .first()
            viewModel.focusOnRecord(recordId)
            Log.i(TAG, "Deep Link: 已聚焦到 record_id=$recordId")
        }
    }
}

/**
 * Simple ViewModelFactory for MainViewModel since we're not using Hilt.
 */
class MainViewModelFactory(private val dao: DisconnectDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
