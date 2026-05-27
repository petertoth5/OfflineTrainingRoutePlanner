package com.routeplanner

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import android.graphics.Color

data class Route(
    val points: List<LatLng>,
    val distance: Double, // meters
    val hasLoops: Boolean = false
) {
    fun toPolylineOptions(): PolylineOptions {
        return PolylineOptions()
            .addAll(points)
            .color(Color.BLUE)
            .width(5f)
    }

    fun toGpx(): String {
        val gpxHeader = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
            <metadata>
                <name>Route Planner</name>
                <time>${System.currentTimeMillis()}</time>
            </metadata>
            <trk>
            <name>Generated Route</name>
            <trkseg>
        """.trimIndent()

        val gpxPoints = points.joinToString("\n") { point ->
            """<trkpt lat="${point.latitude}" lon="${point.longitude}"></trkpt>"""
        }

        val gpxFooter = """
            </trkseg>
            </trk>
            </gpx>
        """.trimIndent()

        return gpxHeader + "\n" + gpxPoints + "\n" + gpxFooter
    }
}
