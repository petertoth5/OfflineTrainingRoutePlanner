# Deployment Checklist Skill

**Purpose**: Comprehensive guide for deploying Route Planner APK to Android devices (physical or emulator) in a Windows/PowerShell environment.

**Quick Start**: Jump to the section matching your scenario:
- [Deploying Debug APK to Physical Device](#deploying-debug-apk-to-physical-device)
- [Deploying Release APK to Physical Device](#deploying-release-apk-to-physical-device)
- [Using Android Emulator](#using-android-emulator)
- [Troubleshooting](#troubleshooting)

---

## Table of Contents

1. [ADB Setup & Verification](#adb-setup--verification)
2. [Device Configuration](#device-configuration)
3. [Physical Device Workflow](#physical-device-workflow)
4. [Emulator Workflow](#emulator-workflow)
5. [APK Installation Commands](#apk-installation-commands)
6. [Deployment Verification](#deployment-verification)
7. [Troubleshooting Flowchart](#troubleshooting-flowchart)
8. [Common Issues & Fixes](#common-issues--fixes)

---

## ADB Setup & Verification

### Install ADB

ADB comes with Android SDK. If not installed separately:

```powershell
# Option 1: Using Android Studio's bundled SDK (recommended)
# Android Studio → Tools → SDK Manager → SDK Tools
# Ensure "Android SDK Platform-Tools" is installed
# Platform-Tools location: C:\Users\<YourUsername>\AppData\Local\Android\sdk\platform-tools

# Option 2: Standalone ADB (if SDK not installed)
# Download from: https://developer.android.com/studio/releases/platform-tools
# Extract to C:\Android\platform-tools (or your preferred location)
```

### Add ADB to PATH (Windows)

Check if ADB is in your PATH:

```powershell
adb --version
# Output: Android Debug Bridge version X.X.X ...
```

If not found, add to PATH:

```powershell
# 1. Open System Properties: Win+X → System → Advanced system settings
# 2. Environment Variables → New User Variable
# Variable name: Path
# Variable value: C:\Users\<YourUsername>\AppData\Local\Android\sdk\platform-tools
# (or wherever you extracted platform-tools)

# Restart PowerShell for changes to take effect
```

### Verify ADB Installation

```powershell
# Check version
adb --version

# Check devices (should show "List of attached devices" even if empty)
adb devices
```

---

## Device Configuration

### Android Phone or Tablet

#### Enable Developer Mode

1. Go to **Settings → About Phone** (or **About Tablet**)
2. Scroll to **Build Number**
3. Tap **Build Number** 7 times until you see "You are now a developer"
4. Back to Settings → **Developer Options** now visible

#### Enable USB Debugging

1. **Settings → Developer Options → USB Debugging** → Toggle ON
2. Accept the "Allow USB Debugging" prompt when connecting to PC

#### Optional: Wireless Debugging (ADB over Network)

```powershell
# Enable on device: Settings → Developer Options → Wireless Debugging → Toggle ON
# On device, note the IP address and port shown in Wireless Debugging settings

# From PowerShell (only after USB connection first to establish trust):
adb connect <device-ip>:<port>
# Example: adb connect 192.168.1.100:5555

# Now you can unplug USB and deploy wirelessly
```

### Android Emulator

#### Create/Launch Emulator

**Using Android Studio**:
1. **Tools → Device Manager → Create device**
2. Select device profile (Pixel 5, Pixel 6, etc.)
3. Select API level (24+ recommended for Route Planner; 34 optimal)
4. Click **Play** to launch

**Using Command Line**:

```powershell
# List available emulator images
emulator -list-avds

# Launch specific emulator
emulator -avd <avd-name>
# Example: emulator -avd Pixel_5_API_34

# Wait for emulator to fully boot (watch for animation to stop)
```

#### Enable ADB on Emulator

Once emulator boots, it automatically appears in `adb devices`:

```powershell
adb devices
# Output should show: emulator-5554 (or similar) device
```

---

## Physical Device Workflow

### Prerequisites

- Phone/tablet connected via USB cable
- USB Debugging enabled (see [Device Configuration](#device-configuration))
- APK built (debug or release)

### Step 1: Connect Device and Verify

```powershell
# Connect device via USB

# Check connection
adb devices

# Expected output:
# List of attached devices
# <device-id> device
# 
# If device shows "offline" or "unauthorized":
#   → See Troubleshooting section below
```

### Step 2: Build APK (if not already built)

**Debug APK** (fastest, for testing):

```powershell
# From project root
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" assembleDebug
# Output: build/outputs/apk/debug/RoutePlanner-debug.apk
```

**Release APK** (requires keystore; see [AGENT_GUIDE.md](../../../AGENT_GUIDE.md)):

```powershell
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" assembleRelease
# Output: build/outputs/apk/release/RoutePlanner-release.apk
```

### Step 3: Install APK

```powershell
# Auto-build + install (recommended)
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug
# Gradle builds and installs to connected device automatically

# OR manually install existing APK
adb install -r build/outputs/apk/debug/RoutePlanner-debug.apk
# -r flag = replace existing app
```

### Step 4: Launch App

```powershell
# Automatic launch (via Gradle)
# installDebug launches the app automatically after install

# Manual launch
adb shell am start -n com.routeplanner/.MainActivity
# or
adb shell am start -n com.routeplanner/.SplashActivity
```

### Step 5: Monitor Logs

```powershell
# View real-time logs (press Ctrl+C to stop)
adb logcat

# Filter logs by app tag (useful to reduce noise)
adb logcat | findstr "RoutePlanner"

# Save logs to file
adb logcat > logs.txt &
# When done, stop with Ctrl+C
```

---

## Emulator Workflow

### Prerequisites

- Android Emulator running (from Android Studio or CLI)
- No USB cable needed (emulator always available as `adb devices`)

### Full Workflow

```powershell
# 1. Launch emulator (if not already running)
emulator -avd Pixel_5_API_34 &

# 2. Wait for boot (watch boot animation)
# Typically 30–60 seconds

# 3. Verify emulator is ready
adb devices
# Should show: emulator-5554    device

# 4. Build and install
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug

# 5. Launch app
adb shell am start -n com.routeplanner/.MainActivity

# 6. View logs
adb logcat
```

### Emulator Tips

- **Multiple Emulators**: If multiple running, specify with `adb -s emulator-5554 <command>`
- **Speed Up**: Set RAM to 4GB+ in emulator settings (Tools → Device Manager → Edit device)
- **Battery Simulation**: Use `adb shell dumpsys batteryproperties` to check emulator battery

---

## APK Installation Commands

### Install APK to Device/Emulator

```powershell
# Install (will fail if app already installed)
adb install path/to/app.apk

# Install + replace existing (recommended)
adb install -r path/to/app.apk

# Install + grant all permissions (rarely needed for Route Planner)
adb install -g path/to/app.apk

# Install + replace + grant permissions
adb install -r -g path/to/app.apk
```

### Uninstall APK

```powershell
# Remove app completely
adb uninstall com.routeplanner

# Remove app + clear data
adb uninstall -k com.routeplanner
```

### Check Installed Apps

```powershell
# List all installed apps
adb shell pm list packages

# Filter to Route Planner
adb shell pm list packages | findstr "routeplanner"
# Output: package:com.routeplanner
```

---

## Deployment Verification

### App Installed Successfully?

```powershell
# Check if app appears in installed packages
adb shell pm list packages | findstr "routeplanner"
# Output: package:com.routeplanner ✓
```

### App Launching Correctly?

```powershell
# Watch for app start
adb logcat | findstr "onCreate\|SplashActivity\|MainActivity"

# Or manually launch and check (output shows PID)
adb shell am start -n com.routeplanner/.MainActivity
```

### Checking Permissions

```powershell
# List all permissions granted to app
adb shell dumpsys package com.routeplanner | findstr "permission"

# Common Route Planner permissions:
# - android.permission.INTERNET (OSM tiles, Geofabrik download)
# - android.permission.ACCESS_FINE_LOCATION (GPS)
# - android.permission.READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE (GPX export, in Android 10 scoped)
```

### Checking App Cache/Data

```powershell
# Clear app data (resets preferences, removes OSM cache)
adb shell pm clear com.routeplanner

# Check cache directory size (on device)
adb shell du -h /data/data/com.routeplanner/

# Important Route Planner paths:
# /data/data/com.routeplanner/cache/osm_data/map.osm.pbf
# /data/data/com.routeplanner/cache/gh/ (GraphHopper graph)
# /data/data/com.routeplanner/shared_prefs/route_planner_prefs.xml
```

---

## Troubleshooting Flowchart

```
┌─────────────────────────────────────────────┐
│ Deployment Issue?                           │
└────────────┬────────────────────────────────┘
             │
             ├─→ Device not appearing in `adb devices`?
             │   └─→ See: Device Not Detected
             │
             ├─→ Device shows "offline" or "unauthorized"?
             │   └─→ See: Offline/Unauthorized Device
             │
             ├─→ Installation fails (Bad install flags, signature mismatch)?
             │   └─→ See: APK Installation Fails
             │
             ├─→ App crashes on launch?
             │   └─→ See: App Crashes on Launch
             │
             ├─→ APK too large / build fails?
             │   └─→ See: Build Issues
             │
             └─→ Other connectivity issue?
                 └─→ See: General Connectivity
```

---

## Common Issues & Fixes

### Device Not Detected

**Symptom**: `adb devices` shows empty list or only emulator.

**Diagnosis**:

```powershell
# Check if device is connected
Get-PnpDevice -Class USB | findstr -i "Android\|Pixel"

# If no output, USB cable not recognized
```

**Fixes**:

1. **Try different USB port** (USB 3.0 sometimes causes issues; try USB 2.0 port)
2. **Check USB cable** (use charging cable, not data cable; or try different cable)
3. **Install USB driver**:
   - Android Studio → Tools → SDK Manager → SDK Tools → Google USB Driver (Windows)
   - Or download from manufacturer (Samsung, etc.)
4. **Restart ADB server**:
   ```powershell
   adb kill-server
   adb start-server
   adb devices
   ```
5. **Check Device Manager** (Windows):
   - Win+X → Device Manager → Android → Right-click device → Update driver
   - Or uninstall and reconnect

---

### Offline / Unauthorized Device

**Symptom**: `adb devices` shows:
```
<device-id> offline
# or
<device-id> unauthorized
```

**Diagnosis**:

```powershell
# Check system log
adb logcat | findstr "unauthorized\|offline"
```

**Fixes**:

1. **Authorize Device** (most common):
   - Unplug USB
   - Reconnect
   - On phone, tap "Allow" on the "Allow USB Debugging?" prompt
   - Verify: `adb devices` → should show `device` (not `unauthorized`)

2. **Clear ADB Server Cache**:
   ```powershell
   # Kill server
   adb kill-server
   # Remove key cache on device: unplug → Settings → Developer Options → Revoke USB debugging authorizations
   # Reconnect and tap Allow
   adb devices
   ```

3. **Wireless Debugging** (if USB causes "offline" after auth):
   ```powershell
   adb disconnect <ip>:<port>
   adb connect <ip>:<port>
   # Re-verify: adb devices
   ```

---

### APK Installation Fails

**Symptom**: `adb install` returns error (e.g., "INSTALL_FAILED_INVALID_APK", "Signature mismatch").

**Diagnosis**:

```powershell
# Check APK exists and is valid
Test-Path build/outputs/apk/debug/RoutePlanner-debug.apk

# Verify APK signature (debug APK should be auto-signed)
keytool -printcert -jarfile build/outputs/apk/debug/RoutePlanner-debug.apk 2>&1 | findstr "Owner"

# Check device storage
adb shell df | findstr "data"
```

**Fixes**:

1. **Rebuild APK** (APK may be corrupted):
   ```powershell
   & "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
     "-Dorg.gradle.appname=gradlew" `
     -jar ".\gradle\wrapper\gradle-wrapper.jar" clean assembleDebug
   ```

2. **Uninstall + Reinstall** (signature conflict):
   ```powershell
   adb uninstall com.routeplanner
   adb install -r build/outputs/apk/debug/RoutePlanner-debug.apk
   ```

3. **Insufficient Storage** (if df shows low data):
   ```powershell
   # Clear cache on device
   adb shell pm clear com.routeplanner
   # Or uninstall bloat apps manually via Settings
   ```

4. **Mismatched Signatures** (release APK signed with different keystore):
   ```powershell
   # Remove old app, install new one
   adb uninstall com.routeplanner
   adb install -r build/outputs/apk/release/RoutePlanner-release.apk
   ```

---

### App Crashes on Launch

**Symptom**: App installs but crashes immediately or shortly after opening.

**Diagnosis**:

```powershell
# View crash logs
adb logcat | findstr "FATAL\|CRASH\|Exception"

# Check memory (Route Planner needs ~400MB for OSM import)
adb shell dumpsys meminfo com.routeplanner | findstr "TOTAL"

# Check manifest
adb shell dumpsys package com.routeplanner | findstr "versionCode\|targetSdk"
```

**Fixes**:

1. **Check Logcat** (most revealing):
   ```powershell
   adb logcat > crash.log
   # Reproduce the crash, then stop (Ctrl+C) and review crash.log
   ```

2. **Insufficient Heap** (GraphHopper needs large heap):
   - Verify `AndroidManifest.xml` has `android:largeHeap="true"`
   - On device with limited RAM (< 2GB), close other apps before launching

3. **Missing OSM Data**:
   - SplashActivity should launch first to download region
   - If it doesn't appear, check network: `adb shell ping 8.8.8.8`

4. **Clear App Data** (if updated from old version):
   ```powershell
   adb shell pm clear com.routeplanner
   # Reinstall and relaunch
   ```

---

### Build Fails: Gradle Issues

**Symptom**: `gradlew assembleDebug` fails partway through.

**Diagnosis**:

```powershell
# Gradle cache may be corrupted
Get-ChildItem -Path "~\.gradle" -Recurse | Measure-Object -Property Length -Sum
```

**Fixes**:

1. **Clean Build**:
   ```powershell
   & "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
     "-Dorg.gradle.appname=gradlew" `
     -jar ".\gradle\wrapper\gradle-wrapper.jar" clean assembleDebug
   ```

2. **Reset Gradle Cache**:
   ```powershell
   # WARNING: This removes all cached dependencies
   Remove-Item -Path ~/.gradle -Recurse -Force
   # Rebuild (will redownload all deps, ~5 min)
   ```

3. **Check Java Version**:
   ```powershell
   java -version
   # Should be Java 11+; Route Planner targets Java 11
   ```

---

### General Connectivity Troubleshooting

**ADB Connection Test**:

```powershell
# Full connectivity check
adb devices -l
# Output:
# List of attached devices
# emulator-5554          device product:sdk_google_phone_armv7 model:Android_SDK_built_for_x86 device:generic_x86
# <device-id> device

# Test device responsiveness
adb shell getprop ro.build.version.release
# Should return Android version (e.g., 12, 13, 14)
```

**Network (if using Wireless Debugging)**:

```powershell
# Check device IP
adb shell getprop dhcp.wlan0.ipaddress

# Test network from device
adb shell ping -c 3 8.8.8.8

# Disconnect wireless
adb disconnect <ip>:<port>
```

---

## Quick Reference: One-Liners

### Deploy to Physical Device (Fastest)

```powershell
# Debug build + install
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug
```

### Deploy to Emulator

```powershell
# (Emulator must be running first)
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug
```

### Monitor Logs

```powershell
adb logcat | findstr "RoutePlanner\|MainActivity\|SplashActivity\|RouteService"
```

### Clear and Reinstall

```powershell
adb uninstall com.routeplanner
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug
```

---

## Related Documentation

- [AGENT_GUIDE.md](../../../AGENT_GUIDE.md) — Full build process, keystore setup, dependencies
- [Android Debug Bridge (ADB) Official Docs](https://developer.android.com/studio/command-line/adb)
- [Android Emulator Docs](https://developer.android.com/studio/run/emulator)
- [Gradle for Android](https://developer.android.com/build)

---

**Last Updated**: 2026-05-29
