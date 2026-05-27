# Route Planner - Agent Implementation Guide

**Purpose**: Enable AI agents to understand, maintain, and extend this Android app.

## Project Overview

**Route Planner** generates optimized sports training routes. Users select start/end points on a map, specify distance in km, and the app generates a GPX file for tracking devices.

**Tech Stack**:
- Language: Kotlin
- Android SDK: 24+ (API 24 = Android 7.0)
- Maps: osmdroid (open source, no API keys)
- Routing: GraphHopper (on-device, offline)
- Build: Gradle 8.1+
- Build UI: ActivityMainBinding (view binding, not XML inflation)

---

## Repository Structure

```
RoutePlanner/
├── build.gradle.kts              # Gradle config, dependencies
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # JVM args, AndroidX flags
├── AndroidManifest.xml           # App manifest, permissions
├── README.md                      # User documentation
├── AGENT_GUIDE.md               # This file
├── src/main/kotlin/com/routeplanner/
│   ├── MainActivity.kt           # Main UI activity, map interaction
│   ├── SplashActivity.kt         # Startup, region selection, OSM download
│   ├── RouteService.kt           # GraphHopper routing engine, route generation
│   ├── DataManager.kt            # OSM data download, storage, SharedPreferences
│   ├── RegionManager.kt          # Region definitions (14 European regions)
│   ├── MapManager.kt             # osmdroid wrapper, marker/polyline rendering
│   ├── MapTouchOverlay.kt        # Custom overlay for tap coordinate detection
│   └── Route.kt                  # Data class: points, distance, GPX export
└── res/
    ├── layout/
    │   ├── activity_main.xml     # Main map + controls UI
    │   └── activity_splash.xml   # Region selector + download progress
    └── values/
        └── strings.xml           # App name string resource
```

---

## Core Components & Responsibilities

### 1. SplashActivity
**File**: `src/main/kotlin/com/routeplanner/SplashActivity.kt`

**Purpose**: First screen user sees. Handles initial OSM data setup.

**Flow**:
1. Check if OSM data exists via `DataManager.hasOsmData()`
2. If exists → launch MainActivity
3. If not → show region spinner + download button
4. On download click:
   - Get selected region from `RegionManager.regions`
   - Call `dataManager.downloadOsmData(region, callbacks)`
   - Show progress bar with percentage
   - On complete → launch MainActivity

**Key Methods**:
- `showRegionSelector()` — display spinner UI
- `downloadSelectedRegion()` — start OSM download
- `showMainContent()` — navigate to MainActivity

**Dependencies**: DataManager, RegionManager

---

### 2. MainActivity
**File**: `src/main/kotlin/com/routeplanner/MainActivity.kt`

**Purpose**: Main app experience. Maps, route generation, GPX export.

**Flow**:
1. Initialize MapManager with osmdroid
2. Setup map click handler (MapTouchOverlay)
3. User taps map twice: start point, end point
4. User enters distance (km)
5. Click "Generate Route" → RouteService.generateRoute()
6. Route displays as polyline
7. Click "Export as GPX" → save to Downloads
8. Settings button → region change, delete OSM data, export GPX files

**Key Methods**:
- `setupMapClickListener()` — attach MapTouchOverlay for taps
- `generateRoute()` — call RouteService, display result
- `displayRoute(route: Route)` — render polyline on map
- `exportRoute()` — save GPX + copy to Downloads
- `openSettings()` — region/data management dialog
- `changeRegion()` — delete data + restart SplashActivity
- `deleteOsmData()` — remove OSM file + download again
- `exportGpxFiles()` — bulk copy all GPX to Downloads

**Lifecycle**: 
- `onCreate()` — init map, set listeners
- `onPause()` — call `mapView.onPause()` (required for osmdroid)
- `onResume()` — call `mapView.onResume()` (required for osmdroid)
- `onDestroy()` — call `routeService.destroy()` (cleanup coroutines)

**Dependencies**: MapManager, RouteService, DataManager

---

### 3. RouteService
**File**: `src/main/kotlin/com/routeplanner/RouteService.kt`

**Purpose**: Offline routing engine. Generates routes using GraphHopper + OSM data.

**Key Responsibilities**:
- Initialize GraphHopper with downloaded OSM data
- Query shortest path between start/end points
- Extend route if too short (add detours)
- Detect loops (segment intersection checks)
- Export routes as GPX XML

**Architecture**:
- Uses `foot` profile (avoids highways, pedestrian-friendly)
- Coroutine-based (`CoroutineScope(Dispatchers.Default)`)
- Non-blocking callbacks for UI updates

**Route Generation Algorithm**:
1. Query shortest path via GraphHopper.route()
2. If distance ≈ target (within ±500m) → return
3. If too short → extend by adding perpendicular detours at midpoints
4. Distance calculation uses Haversine formula
5. Loop detection via segment intersection (CCW algorithm)

**Key Methods**:
- `generateRoute(start, end, distance, callback)` — main entry, async
- `calculateRoute(start, end, target)` — core routing logic
- `extendRoute()` — add detours for short routes
- `detectLoops()` — check for path self-intersections
- `haversineDistance()` — calculate lat/lng distance
- `exportGpx()` — write Route to GPX XML file
- `destroy()` — cleanup coroutines

**Data Format - Route.toGpx()**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata>
    <name>Route Planner</name>
    <time>EPOCH_MS</time>
  </metadata>
  <trk>
    <name>Generated Route</name>
    <trkseg>
      <trkpt lat="48.1234" lon="16.5678"></trkpt>
      ...
    </trkseg>
  </trk>
</gpx>
```

**Dependencies**: DataManager (OSM data location), GraphHopper library

---

### 4. DataManager
**File**: `src/main/kotlin/com/routeplanner/DataManager.kt`

**Purpose**: Manage OSM data lifecycle (download, storage, deletion, persistence).

**Storage**:
- OSM data file: `context.cacheDir/osm_data/map.osm.pbf`
- GraphHopper index: `context.cacheDir/gh/`
- Preferences: SharedPreferences `route_planner_prefs`

**Key Methods**:
- `hasOsmData(): Boolean` — check if file exists
- `downloadOsmData(region, callbacks)` — download from Geofabrik, track progress
- `deleteOsmData(): Boolean` — remove OSM file
- `getCurrentRegion(): Region` — read from SharedPreferences
- `setCurrentRegion(region)` — save to SharedPreferences
- `getDataSizeKb(): Long` — file size for display

**Download Implementation**:
- Uses `URL.openStream()` with 8KB buffer
- Tracks progress: `(downloaded * 100) / fileLength`
- Runs on `Dispatchers.IO` (background thread)
- Calls back on `Dispatchers.Main` for UI updates

**Error Handling**:
- Try/catch around download + file I/O
- Returns null on error
- Dialog shows error message

**Dependencies**: RegionManager (for region names + URLs)

---

### 5. RegionManager
**File**: `src/main/kotlin/com/routeplanner/RegionManager.kt`

**Purpose**: Central registry of downloadable OSM regions.

**Data Structure**:
```kotlin
data class Region(
    val name: String,       // "Hungary", "Austria", etc
    val url: String,        // Geofabrik PBF URL
    val sizeApprox: String  // "~190MB" display
)

object RegionManager {
    val regions = listOf(
        Region("Hungary", "https://download.geofabrik.de/europe/hungary-latest.osm.pbf", "~190MB"),
        Region("Austria", ...),
        ...
    )
}
```

**Key Methods**:
- `getRegionByName(name)` — lookup by name
- `getDefaultRegion()` — returns Hungary region

**Extending Regions**:
To add a new region:
1. Find URL on [Geofabrik](https://download.geofabrik.de/)
2. Add to `RegionManager.regions` list
3. Appears in SplashActivity spinner automatically

**Dependencies**: None (data only)

---

### 6. MapManager
**File**: `src/main/kotlin/com/routeplanner/MapManager.kt`

**Purpose**: Wrapper around osmdroid MapView. Simplifies marker + polyline operations.

**osmdroid Setup**:
```kotlin
Configuration.getInstance().userAgentValue = "RoutePlanner/1.0"
mapView.setTileSource(TileSourceFactory.MAPNIK)  // OpenStreetMap Mapnik tiles
mapView.setMultiTouchControls(true)               // Zoom + pan gestures
```

**Key Methods**:
- `addMarker(latLng, title): Marker` — place marker, add to overlays
- `removeMarker(marker)` — remove from overlays
- `clear()` — remove all overlays (map, markers, polylines)
- `addPolyline(points, color, width)` — render route line
- `centerOnPoint(latLng, zoomLevel)` — pan + zoom

**Coordinate Conversion**:
- osmdroid uses `GeoPoint(lat, lon)`
- App uses `LatLng(lat, lon)` (from Google Maps model)
- MapManager converts automatically

**Dependencies**: osmdroid library

---

### 7. MapTouchOverlay
**File**: `src/main/kotlin/com/routeplanner/MapTouchOverlay.kt`

**Purpose**: Detect map taps, convert screen coordinates to lat/lng.

**How It Works**:
1. Extend osmdroid `Overlay` class
2. Override `onSingleTapConfirmed(event, mapView)`
3. Use `mapView.projection.fromPixels()` to convert tap coordinates
4. Pass lat/lng to callback

**Usage in MainActivity**:
```kotlin
val touchOverlay = MapTouchOverlay(this) { latLng ->
    // Handle tap at latLng
}
mapView.overlays.add(touchOverlay)
```

**Dependencies**: osmdroid library

---

### 8. Route
**File**: `src/main/kotlin/com/routeplanner/Route.kt`

**Purpose**: Data model for generated route. Immutable.

**Properties**:
- `points: List<LatLng>` — waypoints from start to end
- `distance: Double` — total distance in meters
- `hasLoops: Boolean` — whether route self-intersects

**Methods**:
- `toGpx(): String` — serialize to GPX XML

**Note**: Removed `toPolylineOptions()` when switching from Google Maps to osmdroid.

**Dependencies**: None (pure data class)

---

## Key Data Flows

### Flow 1: Initial Setup (First Launch)
```
SplashActivity.onCreate()
  ├─ DataManager.hasOsmData() → false
  ├─ showRegionSelector()
  │  └─ Spinner filled with RegionManager.regions
  └─ User selects region + clicks Download
       ├─ DataManager.downloadOsmData(region)
       │  ├─ URL.openStream() from region.url
       │  ├─ Write to context.cacheDir/osm_data/map.osm.pbf
       │  ├─ Call onProgress callback with % (UI updates)
       │  └─ Store region name in SharedPreferences
       └─ launchMainActivity() → MainActivity
```

### Flow 2: Route Generation
```
MainActivity.generateRoute()
  ├─ Get start/end markers + distance input
  ├─ RouteService.generateRoute(start, end, distance, callback)
  │  └─ Background coroutine:
  │     ├─ DataManager.getOsmDataFile() → /cache/osm_data/map.osm.pbf
  │     ├─ GraphHopper.route(GHRequest) → shortest path
  │     ├─ If distance ≈ target ± 500m → return route
  │     ├─ Else extend via detours + recalculate distance
  │     ├─ detectLoops(path)
  │     └─ callback(Route) on main thread
  └─ displayRoute(route)
     ├─ mapManager.clear()
     ├─ mapManager.addPolyline(route.points)
     └─ Enable btnExport
```

### Flow 3: GPX Export
```
MainActivity.exportRoute()
  ├─ Route.toGpx() → XML string
  ├─ RouteService.exportGpx(route, filename)
  │  └─ Write to context.getExternalFilesDir(null) + timestamp filename
  ├─ copyToDownloads(file)
  │  └─ Copy to context.getExternalFilesDir("Downloads")
  └─ Toast user with file path
```

### Flow 4: Region Change
```
MainActivity.openSettings() → user selects "Change Region"
  ├─ AlertDialog with region list
  ├─ Confirmation: "Delete current OSM data?"
  ├─ DataManager.deleteOsmData() → remove /cache/osm_data/map.osm.pbf
  ├─ startActivity(SplashActivity)
  └─ finish() → back to region selection
```

---

## Build & Run

### Prerequisites
```bash
Android SDK API 24+
Kotlin 1.9+
Gradle 8.1+
```

### Build
```bash
./gradlew build        # Assemble APK
./gradlew installDebug # Build + install on emulator/device
```

### Run on Emulator
```bash
# Create AVD with API 24+
android avd create -n test24 -t android-24 -k default
emulator -avd test24 &
./gradlew installDebug
```

### Run on Device
```bash
# Enable USB Debugging on phone
adb devices  # Verify phone appears
./gradlew installDebug
```

---

## Dependencies & Why

| Dependency | Version | Why |
|------------|---------|-----|
| androidx.appcompat | 1.6.1 | Android compatibility library |
| androidx.constraintlayout | 2.1.4 | Flexible layout system |
| com.google.android.material | 1.9.0 | Material Design UI components |
| org.osmdroid | 6.1.14 | **Open source maps, no API keys** |
| com.graphhopper:graphhopper-core | 8.0 | **On-device routing engine** |
| com.graphhopper:graphhopper-reader-osm | 8.0 | OSM data parsing for GraphHopper |
| org.slf4j:slf4j-api | 2.0.9 | Logging framework (required by GraphHopper) |
| org.slf4j:slf4j-android | 2.0.9 | Android logging adapter |

### Why osmdroid over Google Maps?
- ✅ No API key required
- ✅ Free, open source
- ✅ Local tile caching
- ✅ Works completely offline after download

### Why GraphHopper?
- ✅ On-device (no cloud API)
- ✅ Fully offline after OSM download
- ✅ Supports routing profiles (foot, bike, car)
- ✅ `foot` profile avoids highways

---

## Configuration Points

### 1. Default Region
**File**: `RegionManager.kt`
```kotlin
fun getDefaultRegion(): Region = regions.find { it.name == "Hungary" } ?: regions.first()
```
Change region name to default a different region.

### 2. Distance Tolerance
**File**: `RouteService.kt`
```kotlin
private fun isWithinTolerance(distance: Double, target: Double): Boolean {
    val tolerance = 500 // meters ← CHANGE HERE
    return abs(distance - target) <= tolerance
}
```

### 3. Map Tile Source
**File**: `MapManager.kt`
```kotlin
mapView.setTileSource(TileSourceFactory.MAPNIK)  // ← Change to other sources
```
Options: MAPNIK, STAMEN_TONER, STAMEN_TERRAIN, OPEN_ANDROMAPS, etc.

### 4. Routing Profile
**File**: `RouteService.kt`
```kotlin
profile = "foot"  // ← Change to "bike", "car", "wheelchair", etc.
```

---

## Testing Checklist for Agents

When updating this app, test these flows:

- [ ] **First Launch**: App shows region spinner, can download Hungary (~190MB)
- [ ] **Map Interaction**: Can tap to place start + end markers
- [ ] **Route Generation**: Can enter distance, generate route, see polyline
- [ ] **Distance Tolerance**: Routes within ±500m of target work correctly
- [ ] **Loop Detection**: Routes don't have obvious self-intersecting paths
- [ ] **GPX Export**: Exported GPX files valid (can open in text editor, see trkpts)
- [ ] **Region Change**: Can delete OSM data + download new region
- [ ] **Error Handling**: App doesn't crash on bad input, shows toasts
- [ ] **Offline**: After initial download, works completely offline
- [ ] **Lifecycle**: Pausing/resuming app doesn't crash map

---

## Common Modification Patterns

### Add a New Region
1. Find URL on [Geofabrik Downloads](https://download.geofabrik.de/)
2. Add to `RegionManager.regions` list
3. Appears automatically in spinner

### Change Distance Tolerance
1. Edit `RouteService.kt` → `isWithinTolerance()` → `tolerance = 500`
2. Rebuild + test with various distances

### Improve Route Extension Logic
1. Current: Adds simple perpendicular detours
2. Better: Use GraphHopper waypoint API to route through intermediate points
3. Edit: `RouteService.extendRoute()` method

### Add Restricted Area Filtering
1. Load OSM amenity/landuse tags (military, private, etc.)
2. Implement `isRestrictedArea(lat, lon)` properly
3. Filter route points in `displayRoute()`
4. Currently returns `false` (no filtering)

### Switch Back to Google Maps
1. Replace osmdroid dependency with Play Services Maps
2. Revert MapManager to GoogleMap API
3. Update MainActivity.setupMapClickListener() for Google Maps
4. Add Google Maps API key to AndroidManifest.xml
5. Update layout activity_main.xml to use SupportMapFragment

---

## Known Limitations & Future Work

| Issue | Impact | Fix |
|-------|--------|-----|
| Detour logic is simple | Routes may not be optimal | Use GraphHopper waypoint API with multiple intermediate points |
| No restricted areas | May route through military/private | Load OSM landuse/amenity tags, check during routing |
| No elevation profile | Flat routes only | Add elevation data, use for routing preference |
| Download size large | Slow initial setup | Implement region sub-division (state-level instead of country) |
| No offline tile cache | Maps blank without tile download | Pre-bundle tiles or use offline tile layer |
| Loop detection basic | May miss complex loops | Improve algorithm or use GraphHopper path validation |

---

## File Locations Reference

| Purpose | Path |
|---------|------|
| OSM Data | `context.cacheDir/osm_data/map.osm.pbf` |
| GraphHopper Index | `context.cacheDir/gh/` |
| GPX Export | `context.getExternalFilesDir(null)/*.gpx` |
| GPX Downloads | `context.getExternalFilesDir("Downloads")/*.gpx` |
| SharedPreferences | `route_planner_prefs` |

---

## Glossary

| Term | Meaning |
|------|---------|
| **OSM** | OpenStreetMap — free, editable world map data |
| **PBF** | Protocol Buffer Format — compressed OSM data |
| **GPX** | GPS Exchange Format — XML for GPS tracks/routes |
| **Geofabrik** | OSM data distributor, provides regional PBF files |
| **GraphHopper** | Open source routing engine, uses OSM data |
| **Haversine** | Formula for great-circle distance between two lat/lng points |
| **Overlay** | osmdroid term for renderable map objects (markers, polylines, etc.) |
| **TileSource** | Map tile provider (Mapnik, Stamen, etc.) |
| **Coroutine** | Kotlin lightweight thread, used for async operations |
| **View Binding** | Android feature: type-safe XML layout access |

---

## References

- [osmdroid Docs](https://osmdroid.github.io/)
- [GraphHopper Docs](https://graphhopper.com/api/1/docs/)
- [OpenStreetMap](https://www.openstreetmap.org/)
- [Geofabrik Downloads](https://download.geofabrik.de/)
- [GPX Specification](https://www.topografix.com/gpx.html)
- [Android Docs](https://developer.android.com/)

---

**Last Updated**: 2026-05-27
**For Agents**: This guide enables full understanding + modification. Reference section numbers when asking questions.
