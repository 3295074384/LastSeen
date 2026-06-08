# CLAUDE.md — LastSeen 1.0

This file provides guidance to Claude Code when working with code in this repository.

## Project

**LastSeen** — Android tool app that records the phone's GPS location when a whitelisted Bluetooth device (earbuds) disconnects. Displays disconnect events on an Amap (高德) map with a bottom sheet history list. Chinese-language UI.

Package: `com.tom_crayon.lastseen` | Min SDK 33 | Target SDK 36

## Build Commands

```bash
# Compile (use Git Bash on Windows, JAVA_HOME must point to JDK root, not bin/)
cd D:/codefield/AndroidStudioProjects/MyApplication
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.11.9-hotspot" ./gradlew compileDebugKotlin

# Clean build
JAVA_HOME="..." ./gradlew clean compileDebugKotlin

# Full debug APK
JAVA_HOME="..." ./gradlew assembleDebug

# Get SHA1 fingerprint for Amap console
JAVA_HOME="..." ./gradlew signingReport
```

**Note:** The project's `JAVA_HOME` env var is misconfigured (includes `\bin`). Always override it in the command.

---

## Hardware & Environment

### Tracked Device — OPPO Enco X3 (TWS)

| Earbud | MAC Address | Source |
|--------|-------------|--------|
| Left (左耳) | `40:72:18:C5:9F:12` | Captured via BT scanner, hardcoded in `MainActivity.kt:60` |
| Right (右耳) | `40:72:18:C5:68:63` | Captured via BT scanner, hardcoded in `MainActivity.kt:61` |

Both MACs are seeded into `WhitelistManager.add()` on every `MainActivity.onCreate()`.

### Amap SDK Credentials

| Field | Value |
|-------|-------|
| API Key | `c4f996b81bd85df363de54a30ae8289d` |
| Storage | `local.properties` (`AMAP_KEY=...`) |
| gitignore | ✅ `local.properties` is in `.gitignore` — **absolutely never pushed** |
| Package binding | `com.tom_crayon.lastseen` (set in Amap console) |

### Debug Signing SHA1

```
9E:7D:F7:C8:1C:C2:7E:E5:30:3D:2B:3C:2E:D0:1C:62:DF:B7:7F:FF
```
- Source: default `debug.keystore` (`~/.android/debug.keystore`)
- Must match the SHA1 registered in the [Amap console](https://console.amap.com/dev/key/app) for this API key
- Error code 7 at runtime = SHA1 mismatch

**Security Policy:** The `AMAP_KEY` is locked in `local.properties` and `.gitignore` blocks it from ever reaching version control. Anyone cloning the repo must supply their own Amap key. The debug SHA1 above is safe to commit because it's derived from the public default Android debug keystore.

---

## Architecture

Single-Activity app with a background foreground service. No DI framework — manual `ViewModelFactory`, direct `(application as LastSeenApplication).dao` access.

### Data flow: Bluetooth disconnect → location → storage → UI

```
BluetoothReceiver (manifest, BOOT_COMPLETED)
  → TrackerForegroundService.start()
    → dynamic ACL_DISCONNECTED receiver
      → handleDisconnectConfirmed()
        → AMapTracker.resolve()        // dual-location: cached + Amap high-precision
        → WifiSniffer.sniff()          // SSID/BSSID + SceneLabelManager
        → dao.insert(DisconnectRecord) // Room write
        → showAlertNotification()      // PendingIntent with record_id → Deep Link
```

### Key component responsibilities

- **LastSeenApplication** — Room singleton (`database`/`dao`), unified notification channel (`CHANNEL_ID`), Amap privacy compliance calls
- **BluetoothReceiver** — Manifest-registered seed receiver for `BOOT_COMPLETED`. Also statically registered for `ACL_DISCONNECTED`/`ACL_CONNECTED` as HyperOS fallback
- **TrackerForegroundService** — `foregroundServiceType=connectedDevice`. Dynamically registers ACL receivers. Runs location + WiFi + Room pipeline on `serviceScope` (SupervisorJob + Dispatchers.IO). Self-stops after 5 min idle
- **AMapTracker** — `resolve()` runs cached `getLastKnownLocation()` (instant) + Amap `AMapLocationClient` (15s timeout, `isWifiScan=true`, `Hight_Accuracy`). Returns best result
- **MainViewModel** — `recordsState: StateFlow` from Room Flow, `selectedRecord` drives map camera/marker, `focusOnRecord(id)` for Deep Link
- **MainScreen** — `BottomSheetScaffold` + `AndroidView(MapView)` + `LazyColumn` with `SwipeToDismissBox`

### Notification channel

Single unified channel: `LastSeenApplication.CHANNEL_ID = "lastseen_core_channel"` (IMPORTANCE_HIGH, VISIBILITY_PUBLIC). All notifications (foreground service, alert, test) use this one channel.

### Deep Link flow

`showAlertNotification()` → `Intent.putExtra("record_id", id)` → `PendingIntent` → `MainActivity.onCreate()`/`onNewIntent()` → `handleDeepLink()` → waits for Room data → `viewModel.focusOnRecord(id)` → map camera animates

## Key Files

| File | Purpose |
|------|---------|
| `gradle/libs.versions.toml` | Version catalog — all dependency versions here |
| `app/build.gradle.kts` | Manifest placeholders for Amap key, KSP for Room |
| `local.properties` | `AMAP_KEY` — Amap API key (gitignored) |
| `data/DisconnectRecord.kt` | Room entity — 12 fields including wifi_ssid/bssid/sceneLabel |
| `data/WhitelistManager.kt` | SharedPreferences MAC whitelist |
| `tracker/SceneLabelManager.kt` | BSSID→label mapping (e.g. "家里") |
| `tracker/AMapTracker.kt` | Dual-location strategy with Amap SDK |
| `res/drawable/ic_launcher_foreground.xml` | Adaptive icon foreground — radar arcs + location pin (derived from `logo.svg`) |
| `res/drawable/ic_launcher_background.xml` | Adaptive icon background — blue gradient `#3AC5FA` → `#0E5CAD` |
| `logo.svg` | Source vector asset (root dir); convert to XML assets via manual porting |

## Dependencies

- **Amap 3D Map SDK** (`com.amap.api:3dmap:10.0.600`) — includes location SDK internally. Do NOT add separate `com.amap.api:location` (causes Duplicate class conflict)
- **Room** — KSP annotation processing (not KAPT)
- **Compose BOM** 2026.02.01 — Material 3
- **Hilt** — declared in version catalog but NOT wired up. Prepared for future use only

## Platform Notes

- Target device: Xiaomi 17 Pro Max, HyperOS 3.0, Android 16
- HyperOS aggressively kills background services. Dual receiver strategy (manifest + dynamic) mitigates this
- Amap key must match the debug SHA1 fingerprint on the Amap platform. Code 7 = SHA1 mismatch
- `ACCESS_NETWORK_STATE` permission is required by Amap SDK (not obvious, causes silent crash if missing)
- DEBUG mode: `RECONNECT_BUFFER_MS = 0L` (no 30s debounce) for immediate testing
