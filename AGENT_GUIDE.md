# Route Planner — Agent Guide

**Purpose**: Android app generating sports training routes (running/biking). User picks start point, optional end point, distance → GPX file for tracking device.

---

## Tech Stack

| | |
|-|-|
| Language | Kotlin |
| Android SDK | 24+ (Android 7.0) |
| Maps | osmdroid 6.1.14 (offline, no API key) |
| Routing | **GraphHopper 6.0** — must stay 6.0 (see Dependencies) |
| Build | Gradle 7.5, AGP 7.4.2, Java 11/16 |
| UI | View binding (`ActivityMainBinding`) |

---

## Repository Structure

```
src/main/kotlin/com/routeplanner/
├── MainActivity.kt       # Main UI, map, route generation, GPX export
├── SplashActivity.kt     # First launch, region select, OSM download
├── RouteService.kt       # GraphHopper engine, route gen, GPX write
├── DataManager.kt        # OSM download/storage/deletion, SharedPreferences
├── RegionManager.kt      # 14 European regions with Geofabrik URLs
├── MapManager.kt         # osmdroid wrapper: markers, polylines, zoom
├── MapTouchOverlay.kt    # Tap → lat/lng callback via osmdroid Overlay
└── Route.kt              # Data class: points, distance, hasLoops, toGpx()
res/layout/
├── activity_main.xml     # Map + profile toggle + controls
└── activity_splash.xml   # Region spinner + download progress
```

---

## Build & Install

`gradlew.bat` fails from PowerShell (empty `%CLASSPATH%` expansion). Invoke wrapper JAR directly:

```powershell
& "c:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug
```

Replace `installDebug` with `build` to build only. ADB device must be connected for install.

---

## Dependencies

```kotlin
implementation("com.graphhopper:graphhopper-core:6.0")   // MUST stay 6.0 — see note
implementation("org.osmdroid:osmdroid-android:6.1.14")
implementation("com.google.android.gms:play-services-maps:18.1.0")  // LatLng only
implementation("org.slf4j:slf4j-api:1.7.36")
implementation("org.slf4j:slf4j-android:1.7.36")
// + standard androidx/material deps
```

**GraphHopper version constraint**: GH 7+ and 8+ require `weighting=custom` for foot/bike profiles. Custom weighting compiles expressions via Janino (JVM bytecode) — **incompatible with Android/ART**. GH 6.0 supports `weighting=fastest` natively.

**Packaging excludes** (required — GH 6.0 transitive deps have META-INF conflicts):
```kotlin
packagingOptions { resources { excludes += setOf(
    "META-INF/LICENSE.md", "META-INF/NOTICE.md",
    "META-INF/LICENSE.txt", "META-INF/NOTICE.txt"
) } }
```

---

## Core Components

### SplashActivity
Checks `DataManager.hasOsmData()`. If missing → show region spinner + download button → download from Geofabrik → launch `MainActivity`.

### MainActivity
`onCreate` sequence:
1. Creates `RouteService`, **disables Generate button**, sets status "Preparing routing engine..."
2. Background `Thread` → `routeService.initializeGraphHopperSync()` → on success enables button
3. Sets up map, touch overlay, tolerance sliders, profile toggle
4. Profile toggle (`MaterialButtonToggleGroup`): **Running** (`foot`, default 10000m) / **Biking** (`bike`, default 20000m). Switching updates distance field.

Key fields: `selectedProfile: String` ("foot"/"bike"), `minTolerance`/`maxTolerance` (meters, default 500).

### RouteService

**Initialization** (`initializeGraphHopperSync()`):
- Deletes `cacheDir/gh/lock` (stale lock from process-kill — Android can skip `onDestroy`)
- Checks `gh_profiles` SharedPreference against fingerprint `"foot_bike_gh6_v1"` — if mismatch, wipes `cacheDir/gh/` entirely and reimports from OSM
- Profiles: `foot` + `bike`, both `weighting="fastest"`, `turnCosts=false`
- First run or after profile change: reimport takes **1–3 min** on device

**Routing** (`generateRoute`):
- `@Volatile graphHopper` — write from init thread, read from coroutine thread
- If `graphHopper == null` when called → returns error immediately (button is disabled during init so this is a safety fallback only)
- `GHRequest` uses `setProfile(profile)` + `putHint("ch.disable", true)`
- If shortest path < target: `extendRoute()` adds perpendicular detours at midpoints
- Returns `Pair<Route?, String?>` via callback on Main thread

**Profile change procedure**: edit profiles list in `initializeGraphHopperSync()` + **bump `profileFingerprint`** string → users' graph auto-rebuilds on next launch.

### DataManager
- OSM file: `cacheDir/osm_data/map.osm.pbf`
- GraphHopper graph: `cacheDir/gh/`
- Preferences: `route_planner_prefs`
- `onProgress` callback dispatched via `withContext(Dispatchers.Main)`
- Download: `URL.openStream()`, 30s timeout, 2× space check, ≥90% size verification

### RegionManager
Static object. 14 European regions. Default: Hungary (`~190MB`). Add regions by appending to `regions` list — appears in spinner automatically.

### MapManager
osmdroid wrapper. Accepts `LatLng`, converts to `GeoPoint` internally. Validates lat ±90, lon ±180, zoom 1–20 on all ops.

### Route
```kotlin
data class Route(val points: List<LatLng>, val distance: Double, val hasLoops: Boolean = false)
```
`toGpx()` → GPX 1.1 XML string.

---

## Key Data Flows

**First launch**
```
SplashActivity → download OSM (~190MB) → MainActivity
→ GraphHopper imports OSM (1-3 min) → Generate button enabled
```

**Route generation**
```
generateRoute(start, end, distMeters, profile, minTol, maxTol)
  → calculateRoute() → GHRequest(profile) → shortest path
  → if dist < minTol: extendRoute() (perpendicular detours)
  → if still out of range: error message with actual shortest distance
  → callback(Route?, error?) on Main thread → displayRoute() → polyline
```

**GPX export**
```
Route.toGpx() → getExternalFilesDir(null)/route_TIMESTAMP.gpx
             → copy to getExternalFilesDir("Downloads")/
```

---

## File Locations Reference

| | Path |
|-|------|
| OSM data | `cacheDir/osm_data/map.osm.pbf` |
| GH graph | `cacheDir/gh/` |
| GH lock | `cacheDir/gh/lock` (deleted on init) |
| GPX files | `getExternalFilesDir(null)/*.gpx` |
| GPX downloads | `getExternalFilesDir("Downloads")/*.gpx` |
| SharedPreferences | `route_planner_prefs` |
| GH profile key | pref `gh_profiles` = `"foot_bike_gh6_v1"` |

---

## Common Modifications

**Add region**: append to `RegionManager.regions` with Geofabrik PBF URL.

**Change routing profiles**: edit profiles in `RouteService.initializeGraphHopperSync()` + bump `profileFingerprint` constant.

**Change default distances**: `binding.etDistance.setText(...)` in `MainActivity.onCreate()` and toggle listener.

**Change tolerance defaults**: `minTolerance`/`maxTolerance` fields in `MainActivity`.

---

## Known Limitations

| Issue | Fix |
|-------|-----|
| Detour logic is perpendicular offset only | Use GH waypoint API with intermediate points |
| No restricted area filtering | Load OSM landuse/amenity tags |
| No elevation support | Add elevation data source |
| No offline tile cache | Pre-bundle or download tile layer |
| Loop detection may miss complex paths | Improve segment intersection algorithm |
| Large regions may OOM | Implement sub-region selection |

---

## MANDATORY FOR ALL AGENTS

Update this guide after each development step. If you change GraphHopper profiles or weighting, **bump `profileFingerprint`** in `RouteService.initializeGraphHopperSync()` — this triggers automatic graph rebuild on device.

**Last Updated**: 2026-05-28
