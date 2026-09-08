# SAMEUO Dashcam (Android)

Professional companion app for SAMEUO Wi-Fi dashcams / motorcycle cameras, built
around the **Novatek (联咏) Gen3 Wi-Fi command protocol**. It connects to the
camera's own Wi-Fi access point and provides live preview, remote & local album
management with resumable downloads, full device settings, firmware OTA and
offline GPS-ride analysis.

> Scope of this repository: **Android first** (Kotlin + Jetpack Compose, Material 3,
> single-Activity, MVVM). An iOS port reuses the documented protocol layer.

---

## 1. Feature set

| Area | Phase 1 (this build) | Phase 2 (planned) |
|---|---|---|
| Connection | Join camera AP, process-network binding, ~3 s auto connect, heartbeat/auto-reconnect, recent devices | Bluetooth-assisted provisioning |
| Live view | Media3 RTSP live stream with multi-path fallback, record start/stop, snapshot, dual-cam PIP cycle | Low-latency custom decoder, audio talk-back |
| Remote album | File list (video / emergency / photo / front-rear), grid thumbnails, multi-select, single & batch **delete** | In-app remote trim |
| Downloads | Foreground-service queue, **HTTP Range resumable** downloads, progress notification, browse while downloading, offline local album | Parallel chunks, background Wi-Fi-only policy |
| Player | One player for RTSP / on-device HTTP progressive streams / downloaded files, seek bar | Front+rear PiP playback, telemetry overlay |
| Device settings | Full menu schema (cmd 3031) rendered dynamically, time sync, format, factory reset | Per-model curated menus |
| Firmware | OTA: descriptor → target path → streaming multipart upload → MD5 verify | Cloud firmware catalogue |
| Ride (GPS) | Extract embedded **LIGOGPSINFO** track from downloaded MP4/MOV, distance / top & average speed, route canvas | Map tiles, GPX export, trip list |
| Create | Local-clip hub | Auto highlight reel (G-sensor + speed), vertical 9:16 reframe, telemetry burn-in |
| Me | Dark-first theme, 10-language switcher, download monitor, about | Cloud backup of settings |

### Why it differs from Viidure (录风者)
- **Dark-first, large touch targets (≥48 dp)** for gloved / handlebar use in sunlight.
- **Downloads never block the UI** (Viidure forces you to stay on the download page);
  interrupted 4K transfers **resume via HTTP Range** instead of restarting.
- **Offline GPS ride analysis** decoded straight from the Novatek `gps ` MP4 box — no
  cloud upload, no subscription.
- Clean chip-abstraction so 10 SoC platforms (Novatek / Allwinner / SigmaStar …) slot
  in behind one `DeviceClient` contract instead of forking the app.

---

## 2. Tech stack

- Kotlin 2.0.20, AGP 8.5.2, Gradle 8.9, JDK 17
- minSdk 24 · target/compileSdk 34
- Jetpack Compose (BOM 2024.09), Material 3, Navigation-Compose, Lifecycle
- Media3 1.4.1 (ExoPlayer + **exoplayer-rtsp** + UI)
- OkHttp 4.12 (command / HFS download / multipart OTA)
- Coil available for image loading; DataStore Preferences for settings
- SQLite via `SQLiteOpenHelper` (saved devices + downloaded-media index)
- **Deliberately no Hilt/Dagger/Room/KSP/kapt** — a hand-rolled `ServiceLocator`
  keeps the build green on a clean toolchain with zero annotation processors.

## 3. Architecture

Single-Activity MVVM; unidirectional `StateFlow` from repositories to Compose.

```
ui (Compose screens + ViewModels, StateFlow<UiState>)
        │
data.repository   ConnectionRepository · DeviceRepository · LocalMediaRepository
        │
data.protocol     DeviceClient (chip-agnostic)
                   └─ NovatekDeviceClient  ── DeviceHttpClient (OkHttp)
                       └─ parser/NovatekXmlParser   (Function / LIST / menu / firmware)
                   chip/ProtocolFactory + ChipPlatform (10 platforms)
                   notify/NotifySocketClient (TCP 3333 device-push events)
data.gps          LigoGpsParser (3 encodings) · Mp4GpsExtractor (moov›'gps ' box)
data.download     DownloadEngine (queue + resume) · DownloadService (foreground)
data.firmware     FirmwareUpdater (OTA state machine)
data.local        db/SameuoDatabase (SQLite) · prefs/AppSettings (DataStore)
data.connectivity DeviceWifiManager (bind process to camera network)
di                ServiceLocator
core              Outcome/Cause · AppLog
```

---

## 4. Device protocol (SAMEUO Gen3 / Novatek)

- Camera AP gateway: **`192.168.1.254`**, command/HFS on port **80**, MJPG photo
  preview on **8192**, device-push events on **TCP 3333**.
- Command: `http://192.168.1.254/?custom=1&cmd=<id>[&par=<enum> | &str=<value>]`
- Response XML: `<Function><Cmd>..</Cmd><Status>0</Status>[<Value>/<String>]..</Function>`
  (`Status 0 = OK`).
- Live view: RTSP `rtsp://192.168.1.254/live_rtsp` (several fallback paths tried);
  files are served by the on-device HFS server (device path `A:\NOVATEK\MOVIE\x.MOV`
  → `http://192.168.1.254/A:/NOVATEK/MOVIE/x.MOV`).

Key command ids (full catalogue in `data/protocol/WifiCmd.kt`):

| Cmd | Meaning | Cmd | Meaning |
|----:|---|----:|---|
| 1001 | Capture photo | 3012 | Firmware/version |
| 2001 | Record on/off | 3015 | File list (needs playback mode 3001=2) |
| 2002 | Resolution | 3016 | Heartbeat |
| 2015 | Live-view on/off | 3017 | Free space |
| 2017 | Snapshot in preview | 3019 | Battery |
| 3001 | Mode (0 photo/1 movie/2 playback) | 3024 | Card status |
| 3003/3004 | SSID / passphrase | 3025/3026 | FW descriptor / upload path |
| 3005/3006 | Date / time | 3028 | Dual-cam PIP style |
| 3010 / 3011 | Format / factory reset | 3031 | Full menu schema |
| 3035/3036 | APP session enter/exit | 4001/4002 | Thumbnail / screen-nail |
| 3037 | Operation-mode query | 4003/4004 | Delete one / delete all |
| 5001 | Firmware upload | | |

Authoritative sources used during implementation are kept under
`../需求资料/协议参考/` (Gen3 Set-menu CMD, NT9666x Wi-Fi Command User Guide, gps.js).

---

## 5. Build

Requirements: JDK 17, Android SDK Platform 34, Build-Tools 34.0.0.

```bash
# Debug APK
./gradlew :app:assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# JVM unit tests (XML protocol parser, GPS decoder, endpoints)
./gradlew :app:testDebugUnitTest

# Release (unsigned; configure signing as needed)
./gradlew :app:assembleRelease
```

> If `gradle/wrapper/gradle-wrapper.jar` is missing after a text-only clone, run
> `gradle wrapper --gradle-version 8.9` once (with JDK 17) to regenerate it; the
> wrapper scripts and `gradle-wrapper.properties` are already in the tree.

Open the folder in Android Studio (Koala+ / any AGP 8.5-compatible IDE).
`local.properties` with `sdk.dir=...` is required for command-line builds and is
git-ignored.

### On-device test loop
1. Power the SAMEUO camera; join its Wi-Fi hotspot on the phone.
2. Launch app → **Device** → choose chipset (Novatek) → **Connect**.
3. Grant Nearby-Wi-Fi / Location / Notifications permissions when prompted.
4. Live preview, album, settings and OTA become available on the bottom tabs.

---

## 6. Project layout

```
app/src/main/java/com/sameuo/dashcam/
├─ MainActivity.kt · SameuoApp.kt
├─ core/                 # Outcome, logging
├─ data/
│  ├─ protocol/          # Wi-Fi commands, HTTP, Novatek client, XML parser, chip factory, notify
│  ├─ gps/               # LIGOGPSINFO parser + MP4 box extractor
│  ├─ local/             # SQLite DB + DataStore settings
│  ├─ download/          # queue engine + foreground service
│  ├─ firmware/          # OTA
│  ├─ connectivity/      # Wi-Fi network binding
│  ├─ repository/        # Connection / Device / LocalMedia repositories
│  └─ ServiceLocator.kt
└─ ui/
   ├─ theme · components · navigation
   ├─ device · preview · album · player · settings · ride · create · profile
app/src/test/            # host unit tests
```

## 7. Notes & limitations
- Only the **Novatek / Novatek-compatible** dialect is fully implemented; the other
  nine `ChipPlatform` entries currently fall back to it and are marked unsupported
  in the picker until their dedicated adapters are written.
- The Create tab is intentionally labelled Phase 2 rather than shipping fake editing.
- Ride route is drawn on a Canvas (offline, no Maps API key required); tiled maps and
  GPX export are Phase 2.

## 8. License
Proprietary — © SAMEUO. All rights reserved.
