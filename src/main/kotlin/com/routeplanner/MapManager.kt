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
        try {
            Configuration.getInstance().userAgentValue = "RoutePlanner/1.0"
            mapView.setTileSource(TileSourceFactory.MAPNIK)
            mapView.setMultiTouchControls(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addMarker(latLng: LatLng, title: String, label: String? = null): Marker? {
        return try {
            if (latLng.latitude < -90 || latLng.latitude > 90 ||
                latLng.longitude < -180 || latLng.longitude > 180) {
                return null
            }

            val marker = Marker(mapView)
            marker.position = GeoPoint(latLng.latitude, latLng.longitude)
            marker.title = title
            // Simple osmdroid text label drawn next to the marker icon (S / E / 1 / 2 ...).
            // Fast, no custom drawables — uses osmdroid's built-in text-label rendering.
            if (label != null) {
                try {
                    marker.setTextIcon(label)
                } catch (e: Exception) {
                    // Older/newer osmdroid signature differences — fall back to title only.
                    e.printStackTrace()
                }
            }
            mapView.overlays.add(marker)
            mapView.invalidate()
            marker
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Makes a marker draggable and reports its final position when the user releases it.
     * onDragEnd fires once per drop with the marker's new LatLng (suitable for triggering a
     * route regeneration). onDrag fires continuously during the drag for live UI feedback and
     * may be omitted.
     */
    fun makeMarkerDraggable(
        marker: Marker?,
        onDragEnd: (LatLng) -> Unit,
        onDrag: ((LatLng) -> Unit)? = null
    ) {
        try {
            if (marker == null) return
            marker.isDraggable = true
            marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(m: Marker?) {}

                override fun onMarkerDrag(m: Marker?) {
                    val p = m?.position ?: return
                    onDrag?.invoke(LatLng(p.latitude, p.longitude))
                }

                override fun onMarkerDragEnd(m: Marker?) {
                    val p = m?.position ?: return
                    onDragEnd(LatLng(p.latitude, p.longitude))
                }
            })
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun removeMarker(marker: Marker?) {
        try {
            if (marker != null) {
                mapView.overlays.remove(marker)
                mapView.invalidate()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear() {
        try {
            mapView.overlays.clear()
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearRoute() {
        try {
            mapView.overlays.removeAll { it is Polyline }
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addPolyline(points: List<LatLng>, color: Int = Color.BLUE, width: Float = 5f) {
        try {
            if (points.isEmpty()) {
                return
            }

            val validPoints = points.filter {
                it.latitude >= -90 && it.latitude <= 90 &&
                it.longitude >= -180 && it.longitude <= 180
            }

            if (validPoints.isEmpty()) {
                return
            }

            val polyline = Polyline()
            polyline.setPoints(validPoints.map { GeoPoint(it.latitude, it.longitude) })
            polyline.setColor(color)
            polyline.setWidth(width)
            mapView.overlays.add(polyline)
            mapView.invalidate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMapClickListener(callback: (LatLng) -> Unit) {
        try {
            mapView.setOnClickListener { _ ->
                try {
                    val center = mapView.mapCenter
                    if (center != null) {
                        callback(LatLng(center.latitude, center.longitude))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun centerOnPoint(latLng: LatLng, zoomLevel: Double = 13.0) {
        try {
            if (latLng.latitude < -90 || latLng.latitude > 90 ||
                latLng.longitude < -180 || latLng.longitude > 180) {
                return
            }

            if (zoomLevel < 1.0 || zoomLevel > 20.0) {
                return
            }

            mapView.controller.setCenter(GeoPoint(latLng.latitude, latLng.longitude))
            mapView.controller.setZoom(zoomLevel)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
