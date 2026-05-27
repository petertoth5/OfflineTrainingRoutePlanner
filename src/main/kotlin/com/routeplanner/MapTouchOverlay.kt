package com.routeplanner

import android.content.Context
import android.view.MotionEvent
import com.google.android.gms.maps.model.LatLng
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class MapTouchOverlay(context: Context, private val onTap: (LatLng) -> Unit) : Overlay(context) {

    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        if (e != null && mapView != null) {
            val projection = mapView.projection
            val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt())
            val latLng = LatLng(geoPoint.latitude, geoPoint.longitude)
            onTap(latLng)
            return true
        }
        return false
    }
}
