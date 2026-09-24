# SAMEUO Dashcam (Android)

Official companion Android app for SAMEUO dashcams / action cameras. It connects
to the camera over its own Wi-Fi access point, provides a **live view**, lets you
**browse / search / download / share / delete** files on the memory card, and
exposes the full **camera & app settings**.

> Brand: **SAMEUO**. The page flow follows the product flow reference (the
> reference mock was branded "TOYOTA"; all in-app branding here is SAMEUO).

## Features

- Splash → Safety warning → guided Wi-Fi connection → camera model selection (GEN4 / GEN3)
- Home: camera card (SSID + Connected), status grid (Battery / SD / Free Space / Recording), LIVE VIEW, Disconnect
- Memory Card tab: 5 category tiles (All / Photo / Front / Rear / Emergency) with counts, search
- Phone Memory tab: downloaded files (app-private storage, no runtime permission needed)
- Search: Video / Emergency / Photo filters + From / To date range (Material3 DatePicker)
- Media detail: ExoPlayer for video (progressive HTTP from device, local file on phone),
  pinch-to-zoom photos (1–5×), prev/next, download overlay, delete confirmation
- Share via FileProvider (device files are downloaded first if needed)
- LIVE VIEW: RTSP with automatic candidate fallback, capture, record toggles
- Settings: Video/Photo, Emergency (G-sensor, Parking monitor), Device (Wi-Fi SSID,
  Format card, Firmware update, Device language), App (Appearance, App language),
  Support (About, User manual)
- Light / Dark / Automatic theming; app language preference

## Tech stack

- Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.02), Material 3
- Media3 / ExoPlayer 1.4.1 (incl. RTSP), Coil 2.7.0
- OkHttp 4.12.0, DataStore 1.1.1, Navigation Compose 2.8.0
- Hand-written ServiceLocator (no Hilt / Room / KSP / kapt / appcompat)
- Gradle 8.9, AGP 8.5.2, JDK 17; compile/target SDK 34, min SDK 24

## Build

Requirements: JDK 17, Android SDK (platform `android-34`, build-tools `34.0.0`).

```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Unit tests
./gradlew testDebugUnitTest
```

Point the SDK at your machine via `local.properties`:

```
sdk.dir=/path/to/Android/Sdk
```

## Install on a device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then power on the camera, join its Wi-Fi network (e.g. `DVR_4f12`) from the phone,
open the app, accept the safety warning and follow the connection guide.

## Device protocol (Novatek)

Gateway `http://192.168.1.254/`, commands `/?custom=1&cmd=<id>[&par=<enum>|<str>]`
returning XML (`<Function><Cmd><Status>`). File serving uses the HFS-style path,
e.g. `A:\NOVATEK\MOVIE/x.MOV` → `http://host/A:/NOVATEK/MOVIE/x.MOV`.
See `app/src/main/java/com/sameuo/dashcam/data/protocol/WifiCmd.kt`.

## Repository note

`gradle/wrapper/gradle-wrapper.jar` and the binary launcher PNG icons are binary
artifacts. They are present in local builds; if a distribution was synced over a
text-only channel they may be missing — regenerate with `gradle wrapper
--gradle-version 8.9`, and the adaptive (XML) icons still apply on API 26+.

## License

Proprietary — © SAMEUO. All rights reserved.
