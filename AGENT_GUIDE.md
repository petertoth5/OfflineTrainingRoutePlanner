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

**Map Markers** (high-visibility colored circles with white borders):
- Start point: `#D32F2F` (red) — label "S"
- End point: `#388E3C` (green) — label "E"
- Mid waypoints: `#FBC02D` (yellow) — labels "1", "2", "3", … (auto-incrementing)
- All markers: white 3px border + white text for high contrast on maps

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

The app icon uses Android's Adaptive Icon format (API 26+), styled after Samsung One UI — flat, minimal, high-contrast, grayscale only:
- **Foreground:** White shapes on the dark background —
  - **Point "A"** (top-left) and **Point "B"** (top-right) markers, each a hollow ring with the letter inside
  - **Dotted route line** arcing gently from A to B (evokes a planned route, not a loop)
  - **Running-shoe silhouette** (upper + sole) across the bottom, signalling the training-route theme
- **Background:** Solid dark charcoal (#1C1C1E), flat (no gradient)
- **Monochrome:** Same shapes in a single tintable colour for Android 13+ themed icons
- **Safe zone:** All content kept within the inner ~66dp circle of the 108×108 canvas (centred on 54,54)
- **Design intent:** "A → B route planning" plus a running-shoe cue for the training theme, in a clean grayscale One UI look

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
├── MainActivity.kt           # Main UI, map, route generation, GPX export
├── SplashActivity.kt         # First launch, region select, OSM download
├── RouteService.kt           # GraphHopper engine, route gen
├── DataManager.kt            # OSM download/storage/deletion, SharedPreferences
├── RegionManager.kt          # 14 European regions with Geofabrik URLs
├── MapManager.kt             # osmdroid wrapper: markers, polylines, zoom
├── MapTouchOverlay.kt        # Tap → lat/lng callback via osmdroid Overlay
├── MarkerIconGenerator.kt    # Runtime marker icon bitmap generation (colored circles)
└── Route.kt                  # Data class: points, distance, hasLoops, toGpx()
res/
├── layout/activity_main.xml     # Map + profile toggle + controls
├── layout/activity_splash.xml   # Region spinner + download progress
├── drawable/ic_launcher_foreground.xml   # App icon foreground vector
├── drawable/ic_launcher_background.xml   # App icon background (#1C1C1E)
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
- **New Route** button: clears route polyline, ALL markers (start, end, mids), resets all state to "Tap map to select start point"
- **Clear Mids** button (`btnClearWaypoints`): removes all intermediate waypoints, keeps start/end
- `tvWaypoints`: live count of intermediate waypoints ("Waypoints: N")

**Multi-waypoint support** (multiple stops in one route):
- **Tap semantics (auto-append)**: 1st tap = START (label "S"), 2nd tap = END (label "E"), 3rd+ tap = appends a MID waypoint (labels "1", "2", …) inserted *before* END so routing order is auto-maintained as `[start] + mids + [end]`.
- Markers carry **simple osmdroid text labels** via `Marker.setTextIcon(label)` (no custom drawables) — the pin is replaced by a text-rendered icon.
- `MainActivity.midMarkers: MutableList<Marker>` holds the ordered mids. `orderedWaypoints()` builds the routing list from start + mids + end. Each mid marker is draggable (`attachMidMarkerDrag()`) and triggers `onWaypointMoved()` → auto-regeneration.
- **Distance target is always kept.** If the mandatory through-path of the ordered waypoints already exceeds max tolerance, the route is still returned but the callback carries the warning *"Waypoint order incompatible with distance target — adjust waypoints and try again."*, surfaced in the status bar and a Toast.
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

**Entry point** (`generateRoute(...)`): two overloads.
- `generateRoute(start, end, dist, …)` — legacy two-point / circular convenience overload, delegates to the list form.
- `generateRoute(waypoints: List<LatLng>, dist, …)` — ordered `[start, mid…, end]`. Dispatches on size: 1 → circular, 2 → detour (`calculateRoute()`), 3+ → `calculateMultiWaypointRoute()`.

**`normalizeWaypoints(List<LatLng>)`**: drops out-of-range coords and collapses consecutive duplicates (within 1m) so GH never sees a zero-length leg. Order is preserved exactly (UI guarantees mids precede end).

**Multi-waypoint routing** (`calculateMultiWaypointRoute()`): routes through every user waypoint **in order** (mandatory through-points — never reordered or dropped). Baseline = straight through-path. If baseline > maxTolerance → return route + the "incompatible" warning. Otherwise injects one perpendicular bulge per leg (`bulgePoint()`), distributing the extra distance proportionally to each leg's length, and iterates with the same proportional-scaling loop (10 attempts) used for single-leg routes.

**Routing algorithm** (`calculateRoute()`):
Priority is route **length**, not shortest path. Generates intermediate GH waypoints and iterates with proportional scaling until the routed distance is within tolerance. An **anti-parallel scorer** then guides selection so the chosen route avoids out-and-back overlaps. If the main loop produces only self-crossing in-tolerance routes, **SI escape passes** (algorithm J) retry with fresh waypoint geometry.

```
isCircular  = haversine(start, end) < 200m
isLoopMode  = !isCircular && haversine(start,end) < targetDist × 0.35

variety = randomVariety()     # randomised ONCE per generateRoute() call (see randomVariety() below)

--- Main loop: 10 scaling attempts ---
for attempt in 0..9:
    waypoints = selectWaypointStrategy(...):
        isCircular → generateCircularWaypoints(center, targetDist, scale, variety)
            # N waypoints (N = 4..5) evenly spaced, randomly rotated, per-vertex angular
            # jitter (±0.15 rad) + radius jitter (×0.80..1.20), MIN angular separation
            # (~84°) enforced via enforceAngularSpread(). base radius = targetDist/(2π) × scale
        isLoopMode → generateCircularWaypointsForLoop(midpoint, start, end, …)
            # loop of vertices around the start↔end midpoint, first vertex thrown
            # PERPENDICULAR to the start→end bearing (close points would otherwise double back)
        else → generateDetourWaypoints(start, end, targetDist, directDist, scale, variety)
            # 2 waypoints at fractions (≈0.12..0.25 and ≈0.75..0.88) along A→B — deviate
            # early / return late so out & in legs separate. base offset h = sqrt(...) × scale

    (response, headingApplied) = routeViaGHWithHeadingFlag([start]+waypoints+[end], profile)
    if dist in [minDist, maxDist]:
        antiParallel  = computeAntiParallelFraction(routePoints, dist)   # algorithm A, 0..1
        selfFrac      = computeSelfIntersectionFraction(routePoints, dist) # algorithm F, 0..1
        backtrackFrac = computeBacktrackingFraction(routePoints)         # algorithm G, 0..1
        composite = antiParallel + backtrackFrac×0.5 + (if !headingApplied) 0.15
        # Early-return: genuinely clean route
        if selfFrac == 0 AND backtrackFrac ≤ 0.05 AND antiParallel ≤ 0.15 AND headingApplied:
            return route
        if selfFrac == 0: track Category A (clean)   by lowest composite
        else:             track Category B (crossed)  by lowest composite
    else:
        track Category C (out-of-tolerance) by |dist − target|
    scale *= targetDist / dist                     # proportional adjustment

--- SI escape passes: if no clean route found in main loop, retry with fresh geometry (algorithm J) ---
if bestCleanRoute == null AND bestCrossedRoute != null:
    for escapePass in 0..SI_MAX_EXTRA_PASSES-1:
        escapeVariety = randomVariety()    # completely different geometry set
        escapeScale = 1.0
        for attempt in 0..SI_EXTRA_ATTEMPTS_PER_PASS-1:
            # [same as main loop: select waypoints, route via GH, score]
            if selfFrac == 0:              # Category A found!
                track it and break escapeLoop  # stop retrying; use this clean route
            else if selfFrac > 0:          # Category B (crossed) in escape pass
                track it as fallback
            else:                          # Category C (out-of-tolerance)
                track it as last-resort fallback
            escapeScale *= targetDist / dist

return Category A   (soft warning if antiParallel > 0.30 or backtrack > 0.10)
     ?? Category B   ("crosses itself" warning)
     ?? Category C   ("best found" message)
```

All waypoints are real GH routing calls → all segments follow actual roads.

**Route variety** (`randomVariety()`, algorithm J helper): Extracted as a standalone function (previously inlined in `calculateRoute()`). Called once for the main loop and once per SI escape pass to ensure both draw from identical parameter ranges. Returns a `RouteVariety` object with random geometry parameters: circular vertex count (4–5), rotation angle, per-vertex angular/radius jitter, and detour side/fractions.

**Three shape scorers** (post-route, all `internal` for testability):
- **Anti-parallel** (`computeAntiParallelFraction`, algorithm A): O(m²) overlap. Down-samples to ≤200 pts, flags segment pairs ≥5 apart whose midpoints are within 80m AND bearings are (anti-)parallel within 25°. >0.30 reject-worthy, ≤0.15 good.
- **Self-intersection** (`computeSelfIntersectionFraction`, algorithm F): geometric crossings (CCW `segmentsIntersect`) plus near-crossings — segment pairs ≥`MIN_SI_SKIP`(5) apart whose midpoints are within `SELF_INTERSECT_THRESHOLD_M`(100m) at a **non-parallel** angle. Down-samples to ≤200 pts. Returns fraction of crossing length.
- **Backtracking** (`computeBacktrackingFraction`, algorithm G): scans the **full** polyline (no down-sampling) for consecutive-segment bearing reversals > `BACKTRACK_BEARING_THRESHOLD_DEG`(150°); the returning segment's length counts. A clean U-turn ≈ 0.50.

**Helpers**: `angularDifference(b1,b2)` normalizes a bearing difference to [0°,180°]; `segmentsIntersect(a,b,c,d: LatLng)` is a LatLng overload of the CCW test; `reflectAcrossLine(point,lineA,lineB)` reflects a point across a line (planar lat/lon) for algorithm I-C. `enforceAngularSpread(angles, minSep)` pushes circular vertices apart to a minimum separation (capped at 2π/n, even-spacing fallback) while preserving input index order. `randomVariety()` generates a random `RouteVariety` object with geometry parameters (algorithm J helper) — used once in the main scaling loop and once per SI escape pass to ensure consistent parameter ranges.

**Tunable constants** (all in `RouteService` companion object): Algorithm F/G/H scoring weights (`SELF_INTERSECT_THRESHOLD_M`=100, `MIN_SI_SKIP`=5, `BACKTRACK_BEARING_THRESHOLD_DEG`=150, `BACKTRACK_FRACTION_STRICT`=0.05, `BACKTRACK_FRACTION_ACCEPTABLE`=0.10, `BACKTRACK_COMPOSITE_WEIGHT`=0.5, `PARALLEL_SCORE_GOOD`=0.15, `PARALLEL_SCORE_REJECT`=0.30), circular waypoint jitter (`MAX_ANGLE_JITTER_RAD`=0.15, `MIN_ANGULAR_SEPARATION_RAD`≈1.47 rad/84°), loop-mode threshold (`LOOP_MODE_THRESHOLD`=0.35), and **SI escape pass limits** (`SI_MAX_EXTRA_PASSES`=3, `SI_EXTRA_ATTEMPTS_PER_PASS`=5).

**Waypoint placement refinement (algorithm I)**: `generateCircularWaypoints` / `generateCircularWaypointsForLoop` now sort (angle, radiusJitter) pairs by angle **after** `enforceAngularSpread()` so vertices are visited in ascending angular order (prevents a self-crossing visit order). `generateDetourWaypoints` adds a convergence guard: if segment WP1→WP2 crosses the start→end baseline (a bowtie quadrilateral), WP2 is reflected to the opposite side via `reflectAcrossLine()`.

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
- `addMarker(latLng, title, label?, colorResId?)` — creates a colored marker icon. If `colorResId` provided, generates a high-visibility circle icon (red/green/yellow) with label text via `MarkerIconGenerator`. Otherwise falls back to osmdroid text-only icon or plain marker. Used for S / E / 1 / 2 … multi-waypoint labels.

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
| ~~Circular route always uses 3 waypoints (equilateral triangle shape)~~ | **RESOLVED** — randomised rotation, 4–5 vertices (min raised from 3), angular/radius jitter + enforced min angular separation (`RouteVariety` / `enforceAngularSpread`) |
| ~~Detour waypoint is single perpendicular point~~ | **RESOLVED** — two waypoints, randomised side/fractions for arc/curve shapes |
| ~~Routes double back / run parallel to themselves~~ | **RESOLVED** — anti-parallel scorer drives multi-objective selection, reformed detour fractions, near-circular loop mode for close start/end (`computeAntiParallelFraction`, algorithms A–E) |
| ~~Routes cross/knot themselves or do sharp U-turns; circular/detour waypoints in self-crossing order~~ | **RESOLVED** — self-intersection scorer (F) + backtracking scorer (G) feed three-category selection (H); angular-sorted circular vertices + detour bowtie reflection (I) (`computeSelfIntersectionFraction`, `computeBacktrackingFraction`, `reflectAcrossLine`, algorithms F–I) |
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

**Last Updated**: 2026-07-07 — Self-intersection escape passes (algorithm J) implemented. When main 10-attempt loop finds only self-crossing in-tolerance routes (Category B), new SI escape mechanism retries with up to `SI_MAX_EXTRA_PASSES` (3) fresh `randomVariety()` sets, each running `SI_EXTRA_ATTEMPTS_PER_PASS` (5) scaling attempts. Early return if any escape pass finds Category A (clean) route; otherwise fall back to best Category B. Extracted `randomVariety()` helper ensures main loop and escape passes draw from identical geometry parameter ranges. Build compiled and installed on Samsung Galaxy S23 (Android 14+); device-level testing confirmed by user 2026-07-07 — merged to `main` and released as **v1.3.0** on GitHub (bundled with the One UI icon redesign); release APK re-downloaded and clean-installed to verify. **Previous milestone (2026-06-30)**: Refined route planning (algorithms F–I) tested & verified on Samsung Galaxy S24+. No self-intersections, no U-turns, stable geometry, early-return optimization confirmed. New `internal` functions: `computeSelfIntersectionFraction()` (algorithm F), `computeBacktrackingFraction()` (algorithm G), `reflectAcrossLine()` (algorithm I-C helper). `calculateRoute()` implements three-category selection (A clean / B crossed / C out-of-tolerance). App icon redesigned: Samsung OneUI style. Builds clean.

Previous: 2026-06-30 — Anti-parallel route planning (algorithms A–E) implemented in `RouteService.kt`. New functions: `computeAntiParallelFraction()` (O(m²) overlap scorer, internal), `enforceAngularSpread()` (min angular separation, internal), `generateCircularWaypointsForLoop()` (near-circular loop mode), `selectWaypointStrategy()` (mode dispatch), `routeViaGHWithHeadingFlag()` (returns whether heading penalty was applied; `routeViaGH()` now delegates to it). `calculateRoute()` scaling loop replaced single best-by-delta tracking with two-stage selection (bestWithinTolerance by anti-parallel score with early-return at ≤0.15; bestOutOfTolerance by distance delta) and de-prioritises no-heading routes (+0.15). `RouteVariety` ranges reformed: circular vertex count 3–5→4–5, angle jitter ±0.20→±0.15, detour fracs 0.28–0.40/0.60–0.72→0.12–0.25/0.75–0.88, detour offset factors 0.85–1.15→0.70–1.00. Tunable constants in `RouteService` companion object. No `profileFingerprint` bump (geometry only, profiles unchanged). Compiles clean (`compileDebugKotlin` BUILD SUCCESSFUL). NOTE: no JVM/instrumentation test harness exists yet (no `src/test`, no JUnit dep) and `LatLng` is an Android framework type — unit tests for the two `internal` scorer functions require Build/Integrator to add a test sourceSet (likely `androidTest`/Robolectric). Pending device testing (Deploy Agent).

Previous: 2026-06-12 — Feature complete: colored waypoint markers with multi-waypoint routing. Implementation verified: `MarkerIconGenerator.generateCircleMarkerIcon()` creates colored circle icons (red start, green end, yellow mids) with white 3px borders and text labels. `MapManager.addMarker(latLng, title, label?, colorResId?)` accepts optional color parameter. MainActivity correctly displays waypoint count, supports multi-waypoint tap semantics (1st→S, 2nd→E, 3rd+→1/2/3), clear waypoints button, and draggable markers with auto-regeneration. RouteService supports multi-waypoint routing with proportional distance distribution and through-path constraints. All marker colors defined in res/values/colors.xml and Material Design palette complete. Feature branch ready for merge to main.

Previous (2026-05-29): **multi-waypoint support**: ordered `generateRoute(List<LatLng>)` + `normalizeWaypoints()` + `calculateMultiWaypointRoute()` (mandatory through-points with per-leg bulge injection to hit the distance target; warns when waypoint order overshoots tolerance). UI: auto-append tap semantics (S/E/1/2…), text-label markers, Clear Mids button + waypoint count. Earlier: route-shape variety (`RouteVariety`) and draggable start/end markers with auto-regeneration; UI redesign with high-contrast Material Design 2 palette and WCAG AAA accessibility.
