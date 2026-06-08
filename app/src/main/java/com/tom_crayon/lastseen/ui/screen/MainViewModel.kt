package com.tom_crayon.lastseen.ui.screen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tom_crayon.lastseen.data.DisconnectDao
import com.tom_crayon.lastseen.data.DisconnectRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the main map + list screen.
 *
 * Exposes:
 *  - recordsState: all disconnect records ordered by time (Room Flow → StateFlow)
 *  - selectedRecord: the record currently focused on the map (mutableStateOf for Compose)
 *  - focusOnRecord(id): programmatically focus a record by ID (Deep Link entry point)
 *  - deleteRecord(record): swipe-to-delete handler
 */
class MainViewModel(private val dao: DisconnectDao) : ViewModel() {

    /** All disconnect records, newest first. Driven by Room Flow. */
    val recordsState: StateFlow<List<DisconnectRecord>> =
        dao.getAllRecords()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Currently selected record — drives map camera + marker. */
    val selectedRecord = mutableStateOf<DisconnectRecord?>(null)

    /**
     * Focus the map on a specific record by ID.
     * Called from Deep Link handler when user taps an alert notification.
     *
     * Uses firstOrNull because the record might have been deleted between
     * the notification firing and the user tapping it.
     */
    fun focusOnRecord(id: Long) {
        viewModelScope.launch {
            val record = dao.getRecordById(id)
            selectedRecord.value = record
        }
    }

    /** Select a record from the list (user tap). */
    fun selectRecord(record: DisconnectRecord) {
        selectedRecord.value = record
    }

    /** Delete a record (swipe-to-dismiss). */
    fun deleteRecord(record: DisconnectRecord) {
        viewModelScope.launch {
            dao.deleteById(record.id)
            // If the deleted record was selected, clear selection.
            if (selectedRecord.value?.id == record.id) {
                selectedRecord.value = null
            }
        }
    }

    /** Auto-focus on the latest record when the screen first loads. */
    fun focusLatest() {
        val latest = recordsState.value.firstOrNull()
        if (latest != null && selectedRecord.value == null) {
            selectedRecord.value = latest
        }
    }
}
