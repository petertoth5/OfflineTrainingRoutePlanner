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
        scope.launch {
            try {
                val gh = graphHopper
                if (gh == null) {
                    return@launch callback(null, "Routing engine not ready.")
                }

                val targetDistance = distanceMeters
                val minDistance = targetDistance - minToleranceMeters
                val maxDistance = targetDistance + maxToleranceMeters

                val result = calculateRoute(startPoint, endPoint, targetDistance, minDistance, maxDistance, profile)

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

            // Variety parameters chosen ONCE per generation. Re-randomising inside the scaling
            // loop would change the shape every attempt and break distance convergence. Each call
            // to generateRoute() therefore yields a different (but internally consistent) shape.
            val variety = RouteVariety(
                // Circular: rotate the whole polygon by a random base angle and use 3..5 vertices
                // with small per-vertex angular jitter and per-vertex radius jitter.
                circularStartAngle = random.nextDouble(0.0, 2 * PI),
                circularPointCount = random.nextInt(3, 6), // 3, 4 or 5 waypoints
                circularAngleJitter = (0..4).map { random.nextDouble(-0.20, 0.20) },
                circularRadiusJitter = (0..4).map { random.nextDouble(0.80, 1.20) },
                // Detour: which side of the A->B line the loop bulges, plus where along the line
                // the two control waypoints sit, plus asymmetric offsets for an S/arc shape.
                detourSide = if (random.nextBoolean()) 1.0 else -1.0,
                detourFrac1 = random.nextDouble(0.28, 0.40),
                detourFrac2 = random.nextDouble(0.60, 0.72),
                detourOffset1Factor = random.nextDouble(0.85, 1.15),
                detourOffset2Factor = random.nextDouble(0.85, 1.15)
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

            // Iterate with proportional scaling until distance is within tolerance
            var scale = 1.0
            var bestRoute: Route? = null
            var bestDelta = Double.MAX_VALUE

            for (attempt in 0..9) {
                val waypoints = if (isCircular)
                    generateCircularWaypoints(start, targetDistanceMeters, scale, variety)
                else
                    generateDetourWaypoints(start, end, targetDistanceMeters, directDistM, scale, variety)

                val viaPoints = listOf(start) + waypoints + listOf(end)
                val response = routeViaGH(gh, viaPoints, profile) ?: continue
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
                        hasLoops = isCircular || detectLoops(path.points)
                    )
                }

                if (dist >= minDistanceMeters && dist <= maxDistanceMeters)
                    return Pair(bestRoute, null)

                // Proportional scale adjustment for next attempt
                scale *= targetDistanceMeters / dist
            }

            if (bestRoute != null) {
                val got = bestRoute.distance.toInt()
                val tgt = targetDistanceMeters.toInt()
                Pair(bestRoute, "Best route found: ${got}m (target ${tgt}m). Widen tolerance or adjust points.")
            } else {
                Pair(null, "Cannot generate route. No roads found near waypoints.")
            }
        } catch (e: OutOfMemoryError) {
            Pair(null, "Out of memory: region data too large")
        } catch (e: Exception) {
            Pair(null, "Routing error: ${e.message}")
        }
    }

    private fun routeViaGH(gh: GraphHopper, points: List<LatLng>, profile: String): com.graphhopper.GHResponse? {
        return try {
            val ghPoints = points.map { GHPoint(it.latitude, it.longitude) }
            // Departure heading at each point toward next point — discourages backtracking
            val headings = points.mapIndexed { i, p ->
                if (i < points.size - 1) bearingDeg(p, points[i + 1]) else Double.NaN
            }
            gh.route(
                com.graphhopper.GHRequest(ghPoints).apply {
                    setLocale(Locale.ENGLISH)
                    setProfile(profile)
                    putHint("ch.disable", true)
                    putHint("heading_penalty", 300.0)
                    setHeadings(headings)
                }
            )
        } catch (e: Exception) {
            // Fall back without headings if API differs
            try {
                val ghPoints = points.map { GHPoint(it.latitude, it.longitude) }
                gh.route(
                    com.graphhopper.GHRequest(ghPoints).apply {
                        setLocale(Locale.ENGLISH)
                        setProfile(profile)
                        putHint("ch.disable", true)
                    }
                )
            } catch (e2: Exception) { null }
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

    // Circular route: N waypoints (3..5) spaced evenly around the center, but with a random base
    // rotation plus per-vertex angular and radius jitter so each generation produces a visibly
    // different polygon shape instead of the same equilateral triangle every time.
    // Radius starts at targetDist / (2π) and is scaled each iteration to converge on the target.
    private fun generateCircularWaypoints(center: LatLng, targetDist: Double, scale: Double, variety: RouteVariety): List<LatLng> {
        val n = variety.circularPointCount
        val baseR = (targetDist / (2 * PI)) * scale
        val step = 2 * PI / n
        return (0 until n).map { i ->
            val angle = variety.circularStartAngle + i * step + variety.circularAngleJitter[i]
            val r = baseR * variety.circularRadiusJitter[i]
            val latDeg = r / 111000.0
            val lonDeg = r / (111000.0 * cos(Math.toRadians(center.latitude)))
            LatLng(center.latitude + latDeg * cos(angle), center.longitude + lonDeg * sin(angle))
        }
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

        return listOf(
            pointAt(variety.detourFrac1, variety.detourOffset1Factor),
            pointAt(variety.detourFrac2, variety.detourOffset2Factor)
        )
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
