package com.routeplanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.routeplanner.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleMap: GoogleMap
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var isSelectingStart = true
    private lateinit var routeService: RouteService
    private var currentRoute: Route? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routeService = RouteService(this)
        checkLocationPermissions()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            setupMapClickListener()
        }

        binding.btnGenerate.setOnClickListener { generateRoute() }
        binding.btnClearStart.setOnClickListener { clearStart() }
        binding.btnClearEnd.setOnClickListener { clearEnd() }
        binding.btnExport.setOnClickListener { exportRoute() }
    }

    override fun onDestroy() {
        super.onDestroy()
        routeService.destroy()
    }

    private fun setupMapClickListener() {
        googleMap.setOnMapClickListener { latLng ->
            if (isSelectingStart) {
                startMarker?.remove()
                startMarker = googleMap.addMarker {
                    position(latLng)
                    title("Start Point")
                }
                binding.tvStartPoint.text = "Start: ${latLng.latitude}, ${latLng.longitude}"
                isSelectingStart = false
                binding.tvStatus.text = "Tap map to select end point (or skip)"
            } else {
                endMarker?.remove()
                endMarker = googleMap.addMarker {
                    position(latLng)
                    title("End Point")
                }
                binding.tvEndPoint.text = "End: ${latLng.latitude}, ${latLng.longitude}"
                binding.tvStatus.text = "Ready to generate route"
            }
        }
    }

    private fun generateRoute() {
        val startPoint = startMarker?.position ?: return
        val endPoint = endMarker?.position ?: startPoint
        val distance = binding.etDistance.text.toString().toDoubleOrNull() ?: return

        binding.tvStatus.text = "Generating route..."
        routeService.generateRoute(startPoint, endPoint, distance) { route ->
            runOnUiThread {
                if (route != null) {
                    displayRoute(route)
                    binding.tvStatus.text = "Route generated. Ready to export."
                    binding.btnExport.isEnabled = true
                } else {
                    binding.tvStatus.text = "Failed to generate route"
                }
            }
        }
    }

    private fun displayRoute(route: Route) {
        currentRoute = route
        googleMap.clear()
        val polylineOptions = route.toPolylineOptions()
        googleMap.addPolyline(polylineOptions)
        binding.tvRouteInfo.text = "Route: ${route.distance.toInt()}m, ${route.points.size} points"
    }

    private fun exportRoute() {
        val route = currentRoute ?: return
        val filename = "route_${System.currentTimeMillis()}.gpx"
        val file = routeService.exportGpx(route, filename)
        if (file != null) {
            Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearStart() {
        startMarker?.remove()
        startMarker = null
        binding.tvStartPoint.text = "Start: Not set"
        isSelectingStart = true
    }

    private fun clearEnd() {
        endMarker?.remove()
        endMarker = null
        binding.tvEndPoint.text = "End: Not set"
    }

    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
