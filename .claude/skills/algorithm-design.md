# Algorithm Design Skill

Guide for designing and validating route planning algorithms in OfflineTrainingRoutePlanner context: waypoint generation, GraphHopper integration, Android performance constraints.

---

## 1. Algorithm Specification Format

### Pseudocode Template

```
ALGORITHM: <Name>
PURPOSE: <What problem it solves>
INPUT: <Parameters and their types>
OUTPUT: <Return type and semantics>
CONSTRAINTS: <Time/space limits, platform specifics>

PSEUDOCODE:
  <Algorithm steps in structured pseudocode>
  
ASSUMPTIONS:
  <Dependencies, valid input ranges, external APIs>
```

### Example: Current Circular Waypoint Generation

```
ALGORITHM: GenerateCircularWaypoints
PURPOSE: Create 3 evenly-spaced waypoints around a center point for circular route generation
INPUT:
  - center: LatLng (user's selected route center)
  - targetDist: Double (meters; desired total route length)
  - scale: Double (unitless; radial scale factor from iteration)
OUTPUT: List<LatLng> (3 waypoints at 120° intervals)
CONSTRAINTS:
  - Time: O(1) — hardcoded 3 waypoints
  - Space: O(1)
  - Platform: Works on Android; trigonometry is CPU-efficient

PSEUDOCODE:
  r ← (targetDist / 2π) × scale
  latDeg ← r / 111000.0          # degrees per meter (approx)
  lonDeg ← r / (111000 × cos(lat))  # adjust for latitude
  
  waypoints ← []
  for i ← 0 to 2:
    angle ← i × 120° (in radians)
    lat ← center.lat + latDeg × cos(angle)
    lon ← center.lon + lonDeg × sin(angle)
    waypoints.append(LatLng(lat, lon))
  
  return waypoints

ASSUMPTIONS:
  - Earth is a sphere (approximation valid for <200km routes)
  - 111000 m/degree is constant (valid for Europe ±5% error)
  - Waypoints will be on roads (validated by GraphHopper routing)
  - scale > 0 (enforced by caller)
```

---

## 2. Complexity Analysis Format

### Time Complexity

Express in Big-O notation relative to:
- `n`: number of waypoints
- `m`: number of route points returned from GraphHopper
- `a`: iteration attempts (typically 10 for this app)

**Example analysis template:**

```
OPERATION: Route generation (calculateRoute)

Main loop iterations:    O(a)          [a ≤ 10, fixed]
Per iteration:
  - Waypoint generation: O(1) circular | O(1) detour
  - GraphHopper routing: O(m log m)    [internal graph search, ~linear for small regions]
  - Loop detection:      O(m²)         [nested segment intersection check]
  - Haversine distance:  O(1)

TOTAL: O(a × (m² + m log m)) ≈ O(m²) worst case
       [For Hungary (~2M nodes): ~50–200ms per attempt on device]

PRACTICAL: 10 attempts × 200ms = 2 seconds UI-acceptable on Android 7.0+
```

### Space Complexity

```
OPERATION: Route generation

waypoints list:         O(n)          [3–4 points]
GraphHopper points:     O(m)          [routed path, ~100–1000 points]
Iteration accumulators: O(1)          [scale, bestRoute, bestDelta]

TOTAL: O(m) — dominated by routed path

DEVICE CONSTRAINT: ~300–400MB heap peak during OSM import
                   ~50–100MB steady state
                   Route with 1000 points: <100KB
```

---

## 3. Waypoint Strategy Design Patterns

### Pattern 1: Circular Route (Same Start/End)

**When to use**: User taps same point twice OR haversine distance < 200m

**Key parameters:**
- `radius = targetDist / (2π)`
- `waypoints = 3` (equilateral triangle)
- `spacing = 120°` intervals

**Pros:**
- Deterministic geometry
- Loop detection easy (route naturally closes)
- No backtracking between start and end

**Cons:**
- Always same shape (no variety)
- Limited to 3 waypoints

**Optimization ideas:**
```
// Add randomized bearing offset for variety
offset_angle ← random(-30°, +30°)
for i ← 0 to 2:
  angle ← (i × 120° + offset_angle) in radians
  ...

// Add 4th waypoint for larger circles
if targetDist > 15000m:
  waypoints ← 4 points at 90° intervals
  ...
```

---

### Pattern 2: Detour Route (Different Start/End)

**When to use**: Two distinct points > 200m apart

**Key geometry:**
- Midpoint between start and end
- Perpendicular offset: `h = sqrt((target/2)² - (direct/2)²)`
- Single waypoint offset from midpoint

**Why it works:**
```
A ─────────────── B    direct distance
  \             /
   \    P      /        A→P→B ≈ target distance
    \         /
     \─────────

Right triangle: AP + PB ≈ target
                AB = direct
                
If target ≤ direct: impossible (no detour needed)
If target > direct:
  AP ≈ PB ≈ target/2
  h² + (direct/2)² = (target/2)²
  h = sqrt((target/2)² - (direct/2)²)
```

**Optimization ideas:**
```
// Add second waypoint for complex detours
if targetDist > 2 × directDist:
  // Generate two perpendicular waypoints (left and right sides)
  // or multiple points along perpendicular axis
  waypoints ← [offset_left, offset_right]

// Randomize perpendicular direction (left vs right)
perpendicular_sign ← random(-1, +1)
lonOffset ← h × perpendicular_sign × ...
```

---

### Pattern 3: Multi-Point Waypoint Strategy (Future)

**Concept:** Place 5–10 waypoints strategically to control route shape.

**Design space:**
- **Linear path control**: Equally-spaced waypoints along start→end line
- **Spiral path**: Waypoints spiraling outward from start
- **Grid sampling**: Divide target rectangle into N×N cells, pick one point per cell
- **Bezier curve**: Smooth curve through control points

**Trade-offs:**
| Strategy | Route Quality | Variety | Perf | Code Complexity |
|----------|---------------|---------|------|-----------------|
| Current (1–3) | Good | Low | 2s | Low |
| 5–10 points | Excellent | High | 5–10s | Medium |
| Spiral | Very Good | High | 3s | Medium |
| Grid | Good | Medium | 4s | High |

---

## 4. Route Planning Optimization Techniques

### Technique 1: Proportional Scale Adjustment

**Problem**: First waypoint guess may be too close or too far.

**Solution**: Iterative scaling with proportional feedback.

```
ALGORITHM: ProportionalScaleAdjustment
PURPOSE: Converge toward target distance within tolerance

scale ← 1.0
for attempt ← 0 to 9:
  waypoints ← generate(center, targetDist, scale)
  route ← routeViaGH(waypoints)
  
  if route.distance in [minDist, maxDist]:
    return route  # Success
  
  # Feedback: scale proportionally to error
  scale ← scale × (targetDist / route.distance)

RATIONALE:
  If route.distance = 5000m and target = 10000m:
    scale ← 1.0 × (10000 / 5000) = 2.0
    → Next radius doubles → route approx doubles
    
  Converges exponentially (halves error per 2–3 attempts)
  Typical: 2–4 attempts sufficient

ANDROID CONTEXT:
  Loop limit = 10 (max ~2s on device)
  If not converged: return best-found route + warning
  Never block UI; run in background thread
```

---

### Technique 2: Anti-Backtracking via Heading Hints

**Problem**: GraphHopper might route backwards on the same road segment.

**Solution**: Set departure headings + heading penalty.

```
headings ← [bearing(wp[i], wp[i+1]) for each waypoint i]

gh.route(request.apply {
  setHeadings(headings)
  putHint("heading_penalty", 300.0)  # meters extra cost
})

EFFECT:
  GraphHopper penalizes approaches to waypoint from wrong direction
  e.g., if bearing is 90° (east) but route approaches from 270° (west), add 300m cost
  
FALLBACK:
  If headings API fails (GH version mismatch): retry without headings
  Preserves reliability on different GraphHopper builds

ANDROID CONTEXT:
  setHeadings() API exists in GH 6.0 ✓
  Heading penalty cost is metadata hint (no compilation) ✓
  Fallback ensures app works if metadata is ignored
```

---

### Technique 3: Loop Detection

**Problem**: User doesn't want route to cross itself (aesthetically bad).

**Algorithm**: O(m²) segment intersection check on route points.

```
ALGORITHM: SimpleLoopDetection
PURPOSE: Return true if any route segment crosses another

INPUT: points = list of LatLng from GraphHopper route
OUTPUT: Boolean

for i ← 0 to size-3:
  for j ← i+2 to size-1:
    if segmentsIntersect(
      points[i], points[i+1],
      points[j], points[j+1]
    ):
      return true

return false

SEGMENT INTERSECTION (2D CCW test):
  ccw(p1, p2, p3) =
    (p3.lon - p1.lon) × (p2.lat - p1.lat) >
    (p2.lon - p1.lon) × (p3.lat - p1.lat)
  
  segments (p1,p2) and (p3,p4) intersect iff:
    ccw(p1, p3, p4) ≠ ccw(p2, p3, p4)  AND
    ccw(p1, p2, p3) ≠ ccw(p1, p2, p4)

COMPLEXITY: O(m²) — acceptable for m ≤ 2000
           500–1000 points on typical Android route: <50ms

LIMITATIONS:
  - False positives near poles (latitude distortion)
  - Doesn't detect self-touches (tangent segments)
  - Doesn't prevent route quality issues (tight loops)
  
FUTURE: Angle-based loop quality metric
```

---

## 5. Performance Validation Approach

### 5.1 Unit Test Template

```kotlin
// File: src/test/kotlin/RouteServiceTest.kt

class RouteServiceTest {
  
  @Test
  fun `circular waypoint generation converges within 2 attempts`() {
    val service = RouteService(mockContext)
    val center = LatLng(47.5, 19.0)  // Budapest
    val targetDist = 10000.0
    
    // First attempt: scale=1.0
    val wp1 = service.generateCircularWaypoints(center, targetDist, 1.0)
    assertTrue(wp1.size == 3)
    
    // Estimate first route distance (mock or use real GH)
    val estimatedDist1 = (wp1[0] haversine wp1[1]) * 3  // rough
    val scale2 = targetDist / estimatedDist1
    
    val wp2 = service.generateCircularWaypoints(center, targetDist, scale2)
    // Should be closer to target after scale adjustment
    assertTrue(wp2.size == 3)
  }
  
  @Test
  fun `detour waypoint geometry satisfies Pythagorean constraint`() {
    val service = RouteService(mockContext)
    val start = LatLng(47.0, 19.0)
    val end = LatLng(47.1, 19.0)
    val targetDist = 20000.0
    val directDist = haversineDistance(start, end)
    
    val wp = service.generateDetourWaypoints(start, end, targetDist, directDist, 1.0)
    assertTrue(wp.size == 1)
    
    // Validate: perpendicular offset should satisfy h² + (direct/2)² ≈ (target/2)²
    val h = haversineDistance(LatLng((start.latitude + end.latitude)/2, ...), wp[0])
    val expected = sqrt((targetDist/2)*(targetDist/2) - (directDist/2)*(directDist/2))
    assertTrue(abs(h - expected) / expected < 0.1)  // ±10% tolerance
  }
  
  @Test
  fun `loop detection marks crossing routes`() {
    // Create figure-8 route points manually
    val figure8Points = listOf(
      LatLng(47.0, 19.0), LatLng(47.05, 19.05),
      LatLng(47.1, 19.0), LatLng(47.05, 18.95),
      LatLng(47.0, 19.0)
    )
    assertTrue(service.detectLoops(figure8Points.toGHPoints()))
  }
}
```

### 5.2 Integration Test Template

```kotlin
@Test
fun `calculateRoute converges to target distance within 5 attempts`() {
  val service = RouteService(context)  // Real GraphHopper
  service.initializeGraphHopperSync()
  
  val start = LatLng(47.497, 19.040)  // Budapest Pest side
  val end = start  // Circular
  val targetDist = 10000.0  // 10km
  val minTol = 500
  val maxTol = 500
  
  var resultRoute: Route? = null
  var resultError: String? = null
  
  service.generateRoute(start, end, targetDist, "foot", minTol, maxTol) { route, error ->
    resultRoute = route
    resultError = error
  }
  
  Thread.sleep(3000)  // Wait for async result
  
  assertNotNull(resultRoute)
  assertTrue(resultRoute!!.distance in (targetDist - maxTol)..(targetDist + maxTol))
  assertTrue(resultRoute!!.points.size > 10)  // Routed path has many points
  assertTrue(resultRoute!!.distance < 20000)  // Sanity check
}
```

### 5.3 Performance Benchmark

```kotlin
@Test
@LargeTest
fun `route generation completes in < 2 seconds on device`() {
  val service = RouteService(context)
  service.initializeGraphHopperSync()
  
  val start = LatLng(47.5, 19.0)
  val end = LatLng(47.6, 19.1)
  val targetDist = 15000.0
  
  val startTime = System.currentTimeMillis()
  
  var completed = false
  service.generateRoute(start, end, targetDist, "foot") { route, _ ->
    completed = true
  }
  
  while (!completed && System.currentTimeMillis() - startTime < 3000) {
    Thread.sleep(100)
  }
  
  val elapsedMs = System.currentTimeMillis() - startTime
  assertTrue(elapsedMs < 2500, "Route generation took ${elapsedMs}ms (should be <2500ms)")
}
```

---

## 6. Testing Algorithmic Correctness

### 6.1 Invariant Checks

**Invariant 1: Route contains start and end**
```
route.points.first() ≈ start (within 50m — GH snap-to-road tolerance)
route.points.last() ≈ end
```

**Invariant 2: Route is monotonically increasing in distance**
```
for i ← 0 to size-1:
  cumulative_dist[i+1] ≥ cumulative_dist[i]
```

**Invariant 3: Haversine distance ≤ routed distance**
```
haversine(start, end) ≤ route.distance
```

**Invariant 4: Waypoints lie on valid lat/lng grid**
```
for each waypoint:
  -90 ≤ lat ≤ 90
  -180 ≤ lon ≤ 180
```

### 6.2 Property-Based Testing

```kotlin
// Use QuickTheories or similar
@RunWith(QuickTheories::class)
class RoutePropertyTests {
  
  @Property
  fun `all generated waypoints have valid coordinates`(
    centerLat: Double,
    centerLon: Double,
    targetDist: Double,
    scale: Double
  ) {
    assume(centerLat in -60..60 && centerLon in -25..35)  // Europe bounds
    assume(targetDist in 1000..100000 && scale in 0.5..3.0)
    
    val wp = service.generateCircularWaypoints(
      LatLng(centerLat, centerLon), targetDist, scale
    )
    
    assertTrue(wp.all { it.latitude in -90..90 && it.longitude in -180..180 })
  }
  
  @Property
  fun `detour waypoint is perpendicular to start-end line`(
    startLat: Double, startLon: Double,
    endLat: Double, endLon: Double,
    targetDist: Double
  ) {
    assume(haversineDistance(startLat, startLon, endLat, endLon) > 100)
    
    val wp = service.generateDetourWaypoints(
      LatLng(startLat, startLon), LatLng(endLat, endLon),
      targetDist, haversineDistance(...), 1.0
    )
    
    // Waypoint should be near perpendicular bisector
    val midLat = (startLat + endLat) / 2
    val midLon = (startLon + endLon) / 2
    
    val dotProduct = ...  // Vector dot product
    assertTrue(abs(dotProduct) < 0.1)  // Nearly perpendicular
  }
}
```

### 6.3 Edge Case Testing

| Case | Input | Expected | Test |
|------|-------|----------|------|
| Same point | start=end, dist=10km | Circular route | `assertTrue(route.hasLoops)` |
| Very close points | haversine < 200m | Circular route | Same as above |
| Target < shortest road path | start A, end B, targetDist < road(A,B) | Error message | `assertNull(result)` |
| Huge target distance | targetDist = 500km | Best-found route + warning | `assertNotNull(result)` |
| Invalid coordinates | lat=200, lon=400 | Early error return | Test in `calculateRoute()` |
| Equator crossing | start=89°, end=-89° | Handle 180° meridian | `beringDeg()` wraps correctly |
| Restricted area | waypoint in military zone | Filter or warning | `filterRestrictedPoints()` |

---

## 7. Android-Specific Performance Constraints

### Memory Budget

| Component | Typical Peak | Limit |
|-----------|--------------|-------|
| OSM import (GH) | 300–400MB | 512MB heap (largeHeap=true) |
| Route + UI | 50–100MB | Available heap |
| Single route (10k points) | <100KB | Negligible |

**Implication**: Proportional scaling loop is safe (10 attempts × 50ms = 500ms, <100MB temp memory).

### CPU Budget

| Operation | Device (Snapdragon 400 equiv) | Limit |
|-----------|-------------------------------|-------|
| Waypoint generation (O(1)) | <1ms | — |
| GraphHopper route (O(m log m)) | 100–200ms | <500ms per attempt |
| Loop detection (O(m²)) | 20–50ms | <100ms |
| **Total per attempt** | 150–250ms | <500ms |
| **10 attempts** | 1500–2500ms | <3000ms (user tolerance) |

**Implication**: Run `calculateRoute()` on background thread; show progress spinner.

### Android Version Support

- **Min API 24** (Android 7.0)
- Trigonometry functions (`sin`, `cos`, `atan2`) → built-in since API 1
- Haversine distance → pure math, no API calls
- GraphHopper 6.0 → vetted for GH6.0 behavior

---

## 8. Checklist: Designing New Algorithms

- [ ] **Specification**: Write pseudocode in template format
- [ ] **Complexity**: Analyze time (Big-O) and space; verify Android constraints
- [ ] **Waypoint strategy**: Choose pattern (circular, detour, multi-point); document pros/cons
- [ ] **Optimization**: Apply feedback mechanisms (proportional scaling, headings, loop detection)
- [ ] **Performance test**: Benchmark on device or emulator; must complete in <2s
- [ ] **Correctness test**: Unit test edge cases + invariant checks
- [ ] **Integration test**: Test with real GraphHopper instance (slow, run on CI)
- [ ] **Property test**: Randomized inputs to find edge cases
- [ ] **Update AGENT_GUIDE.md**: Document algorithm changes and fingerprint bump if profiles change
- [ ] **Commit**: `feat: add/optimize [AlgorithmName]` with pseudocode in commit message

---

## References

- **Current implementation**: `/app/src/main/kotlin/com/routeplanner/RouteService.kt`
- **Route data class**: `/app/src/main/kotlin/com/routeplanner/Route.kt`
- **GraphHopper 6.0 docs**: [https://graphhopper.com/api/1/docs/](https://graphhopper.com/api/1/docs/)
- **Android math**: `kotlin.math.*`
- **Test framework**: JUnit4 + Espresso (device tests)
