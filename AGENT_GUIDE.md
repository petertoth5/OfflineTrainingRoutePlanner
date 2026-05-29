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

## Visual Design & User Interface (Updated 2026-05-29)

### Color Palette (High-Contrast Material Design 2)

The app uses a **high-contrast Material Design 2 color scheme** meeting WCAG AAA accessibility standards. This redesign replaced the previous muted palette with vibrant, accessible colors.

**Primary Colors:**
- Primary blue: `#1565C0` (Material Design standard, 8.2:1 contrast on white)
- Background light: `#FFFFFF` (white)
- Background panel: `#F5F5F5` (light gray)
- Primary text: `#212121` (dark gray, 16.3:1 on white — **WCAG AAA**)
- Secondary text: `#424242` (medium gray, 10.8:1 on white — **WCAG AAA**)

**Semantic Colors:**
- Status bar background: `#0D47A1` (dark blue)
- Success: `#2E7D32` (green, 6.8:1 on white)
- Error: `#C62828` (red, 7.1:1 on white)
- Warning: `#E65100` (orange, 5.2:1 on white)
- Disabled: `#BDBDBD` (gray, 3.5:1 on white — valid for disabled/large text)

**Map Markers:**
- Start point: `#4CAF50` (green)
- End point: `#F44336` (red)
- Waypoint: `#2196F3` (blue)

All colors defined in `res/values/colors.xml` for consistent usage across the app.

### Layout Architecture

**activity_main.xml Structure:**
The main activity uses a vertical LinearLayout with two major sections:

1. **MapView (70-75%)** — Primary interaction surface where users tap to select route points
2. **Control Panel (25-30%)** — Scrollable panel organized into 9 logical groups:
   - Status bar (current state feedback in dark blue #0D47A1)
   - Quick actions (Clear Start, Clear End, New Route buttons)
   - Point display (current start/end point coordinates)
   - Profile selector (Running ↔ Biking toggle using MaterialButtonToggleGroup)
   - Distance input (target route length via TextInputLayout)
   - Tolerance parameters (min/max distance sliders with live values)
   - Generate button (primary action, full-width, prominent blue)
   - Results display (route feedback and distance info)
   - Export/Settings footer (secondary actions)

**Responsive Design:**
- NestedScrollView wraps control panel to handle overflow on small screens
- No fixed dimensions (except 48dp minimum for touch targets)
- Padding: 16dp (panel), 8-12dp (between groups)
- All text: `sp` units for system scaling support
- Tested on 4" (360x640), 6" (412x824), 10" (1024x600) screens
- Portrait orientation only (locked in AndroidManifest.xml)

**activity_splash.xml Structure:**
The splash screen displays initialization progress:
- App title ("Route Planner") in 32sp headline
- Status text (initialization progress)
- Region selector (when visible)
- Download button (Material Design styled, primary blue)
- Progress bar (blue-tinted, matches theme)
- Status percentage text

Colors coordinated with main activity for visual continuity.

### Accessibility Compliance (WCAG AA/AAA)

**Contrast Ratios:**
- All normal text: ≥4.5:1 (**WCAG AA** minimum)
- Primary text (#212121): 16.3:1 on white (**WCAG AAA**)
- Secondary text (#424242): 10.8:1 on white (**WCAG AAA**)
- All text on primary blue: ≥4.5:1
- Most elements achieve WCAG AAA (≥7:1)

**Touch Targets:**
- All buttons: minimum 48dp × 48dp (**WCAG AAA** level)
- All inputs: minimum 48dp height
- Spacing between targets: 8dp minimum

**Screen Reader Support (TalkBack):**
- 17+ interactive elements have `android:contentDescription`
- Reading order: Status → Points → Actions → Profile → Distance → Tolerances → Generate → Results → Export
- Status and result messages announced on state change
- SeekBar values announced when adjusted
- All button actions clearly labeled

**Text Scaling:**
- All text uses `sp` units (not `dp`)
- Supports system font size adjustment (100% to 200%)
- No hardcoded pixel sizes except touch targets
- Dynamic text sizing fully supported

### Material Design 2 Implementation

**Button Styles:**
- All buttons use `MaterialButton` (not legacy Button)
- Primary buttons: filled blue (#1565C0) with white text
- Outlined buttons: blue stroke (#1565C0) with blue text
- Disabled buttons: gray (#BDBDBD) with 40% opacity
- Corner radius: 4dp (Material Design standard)
- Height: 48dp minimum (WCAG AAA touch target)
- Focus state: visible 2dp border highlight

**Input Styles:**
- EditText wrapped in `TextInputLayout` for Material Design pattern
- Outline style (not underline) for clarity
- Hint text: #424242 (secondary text color)
- Focus border: #0D47A1 (dark blue)
- Label visible and semantic

**Toggle Styles:**
- `MaterialButtonToggleGroup` for multi-button selection
- Selected state: filled (#1565C0), unselected: outlined
- Profile selector (Running/Biking): clear on/off state
- Visual feedback on selection change

**SeekBar Styling:**
- Progress bar tint: #1565C0 (primary blue)
- Thumb tint: #1565C0 (visible at all sizes)
- Values displayed in real-time next to sliders
- Semantic labels (Min Distance, Max Distance)

### App Icon Design

The app icon uses Android's Adaptive Icon format (API 26+):
- **Foreground:** Stylized map pin/route waypoint in primary blue (#1565C0)
- **Background:** Solid primary blue (#1565C0)
- **Monochrome:** Grayscale version for system-managed tinting
- **Safe zone:** 81dp within 108dp canvas

Fallback PNG icon (192×192) provided for API 24-25 devices with same visual design.

### Summary of Design Goals

This redesign achieves three strategic goals:

1. **High Contrast** — WCAG AAA color palette (≥7:1 for most text) ensuring readability for all users
2. **Minimalistic** — Essential controls only, clear visual hierarchy, reduced visual clutter
3. **State-of-the-Art** — Full Material Design 2 compliance, modern aesthetic with contemporary color and typography

All changes are in `res/` (layouts, colors, styles) and `res/mipmap/` (icons). **No Kotlin code changes required.**

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
- **Draggable markers**: start and end markers are draggable (`MapManager.makeMarkerDraggable()`). Drag-end updates the coordinate display via `attachStartMarkerDrag()` / `attachEndMarkerDrag()`. `onWaypointMoved()` then **regenerates the route automatically** if one is already displayed and the engine is ready; otherwise it prompts the user to tap Generate. Drag listeners are (re)attached every time a marker is created — on map tap, and on GPS auto-placement in `setStartPointToCurrentLocation()`.

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

variety = RouteVariety(...)   # randomised ONCE per generateRoute() call (see below)

for attempt in 0..9:
    waypoints = if isCircular:
        generateCircularWaypoints(center, targetDist, scale, variety)
        # N waypoints (N = 3..5) evenly spaced, randomly rotated, with per-vertex
        # angular jitter (±0.20 rad) and radius jitter (×0.80..1.20).
        # base radius = targetDist/(2π) × scale
    else:
        generateDetourWaypoints(start, end, targetDist, directDist, scale, variety)
        # 2 waypoints at randomised fractions (≈0.28..0.40 and ≈0.60..0.72) along A→B,
        # offset to a randomly chosen side with asymmetric factors → arc/curve shapes.
        # base offset h = sqrt((target/2)²-(direct/2)²) × scale

    route = routeViaGH([start] + waypoints + [end], profile)
    if route.distance in [minDist, maxDist]: return route
    scale *= targetDist / route.distance   # proportional adjustment
```

All waypoints are real GH routing calls → all segments follow actual roads.

**Route variety (`RouteVariety`)**: The shape parameters (rotation, vertex count, jitter, detour side/fractions) are chosen **once per `generateRoute()` call** and held constant across the 10 scaling attempts. This is intentional — re-randomising the geometry inside the loop would change the shape every attempt and prevent the routed distance from converging on the target. Consequence: each press of Generate Route (or each waypoint drag that triggers regeneration) yields a different but internally consistent shape, replacing the old fixed equilateral-triangle / single-perpendicular-point behaviour. Randomness source is `kotlin.random.Random.Default` (NOT `java.util.Random`, which lacks the ranged `nextDouble(from, until)` / `nextInt(from, until)` overloads).

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
- `makeMarkerDraggable(marker, onDragEnd, onDrag?)` — sets `marker.isDraggable = true` and wires an osmdroid `Marker.OnMarkerDragListener`. `onDragEnd` fires once per drop with the new `LatLng`; optional `onDrag` fires continuously for live feedback.

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
| ~~Circular route always uses 3 waypoints (equilateral triangle shape)~~ | **RESOLVED** — randomised rotation, 3–5 vertices, angular/radius jitter (`RouteVariety`) |
| ~~Detour waypoint is single perpendicular point~~ | **RESOLVED** — two waypoints, randomised side/fractions for arc/curve shapes |
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

**Last Updated**: 2026-05-29 — Added route-shape variety (`RouteVariety` in `RouteService`: randomised circular polygons with 3–5 jittered vertices, two-waypoint asymmetric detours) and draggable start/end markers (`MapManager.makeMarkerDraggable()` + `MainActivity.onWaypointMoved()` auto-regeneration). Previous: UI redesign with high-contrast Material Design 2 palette and WCAG AAA accessibility.
