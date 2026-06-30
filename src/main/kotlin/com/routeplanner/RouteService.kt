package com.routeplanner

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.util.shapes.GHPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.*
import kotlin.math.PI

class RouteService(private val context: Context) {

    @Volatile private var graphHopper: GraphHopper? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val dataManager = DataManager(context)
    private val random = kotlin.random.Random.Default

    companion object {
        // --- Anti-parallel route analysis (algorithm A) ---
        // Two route segments are considered to overlap/backtrack when their midpoints are within
        // this distance AND their bearings are (anti-)parallel within the tolerance below.
        private const val PROXIMITY_THRESHOLD_M = 80.0
        private const val BEARING_PARALLEL_TOL_DEG = 25.0
        // Only compare segments that are at least this far apart in the (down-sampled) point list,
        // so genuinely consecutive segments along a road are never flagged against each other.
        private const val MIN_SEGMENT_SKIP = 5
        // Cap the O(m²) pairwise scan by down-sampling long polylines to this many points.
        private const val MAX_SAMPLE_POINTS = 200
        // A route whose anti-parallel fraction exceeds this is "ugly" (heavy out-and-back).
        private const val PARALLEL_SCORE_REJECT = 0.30
        // A route at or below this is considered a good shape — accept immediately if in tolerance.
        private const val PARALLEL_SCORE_GOOD = 0.15
        // Penalty added to the score of routes where the GH heading penalty could not be applied
        // (so they are de-prioritised relative to equally-shaped routes that respect headings).
        private const val HEADING_FAIL_PENALTY = 0.15

        // --- Self-intersection detection (algorithm F) ---
        // Two non-adjacent segments count as a near-crossing when their midpoints fall within this
        // distance and they meet at a non-parallel angle (a genuine geometric crossing is detected
        // separately via the CCW test).
        private const val SELF_INTERSECT_THRESHOLD_M = 100.0
        // Minimum index gap between segments compared for self-intersection — adjacent road segments
        // that simply share a vertex must never be flagged as crossing.
        private const val MIN_SI_SKIP = 5

        // --- Backtracking / U-turn detection (algorithm G) ---
        // Consecutive segments whose bearings reverse by more than this are treated as a U-turn; the
        // returning segment's length is counted toward the backtracking fraction.
        private const val BACKTRACK_BEARING_THRESHOLD_DEG = 150.0
        // Backtracking fraction at or below which a route is considered clean enough to early-return.
        private const val BACKTRACK_FRACTION_STRICT = 0.05
        // Backtracking fraction above STRICT but at/below this is still acceptable (warning, no reject).
        private const val BACKTRACK_FRACTION_ACCEPTABLE = 0.10
        // Weight applied to the backtracking fraction when folding it into the composite shape score.
        private const val BACKTRACK_COMPOSITE_WEIGHT = 0.5

        // --- Circular waypoint geometry (algorithms B & D) ---
        // If the start→end straight-line distance is below targetDist × this, the points are close
        // enough that a perpendicular detour would double back — use a loop around the midpoint.
        private const val LOOP_MODE_THRESHOLD = 0.35
        private const val MAX_ANGLE_JITTER_RAD = 0.15
        // Minimum angular separation enforced between circular vertices (~84°, i.e. 90° − 0.10 rad).
        private val MIN_ANGULAR_SEPARATION_RAD = PI / 2 - 0.10
    }

    // Per-generation variety parameters. Picked once per generateRoute() call so the geometry is
    // stable across the proportional-scaling iterations (changing shape each attempt would prevent
    // the distance from converging). See calculateRoute().
    private data class RouteVariety(
        val circularStartAngle: Double,
        val circularPointCount: Int,
        val circularAngleJitter: List<Double>,
        val circularRadiusJitter: List<Double>,
        val detourSide: Double,
        val detourFrac1: Double,
        val detourFrac2: Double,
        val detourOffset1Factor: Double,
        val detourOffset2Factor: Double
    )

    fun initializeGraphHopperSync(): String {
        return try {
            val osmFile = dataManager.getOsmDataFile()
            if (!osmFile.exists()) {
                val msg = "OSM file not found at ${osmFile.absolutePath}"
                android.util.Log.e("RouteService", msg)
                return msg
            }

            val fileSize = osmFile.length() / (1024 * 1024)
            android.util.Log.d("RouteService", "OSM file size: ${fileSize}MB at ${osmFile.absolutePath}")

            if (!osmFile.canRead()) {
                return "OSM file not readable: ${osmFile.absolutePath}"
            }

            val graphDir = File(context.cacheDir, "gh")
            graphDir.mkdirs()
            File(graphDir, "lock").delete()

            val profileFingerprint = "foot_bike_gh6_v1"
            val prefs = context.getSharedPreferences("route_planner_prefs", android.content.Context.MODE_PRIVATE)
            if (prefs.getString("gh_profiles", null) != profileFingerprint) {
                android.util.Log.d("RouteService", "Profile config changed, clearing graph cache")
                graphDir.deleteRecursively()
                graphDir.mkdirs()
                // Save fingerprint BEFORE import — if process is killed mid-import, next launch
                // skips the wipe and lets GraphHopper attempt to resume/reimport on its own
                prefs.edit().putString("gh_profiles", profileFingerprint).apply()
            }

            android.util.Log.d("RouteService", "Creating GraphHopper instance (GH 6.0)")
            graphHopper = GraphHopper().apply {
                setOSMFile(osmFile.absolutePath)
                setGraphHopperLocation(graphDir.absolutePath)
                setProfiles(listOf(
                    Profile("foot").setVehicle("foot").setWeighting("fastest").setTurnCosts(false),
                    Profile("bike").setVehicle("bike").setWeighting("fastest").setTurnCosts(false)
                ))
                android.util.Log.d("RouteService", "Calling importOrLoad")
                importOrLoad()
                android.util.Log.d("RouteService", "GraphHopper initialized successfully")
            }
            ""
        } catch (e: OutOfMemoryError) {
            val msg = "Out of memory: region too large (${e.message})"
            android.util.Log.e("RouteService", msg, e)
            msg
        } catch (e: Exception) {
            val msg = "Init failed: ${e.javaClass.simpleName}: ${e.message}"
            android.util.Log.e("RouteService", msg, e)
            msg
        }
    }

    fun generateRoute(
        startPoint: LatLng,
        endPoint: LatLng,
        distanceMeters: Double,
        profile: String = "foot",
        minToleranceMeters: Int = 500,
        maxToleranceMeters: Int = 500,
        callback: (Route?, String?) -> Unit
    ) {
        // Backwards-compatible overload: two-point / circular routing.
        generateRoute(listOf(startPoint, endPoint), distanceMeters, profile, minToleranceMeters, maxToleranceMeters, callback)
    }

    /**
     * Multi-waypoint route generation. [waypoints] is an ordered list: [start, mid..., end].
     * - 1 entry  → circular route around that point.
     * - 2 entries → start→end detour route (existing behaviour).
     * - 3+ entries → multi-leg route passing through every mid waypoint IN ORDER, with extra
     *   detour sub-waypoints injected to reach the distance target.
     *
     * Per the product decision, the distance target is always kept. If the through-path of the
     * user's ordered waypoints already exceeds the max tolerance, the route is still returned but
     * the callback error carries a warning so the UI can surface it.
     */
    fun generateRoute(
        waypoints: List<LatLng>,
        distanceMeters: Double,
        profile: String = "foot",
        minToleranceMeters: Int = 500,
        maxToleranceMeters: Int = 500,
        callback: (Route?, String?) -> Unit
    ) {
        scope.launch {
            try {
                val gh = graphHopper
                if (gh == null) {
                    return@launch callback(null, "Routing engine not ready.")
                }

                val normalized = normalizeWaypoints(waypoints)
                if (normalized.isEmpty()) {
                    return@launch callback(null, "No start point selected.")
                }

                val targetDistance = distanceMeters
                val minDistance = targetDistance - minToleranceMeters
                val maxDistance = targetDistance + maxToleranceMeters

                val result = when {
                    normalized.size <= 1 -> {
                        // Circular around the single start point (end == start).
                        val p = normalized.first()
                        calculateRoute(p, p, targetDistance, minDistance, maxDistance, profile)
                    }
                    normalized.size == 2 ->
                        calculateRoute(normalized[0], normalized[1], targetDistance, minDistance, maxDistance, profile)
                    else ->
                        calculateMultiWaypointRoute(normalized, targetDistance, minDistance, maxDistance, profile)
                }

                withContext(Dispatchers.Main) {
                    if (result != null) {
                        callback(result.first, result.second)
                    } else {
                        callback(null, "Could not generate route within tolerance range")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null, "Error: ${e.message}")
            }
        }
    }

    /**
     * Normalizes a raw waypoint list into ordered routing order [start, mid..., end].
     * Drops out-of-range coordinates. Order is preserved exactly as the caller supplied it
     * (the UI guarantees mids are appended before the end on tap), so this is primarily a
     * validation/sanitisation pass with a guard against duplicate consecutive points.
     */
    fun normalizeWaypoints(waypoints: List<LatLng>): List<LatLng> {
        val valid = waypoints.filter {
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        if (valid.isEmpty()) return emptyList()
        // Collapse consecutive duplicates (GH rejects zero-length legs).
        val deduped = ArrayList<LatLng>(valid.size)
        for (p in valid) {
            val last = deduped.lastOrNull()
            if (last == null || haversineDistance(last.latitude, last.longitude, p.latitude, p.longitude) > 1.0) {
                deduped.add(p)
            }
        }
        return deduped
    }

    /**
     * Routes through every user waypoint in order, then injects detour sub-waypoints to reach the
     * distance target. The user's waypoints are mandatory through-points and are never reordered or
     * removed. Extra distance is distributed across the legs proportionally to each leg's length,
     * applied as a perpendicular bulge mid-leg, and scaled iteratively toward the target.
     */
    private fun calculateMultiWaypointRoute(
        ordered: List<LatLng>,
        targetDistanceMeters: Double,
        minDistanceMeters: Double,
        maxDistanceMeters: Double,
        profile: String
    ): Pair<Route?, String?>? {
        return try {
            val gh = graphHopper ?: return Pair(null, "Routing engine not ready.")

            // 1. Baseline: route straight through the ordered waypoints (no detours).
            val baseResponse = routeViaGH(gh, ordered, profile)
            if (baseResponse == null || baseResponse.hasErrors()) {
                return Pair(null, "Could not route through the selected waypoints. Move them closer to roads.")
            }
            val basePath = baseResponse.best ?: return Pair(null, "No route found through waypoints.")
            val baseDist = basePath.distance

            // Per product decision: keep the target, but warn if the mandatory through-path already
            // overshoots the tolerance — the route still proceeds as a direct multi-stop path.
            if (baseDist > maxDistanceMeters) {
                val route = Route(
                    points = latLngFromGHPoints(basePath.points),
                    distance = baseDist,
                    hasLoops = detectLoops(basePath.points)
                )
                return Pair(route, "Waypoint order incompatible with distance target — adjust waypoints and try again.")
            }

            // If already within tolerance, done.
            if (baseDist >= minDistanceMeters) {
                val route = Route(
                    points = latLngFromGHPoints(basePath.points),
                    distance = baseDist,
                    hasLoops = detectLoops(basePath.points)
                )
                return Pair(route, null)
            }

            // 2. Need to add distance. Inject one perpendicular bulge per leg, sized so the total
            //    extra length ≈ (target - baseDist), distributed proportionally to leg length.
            //    Iterate with proportional scaling like the single-leg path.
            val legCount = ordered.size - 1
            val sides = (0 until legCount).map { if (random.nextBoolean()) 1.0 else -1.0 }
            val fracJitter = (0 until legCount).map { random.nextDouble(0.42, 0.58) }

            var scale = 1.0
            var bestRoute: Route? = null
            var bestDelta = Double.MAX_VALUE

            for (attempt in 0..9) {
                val extraNeeded = (targetDistanceMeters - baseDist).coerceAtLeast(0.0) * scale
                val augmented = ArrayList<LatLng>()
                augmented.add(ordered.first())
                for (i in 0 until legCount) {
                    val a = ordered[i]
                    val b = ordered[i + 1]
                    val legLen = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
                    val legExtra = if (baseDist > 1.0) extraNeeded * (legLen / baseDist) else extraNeeded / legCount
                    // Perpendicular bulge h from right-triangle geometry: detour leg ≈ legLen + legExtra.
                    val newLegLen = legLen + legExtra
                    val half = newLegLen / 2.0
                    val halfDirect = legLen / 2.0
                    val h = if (half > halfDirect) sqrt(half * half - halfDirect * halfDirect) else legExtra * 0.4
                    if (h > 1.0) {
                        augmented.add(bulgePoint(a, b, fracJitter[i], h, sides[i]))
                    }
                    augmented.add(b)
                }

                val response = routeViaGH(gh, augmented, profile) ?: continue
                if (response.hasErrors()) continue
                val path = response.best ?: continue
                val dist = path.distance
                if (dist <= 0) continue

                val delta = abs(dist - targetDistanceMeters)
                if (delta < bestDelta) {
                    bestDelta = delta
                    bestRoute = Route(
                        points = latLngFromGHPoints(path.points),
                        distance = dist,
                        hasLoops = detectLoops(path.points)
                    )
                }

                if (dist in minDistanceMeters..maxDistanceMeters) return Pair(bestRoute, null)

                scale *= targetDistanceMeters / dist
            }

            if (bestRoute != null) {
                val got = bestRoute.distance.toInt()
                val tgt = targetDistanceMeters.toInt()
                Pair(bestRoute, "Best route found: ${got}m (target ${tgt}m). Widen tolerance or adjust waypoints.")
            } else {
                Pair(null, "Cannot generate route through the selected waypoints.")
            }
        } catch (e: OutOfMemoryError) {
            Pair(null, "Out of memory: region data too large")
        } catch (e: Exception) {
            Pair(null, "Routing error: ${e.message}")
        }
    }

    // Perpendicular bulge point at fraction [frac] along leg a→b, offset [h] meters to [side].
    private fun bulgePoint(a: LatLng, b: LatLng, frac: Double, h: Double, side: Double): LatLng {
        val dLat = b.latitude - a.latitude
        val dLon = b.longitude - a.longitude
        val len = sqrt(dLat * dLat + dLon * dLon)
        val perpLat = (if (len > 1e-9) -dLon / len else 1.0) * side
        val perpLon = (if (len > 1e-9) dLat / len else 0.0) * side
        val baseLat = a.latitude + dLat * frac
        val baseLon = a.longitude + dLon * frac
        val latOffset = (h / 111000.0) * perpLat
        val lonOffset = (h / (111000.0 * cos(Math.toRadians(baseLat)))) * perpLon
        return LatLng(baseLat + latOffset, baseLon + lonOffset)
    }

    private fun calculateRoute(
        start: LatLng,
        end: LatLng,
        targetDistanceMeters: Double,
        minDistanceMeters: Double,
        maxDistanceMeters: Double,
        profile: String = "foot"
    ): Pair<Route?, String?>? {
        return try {
            val gh = graphHopper ?: return Pair(null, "Routing engine not ready.")

            if (start.latitude < -90 || start.latitude > 90 || start.longitude < -180 || start.longitude > 180)
                return Pair(null, "Invalid start point coordinates")
            if (end.latitude < -90 || end.latitude > 90 || end.longitude < -180 || end.longitude > 180)
                return Pair(null, "Invalid end point coordinates")

            val directDistM = haversineDistance(start.latitude, start.longitude, end.latitude, end.longitude)
            val isCircular = directDistM < 200.0
            // Near-circular loop mode (algorithm D): start and end are distinct but close relative to
            // the target, so a perpendicular detour would double back on itself. Loop around the
            // midpoint instead, with the first vertex thrown perpendicular to the start→end line.
            val isLoopMode = !isCircular && directDistM < targetDistanceMeters * LOOP_MODE_THRESHOLD

            // Variety parameters chosen ONCE per generation. Re-randomising inside the scaling
            // loop would change the shape every attempt and break distance convergence. Each call
            // to generateRoute() therefore yields a different (but internally consistent) shape.
            val variety = RouteVariety(
                // Circular: rotate the whole polygon by a random base angle and use 4..5 vertices
                // with small per-vertex angular jitter and per-vertex radius jitter. Min vertex
                // count raised from 3 to 4 (algorithm B) to eliminate the converging-triangle shape.
                circularStartAngle = random.nextDouble(0.0, 2 * PI),
                circularPointCount = random.nextInt(4, 6), // 4 or 5 waypoints
                circularAngleJitter = (0..4).map { random.nextDouble(-MAX_ANGLE_JITTER_RAD, MAX_ANGLE_JITTER_RAD) },
                circularRadiusJitter = (0..4).map { random.nextDouble(0.80, 1.20) },
                // Detour: which side of the A->B line the loop bulges, plus where along the line
                // the two control waypoints sit, plus asymmetric offsets for an S/arc shape.
                // Fractions reformed (algorithm C): deviate earlier and return later so the outbound
                // and inbound legs separate instead of running parallel down the middle.
                detourSide = if (random.nextBoolean()) 1.0 else -1.0,
                detourFrac1 = random.nextDouble(0.12, 0.25),
                detourFrac2 = random.nextDouble(0.75, 0.88),
                detourOffset1Factor = random.nextDouble(0.70, 1.00),
                detourOffset2Factor = random.nextDouble(0.70, 1.00)
            )

            // If target < shortest possible road distance, can't route
            if (!isCircular) {
                val directRoad = routeViaGH(gh, listOf(start, end), profile)
                if (directRoad != null && !directRoad.hasErrors()) {
                    val roadDist = directRoad.best?.distance ?: 0.0
                    if (roadDist > maxDistanceMeters) {
                        return Pair(null, "Shortest road path between points is ${roadDist.toInt()}m — exceeds target. Move points closer or increase distance.")
                    }
                }
            }

            // Iterate with proportional scaling until distance is within tolerance.
            //
            // Multi-objective, three-category selection (algorithm H). For every in-tolerance
            // attempt we score three shape defects:
            //   • anti-parallel fraction (algorithm A) — out-and-back overlap,
            //   • self-intersection fraction (algorithm F) — geometric crossings / bowties,
            //   • backtracking fraction (algorithm G) — sharp U-turn reversals,
            // and fold them into a single composite score. Candidates are bucketed into:
            //   • Category A (clean)  — zero self-intersections,
            //   • Category B (crossed) — has self-intersections but is still in tolerance,
            //   • Category C (out-tolerance) — wrong distance, kept only as a last-resort fallback.
            // Within a category the lowest composite (Category A/B) or smallest distance delta
            // (Category C) wins. We early-return the moment an attempt is genuinely clean: zero
            // self-intersections AND ≤5% backtracking AND ≤15% anti-parallel AND the GH heading
            // penalty was actually applied.
            var scale = 1.0
            var bestCleanRoute: Route? = null
            var bestCleanScore = Double.MAX_VALUE
            var bestCleanAntiParallel = Double.MAX_VALUE
            var bestCleanBacktrack = Double.MAX_VALUE
            var bestCrossedRoute: Route? = null
            var bestCrossedScore = Double.MAX_VALUE
            var bestOutRoute: Route? = null
            var bestOutDelta = Double.MAX_VALUE

            for (attempt in 0..9) {
                val waypoints = selectWaypointStrategy(
                    start, end, targetDistanceMeters, directDistM, scale, variety, isCircular, isLoopMode
                )

                val viaPoints = listOf(start) + waypoints + listOf(end)
                val (response, headingApplied) = routeViaGHWithHeadingFlag(gh, viaPoints, profile)
                if (response == null || response.hasErrors()) continue

                val path = response.best ?: continue
                val dist = path.distance
                if (dist <= 0) continue

                val routePoints = latLngFromGHPoints(path.points)
                val route = Route(
                    points = routePoints,
                    distance = dist,
                    hasLoops = isCircular || isLoopMode || detectLoops(path.points)
                )

                if (dist in minDistanceMeters..maxDistanceMeters) {
                    val antiParallel = computeAntiParallelFraction(routePoints, dist)
                    val selfFrac = computeSelfIntersectionFraction(routePoints, dist)
                    val backtrackFrac = computeBacktrackingFraction(routePoints)
                    val composite = antiParallel +
                        backtrackFrac * BACKTRACK_COMPOSITE_WEIGHT +
                        (if (headingApplied) 0.0 else HEADING_FAIL_PENALTY)

                    // Early return: perfectly clean, in-tolerance, heading-respecting route.
                    if (selfFrac <= 0.0 &&
                        backtrackFrac <= BACKTRACK_FRACTION_STRICT &&
                        antiParallel <= PARALLEL_SCORE_GOOD &&
                        headingApplied
                    ) {
                        return Pair(route, null)
                    }

                    if (selfFrac <= 0.0) {
                        // Category A — no self-intersection.
                        if (composite < bestCleanScore) {
                            bestCleanScore = composite
                            bestCleanAntiParallel = antiParallel
                            bestCleanBacktrack = backtrackFrac
                            bestCleanRoute = route
                        }
                    } else {
                        // Category B — crosses itself, but distance is acceptable.
                        if (composite < bestCrossedScore) {
                            bestCrossedScore = composite
                            bestCrossedRoute = route
                        }
                    }
                } else {
                    // Category C — out of tolerance; track the closest by distance as a fallback.
                    val delta = abs(dist - targetDistanceMeters)
                    if (delta < bestOutDelta) {
                        bestOutDelta = delta
                        bestOutRoute = route
                    }
                }

                // Proportional scale adjustment for next attempt
                scale *= targetDistanceMeters / dist
            }

            when {
                bestCleanRoute != null -> {
                    // Best clean (non-self-intersecting) route. Surface a soft warning only if it
                    // still doubles back (anti-parallel-heavy) or backtracks beyond the acceptable
                    // fraction.
                    val warn = if (bestCleanAntiParallel > PARALLEL_SCORE_REJECT ||
                        bestCleanBacktrack > BACKTRACK_FRACTION_ACCEPTABLE)
                        "Route generated, but it doubles back on itself in places. Regenerate for a cleaner shape."
                    else null
                    Pair(bestCleanRoute, warn)
                }
                bestCrossedRoute != null -> {
                    // In tolerance but every candidate crosses itself.
                    Pair(bestCrossedRoute, "Route generated, but it crosses itself in places. Regenerate for a cleaner shape.")
                }
                bestOutRoute != null -> {
                    val got = bestOutRoute.distance.toInt()
                    val tgt = targetDistanceMeters.toInt()
                    Pair(bestOutRoute, "Best route found: ${got}m (target ${tgt}m). Widen tolerance or adjust points.")
                }
                else -> Pair(null, "Cannot generate route. No roads found near waypoints.")
            }
        } catch (e: OutOfMemoryError) {
            Pair(null, "Out of memory: region data too large")
        } catch (e: Exception) {
            Pair(null, "Routing error: ${e.message}")
        }
    }

    private fun routeViaGH(gh: GraphHopper, points: List<LatLng>, profile: String): com.graphhopper.GHResponse? {
        return routeViaGHWithHeadingFlag(gh, points, profile).first
    }

    /**
     * Routes through [points] and reports whether the anti-backtracking heading penalty was
     * actually applied. The primary attempt passes per-waypoint departure headings plus a
     * heading_penalty hint to discourage U-turns; if GH rejects that request shape we retry
     * without headings. The boolean lets the caller (algorithm E) de-prioritise routes that
     * could not benefit from the anti-backtracking constraint.
     *
     * @return Pair(response, headingApplied). response is null only if both attempts threw.
     */
    private fun routeViaGHWithHeadingFlag(
        gh: GraphHopper,
        points: List<LatLng>,
        profile: String
    ): Pair<com.graphhopper.GHResponse?, Boolean> {
        return try {
            val ghPoints = points.map { GHPoint(it.latitude, it.longitude) }
            // Departure heading at each point toward next point — discourages backtracking
            val headings = points.mapIndexed { i, p ->
                if (i < points.size - 1) bearingDeg(p, points[i + 1]) else Double.NaN
            }
            val response = gh.route(
                com.graphhopper.GHRequest(ghPoints).apply {
                    setLocale(Locale.ENGLISH)
                    setProfile(profile)
                    putHint("ch.disable", true)
                    putHint("heading_penalty", 300.0)
                    setHeadings(headings)
                }
            )
            Pair(response, true)
        } catch (e: Exception) {
            // Fall back without headings if API differs
            try {
                val ghPoints = points.map { GHPoint(it.latitude, it.longitude) }
                val response = gh.route(
                    com.graphhopper.GHRequest(ghPoints).apply {
                        setLocale(Locale.ENGLISH)
                        setProfile(profile)
                        putHint("ch.disable", true)
                    }
                )
                Pair(response, false)
            } catch (e2: Exception) { Pair(null, false) }
        }
    }

    private fun bearingDeg(from: LatLng, to: LatLng): Double {
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val x = sin(dLon) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(x, y)) + 360) % 360
    }

    /**
     * Chooses and runs the waypoint-generation strategy for a single start→end pair (algorithm E
     * dispatch). Circular (start≈end), near-circular loop (close but distinct points), or the
     * standard two-point detour.
     */
    private fun selectWaypointStrategy(
        start: LatLng,
        end: LatLng,
        targetDist: Double,
        directDist: Double,
        scale: Double,
        variety: RouteVariety,
        isCircular: Boolean,
        isLoopMode: Boolean
    ): List<LatLng> = when {
        isCircular -> generateCircularWaypoints(start, targetDist, scale, variety)
        isLoopMode -> {
            val midpoint = LatLng((start.latitude + end.latitude) / 2.0, (start.longitude + end.longitude) / 2.0)
            generateCircularWaypointsForLoop(midpoint, start, end, targetDist, directDist, scale, variety)
        }
        else -> generateDetourWaypoints(start, end, targetDist, directDist, scale, variety)
    }

    // Circular route: N waypoints (4..5) spaced evenly around the center, but with a random base
    // rotation plus per-vertex angular and radius jitter so each generation produces a visibly
    // different polygon shape. A minimum angular separation (algorithm B) is enforced after the
    // jitter so two vertices can never collapse together into a degenerate sliver.
    // Radius starts at targetDist / (2π) and is scaled each iteration to converge on the target.
    private fun generateCircularWaypoints(center: LatLng, targetDist: Double, scale: Double, variety: RouteVariety): List<LatLng> {
        val n = variety.circularPointCount
        val baseR = (targetDist / (2 * PI)) * scale
        val step = 2 * PI / n
        val rawAngles = DoubleArray(n) { i ->
            variety.circularStartAngle + i * step + variety.circularAngleJitter[i]
        }
        val angles = enforceAngularSpread(rawAngles, MIN_ANGULAR_SEPARATION_RAD)
        // Algorithm I-A: enforceAngularSpread preserves input index order so the parallel radius
        // jitter stays aligned, but the polygon must be visited in ascending angular order or the
        // routed loop crosses itself. Pair each (angle, radiusJitter), sort by angle, then project.
        return (0 until n)
            .map { i -> Pair(angles[i], variety.circularRadiusJitter[i]) }
            .sortedBy { normalizeAngle(it.first) }
            .map { (angle, radiusJitter) -> projectFromCenter(center, angle, baseR * radiusJitter) }
    }

    /**
     * Near-circular loop waypoints (algorithm D). When start and end are close relative to the
     * target distance, a perpendicular detour would just double back; instead we wrap a loop of
     * vertices around the [midpoint]. The first vertex is oriented perpendicular to the start→end
     * bearing so the loop bulges out to the side rather than along the (short) start→end axis.
     */
    private fun generateCircularWaypointsForLoop(
        midpoint: LatLng,
        start: LatLng,
        end: LatLng,
        targetDist: Double,
        directDist: Double,
        scale: Double,
        variety: RouteVariety
    ): List<LatLng> {
        val n = variety.circularPointCount
        // Perpendicular to the start→end compass bearing (bearingDeg: 0°=N, clockwise; the circular
        // projection below uses the same convention, lat∝cos, lon∝sin).
        val perpBaseAngle = Math.toRadians(bearingDeg(start, end)) + PI / 2.0
        // Bias the loop radius outward by the start↔end separation so the loop comfortably clears
        // both endpoints rather than collapsing between them.
        val baseR = ((targetDist / (2 * PI)) + directDist / 2.0) * scale
        val step = 2 * PI / n
        val rawAngles = DoubleArray(n) { i ->
            perpBaseAngle + i * step + variety.circularAngleJitter[i]
        }
        val angles = enforceAngularSpread(rawAngles, MIN_ANGULAR_SEPARATION_RAD)
        // Algorithm I-B: visit loop vertices in ascending angular order (see generateCircularWaypoints).
        return (0 until n)
            .map { i -> Pair(angles[i], variety.circularRadiusJitter[i]) }
            .sortedBy { normalizeAngle(it.first) }
            .map { (angle, radiusJitter) -> projectFromCenter(midpoint, angle, baseR * radiusJitter) }
    }

    // Projects a point [r] meters from [center] along compass [angle] (radians, 0=N clockwise).
    private fun projectFromCenter(center: LatLng, angle: Double, r: Double): LatLng {
        val latDeg = r / 111000.0
        val lonDeg = r / (111000.0 * cos(Math.toRadians(center.latitude)))
        return LatLng(center.latitude + latDeg * cos(angle), center.longitude + lonDeg * sin(angle))
    }

    /**
     * Anti-parallel fraction scorer (algorithm A). Measures how much of [routePoints] runs parallel
     * to, or backtracks over, another part of the same route — the visual signature of an ugly
     * out-and-back. Two segments count as overlapping when their midpoints are within
     * [PROXIMITY_THRESHOLD_M] AND their bearings are parallel or anti-parallel within
     * [BEARING_PARALLEL_TOL_DEG]. Only segment pairs at least [MIN_SEGMENT_SKIP] apart in the list
     * are compared, so consecutive road segments are never falsely flagged. Long polylines are
     * down-sampled to [MAX_SAMPLE_POINTS] to bound the O(m²) scan.
     *
     * @return fraction of route length (0.0..1.0) belonging to overlapping segments.
     */
    internal fun computeAntiParallelFraction(routePoints: List<LatLng>, totalDistM: Double): Double {
        if (routePoints.size < 2 || totalDistM <= 0.0) return 0.0

        val pts = downsample(routePoints, MAX_SAMPLE_POINTS)
        val segCount = pts.size - 1
        if (segCount < 2) return 0.0

        val segLen = DoubleArray(segCount)
        val segBearing = DoubleArray(segCount)
        val midLat = DoubleArray(segCount)
        val midLon = DoubleArray(segCount)
        var sampledTotal = 0.0
        for (i in 0 until segCount) {
            val a = pts[i]
            val b = pts[i + 1]
            segLen[i] = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
            segBearing[i] = bearingDeg(a, b)
            midLat[i] = (a.latitude + b.latitude) / 2.0
            midLon[i] = (a.longitude + b.longitude) / 2.0
            sampledTotal += segLen[i]
        }
        if (sampledTotal <= 0.0) return 0.0

        val affected = BooleanArray(segCount)
        for (i in 0 until segCount) {
            var j = i + MIN_SEGMENT_SKIP
            while (j < segCount) {
                val d = haversineDistance(midLat[i], midLon[i], midLat[j], midLon[j])
                if (d <= PROXIMITY_THRESHOLD_M && bearingsParallel(segBearing[i], segBearing[j])) {
                    affected[i] = true
                    affected[j] = true
                }
                j++
            }
        }

        var affectedLen = 0.0
        for (i in 0 until segCount) if (affected[i]) affectedLen += segLen[i]
        return (affectedLen / sampledTotal).coerceIn(0.0, 1.0)
    }

    // True when two compass bearings are parallel (same heading) or anti-parallel (opposite
    // heading) within BEARING_PARALLEL_TOL_DEG. Anti-parallel = backtracking on the same line.
    private fun bearingsParallel(b1: Double, b2: Double): Boolean {
        val diff = angularDifference(b1, b2)
        return diff <= BEARING_PARALLEL_TOL_DEG || diff >= 180.0 - BEARING_PARALLEL_TOL_DEG
    }

    // Normalizes the absolute difference between two compass bearings to the range [0°, 180°].
    private fun angularDifference(b1: Double, b2: Double): Double {
        var diff = abs(b1 - b2) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        return diff
    }

    /**
     * Self-intersection fraction scorer (algorithm F). Measures how much of [routePoints] crosses
     * over itself — true geometric segment crossings (CCW test) as well as near-crossings where two
     * non-adjacent segments pass within [SELF_INTERSECT_THRESHOLD_M] of each other at a non-parallel
     * angle (a near miss that visually reads as a knot). Anti-parallel overlaps are intentionally
     * excluded here — those belong to the out-and-back detector (algorithm A). Only segment pairs at
     * least [MIN_SI_SKIP] apart in the list are compared so adjacent road segments sharing a vertex
     * are never flagged. Long polylines are down-sampled to [MAX_SAMPLE_POINTS] to bound the O(m²)
     * scan.
     *
     * @return fraction of route length (0.0..1.0) belonging to self-intersecting segments.
     */
    internal fun computeSelfIntersectionFraction(routePoints: List<LatLng>, totalDistM: Double): Double {
        if (routePoints.size < 2 || totalDistM <= 0.0) return 0.0

        val pts = downsample(routePoints, MAX_SAMPLE_POINTS)
        val segCount = pts.size - 1
        if (segCount < 2) return 0.0

        val segLen = DoubleArray(segCount)
        val segBearing = DoubleArray(segCount)
        val midLat = DoubleArray(segCount)
        val midLon = DoubleArray(segCount)
        var sampledTotal = 0.0
        for (i in 0 until segCount) {
            val a = pts[i]
            val b = pts[i + 1]
            segLen[i] = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
            segBearing[i] = bearingDeg(a, b)
            midLat[i] = (a.latitude + b.latitude) / 2.0
            midLon[i] = (a.longitude + b.longitude) / 2.0
            sampledTotal += segLen[i]
        }
        if (sampledTotal <= 0.0) return 0.0

        val affected = BooleanArray(segCount)
        for (i in 0 until segCount) {
            var j = i + MIN_SI_SKIP
            while (j < segCount) {
                val crosses = segmentsIntersect(pts[i], pts[i + 1], pts[j], pts[j + 1])
                val nearCrossNonParallel = if (!crosses) {
                    val d = haversineDistance(midLat[i], midLon[i], midLat[j], midLon[j])
                    val ad = angularDifference(segBearing[i], segBearing[j])
                    d <= SELF_INTERSECT_THRESHOLD_M &&
                        ad > BEARING_PARALLEL_TOL_DEG &&
                        ad < 180.0 - BEARING_PARALLEL_TOL_DEG
                } else false
                if (crosses || nearCrossNonParallel) {
                    affected[i] = true
                    affected[j] = true
                }
                j++
            }
        }

        var affectedLen = 0.0
        for (i in 0 until segCount) if (affected[i]) affectedLen += segLen[i]
        return (affectedLen / sampledTotal).coerceIn(0.0, 1.0)
    }

    /**
     * Backtracking / U-turn fraction scorer (algorithm G). Scans the FULL polyline (no down-sampling,
     * so sharp single-vertex reversals are not smoothed away) and flags every segment whose bearing
     * reverses by more than [BACKTRACK_BEARING_THRESHOLD_DEG] relative to the preceding segment. The
     * length of each flagged (returning) segment is summed.
     *
     * @return fraction of route length (0.0..1.0) that immediately doubles back on the prior heading.
     */
    internal fun computeBacktrackingFraction(routePoints: List<LatLng>): Double {
        if (routePoints.size < 3) return 0.0
        val segCount = routePoints.size - 1

        val segLen = DoubleArray(segCount)
        val segBearing = DoubleArray(segCount)
        var total = 0.0
        for (i in 0 until segCount) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            segLen[i] = haversineDistance(a.latitude, a.longitude, b.latitude, b.longitude)
            segBearing[i] = bearingDeg(a, b)
            total += segLen[i]
        }
        if (total <= 0.0) return 0.0

        val affected = BooleanArray(segCount)
        for (i in 0 until segCount - 1) {
            if (angularDifference(segBearing[i], segBearing[i + 1]) > BACKTRACK_BEARING_THRESHOLD_DEG) {
                affected[i + 1] = true
            }
        }

        var backLen = 0.0
        for (i in 0 until segCount) if (affected[i]) backLen += segLen[i]
        return (backLen / total).coerceIn(0.0, 1.0)
    }

    // Down-samples a polyline to at most [maxPts] points by uniform striding, always keeping the
    // first and last point so total length is approximately preserved.
    private fun downsample(points: List<LatLng>, maxPts: Int): List<LatLng> {
        if (points.size <= maxPts) return points
        val stride = ceil(points.size.toDouble() / maxPts).toInt().coerceAtLeast(1)
        val result = ArrayList<LatLng>(maxPts + 1)
        var i = 0
        while (i < points.size) {
            result.add(points[i])
            i += stride
        }
        if (result.last() !== points.last()) result.add(points.last())
        return result
    }

    /**
     * Enforces a minimum angular separation between [angles] (radians) arranged on a circle
     * (algorithm B). Returns a new array in the SAME index order as the input, so callers that map
     * a parallel array (e.g. per-vertex radius jitter) stay aligned. The requested separation is
     * capped at 2π/n so it is always geometrically satisfiable. Vertices are pushed apart with
     * minimal displacement; if forward relaxation cannot also satisfy the wrap-around gap the
     * angles fall back to a perfectly even distribution anchored at the smallest input angle.
     */
    internal fun enforceAngularSpread(angles: DoubleArray, minSepRad: Double): DoubleArray {
        val n = angles.size
        if (n <= 1) return angles.copyOf()

        val twoPi = 2 * PI
        val sep = min(minSepRad, twoPi / n)

        // Sort by normalized angle, remembering original indices to restore order at the end.
        val order = (0 until n).sortedBy { normalizeAngle(angles[it]) }
        val sorted = DoubleArray(n) { normalizeAngle(angles[order[it]]) }

        // Forward relaxation: push each vertex out so it is at least [sep] beyond its predecessor.
        for (i in 1 until n) {
            if (sorted[i] - sorted[i - 1] < sep) sorted[i] = sorted[i - 1] + sep
        }

        // Wrap-around gap between the last vertex and the first (+2π). If forward relaxation grew
        // the chain too long to leave room here, degrade gracefully to even spacing.
        val wrapGap = (sorted[0] + twoPi) - sorted[n - 1]
        if (wrapGap < sep - 1e-9) {
            val even = twoPi / n
            for (i in 0 until n) sorted[i] = sorted[0] + i * even
        }

        val result = DoubleArray(n)
        for (k in 0 until n) result[order[k]] = sorted[k]
        return result
    }

    private fun normalizeAngle(a: Double): Double {
        val twoPi = 2 * PI
        var x = a % twoPi
        if (x < 0) x += twoPi
        return x
    }

    // A→B detour: TWO waypoints offset perpendicularly from points along the A→B line (at
    // randomised fractions of the way), both bulging to a randomly chosen side with asymmetric
    // offsets. This yields varied arc/curve shapes rather than a single fixed perpendicular point.
    // Total perpendicular offset h is derived from the right-triangle geometry of A→P→B so the
    // routed distance still scales toward targetDist.
    private fun generateDetourWaypoints(start: LatLng, end: LatLng, targetDist: Double, directDist: Double, scale: Double, variety: RouteVariety): List<LatLng> {
        // Perpendicular unit vector to the start→end direction
        val dLat = end.latitude - start.latitude
        val dLon = end.longitude - start.longitude
        val len = sqrt(dLat * dLat + dLon * dLon)
        val perpLat = (if (len > 1e-9) -dLon / len else 1.0) * variety.detourSide
        val perpLon = (if (len > 1e-9) dLat / len else 0.0) * variety.detourSide

        // h = perpendicular offset so that A→P→B ≈ targetDist
        val half = targetDist / 2.0
        val halfDirect = directDist / 2.0
        val h = if (half > halfDirect) sqrt(half * half - halfDirect * halfDirect) else targetDist * 0.4
        val hScaled = h * scale

        fun pointAt(frac: Double, offsetFactor: Double): LatLng {
            val baseLat = start.latitude + (end.latitude - start.latitude) * frac
            val baseLon = start.longitude + (end.longitude - start.longitude) * frac
            val off = hScaled * offsetFactor
            val latOffset = (off / 111000.0) * perpLat
            val lonOffset = (off / (111000.0 * cos(Math.toRadians(baseLat)))) * perpLon
            return LatLng(baseLat + latOffset, baseLon + lonOffset)
        }

        val wp1 = pointAt(variety.detourFrac1, variety.detourOffset1Factor)
        var wp2 = pointAt(variety.detourFrac2, variety.detourOffset2Factor)

        // Algorithm I-C: convergence guard. When the two control waypoints end up offset such that
        // segment WP1→WP2 crosses the start→end baseline, the quadrilateral {start, WP1, WP2, end}
        // is a self-intersecting bowtie and the routed detour folds over itself. Reflect WP2 across
        // the start→end line to the opposite side so the quadrilateral becomes simple (convex/arc).
        if (segmentsIntersect(wp1, wp2, start, end)) {
            wp2 = reflectAcrossLine(wp2, start, end)
        }

        return listOf(wp1, wp2)
    }

    /**
     * Reflects [point] across the (infinite) line through [lineA] and [lineB] using a planar
     * lat/lon approximation (consistent with the rest of the detour geometry). Returns [point]
     * unchanged for a degenerate (zero-length) line. Used by algorithm I-C to flip a control
     * waypoint to the opposite side of the start→end baseline.
     */
    internal fun reflectAcrossLine(point: LatLng, lineA: LatLng, lineB: LatLng): LatLng {
        val dLat = lineB.latitude - lineA.latitude
        val dLon = lineB.longitude - lineA.longitude
        val lenSq = dLat * dLat + dLon * dLon
        if (lenSq < 1e-18) return point
        // Project (point - lineA) onto the line direction to find the foot of the perpendicular,
        // then reflect: reflected = 2·foot − point.
        val t = ((point.latitude - lineA.latitude) * dLat + (point.longitude - lineA.longitude) * dLon) / lenSq
        val footLat = lineA.latitude + t * dLat
        val footLon = lineA.longitude + t * dLon
        return LatLng(2 * footLat - point.latitude, 2 * footLon - point.longitude)
    }


    private fun detectLoops(points: com.graphhopper.util.PointList): Boolean {
        // Simple loop detection: check if any segment intersects with another
        for (i in 0 until points.size() - 3) {
            for (j in i + 2 until points.size() - 1) {
                if (segmentsIntersect(
                    points.getLat(i), points.getLon(i),
                    points.getLat(i + 1), points.getLon(i + 1),
                    points.getLat(j), points.getLon(j),
                    points.getLat(j + 1), points.getLon(j + 1)
                )) {
                    return true
                }
            }
        }
        return false
    }

    // LatLng convenience overload of the CCW segment-intersection test below. Treats latitude as the
    // y axis and longitude as the x axis (planar approximation), matching detectLoops().
    private fun segmentsIntersect(a: LatLng, b: LatLng, c: LatLng, d: LatLng): Boolean =
        segmentsIntersect(
            a.latitude, a.longitude,
            b.latitude, b.longitude,
            c.latitude, c.longitude,
            d.latitude, d.longitude
        )

    private fun segmentsIntersect(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
        lat3: Double, lon3: Double,
        lat4: Double, lon4: Double
    ): Boolean {
        val ccw = { p1lat: Double, p1lon: Double, p2lat: Double, p2lon: Double, p3lat: Double, p3lon: Double ->
            (p3lon - p1lon) * (p2lat - p1lat) > (p2lon - p1lon) * (p3lat - p1lat)
        }
        return ccw(lat1, lon1, lat3, lon3, lat4, lon4) != ccw(lat2, lon2, lat3, lon3, lat4, lon4) &&
               ccw(lat1, lon1, lat2, lon2, lat3, lon3) != ccw(lat1, lon1, lat2, lon2, lat4, lon4)
    }

    private fun latLngFromGHPoints(points: com.graphhopper.util.PointList): List<LatLng> {
        return (0 until points.size()).map { LatLng(points.getLat(it), points.getLon(it)) }
    }

    private fun distanceFromPoints(points: com.graphhopper.util.PointList): Double {
        var distance = 0.0
        for (i in 0 until points.size() - 1) {
            val lat1 = points.getLat(i)
            val lon1 = points.getLon(i)
            val lat2 = points.getLat(i + 1)
            val lon2 = points.getLon(i + 1)
            distance += haversineDistance(lat1, lon1, lat2, lon2)
        }
        return distance
    }

    private fun distanceFromLatLng(points: List<LatLng>): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
            distance += haversineDistance(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return distance
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun isRestrictedArea(lat: Double, lon: Double): Boolean {
        // Placeholder: check against known restricted areas (military bases, private zones, etc)
        // In real app, load from local database or OSM data
        // For now, always return false - integrate real data later
        return false
    }

    private fun filterRestrictedPoints(points: List<LatLng>): List<LatLng> {
        return points.filter { !isRestrictedArea(it.latitude, it.longitude) }
    }

    fun exportGpx(route: Route, filename: String): File? {
        return try {
            if (route.points.isEmpty()) {
                return null
            }

            val externalDir = context.getExternalFilesDir(null)
            if (externalDir == null) {
                return null
            }

            if (!externalDir.exists() && !externalDir.mkdirs()) {
                return null
            }

            val file = File(externalDir, filename)
            val gpxContent = route.toGpx()

            if (gpxContent.isEmpty()) {
                return null
            }

            file.writeText(gpxContent)

            if (!file.exists() || file.length() == 0L) {
                return null
            }

            file
        } catch (e: SecurityException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun destroy() {
        scope.cancel()
        graphHopper?.close()
    }
}
