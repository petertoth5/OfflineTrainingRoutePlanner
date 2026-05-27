package com.routeplanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.routeplanner.databinding.ActivityMainBinding
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.IOException
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
    private var minTolerance: Int = 500 // default -500m (can be shorter)
    private var maxTolerance: Int = 500 // default +500m (can be longer)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routeService = RouteService(this)
        checkLocationPermissions()

        mapManager = MapManager(this, binding.mapView)
        setupMapClickListener()
        setupToleranceSlider()

        binding.btnGenerate.setOnClickListener { generateRoute() }
        binding.btnClearStart.setOnClickListener { clearStart() }
        binding.btnClearEnd.setOnClickListener { clearEnd() }
        binding.btnExport.setOnClickListener { exportRoute() }
        binding.btnSettings.setOnClickListener { openSettings() }
    }

    private fun setupToleranceSlider() {
        // Min tolerance slider (shorter): 0 to 2000 meters
        binding.seekBarMinTolerance.max = 2000
        binding.seekBarMinTolerance.progress = 500 // default -500m

        binding.seekBarMinTolerance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                minTolerance = progress
                updateMinToleranceDisplay(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Max tolerance slider (longer): 0 to 2000 meters
        binding.seekBarMaxTolerance.max = 2000
        binding.seekBarMaxTolerance.progress = 500 // default +500m

        binding.seekBarMaxTolerance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                maxTolerance = progress
                updateMaxToleranceDisplay(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateMinToleranceDisplay(tolerance: Int) {
        binding.tvMinToleranceValue.text = "-${tolerance}m"
    }

    private fun updateMaxToleranceDisplay(tolerance: Int) {
        binding.tvMaxToleranceValue.text = "+${tolerance}m"
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
        try {
            val touchOverlay = MapTouchOverlay(this) { latLng ->
                try {
                    if (latLng.latitude < -90 || latLng.latitude > 90 ||
                        latLng.longitude < -180 || latLng.longitude > 180) {
                        Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show()
                        return@MapTouchOverlay
                    }

                    if (isSelectingStart) {
                        startMarker?.let { mapManager.removeMarker(it) }
                        startMarker = mapManager.addMarker(latLng, "Start Point")
                        if (startMarker != null) {
                            binding.tvStartPoint.text = "Start: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                            isSelectingStart = false
                            binding.tvStatus.text = "Tap map to select end point (or skip)"
                        } else {
                            Toast.makeText(this, "Failed to place marker", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        endMarker?.let { mapManager.removeMarker(it) }
                        endMarker = mapManager.addMarker(latLng, "End Point")
                        if (endMarker != null) {
                            binding.tvEndPoint.text = "End: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                            binding.tvStatus.text = "Ready to generate route"
                        } else {
                            Toast.makeText(this, "Failed to place marker", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error placing marker: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            binding.mapView.overlays.add(touchOverlay)
        } catch (e: Exception) {
            Toast.makeText(this, "Error setting up map: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateRoute() {
        try {
            val startPoint = startMarker?.position
            if (startPoint == null) {
                Toast.makeText(this, "Please select start point", Toast.LENGTH_SHORT).show()
                return
            }

            val endPoint = endMarker?.position ?: startPoint

            val distanceText = binding.etDistance.text.toString().trim()
            if (distanceText.isEmpty()) {
                Toast.makeText(this, "Please enter distance", Toast.LENGTH_SHORT).show()
                return
            }

            val distance = distanceText.toDoubleOrNull()
            if (distance == null || distance <= 0) {
                Toast.makeText(this, "Distance must be positive number (km)", Toast.LENGTH_SHORT).show()
                return
            }

            if (distance > 100) {
                Toast.makeText(this, "Distance too large (max 100 km)", Toast.LENGTH_SHORT).show()
                return
            }

            binding.tvStatus.text = "Generating route..."
            binding.btnGenerate.isEnabled = false

            routeService.generateRoute(startPoint, endPoint, distance, minTolerance, maxTolerance) { route, error ->
                runOnUiThread {
                    binding.btnGenerate.isEnabled = true
                    if (route != null) {
                        displayRoute(route)
                        binding.tvStatus.text = "Route generated (${(route.distance/1000).toInt()} km). Ready to export."
                        binding.btnExport.isEnabled = true
                    } else {
                        binding.tvStatus.text = "Failed to generate route"
                        val errorMsg = error ?: "Unknown error occurred"
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            binding.btnGenerate.isEnabled = true
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            binding.tvStatus.text = "Route generation failed"
        }
    }

    private fun displayRoute(route: Route) {
        currentRoute = route
        mapManager.clear()
        mapManager.addPolyline(route.points)
        binding.tvRouteInfo.text = "Route: ${route.distance.toInt()}m, ${route.points.size} points"
    }

    private fun exportRoute() {
        try {
            val route = currentRoute
            if (route == null) {
                Toast.makeText(this, "No route generated yet", Toast.LENGTH_SHORT).show()
                return
            }

            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val filename = "route_${sdf.format(Date())}.gpx"

            val file = routeService.exportGpx(route, filename)
            if (file != null) {
                Toast.makeText(this, "Exported to: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                copyToDownloads(file)
            } else {
                Toast.makeText(this, "Export failed: could not write GPX file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToDownloads(sourceFile: File) {
        try {
            val downloadsDir = getExternalFilesDir("Downloads")
            if (downloadsDir == null) {
                Toast.makeText(this, "Downloads directory not available", Toast.LENGTH_SHORT).show()
                return
            }

            if (!downloadsDir.mkdirs() && !downloadsDir.exists()) {
                Toast.makeText(this, "Could not create Downloads directory", Toast.LENGTH_SHORT).show()
                return
            }

            val destFile = File(downloadsDir, sourceFile.name)
            sourceFile.copyTo(destFile, overwrite = true)
            Toast.makeText(this, "Also saved to Downloads folder", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: cannot write to Downloads", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Toast.makeText(this, "IO error: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error copying to Downloads: ${e.message}", Toast.LENGTH_SHORT).show()
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
            .setMessage("This will remove the downloaded map data. App will restart.")
            .setPositiveButton("Delete") { _, _ ->
                try {
                    val dataManager = DataManager(this)
                    if (dataManager.deleteOsmData()) {
                        Toast.makeText(this, "OSM data deleted", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, SplashActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to delete data file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error deleting data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun exportGpxFiles() {
        try {
            val appFilesDir = getExternalFilesDir(null)
            if (appFilesDir == null) {
                Toast.makeText(this, "External files directory not available", Toast.LENGTH_SHORT).show()
                return
            }

            val gpxFiles = appFilesDir.listFiles { file ->
                try {
                    file.isFile && file.extension == "gpx"
                } catch (e: Exception) {
                    false
                }
            } ?: arrayOf()

            if (gpxFiles.isEmpty()) {
                Toast.makeText(this, "No GPX files found in app directory", Toast.LENGTH_SHORT).show()
                return
            }

            val downloadsDir = getExternalFilesDir("Downloads")
            if (downloadsDir == null) {
                Toast.makeText(this, "Downloads directory not available", Toast.LENGTH_SHORT).show()
                return
            }

            if (!downloadsDir.mkdirs() && !downloadsDir.exists()) {
                Toast.makeText(this, "Could not create Downloads directory", Toast.LENGTH_SHORT).show()
                return
            }

            var copied = 0
            gpxFiles.forEach { file ->
                try {
                    file.copyTo(File(downloadsDir, file.name), overwrite = true)
                    copied++
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to copy ${file.name}: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            if (copied > 0) {
                Toast.makeText(this, "Exported $copied GPX file(s) to Downloads", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "No files were copied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission denied: cannot access files", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
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
