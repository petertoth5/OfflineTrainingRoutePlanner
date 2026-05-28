package com.routeplanner

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.util.shapes.GHPoint
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.*

class RouteService(private val context: Context) {

    private var graphHopper: GraphHopper? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val dataManager = DataManager(context)
    @Volatile private var isGraphHopperReady = false

    init {
        initializeGraphHopper()
    }

    private fun initializeGraphHopper() {
        scope.launch {
            try {
                val osmFile = dataManager.getOsmDataFile()
                if (!osmFile.exists()) {
                    return@launch
                }

                if (!osmFile.canRead()) {
                    return@launch
                }

                graphHopper = GraphHopper().apply {
                    setOSMFile(osmFile.absolutePath)
                    val graphDir = File(context.cacheDir, "gh")
                    if (!graphDir.exists() && !graphDir.mkdirs()) {
                        // Continue anyway
                    }
                    setGraphHopperLocation(graphDir.absolutePath)
                    setProfiles(
                        Profile("foot").setVehicle("foot").setWeighting("fastest"),
                        Profile("bike").setVehicle("bike").setWeighting("fastest")
                    )
                    getCHPreparationHandler().setCHProfiles(
                        CHProfile("foot"),
                        CHProfile("bike")
                    )
                    importOrLoad()
                }
                isGraphHopperReady = true
            } catch (e: OutOfMemoryError) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateRoute(
        startPoint: LatLng,
        endPoint: LatLng,
        distanceMeters: Double,
        minToleranceMeters: Int = 500,
        maxToleranceMeters: Int = 500,
        callback: (Route?, String?) -> Unit
    ) {
        scope.launch {
            try {
                if (!isGraphHopperReady) {
                    Thread.sleep(500)
                    if (!isGraphHopperReady) {
                        return@launch callback(null, "Map data still loading. Please retry in a moment.")
                    }
                }

                val gh = graphHopper ?: return@launch callback(null, "Map data not available. Try changing region.")

                val targetDistance = distanceMeters
                val minDistance = targetDistance - minToleranceMeters
                val maxDistance = targetDistance + maxToleranceMeters

                val result = calculateRoute(startPoint, endPoint, targetDistance, minDistance, maxDistance)

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
        maxDistanceMeters: Double
    ): Pair<Route?, String?>? {
        try {
            val gh = graphHopper ?: return Pair(null, "Routing engine not ready. Retry in a moment.")

            if (start.latitude < -90 || start.latitude > 90 || start.longitude < -180 || start.longitude > 180) {
                return Pair(null, "Invalid start point coordinates")
            }

            if (end.latitude < -90 || end.latitude > 90 || end.longitude < -180 || end.longitude > 180) {
                return Pair(null, "Invalid end point coordinates")
            }

            // Query shortest path first using foot profile (avoids highways)
            val response = gh.route(
                com.graphhopper.GHRequest(
                    GHPoint(start.latitude, start.longitude),
                    GHPoint(end.latitude, end.longitude)
                ).apply {
                    setLocale(Locale.ENGLISH)
                    setProfile("foot")
                    putHint("ch.disable", true) // disable CH for custom edge filtering
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
            (p3lon - p1lon) * (p1lat - p2lat) > (p1lon - p2lon) * (p3lat - p2lat)
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
