# Route Planner

Sports route planning app. Users select start/end points on OpenStreetMap, specify desired distance, app generates optimized GPX route for tracking devices.

**No API keys required** — uses free osmdroid + OpenStreetMap tiles.

## Setup

### Prerequisites
- Android SDK 24+
- Kotlin 1.9+
- Gradle 8.1+

## Building

```bash
./gradlew build
./gradlew installDebug
```

## Release

### Steps to create a release

**1. Prepare main branch**
```powershell
git branch
git pull origin main
```

**2. Version bump**
```powershell
# Edit build.gradle.kts: increment versionCode and versionName
git add build.gradle.kts
git commit -m "bump version to X.Y.Z"
git push origin main
```

**3. Build signed APK**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat clean assembleRelease
```

**4. Verify APK**
```powershell
# APK output: build/outputs/apk/release/RoutePlanner-release.apk
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat verifySigning -PapkPath="build/outputs/apk/release/RoutePlanner-release.apk"
```

**5. Create GitHub release**
```powershell
gh release create vX.Y.Z `
  --title "vX.Y.Z" `
  --notes "Release notes here" `
  build/outputs/apk/release/RoutePlanner-release.apk
```

Or manually via GitHub web UI:
- Releases → Draft new release
- Tag: `vX.Y.Z`
- Title: `vX.Y.Z`
- Description: release notes
- Upload APK as asset
- Publish

## Architecture

- **MainActivity** — osmdroid map UI, marker placement
- **RouteService** — GraphHopper routing, GPX generation
- **DataManager** — OSM data download on first launch (14 regions)
- **MapManager** — osmdroid wrapper
- **SplashActivity** — Region selector + download progress

## Features

- Tap map to select start/end points (osmdroid, no API key)
- 14 European regions to choose from (Hungary default)
- Specify distance in km
- Auto-generates route avoiding highways
- Loop detection
- ±500m distance tolerance
- GPX export to Downloads folder
- Delete OSM data
- Change region anytime

## Limitations

- Route extension via detours is basic (can be improved)
- Restricted area filtering needs OSM tag data integration
- osmdroid tiles cached locally (disk space required)
