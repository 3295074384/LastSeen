package com.tom_crayon.lastseen.ui.screen

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.tom_crayon.lastseen.data.DisconnectRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main screen: full-screen Amap + bottom sheet with disconnect history.
 *
 * @param viewModel provides recordsState, selectedRecord, and action callbacks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val records by viewModel.recordsState.collectAsState()
    val selectedRecord by viewModel.selectedRecord

    // Auto-focus latest on first composition.
    LaunchedEffect(records) {
        if (records.isNotEmpty() && selectedRecord == null) {
            viewModel.focusLatest()
        }
    }

    // Remember the Amap instance for camera/marker operations.
    val amapRef = remember { mutableMapOf<String, AMap?>() }
    val markerRef = remember { mutableMapOf<String, Marker?>() }

    // Camera + marker sync when selectedRecord changes.
    LaunchedEffect(selectedRecord) {
        val amap = amapRef["amap"] ?: return@LaunchedEffect
        val record = selectedRecord ?: return@LaunchedEffect

        // Clear old marker.
        markerRef["marker"]?.remove()

        // Add new marker.
        val position = LatLng(record.latitude, record.longitude)
        val markerOptions = MarkerOptions()
            .position(position)
            .title(record.deviceName)
            .snippet(buildSnippet(record))
            .draggable(false)
        val newMarker = amap.addMarker(markerOptions)
        markerRef["marker"] = newMarker

        // Animate camera to the position.
        amap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(position, 17f),
            1000,  // animation duration ms
            null
        )
    }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded
    )
    val scaffoldState = rememberBottomSheetScaffoldState(sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            SheetContent(
                records = records,
                selectedId = selectedRecord?.id,
                onRecordClick = { record ->
                    viewModel.selectRecord(record)
                },
                onRecordDelete = { record ->
                    viewModel.deleteRecord(record)
                }
            )
        },
        sheetPeekHeight = 200.dp,
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        // Map fills the remaining space above the sheet.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AmapView(
                onMapReady = { amap -> amapRef["amap"] = amap },
                selectedRecord = selectedRecord
            )

            // Empty state overlay when no records.
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📡",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在守护你的设备连接",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当受追踪的设备断开连接时，位置将自动记录在这里",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────
// Amap MapView wrapper
// ──────────────────────────────────────────────────

/**
 * Wraps Amap MapView in AndroidView with proper lifecycle management.
 * Calls onMapReady once the AMap instance is available.
 */
@Composable
private fun AmapView(
    onMapReady: (AMap) -> Unit,
    selectedRecord: DisconnectRecord?
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    // Lifecycle observer for MapView.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    mapView.onCreate(null)
                    mapView.map?.let { amap ->
                        // Default: show China center.
                        amap.moveCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(35.86, 104.19), 4f)
                        )
                        amap.uiSettings.isZoomControlsEnabled = true
                        amap.uiSettings.isCompassEnabled = true
                        onMapReady(amap)
                    }
                }
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize()
    )
}

// ──────────────────────────────────────────────────
// Bottom Sheet content
// ──────────────────────────────────────────────────

@Composable
private fun SheetContent(
    records: List<DisconnectRecord>,
    selectedId: Long?,
    onRecordClick: (DisconnectRecord) -> Unit,
    onRecordDelete: (DisconnectRecord) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Drag handle indicator.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }

        Text(
            text = "断连记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无断连记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = records,
                    key = { it.id }
                ) { record ->
                    DismissibleRecordCard(
                        record = record,
                        isSelected = record.id == selectedId,
                        onClick = { onRecordClick(record) },
                        onDelete = { onRecordDelete(record) }
                    )
                }
                // Bottom spacer so the last item isn't clipped.
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ──────────────────────────────────────────────────
// Swipeable record card
// ──────────────────────────────────────────────────

/**
 * A record card wrapped in SwipeToDismissBox.
 * Swipe left → delete. Tap → focus on map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DismissibleRecordCard(
    record: DisconnectRecord,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Red delete background, visible during swipe.
            val progress by animateFloatAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0f,
                label = "swipe_progress"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Red.copy(alpha = 0.8f * progress))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "🗑️ 删除",
                    color = Color.White.copy(alpha = progress),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        enableDismissFromStartToEnd = false,   // only swipe left
        enableDismissFromEndToStart = true
    ) {
        RecordCard(
            record = record,
            isSelected = isSelected,
            onClick = onClick
        )
    }
}

// ──────────────────────────────────────────────────
// Record card UI
// ──────────────────────────────────────────────────

@Composable
private fun RecordCard(
    record: DisconnectRecord,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator dot.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Row 1: device name + time.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = record.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatTimestamp(record.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: scene label or location type + accuracy.
                Text(
                    text = buildSnippet(record),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────
// Formatting helpers
// ──────────────────────────────────────────────────

/**
 * Build the subtitle text for a record.
 * Priority: sceneLabel > locationType + accuracy.
 */
private fun buildSnippet(record: DisconnectRecord): String {
    return record.sceneLabel
        ?: "${record.locationType}定位 · 精度${String.format("%.0f", record.accuracy)}m"
}

/**
 * Format a Unix timestamp to a human-readable relative/absolute time string.
 * - Within today: "14:32"
 * - Yesterday: "昨天 14:32"
 * - Older: "06-05 14:32"
 */
private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val sdfFull = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    return when {
        diff < 60_000 -> "刚刚"
        diff < 86_400_000 && isSameDay(timestamp, now) -> sdf.format(Date(timestamp))
        diff < 172_800_000 && isYesterday(timestamp, now) -> "昨天 ${sdf.format(Date(timestamp))}"
        else -> sdfFull.format(Date(timestamp))
    }
}

private fun isSameDay(ts1: Long, ts2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = ts1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = ts2 }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun isYesterday(ts: Long, now: Long): Boolean {
    val yesterday = now - 86_400_000
    return isSameDay(ts, yesterday)
}
