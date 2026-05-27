package com.routeplanner

import android.content.Context
import com.google.android.gms.maps.model.LatLng
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.util.shapes.GHPoint
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

class RouteService(private val context: Context) {

    private var graphHopper: GraphHopper? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val dataManager = DataManager(context)

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

                graphHopper = GraphHopper().apply {
                    dataReaderFile = osmFile.absolutePath
                    graphFolder = File(context.cacheDir, "gh").absolutePath
                    profiles = listOf(
                        CHProfile("foot"),
                        CHProfile("bike")
                    )
                    ch.enabled = true
                    build()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateRoute(
        startPoint: LatLng,
        endPoint: LatLng,
        distanceKm: Double,
        callback: (Route?) -> Unit
    ) {
        scope.launch {
            try {
                val gh = graphHopper ?: return@launch callback(null)

                val route = calculateRoute(
                    startPoint,
                    endPoint,
                    distanceKm * 1000 // convert to meters
                )

                withContext(Dispatchers.Main) {
                    callback(route)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        }
    }

    private fun calculateRoute(
        start: LatLng,
        end: LatLng,
        targetDistanceMeters: Double
    ): Route? {
        val gh = graphHopper ?: return null

        // Query shortest path first using foot profile (avoids highways)
        val request = gh.route(
            com.graphhopper.GHRequest(
                GHPoint(start.latitude, start.longitude),
                GHPoint(end.latitude, end.longitude)
            ).apply {
                locale = "en"
                profile = "foot"
                putHint("ch.disable", true) // disable CH for custom edge filtering
            }
        )

        val response = gh.route(request)
        if (response.hasErrors()) return null

        val shortestPath = response.best.points
        val shortestDistance = response.best.distance

        // If shortest path is close enough to target, return it
        if (isWithinTolerance(shortestDistance, targetDistanceMeters)) {
            return Route(
                points = latLngFromGHPoints(shortestPath),
                distance = shortestDistance.toDouble(),
                hasLoops = detectLoops(shortestPath)
            )
        }

        // Otherwise, extend path by adding detours
        return extendRoute(start, end, targetDistanceMeters, shortestPath)
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

    private fun isWithinTolerance(distance: Double, target: Double): Boolean {
        val tolerance = 500 // meters
        return abs(distance - target) <= tolerance
    }

    private fun detectLoops(points: com.graphhopper.util.PointList): Boolean {
        // Simple loop detection: check if any segment intersects with another
        for (i in 0 until points.size - 3) {
            for (j in i + 2 until points.size - 1) {
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
        return (0 until points.size).map { LatLng(points.getLat(it), points.getLon(it)) }
    }

    private fun distanceFromPoints(points: com.graphhopper.util.PointList): Double {
        var distance = 0.0
        for (i in 0 until points.size - 1) {
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
            val file = File(context.getExternalFilesDir(null), filename)
            file.writeText(route.toGpx())
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun destroy() {
        scope.cancel()
        graphHopper?.close()
    }
}
