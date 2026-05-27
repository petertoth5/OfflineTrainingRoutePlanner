package com.routeplanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.routeplanner.databinding.ActivityMainBinding
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapManager: MapManager
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

        mapManager = MapManager(this, binding.mapView)
        setupMapClickListener()

        binding.btnGenerate.setOnClickListener { generateRoute() }
        binding.btnClearStart.setOnClickListener { clearStart() }
        binding.btnClearEnd.setOnClickListener { clearEnd() }
        binding.btnExport.setOnClickListener { exportRoute() }
        binding.btnSettings.setOnClickListener { openSettings() }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        routeService.destroy()
    }

    private fun setupMapClickListener() {
        val touchOverlay = MapTouchOverlay(this) { latLng ->
            if (isSelectingStart) {
                startMarker?.let { mapManager.removeMarker(it) }
                startMarker = mapManager.addMarker(latLng, "Start Point")
                binding.tvStartPoint.text = "Start: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                isSelectingStart = false
                binding.tvStatus.text = "Tap map to select end point (or skip)"
            } else {
                endMarker?.let { mapManager.removeMarker(it) }
                endMarker = mapManager.addMarker(latLng, "End Point")
                binding.tvEndPoint.text = "End: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                binding.tvStatus.text = "Ready to generate route"
            }
        }
        binding.mapView.overlays.add(touchOverlay)
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
        mapManager.clear()
        mapManager.addPolyline(route.points)
        binding.tvRouteInfo.text = "Route: ${route.distance.toInt()}m, ${route.points.size} points"
    }

    private fun exportRoute() {
        val route = currentRoute ?: return
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val filename = "route_${sdf.format(Date())}.gpx"
        val file = routeService.exportGpx(route, filename)
        if (file != null) {
            Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            copyToDownloads(file)
        } else {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToDownloads(sourceFile: File) {
        try {
            val downloadsDir = getExternalFilesDir("Downloads") ?: return
            downloadsDir.mkdirs()
            val destFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            Toast.makeText(this, "Also copied to Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openSettings() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(arrayOf("Change Region", "Delete OSM Data", "Export GPX Files")) { _, which ->
                when (which) {
                    0 -> changeRegion()
                    1 -> deleteOsmData()
                    2 -> exportGpxFiles()
                }
            }
            .create()
        dialog.show()
    }

    private fun changeRegion() {
        val regionNames = RegionManager.regions.map { it.name }.toTypedArray()
        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Region")
            .setItems(regionNames) { _, which ->
                AlertDialog.Builder(this)
                    .setTitle("Download New Region?")
                    .setMessage("Current OSM data will be deleted. Continue?")
                    .setPositiveButton("Yes") { _, _ ->
                        startActivity(Intent(this, SplashActivity::class.java))
                        finish()
                    }
                    .setNegativeButton("No", null)
                    .create()
                    .show()
            }
            .create()
        dialog.show()
    }

    private fun deleteOsmData() {
        AlertDialog.Builder(this)
            .setTitle("Delete OSM Data?")
            .setMessage("This will remove the downloaded map data.")
            .setPositiveButton("Delete") { _, _ ->
                val dataManager = DataManager(this)
                if (dataManager.deleteOsmData()) {
                    Toast.makeText(this, "OSM data deleted", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, SplashActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun exportGpxFiles() {
        try {
            val appFilesDir = getExternalFilesDir(null) ?: return
            val gpxFiles = appFilesDir.listFiles { file -> file.extension == "gpx" } ?: arrayOf()

            if (gpxFiles.isEmpty()) {
                Toast.makeText(this, "No GPX files found", Toast.LENGTH_SHORT).show()
                return
            }

            val downloadsDir = getExternalFilesDir("Downloads") ?: return
            downloadsDir.mkdirs()

            gpxFiles.forEach { file ->
                file.copyTo(File(downloadsDir, file.name), overwrite = true)
            }

            Toast.makeText(this, "Exported ${gpxFiles.size} GPX files to Downloads", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearStart() {
        startMarker?.let { mapManager.removeMarker(it) }
        startMarker = null
        binding.tvStartPoint.text = "Start: Not set"
        isSelectingStart = true
    }

    private fun clearEnd() {
        endMarker?.let { mapManager.removeMarker(it) }
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
