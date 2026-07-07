# Resume Development — OfflineTrainingRoutePlanner

**Last Checkpoint**: 2026-07-07 (Commit: SI escape passes — algorithm J)

---

## What Was Done

### Algorithm J: SI Escape Passes (Self-Intersection Avoidance Retry)
- **Problem**: Main 10-attempt loop sometimes converges to a geometry set that always self-crosses (Category B), yielding no clean (Category A) routes even with proportional scaling
- **Solution**: If main loop ends with `bestCleanRoute == null && bestCrossedRoute != null`, run up to 3 additional passes, each with completely fresh `randomVariety()` geometry and 5 scaling attempts
- **Implementation**: `escapeLoop` in `calculateRoute()` (lines 502–582); triggered only when in-tolerance crossing routes exist but no clean route was found in main 10 attempts
- **Result**: Early return if any escape pass finds Category A; otherwise use best Category B from main or escape passes; worst case 10+3×5=25 GH calls total
- **Constants**: `SI_MAX_EXTRA_PASSES`=3, `SI_EXTRA_ATTEMPTS_PER_PASS`=5 (both tunable in `RouteService` companion object)
- **Helper**: Extracted `randomVariety(): RouteVariety` (line 681) — ensures main loop and escape passes draw from identical parameter ranges

### Algorithms F–I: Self-Intersection & Backtracking Prevention (Previous Milestone)
- **Algorithm F** (`computeSelfIntersectionFraction`): Detects polyline crossings via CCW test + non-parallel near-crossings (100m proximity, O(m²))
- **Algorithm G** (`computeBacktrackingFraction`): Flags >150° bearing reversals (U-turns), returns backtracking fraction
- **Algorithm H** (`calculateRoute` revised): Three-category selection — clean routes (A) preferred, crossed routes (B) accepted with warning, out-of-tolerance (C) fallback
- **Algorithm I**: Waypoint placement fixes — angular-sorted circular vertices (I-A/I-B), convergence guard + WP2 reflection for detours (I-C)

### Testing Status
✓ **Device Tested & Confirmed** (2026-07-07, Samsung Galaxy S23, Android 14+): Algorithm J self-intersection avoidance confirmed working — user reports "much better result," approved for commit and release
- Algorithm J escape passes mechanism integrated into `calculateRoute()`, compiles clean
- APK built successfully (11.0 MB, not committed to git)
- Routes that previously self-crossed now resolved by escape passes, per user confirmation

✓ **Previous Device Testing** (2026-06-30, Algorithms F–I on Samsung Galaxy S24+ Android 16):
- Circular routes: No self-crossing, even waypoint distribution, symmetric shape
- Detour routes: No U-turns, smooth arc geometry, no bowtie intersections
- Loop mode: Perpendicular bulge, fast generation (< 3s via early-return)
- Stability: Identical routes across multiple generations
- Performance: Clean routes complete in 1-3s (1-2 attempts); problematic routes refine over 5-10s (3-5 attempts)

### UI/Visual Updates
- App icon redesigned to Samsung OneUI aesthetic: white geometric ring + runner silhouette + colored waypoint dots (red/yellow/green)
- Icon files modified: `ic_launcher_background.xml` (gradient), `ic_launcher_foreground.xml` (ring+runner), `ic_launcher_monochrome.xml` (new, API 33+ support)

---

## Current State

**Branch**: `main` (feature branch `feature/no-self-intersection-routing` merged 2026-07-07 and deleted)

**Key Files Modified:**
- `src/main/kotlin/com/routeplanner/RouteService.kt` — Algorithms A–J implemented; Algorithm J SI escape passes added to `calculateRoute()` (~lines 502–582); `randomVariety()` helper extracted (~line 681)
- `res/drawable/ic_launcher_*.xml` — App icon (from previous milestone, already in branch)
- `res/mipmap-anydpi-v26/ic_launcher*.xml` — Icon references (from previous milestone, already in branch)
- `AGENT_GUIDE.md` — Updated for Algorithm J (last updated 2026-07-07)
- `RESUME.md` — Updated for Algorithm J (this file, last updated 2026-07-07)

**Build Status**: ✓ Compiles clean (`./gradlew assembleDebug` succeeded; 11.0 MB APK, not committed to git)
**Device Status**: Installed & confirmed working on Samsung Galaxy S23

**Known Limitations** (unchanged, same as before):
- No restricted area filtering (OSM landuse/amenity tags)
- No elevation support
- No offline tile cache (pre-bundled or downloadable)
- Large regions may OOM even with `largeHeap` (would need sub-region selection)

---

## How to Resume Development

### Starting a New Session

Copy and paste this into the Claude Code prompt:

```
@.claude\agents\orchestrator-agent.md

**Status Update**: Algorithm J (SI escape passes) implemented, merged to main, and released as v1.3.0 on 2026-07-07 (bundled with the earlier One UI icon redesign). Device-tested and confirmed on Samsung Galaxy S23; release APK clean-installed and verified. Previous milestone: Algorithms F-I tested & verified on 2026-06-30.

**Next Steps**: [User specifies next action: (1) test current build and report results, (2) proceed with different feature request, or (3) refine Algorithm J parameters]

Example prompts:
- "Add elevation profile support (load srtm3 data, display elevation gain in UI)"
- "Implement region filtering (user selects region from spinner, app loads sub-region OSM data)"
- "Add offline tile cache (pre-bundle tile layer for offline map display)"
- "Optimize route calculation for large routes (currently may OOM on 50km+ routes)"
- "Add route difficulty assessment (based on bearing changes, elevation gain, road type)"
- "Implement route sharing (export to URL, share with others)"
```

### What the Orchestrator Will Do

The Orchestrator Agent (`.claude/agents/orchestrator-agent.md`) will:
1. **Assess your request** against the current codebase state
2. **Route to appropriate specialists** (Algorithm Dev, UI Designer, Software Dev, Build/Integrator, Deploy, Documentation)
3. **Execute the workflow** in order (design → implement → test → deploy)
4. **Report results** and ask for feedback

---

## Developer Notes

### Architecture Overview

**RouteService.kt** is the core routing engine. Key functions:

| Function | Purpose | Status |
|---|---|---|
| `generateRoute(waypoints, distance, ...)` | Main entry point for route generation | Active (Algorithms A-J) |
| `calculateRoute()` | Main loop: 10 scaling attempts + SI escape passes if needed | Three-category selection (Algorithm H) + escape-pass retry mechanism (Algorithm J) |
| `selectWaypointStrategy()` | Picks circular/detour/loop mode based on geometry | Active (Algorithm D) |
| `generateCircularWaypoints()` | 4-5 vertices at enforced angular spread, sorted by angle | Implemented (Algorithm I-A) |
| `generateDetourWaypoints()` | Two waypoints with early/late deviation + convergence guard | Implemented (Algorithm I-C) |
| `generateCircularWaypointsForLoop()` | Circular waypoints around midpoint for near-circular routes | Implemented (Algorithm I-B) |
| `computeAntiParallelFraction()` | Scores parallel/backtracking overlap (Algorithm A) | Active |
| `computeSelfIntersectionFraction()` | Scores self-crossing via CCW + non-parallel near-crossings | Implemented (Algorithm F) |
| `computeBacktrackingFraction()` | Scores bearing reversals > 150° | Implemented (Algorithm G) |
| `routeViaGHWithHeadingFlag()` | Routes via GraphHopper, returns whether heading penalty was applied | Active (Algorithm E) |
| `randomVariety()` | Generates random geometry parameters (extraction helper for Algorithms J) | New (Algorithm J helper) |

### Constants to Tune (if needed)

All tunable in `RouteService` companion object:

- `BACKTRACK_BEARING_THRESHOLD_DEG = 150.0` — U-turn detection threshold (degrees)
- `BACKTRACK_FRACTION_STRICT = 0.05` — Early-return gate: max 5% backtracking
- `SELF_INTERSECT_THRESHOLD_M = 100.0` — Near-crossing proximity (meters)
- `PARALLEL_SCORE_GOOD = 0.15` — Anti-parallel early-return threshold (fraction)
- `LOOP_MODE_THRESHOLD = 0.35` — Trigger loop mode if directDist < targetDist × 0.35
- `SI_MAX_EXTRA_PASSES = 3` — Max number of SI escape passes (algorithm J) — tune to balance avoidance vs. user wait time
- `SI_EXTRA_ATTEMPTS_PER_PASS = 5` — Scaling attempts per SI escape pass (algorithm J) — tune to balance thoroughness vs. computation

### Testing Checklist (before committing any changes)

1. **Build**: `./gradlew assembleDebug` → succeeds
2. **Install**: `adb install -r build/outputs/apk/debug/RoutePlanner-debug.apk`
3. **Manual tests on device**:
   - Circular route (6000m) → no self-crossing, 4+ waypoints
   - Detour route (12000m on distant points) → no U-turns, smooth arc
   - Loop mode (two close points, 8000m) → perpendicular bulge
   - Stability (3× generation on same points) → identical routes
   - Performance → clean routes < 3s, problematic routes 5-10s
4. **Update AGENT_GUIDE.md** with final status
5. **Commit** with message following conventional commits

### Conventional Commit Style (for this repo)

```
<type>: <subject> (max 50 chars)

<body — details of what and why, wrapped at 72 chars>

<footer if needed>
```

Examples:
- `feat: add elevation profile support (load srtm3, display gain)`
- `fix: reduce route generation time by optimizing waypoint search`
- `refactor: extract CCW test as shared helper in RouteService`

---

## Next Feature Ideas

Based on known limitations and user feedback, consider:

1. **Elevation Support** (easy complexity)
   - Source: SRTM3 (90m resolution, free)
   - Display: Show elevation gain in route details, plot elevation profile
   - Implementation: Add `RouteElevation` data class, load SRTM tiles at route points

2. **Offline Tile Cache** (medium complexity)
   - Source: Pre-bundle or download .mbtiles file
   - Display: Use osmdroid's MBTilesProvider instead of online tiles
   - Implementation: Add tile download UI, cache management

3. **Region Filtering** (medium complexity)
   - Allow user to select sub-regions (e.g., Budapest vs Hungary)
   - Smaller downloads, faster import
   - Implementation: Sub-region list in SplashActivity, dynamic OSM URL selection

4. **Route Difficulty Assessment** (medium complexity)
   - Score based on bearing changes (turns), elevation gain, road type
   - Show difficulty badge (easy/moderate/hard)
   - Implementation: Add scoring function in RouteService, update UI

5. **Route Sharing** (hard complexity)
   - Export route as shareable URL or encoded GPX link
   - Implementation: Server-side storage (optional) or client-side QR encoding

---

## Git & Build Artifacts

**APKs are NOT committed** (see `.gitignore` line 3: `*.apk`). Only source code, resources, and config files are in git. Build artifacts are regenerated via gradle.

## Quick Reference Commands

**Build debug APK:**
```powershell
& "C:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" assembleDebug
```

**Install to device:**
```powershell
adb install -r build/outputs/apk/debug/RoutePlanner-debug.apk
```

**Check device logs:**
```powershell
adb logcat | grep -i routeplanner
```

**Force clear app data (if needed):**
```powershell
adb shell pm clear com.routeplanner
```

---

## Agent Guide Reference

**If you need to understand the agent workflow**, refer to:
- `.claude/agents/orchestrator-agent.md` — Main entry point, routing logic
- `.claude/agents/algorithm-developer-agent.md` — Algorithm design & refinement
- `.claude/agents/software-developer-agent.md` — Kotlin implementation
- `.claude/agents/deploy-agent.md` — Build & device testing

The orchestrator automatically routes your request to the right agent(s) and coordinates their work.

---

## Summary

**You are here**: Algorithm J (SI escape passes) implemented, device-tested & confirmed on Samsung Galaxy S23, merged to `main`, and released as **v1.3.0** on GitHub (2026-07-07) — release APK re-downloaded and clean-installed to verify. Previous milestone (Algorithms F-I) tested & verified on device (2026-06-30).

**Next actions**: 
1. Post new feature/bug request to Orchestrator Agent for next phase
2. Or refine Algorithm J constants (`SI_MAX_EXTRA_PASSES`, `SI_EXTRA_ATTEMPTS_PER_PASS`) if further tuning is wanted

---

**File**: `RESUME.md` (this file)  
**Updated**: 2026-07-07  
**Status**: v1.3.0 released; ready for next development cycle
