<div align="center">
  <img src="assets/icon.svg" alt="TVLink icon" width="120"/>
  <h1>TVLink</h1>
  <p>Manage your Android TV wirelessly from your phone — no USB, no root.</p>

  <a href="https://github.com/Vinindra/TVLink/releases/latest">
    <img src="https://img.shields.io/github/v/release/Vinindra/TVLink?label=Download&logo=android&color=4CAF50" alt="Download latest release"/>
  </a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-blue?logo=android" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/License-MIT-purple" alt="MIT License"/>
</div>

---

## Download

Grab the latest APK from the [**Releases**](https://github.com/Vinindra/TVLink/releases/latest) page and install it directly on your phone.

> **Note:** You'll need to allow installation from unknown sources.
> Go to **Settings → Security → Install unknown apps** and enable it for your browser or file manager.

---

## Features

| | |
|---|---|
| **Remote Control** | Full D-pad, volume, media keys, and a text-input mode for typing on your TV |
| **File Browser** | Navigate your TV's file system directory by directory |
| **App Manager** | View and manage all installed apps on your TV |
| **APK Installer** | Sideload APK files directly from your phone |
| **Terminal** | Run ADB shell commands with colour-coded output |
| **Device Info** | See hardware specs, RAM, and storage usage at a glance |
| **Material You** | Dynamic colour theming on Android 12+ with System / Light / Dark mode |

---

## Requirements

- **Your phone:** Android 8.0+ (API 26)
- **Your TV:** Android TV / Google TV with **Wireless Debugging** enabled
- **Network:** Both devices on the same Wi-Fi network

### Enable Wireless Debugging on your TV

1. Open **Settings → Device Preferences → Developer options**
   *(If Developer options isn't visible: Settings → Device Preferences → About → click Build several times)*
2. Enable **USB debugging**, then enable **Wireless debugging**
3. Tap **Wireless debugging** to see the IP address and port — enter these in the app to connect

---

## Tech Stack

- **Kotlin** + **Jetpack Compose** — 100% declarative UI
- **Material 3 / Material You** — dynamic colour with light/dark/system modes
- **Dadb** — pure Kotlin ADB over TCP/IP (no `adb` binary required)
- **Hilt** — dependency injection
- **Navigation Compose** — single-activity navigation
- **DataStore** — persistent preferences
- **Coil** — media artwork loading
- **Coroutines + StateFlow** — reactive async state

---

## Project Structure

```
app/src/main/
├── java/com/tvlink/
│   ├── data/
│   │   ├── adb/          # ADB connection, device discovery, repository
│   │   └── prefs/        # DataStore preferences
│   ├── di/               # Hilt modules
│   ├── ui/
│   │   ├── apps/         # App manager
│   │   ├── components/   # Shared composables
│   │   ├── connect/      # Connect tab, Remote, File browser, Device info
│   │   ├── installer/    # APK installer
│   │   ├── navigation/   # NavGraph + routes
│   │   ├── settings/     # Theme picker
│   │   ├── terminal/     # ADB shell terminal
│   │   └── theme/        # Colors, typography, ThemeMode
│   ├── MainActivity.kt
│   └── TVLinkApp.kt
└── res/
```

---

## License

Distributed under the [MIT License](LICENSE).
