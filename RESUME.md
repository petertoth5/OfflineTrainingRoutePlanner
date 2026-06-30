# Resume Development — OfflineTrainingRoutePlanner

**Last Checkpoint**: 2026-06-30 (Commit: Refined route planning algorithms F-I + Samsung OneUI icon)

---

## What Was Done

### Algorithms F–I: Self-Intersection & Backtracking Prevention
- **Algorithm F** (`computeSelfIntersectionFraction`): Detects polyline crossings via CCW test + non-parallel near-crossings (100m proximity, O(m²))
- **Algorithm G** (`computeBacktrackingFraction`): Flags >150° bearing reversals (U-turns), returns backtracking fraction
- **Algorithm H** (`calculateRoute` revised): Three-category selection — clean routes (A) preferred, crossed routes (B) accepted with warning, out-of-tolerance (C) fallback
- **Algorithm I**: Waypoint placement fixes — angular-sorted circular vertices (I-A/I-B), convergence guard + WP2 reflection for detours (I-C)

### Testing Status
✓ **Device Tested** on Samsung Galaxy S24+ (Android 16):
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

**Branch**: `main` (all changes committed)

**Key Files Modified:**
- `src/main/kotlin/com/routeplanner/RouteService.kt` — Algorithms A–I implemented, `calculateRoute()` rewritten for three-category selection
- `res/drawable/ic_launcher_*.xml` — App icon updated
- `res/mipmap-anydpi-v26/ic_launcher*.xml` — Icon references updated
- `AGENT_GUIDE.md` — Status updated (test verified, ready for production)

**Build Status**: ✓ Compiles clean (11.0 MB APK, not committed to git)

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

**Status Update**: Route planner Algorithms F-I (self-intersection + backtracking prevention) + Samsung OneUI app icon completed and device-tested on 2026-06-30. All tests passed. Ready for next phase.

**Next Steps**: [User specifies feature, bug fix, or enhancement request]

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
| `generateRoute(waypoints, distance, ...)` | Main entry point for route generation | Active (Algorithms A-I) |
| `calculateRoute()` | Iterates 10 scaling attempts, selects best route | Rewritten for three-category selection (Algorithm H) |
| `selectWaypointStrategy()` | Picks circular/detour/loop mode based on geometry | Active (Algorithm D) |
| `generateCircularWaypoints()` | 4-5 vertices at enforced angular spread, sorted by angle | Implemented (Algorithm I-A) |
| `generateDetourWaypoints()` | Two waypoints with early/late deviation + convergence guard | Implemented (Algorithm I-C) |
| `generateCircularWaypointsForLoop()` | Circular waypoints around midpoint for near-circular routes | Implemented (Algorithm I-B) |
| `computeAntiParallelFraction()` | Scores parallel/backtracking overlap (Algorithm A) | Active |
| `computeSelfIntersectionFraction()` | Scores self-crossing via CCW + non-parallel near-crossings | Implemented (Algorithm F) |
| `computeBacktrackingFraction()` | Scores bearing reversals > 150° | Implemented (Algorithm G) |
| `routeViaGHWithHeadingFlag()` | Routes via GraphHopper, returns whether heading penalty was applied | Active (Algorithm E) |

### Constants to Tune (if needed)

All tunable in `RouteService` companion object:

- `BACKTRACK_BEARING_THRESHOLD_DEG = 150.0` — U-turn detection threshold (degrees)
- `BACKTRACK_FRACTION_STRICT = 0.05` — Early-return gate: max 5% backtracking
- `SELF_INTERSECT_THRESHOLD_M = 100.0` — Near-crossing proximity (meters)
- `PARALLEL_SCORE_GOOD = 0.15` — Anti-parallel early-return threshold (fraction)
- `LOOP_MODE_THRESHOLD = 0.35` — Trigger loop mode if directDist < targetDist × 0.35

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

**You are here**: Algorithms F-I (self-intersection + backtracking prevention) fully implemented, tested, and verified on device. App icon redesigned for Samsung OneUI. Ready for production or further feature development.

**To continue**: Post your next request to the Orchestrator Agent (see "How to Resume Development" above). It will assess, route, and execute the work.

---

**File**: `RESUME.md` (this file)  
**Updated**: 2026-06-30  
**Status**: Ready for next development cycle
