# Kotlin Development Skill

Actionable guidance for writing Kotlin in OfflineTrainingRoutePlanner, an Android route planning app using GraphHopper routing engine and OSM data.

## Kotlin Conventions & Naming

**Package & Class Naming**
- Packages: lowercase, no underscores (`com.routeplanner`, not `com.route_planner`)
- Classes: PascalCase, descriptive (`RouteService`, `DataManager`, `MapManager`)
- Objects/Singletons: PascalCase (`RegionManager`)
- Constants: UPPER_SNAKE_CASE in `companion object`
  ```kotlin
  companion object {
      private const val PREFS_NAME = "route_planner_prefs"
      private const val PERMISSION_REQUEST_CODE = 100
  }
  ```

**Function & Variable Naming**
- Functions: camelCase, verb-first for actions (`generateRoute()`, `addMarker()`)
- Private functions: camelCase with leading underscore convention optional (not used here, avoid it)
- Variables: camelCase, nouns (`startMarker`, `routeService`, `currentRoute`)
- Boolean variables: is/has prefix (`isSelectingStart`, `hasErrors()`)
- Nullable types: nullable names hint nullability (`startMarker: Marker?`, `currentRoute: Route?`)

**File Organization**
- One public class per file (RouteService in RouteService.kt)
- Imports: stdlib first, then android/androidx, then third-party (graphhopper, osmdroid), then local
- Keep files under 400 lines; split large services into focused classes

## Android Lifecycle & Patterns

**Activity Lifecycle Management** (MainActivity.kt pattern)
- Override lifecycle methods explicitly: `onCreate()`, `onResume()`, `onPause()`, `onDestroy()`
- Cleanup in `onDestroy()` — call `routeService.destroy()` and cancel coroutines
- Use View Binding with `ActivityMainBinding.inflate()`, not findViewById
- Initialize heavy resources (GraphHopper routing) off main thread:
  ```kotlin
  Thread {
      val error = routeService.initializeGraphHopperSync()
      runOnUiThread { /* update UI */ }
  }.start()
  ```

**Coroutines for Threading** (RouteService.kt pattern)
- Create class-scoped CoroutineScope: `CoroutineScope(Dispatchers.Default + Job())`
- Launch heavy work on `Dispatchers.Default` or `Dispatchers.IO`
- Switch back to UI updates with `withContext(Dispatchers.Main)`
- Cancel scope in `destroy()`: `scope.cancel()`
  ```kotlin
  scope.launch {
      // background work
      withContext(Dispatchers.Main) { callback(result, null) }
  }
  ```

**Nullable Handling**
- Prefer safe-call operator: `marker?.let { mapManager.removeMarker(it) }`
- Use `?.use {}` for resource management (streams, file I/O): `file.use { it.writeText(gpxContent) }`
- Avoid double-null checks; extract or validate early

**Error Handling Pattern** (consistent across classes)
- Wrap I/O and system calls in try-catch with specific exception types
- Log errors: `android.util.Log.e("TAG", "message", exception)`
- Provide user feedback via Toast for UI errors
- Return null or error message via callback for async operations
  ```kotlin
  fun generateRoute(..., callback: (Route?, String?) -> Unit) {
      try { ... }
      catch (e: OutOfMemoryError) { callback(null, "Out of memory: ...") }
      catch (e: Exception) { callback(null, "Error: ${e.message}") }
  }
  ```

## GraphHopper API Usage Patterns

**Initialization** (RouteService.kt line 21-74)
- GraphHopper is expensive to initialize (5-15 min first run); use a flag/SharedPreferences to detect profile changes
- Store graph in `context.cacheDir` with a dedicated directory (`gh/`)
- Delete lock file before init to prevent corruption: `File(graphDir, "lock").delete()`
- Use fingerprinting to detect config changes:
  ```kotlin
  val profileFingerprint = "foot_bike_gh6_v1"
  val prefs = context.getSharedPreferences("route_planner_prefs", MODE_PRIVATE)
  if (prefs.getString("gh_profiles", null) != profileFingerprint) {
      graphDir.deleteRecursively()  // force reimport
  }
  ```

**Route Calculation with Waypoints** (calculateRoute() pattern)
- Start with direct routing (GHRequest with 2 points) to validate feasibility
- Expand to multi-point routing with waypoints to meet distance targets
- Use iterative scaling: increase/decrease waypoint radius until distance is within tolerance
  ```kotlin
  for (attempt in 0..9) {
      val waypoints = generateDetourWaypoints(start, end, targetDist, directDist, scale)
      val viaPoints = listOf(start) + waypoints + listOf(end)
      val response = routeViaGH(gh, viaPoints, profile)
      scale *= targetDistanceMeters / actualDistance  // proportional adjustment
  }
  ```

**Headings & Turn Costs**
- Set heading hints (bearing toward next waypoint) to discourage backtracking:
  ```kotlin
  val headings = points.mapIndexed { i, p ->
      if (i < points.size - 1) bearingDeg(p, points[i + 1]) else Double.NaN
  }
  ```
- Disable turn costs for simpler routes: `Profile("foot").setTurnCosts(false)`
- Use `heading_penalty: 300.0` to enforce directional preference without hard constraints

**Error Handling**
- Check `response.hasErrors()` before accessing `response.best`
- Catch `OutOfMemoryError` separately (large regions on memory-constrained devices)
- Fall back gracefully if advanced API features (headings) aren't available:
  ```kotlin
  try { gh.route(request.apply { setHeadings(headings) }) }
  catch (e: Exception) { gh.route(request) }  // retry without headings
  ```

**Points Conversion**
- Convert LatLng to GHPoint for API calls: `GHPoint(latLng.latitude, latLng.longitude)`
- Convert PointList back to LatLng for UI: `LatLng(points.getLat(i), points.getLon(i))`

## Data Manager & Persistence Patterns

**Shared Preferences** (DataManager.kt pattern)
- Use MODE_PRIVATE: `context.getSharedPreferences(name, MODE_PRIVATE)`
- Store region info for persistence across sessions
- Create a `companion object` with constants for keys:
  ```kotlin
  companion object {
      private const val PREFS_NAME = "route_planner_prefs"
      private const val PREFS_CURRENT_REGION = "current_region"
  }
  ```
- Use `prefs.edit().putString(key, value).apply()` (async) not `commit()` (blocks)

**File I/O**
- Use `context.cacheDir` for temporary data (auto-cleared by system)
- Use `context.getExternalFilesDir(null)` for app-specific user files (permissions-aware)
- Always check existence and readability before processing:
  ```kotlin
  if (!osmFile.exists()) return "OSM file not found"
  if (!osmFile.canRead()) return "OSM file not readable"
  ```
- Download with progress: read chunks and report via callback:
  ```kotlin
  val data = ByteArray(8192)
  while (input.read(data).also { count = it } != -1) {
      output.write(data, 0, count)
      val progress = ((downloaded * 100) / fileLength).toInt()
      withContext(Dispatchers.Main) { onProgress(progress) }
  }
  ```
- Validate file size after download; allow 10% tolerance for partial writes

**Storage Permissions**
- Check available space before large downloads: `file.parentFile?.freeSpace`
- Handle InterruptedIOException on cancelled downloads; delete incomplete files
- Use try-catch for SecurityException (permission denied)

## UI & Map Integration Patterns

**View Binding** (MainActivity.kt pattern)
- Initialize in onCreate: `binding = ActivityMainBinding.inflate(layoutInflater)`
- Use `binding.root` to set as content view
- Reference views via binding: `binding.btnGenerate.setOnClickListener { ... }`

**Map Rendering** (MapManager.kt pattern)
- Initialize osmdroid config in init block (once per lifecycle)
- Validate coordinates before adding markers/polylines:
  ```kotlin
  if (latLng.latitude < -90 || latLng.latitude > 90 || 
      latLng.longitude < -180 || latLng.longitude > 180) return null
  ```
- Filter invalid points before rendering polylines
- Always call `mapView.invalidate()` after modifying overlays
- Remove specific overlay types with filter: `mapView.overlays.removeAll { it is Polyline }`

**User Input & Callbacks**
- Use activity result contracts for file pickers (Android 11+):
  ```kotlin
  private val saveGpxLauncher = registerForActivityResult(
      ActivityResultContracts.StartActivityForResult()
  ) { result ->
      if (result.resultCode == RESULT_OK) {
          result.data?.data?.let { uri -> /* handle URI */ }
      }
  }
  ```
- Validate user input before processing (distance range, coordinate bounds)
- Provide feedback via Toast: `Toast.makeText(this, "message", Toast.LENGTH_SHORT).show()`

**Background Task UI Updates**
- Always switch to UI thread with `runOnUiThread { ... }` for updates from worker threads
- Disable buttons during long operations to prevent re-triggering: `binding.btnGenerate.isEnabled = false`
- Update status messages for user feedback: `binding.tvStatus.text = "Generating route..."`

## Data Structures & Models

**Route Data Class** (Route.kt pattern)
- Use data classes with immutable properties: `data class Route(val points: List<LatLng>, val distance: Double)`
- Add domain-specific methods: `toGpx()` for serialization
- Keep calculation logic in service classes, model methods for conversion only

**Coordinate Systems**
- Use `LatLng` (Google Play Services) and `GeoPoint` (osmdroid) as needed; convert at boundaries
- Store internally as LatLng; convert to GeoPoint only for map rendering
- Use Haversine distance for point-to-point calculations: 6371000 m (Earth radius)
- Degrees-to-meters: lat: 111000 m/degree; lon: 111000 * cos(latitude) m/degree

## Code Review Standards

**Always Check**
1. **Null Safety**: All nullable properties have safe-call (`?.`) or let blocks
2. **Thread Safety**: UI updates on Main thread, heavy work off-thread
3. **Resource Cleanup**: Listeners removed, coroutines cancelled, files closed
4. **Error Handling**: Try-catch around I/O, API calls, and system services
5. **Validation**: User input bounds-checked; coordinates valid before routing
6. **Logging**: Errors logged with tags, progress/state logged for debugging
7. **Performance**: No blocking on main thread; progress callbacks for long ops
8. **Consistency**: Naming matches conventions, error messages match style, patterns replicate existing code

**Example Review Question**
- "Is this network or I/O call wrapped in Dispatchers.IO with progress callbacks?"
- "Does this nullable value have a null check or safe operator?"
- "Are resources (file handles, coroutines) cleaned up in try-finally or scope cleanup?"
- "Does this match the existing error message style and logging pattern?"

## Testing Patterns (for future use)

**Unit Tests** (service layer)
- Test route calculation with mock GraphHopper instances
- Test distance calculations with known coordinates
- Test DataManager file I/O with temporary files
- Structure: `@Test fun testGenerateCircularRoute_withinTolerance() { ... }`

**Integration Tests** (with real GraphHopper)
- Test with small OSM extracts (local test data)
- Verify callback chains and error propagation
- Test coroutine scopes complete properly

**UI Tests** (Espresso, for future)
- Click markers, verify display updates
- Test permission flows
- Verify Toast messages appear

**Test Conventions**
- Use `@Rule val instantExecutorRule = InstantTaskExecutorRule()` to run coroutines synchronously
- Mock Android context with Mockito
- Create test fixtures in `src/test/resources/` for small OSM files
