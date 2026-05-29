# Route Planner — Agent Guide

**Purpose**: Android app generating sports training routes (running/biking). User picks start/end on map, sets distance → app generates a GPX route of that length via real road network.

---

## Agent Ecosystem Overview

This project uses a **7-agent + 5-skill ecosystem** to coordinate specialized development work. The ecosystem enables parallel development, clear specialization, and systematic workflow management across algorithm design, UI/UX, implementation, build systems, deployment, and documentation.

**The 7 agents:**
- **Orchestrator Agent** (Claude Opus 4.7) — Entry point; assesses user requests and routes work to specialists
- **Algorithm Developer Agent** (Claude Sonnet 4.6) — Designs routing algorithms and waypoint generation logic
- **UI Designer Agent** (Claude Sonnet 4.6) — Creates Android UI/UX specs and layout improvements
- **Software Developer Agent** (Claude Opus 4.7) — Implements Kotlin code and integrates specifications
- **Build/Integrator Agent** (Claude Haiku 4.5) — Manages Gradle builds, ProGuard rules, and release configuration
- **Deploy Agent** (Claude Haiku 4.5) — Handles APK signing, device testing, and release verification
- **Documentation Agent** (Claude Haiku 4.5) — Maintains AGENT_GUIDE.md and project documentation

**The 5 supporting skills** reside in `.claude/skills/`:
- **Kotlin Development** — Kotlin/Android patterns and best practices
- **Android Build** — Gradle setup, ProGuard configuration, dependency management
- **Algorithm Design** — Algorithm specification and complexity analysis
- **Android UI Design** — Layout patterns, accessibility, Material Design 2
- **Deployment Checklist** — APK signing, device testing, release procedures

### Directory Structure

```
.claude/
├── agents/                          # 7 agent definitions
│   ├── orchestrator-agent.md
│   ├── algorithm-developer-agent.md
│   ├── ui-designer-agent.md
│   ├── software-developer-agent.md
│   ├── build-integrator-agent.md
│   ├── deploy-agent.md
│   └── documentation-agent.md
│
└── skills/                          # 5 skill definitions
    ├── kotlin-development.md
    ├── android-build.md
    ├── algorithm-design.md
    ├── android-ui-design.md
    └── deployment-checklist.md

docs/agents/                         # Agent ecosystem documentation
├── README.md                        # Overview and quick reference
├── orchestrator-workflow.md         # Detailed workflow diagrams and examples
└── agent-capabilities.md            # Detailed capability matrix for each agent
```

For comprehensive documentation, see [`docs/agents/README.md`](docs/agents/README.md).

### Invoking the Orchestrator

**Entry point for all work**: Users should initiate tasks by speaking with the **Orchestrator Agent**, providing a clear description of:
- What you want to accomplish (feature request, bug report, enhancement)
- What's happening now vs. what should happen
- Any device/region-specific context

**Workflow overview:**
```
User Request
    ↓
[Orchestrator: Assess scope & Route]
    ↓
[Specialists: Algorithm Dev → UI Designer → Software Dev → Build → Deploy → Documentation]
    ↓
[Orchestrator: Gather feedback & iterate if needed]
    ↓
User receives completed work + updated documentation
```

**Typical flow for a complete feature:**
1. **Algorithm Developer** designs the algorithm (if needed)
2. **UI Designer** creates the layout specification (if needed)
3. **Software Developer** implements Kotlin code and integrates changes
4. **Build/Integrator** resolves any build issues and prepares release configuration
5. **Deploy Agent** tests on device and prepares signed APK
6. **Documentation Agent** updates AGENT_GUIDE.md and code comments

**Not all steps are always needed.** A simple bug fix might be: Orchestrator → Software Dev → Documentation → done.

For detailed workflow examples, see [`docs/agents/orchestrator-workflow.md`](docs/agents/orchestrator-workflow.md).

### For Future Development

**Extending the agent ecosystem:**

1. **New agents** — Create a new agent definition in `.claude/agents/[agent-name]-agent.md` following the format of existing agents. Update the list above and in `docs/agents/README.md`.

2. **New skills** — Create a new skill in `.claude/skills/[skill-name].md`. Reference it in this guide's skills list above.

3. **Updating after changes** — After any development iteration:
   - The **Documentation Agent** automatically updates AGENT_GUIDE.md
   - Verify all agent definitions in `.claude/agents/` match implementation reality
   - Keep this guide's "Last Updated" timestamp current

4. **Maintaining consistency** — Always reference:
   - `.claude/agents/` for individual agent responsibilities and trigger conditions
   - `docs/agents/` for detailed workflow documentation
   - `AGENT_GUIDE.md` (this file) as the single source of truth for the codebase

5. **Adding constraints or rules** — New mandatory rules should be added to the **MANDATORY FOR ALL AGENTS** section at the end of this guide. Update agent definitions and `docs/agents/` accordingly.

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
├── RouteService.kt       # GraphHopper engine, route gen
├── DataManager.kt        # OSM download/storage/deletion, SharedPreferences
├── RegionManager.kt      # 14 European regions with Geofabrik URLs
├── MapManager.kt         # osmdroid wrapper: markers, polylines, zoom
├── MapTouchOverlay.kt    # Tap → lat/lng callback via osmdroid Overlay
└── Route.kt              # Data class: points, distance, hasLoops, toGpx()
res/
├── layout/activity_main.xml     # Map + profile toggle + controls
├── layout/activity_splash.xml   # Region spinner + download progress
├── drawable/ic_launcher_foreground.xml   # App icon foreground vector
├── drawable/ic_launcher_background.xml   # App icon background (#1565C0)
├── mipmap-anydpi-v26/ic_launcher.xml     # Adaptive icon (API 26+)
├── mipmap-anydpi-v26/ic_launcher_round.xml
└── mipmap-anydpi/ic_launcher.xml         # Legacy layer-list fallback
proguard-rules.pro        # ProGuard keeps for GH, osmdroid, Jackson, Kotlin
AndroidManifest.xml       # android:largeHeap="true" required (see below)
```

---

## Build — Debug (install to device)

`gradlew.bat` fails from PowerShell (empty `%CLASSPATH%` expansion). Invoke wrapper JAR directly:

```powershell
& "c:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" installDebug
```

ADB device must be connected. Replace `installDebug` with `build` to build only.

## Build — Release APK

**One-time keystore setup** (`release.jks` and `keystore.properties` are gitignored — not in repo):

```powershell
# 1. Generate keystore (run once, keep the .jks file safe — losing it = cannot publish updates)
keytool -genkeypair -v -keystore release.jks -alias routeplanner `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass YOUR_PASSWORD -keypass YOUR_PASSWORD `
  -dname "CN=RoutePlanner, O=YourOrg, C=HU"

# 2. Create keystore.properties in project root:
# storeFile=release.jks
# storePassword=YOUR_PASSWORD
# keyAlias=routeplanner
# keyPassword=YOUR_PASSWORD
```

```powershell
# 3. Build signed release APK
& "c:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" assembleRelease
# Output: build/outputs/apk/release/RoutePlanner-release.apk
```

`isMinifyEnabled = false` (safe default — ProGuard rules in `proguard-rules.pro` are ready if minification is needed later).

---

## Dependencies

```kotlin
implementation("com.graphhopper:graphhopper-core:6.0")   // MUST stay 6.0
implementation("org.osmdroid:osmdroid-android:6.1.14")
implementation("com.google.android.gms:play-services-maps:18.1.0")  // LatLng only
implementation("org.slf4j:slf4j-api:1.7.36")
implementation("org.slf4j:slf4j-android:1.7.36")
// + standard androidx/material deps
```

**GraphHopper version constraint — critical**: GH 7+/8+ require `weighting=custom` for foot/bike profiles. Custom weighting compiles expressions via Janino (JVM bytecode compiler) — **incompatible with Android/ART**. GH 6.0 supports `weighting=fastest` natively without Janino.

**Packaging excludes** (required — GH 6.0 transitive deps have META-INF conflicts):
```kotlin
packagingOptions { resources { excludes += setOf(
    "META-INF/LICENSE.md", "META-INF/NOTICE.md",
    "META-INF/LICENSE.txt", "META-INF/NOTICE.txt"
) } }
```

**`android:largeHeap="true"` in manifest — required**: GH import of Hungary (~190MB OSM) peaks at ~300–400MB RAM. Default Android heap (~192MB) causes OOM process kill mid-import.

---

## Core Components

### SplashActivity
Checks `DataManager.hasOsmData()`. If missing → region spinner + download button → Geofabrik download → launch `MainActivity`.

### MainActivity

`onCreate` sequence:
1. Creates `RouteService`, **disables Generate button**, starts 1-second elapsed timer in status text
2. Background Thread → `routeService.initializeGraphHopperSync()` → on success enables button, shows load time
3. Sets up map touch overlay, tolerance sliders, profile toggle, default distance

**UI elements**:
- `MaterialButtonToggleGroup`: **Running** (`foot`, 10000m default) / **Biking** (`bike`, 20000m default) — switching updates distance field
- `seekBarMinTolerance` / `seekBarMaxTolerance`: route can be shorter/longer by this many meters (default 500m each)
- **New Route** button: clears route polyline, both markers, resets all state to "Tap map to select start point"
- **Export as GPX**: opens Android SAF file picker (`ACTION_CREATE_DOCUMENT`) — user browses and picks save location; written via `contentResolver.openOutputStream(uri)`

Key fields: `selectedProfile: String`, `minTolerance: Int`, `maxTolerance: Int`, `saveGpxLauncher` (ActivityResultLauncher).

**Important**: `displayRoute()` calls `mapManager.clearRoute()` (not `mapManager.clear()`) — `clear()` removes the `MapTouchOverlay` and breaks map tapping.

### RouteService

**Initialization** (`initializeGraphHopperSync()`):
- Deletes `cacheDir/gh/lock` — stale lock from process-kill (Android can skip `onDestroy`)
- Checks `gh_profiles` SharedPreference against fingerprint `"foot_bike_gh6_v1"` — if mismatch, wipes `cacheDir/gh/` and saves new fingerprint **before** importing (if saved after and process is killed mid-import, next launch sees mismatch → wipe → infinite restart loop)
- Profiles: `foot` + `bike`, `weighting="fastest"`, `turnCosts=false`
- First run / profile change: reimport takes **5–15 min** on device (one-time)

**Routing algorithm** (`calculateRoute()`):
Priority is route **length**, not shortest path. Generates intermediate GH waypoints and iterates with proportional scaling until the routed distance is within tolerance.

```
isCircular = haversine(start, end) < 200m

for attempt in 0..9:
    waypoints = if isCircular:
        generateCircularWaypoints(center, targetDist, scale)
        # 3 waypoints at 120° intervals, radius = targetDist/(2π) × scale
    else:
        generateDetourWaypoints(start, end, targetDist, directDist, scale)
        # 1 perpendicular midpoint, offset h = sqrt((target/2)²-(direct/2)²) × scale

    route = routeViaGH([start] + waypoints + [end], profile)
    if route.distance in [minDist, maxDist]: return route
    scale *= targetDist / route.distance   # proportional adjustment
```

All waypoints are real GH routing calls → all segments follow actual roads.

**Anti-backtracking**: `routeViaGH()` calculates bearing from each consecutive waypoint pair and passes `setHeadings(bearings)` + `putHint("heading_penalty", 300.0)` to GH — penalises routes that approach a waypoint from the wrong direction (avoids U-turns on same road segment). Falls back to no-headings if GH rejects it.

**Profile change procedure**: edit profiles in `initializeGraphHopperSync()` + **bump `profileFingerprint`** constant → graph auto-rebuilds on next launch.

### DataManager
- OSM file: `cacheDir/osm_data/map.osm.pbf`
- GH graph: `cacheDir/gh/`
- Preferences: `route_planner_prefs`
- `onProgress` dispatched via `withContext(Dispatchers.Main)`
- Download: `URL.openStream()`, 30s timeout, 2× space check, ≥90% size verification

### RegionManager
Static object, 14 European regions. Default: Hungary (~190MB). Add regions by appending to `regions` list.

### MapManager
osmdroid wrapper. Accepts `LatLng`, converts to `GeoPoint` internally.
- `clear()` — removes ALL overlays including `MapTouchOverlay` (use only on full reset)
- `clearRoute()` — removes only `Polyline` overlays; safe to call after generating a route

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
→ init timer ticks → GraphHopper imports OSM (5–15 min first run)
→ Generate button enabled, time shown in status
```

**Route generation**
```
user taps map → start marker placed (auto-set to GPS location on launch)
user taps map → end marker placed (optional; if omitted = start = circular route)
user sets distance, tolerance sliders, Running/Biking toggle
Generate Route →
  RouteService.generateRoute(start, end, distMeters, profile, minTol, maxTol)
    → calculateRoute() iterates waypoints + GH routing + scale adjustment
    → callback(Route?, error?) on Main thread
    → displayRoute() → mapManager.clearRoute() + addPolyline()
    → Export as GPX enabled
```

**GPX export (SAF)**
```
Export as GPX → ACTION_CREATE_DOCUMENT picker (user chooses folder + filename)
→ saveGpxToUri(uri) → Route.toGpx() → contentResolver.openOutputStream(uri)
```

---

## File Locations (on device)

| | Path |
|-|------|
| OSM data | `cacheDir/osm_data/map.osm.pbf` |
| GH graph | `cacheDir/gh/` |
| GH lock | `cacheDir/gh/lock` (deleted on init) |
| SharedPreferences | `route_planner_prefs` |
| GH profile key | pref `gh_profiles` = `"foot_bike_gh6_v1"` |
| GPX output | user-chosen via SAF file picker |

---

## Common Modifications

**Add region**: append to `RegionManager.regions` with Geofabrik PBF URL.

**Change routing profiles or weighting**: edit profiles in `RouteService.initializeGraphHopperSync()` + **bump `profileFingerprint`** string. Do NOT upgrade GraphHopper past 6.0.

**Change default distances**: `binding.etDistance.setText(...)` in `MainActivity.onCreate()` and toggle listener.

**Change tolerance defaults**: `minTolerance`/`maxTolerance` fields in `MainActivity` (meters).

**Change waypoint strategy**: `generateCircularWaypoints()` and `generateDetourWaypoints()` in `RouteService`. Number of waypoints or placement geometry can be tuned here.

---

## Known Limitations

| Issue | Fix |
|-------|-----|
| Circular route always uses 3 waypoints (equilateral triangle shape) | Add randomised bearing offsets for variety |
| Detour waypoint is single perpendicular point | Add second waypoint for complex detour shapes |
| No restricted area filtering | Load OSM landuse/amenity tags |
| No elevation support | Add elevation data source |
| No offline tile cache | Pre-bundle or download tile layer |
| Large regions may OOM even with largeHeap | Implement sub-region selection |

---

## MANDATORY FOR ALL AGENTS

1. Update this guide after each development step.
2. If you change GH profiles or weighting, **bump `profileFingerprint`** in `RouteService.initializeGraphHopperSync()`.
3. Never upgrade GraphHopper past 6.0 — see Dependencies constraint above.
4. Never call `mapManager.clear()` after route display — use `mapManager.clearRoute()`.

**Last Updated**: 2026-05-29
