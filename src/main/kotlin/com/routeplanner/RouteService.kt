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

class RouteService(private val context: Context) {

    @Volatile private var graphHopper: GraphHopper? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val dataManager = DataManager(context)

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

            // Wipe graph dir when profile config changes — old graph is incompatible with new encoders
            val profileFingerprint = "foot_bike_gh6_v1"
            val prefs = context.getSharedPreferences("route_planner_prefs", android.content.Context.MODE_PRIVATE)
            if (prefs.getString("gh_profiles", null) != profileFingerprint) {
                android.util.Log.d("RouteService", "Profile config changed, clearing graph cache")
                graphDir.deleteRecursively()
                graphDir.mkdirs()
            }

            android.util.Log.d("RouteService", "Creating GraphHopper instance (v8.0)")
            graphHopper = GraphHopper().apply {
                setOSMFile(osmFile.absolutePath)
                setGraphHopperLocation(graphDir.absolutePath)
                setProfiles(listOf(
                    Profile("foot").setVehicle("foot").setWeighting("fastest").setTurnCosts(false),
                    Profile("bike").setVehicle("bike").setWeighting("fastest").setTurnCosts(false)
                ))
                android.util.Log.d("RouteService", "Calling importOrLoad - this may take a minute")
                importOrLoad()
                android.util.Log.d("RouteService", "GraphHopper initialized successfully")
            }
            prefs.edit().putString("gh_profiles", profileFingerprint).apply()
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
        try {
            val gh = graphHopper ?: return Pair(null, "Routing engine not ready. Retry in a moment.")

            if (start.latitude < -90 || start.latitude > 90 || start.longitude < -180 || start.longitude > 180) {
                return Pair(null, "Invalid start point coordinates")
            }

            if (end.latitude < -90 || end.latitude > 90 || end.longitude < -180 || end.longitude > 180) {
                return Pair(null, "Invalid end point coordinates")
            }

            // Query shortest path using car profile
            val response = gh.route(
                com.graphhopper.GHRequest(
                    GHPoint(start.latitude, start.longitude),
                    GHPoint(end.latitude, end.longitude)
                ).apply {
                    setLocale(Locale.ENGLISH)
                    setProfile(profile)
                    putHint("ch.disable", true)
                }
            )

            if (response.hasErrors()) {
                val error = response.errors.firstOrNull()?.message ?: "No route found"
                return Pair(null, "Routing error: $error")
            }

            val shortestPath = response.best?.points
            if (shortestPath == null || shortestPath.size() == 0) {
                return Pair(null, "No valid path found between points")
            }

            val shortestDistance = response.best.distance

            if (shortestDistance <= 0) {
                return Pair(null, "Points too close or invalid")
            }

            // If shortest path is within tolerance range, return it
            if (shortestDistance >= minDistanceMeters && shortestDistance <= maxDistanceMeters) {
                return Pair(Route(
                    points = latLngFromGHPoints(shortestPath),
                    distance = shortestDistance.toDouble(),
                    hasLoops = detectLoops(shortestPath)
                ), null)
            }

            // If too short, extend path by adding detours
            if (shortestDistance < minDistanceMeters) {
                val extendedRoute = extendRoute(start, end, targetDistanceMeters, shortestPath)
                if (extendedRoute != null && extendedRoute.distance >= minDistanceMeters && extendedRoute.distance <= maxDistanceMeters) {
                    return Pair(extendedRoute, null)
                }
            }

            // Cannot fulfill requirements
            val minM = minDistanceMeters.toInt()
            val maxM = maxDistanceMeters.toInt()
            val shortestM = shortestDistance.toInt()
            return Pair(null, "Cannot generate $minM-$maxM m route. Shortest path is $shortestM m.")
        } catch (e: OutOfMemoryError) {
            return Pair(null, "Out of memory: region data too large")
        } catch (e: Exception) {
            return Pair(null, "Routing error: ${e.message}")
        }
    }

    private fun extendRoute(
        start: LatLng,
        end: LatLng,
        targetDistance: Double,
        basePath: com.graphhopper.util.PointList
    ): Route? {
        val baseDistance = distanceFromPoints(basePath)
        val extraDistance = targetDistance - baseDistance

        if (extraDistance <= 0) {
            return Route(
                points = latLngFromGHPoints(basePath),
                distance = baseDistance.toDouble()
            )
        }

        // Simple extension: add detours at midpoints
        val extendedPoints = mutableListOf<LatLng>()
        val points = latLngFromGHPoints(basePath)

        for (i in points.indices) {
            extendedPoints.add(points[i])

            // Add detour waypoint at certain intervals
            if (i < points.size - 1 && i % 5 == 0) {
                val detour = createDetour(points[i], points[i + 1], extraDistance / 10)
                extendedPoints.add(detour)
            }
        }

        val finalDistance = distanceFromLatLng(extendedPoints)
        return Route(
            points = extendedPoints,
            distance = finalDistance.toDouble()
        )
    }

    private fun createDetour(from: LatLng, to: LatLng, extraDist: Double): LatLng {
        // Perpendicular offset to create mild detour
        val midLat = (from.latitude + to.latitude) / 2
        val midLng = (from.longitude + to.longitude) / 2
        val offset = (extraDist / 100000) // rough conversion to degrees
        return LatLng(midLat + offset, midLng + offset)
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
