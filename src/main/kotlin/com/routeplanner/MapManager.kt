package com.routeplanner

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.GeoPoint
import com.google.android.gms.maps.model.LatLng
import android.graphics.Color

class MapManager(private val context: Context, private val mapView: MapView) {

    init {
        Configuration.getInstance().userAgentValue = "RoutePlanner/1.0"
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
    }

    fun addMarker(latLng: LatLng, title: String): Marker {
        val marker = Marker(mapView)
        marker.position = GeoPoint(latLng.latitude, latLng.longitude)
        marker.title = title
        mapView.overlays.add(marker)
        mapView.invalidate()
        return marker
    }

    fun removeMarker(marker: Marker) {
        mapView.overlays.remove(marker)
        mapView.invalidate()
    }

    fun clear() {
        mapView.overlays.clear()
        mapView.invalidate()
    }

    fun addPolyline(points: List<LatLng>, color: Int = Color.BLUE, width: Float = 5f) {
        val polyline = Polyline()
        polyline.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
        polyline.setColor(color)
        polyline.setWidth(width)
        mapView.overlays.add(polyline)
        mapView.invalidate()
    }

    fun setMapClickListener(callback: (LatLng) -> Unit) {
        mapView.setOnClickListener { _ ->
            // Get map center on click (osmdroid doesn't have direct click coordinates)
            // Alternative: use GestureDetector overlay for precise tap coordinates
            val center = mapView.mapCenter
            callback(LatLng(center.latitude, center.longitude))
        }
    }

    fun centerOnPoint(latLng: LatLng, zoomLevel: Double = 13.0) {
        mapView.controller.setCenter(GeoPoint(latLng.latitude, latLng.longitude))
        mapView.controller.setZoom(zoomLevel)
    }
}
