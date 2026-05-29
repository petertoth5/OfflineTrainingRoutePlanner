package com.routeplanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import java.io.File
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.model.LatLng
import com.routeplanner.databinding.ActivityMainBinding
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mapManager: MapManager

    private val saveGpxLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> saveGpxToUri(uri) }
        }
    }
    private var startMarker: Marker? = null
    private var endMarker: Marker? = null
    private var isSelectingStart = true
    private lateinit var routeService: RouteService
    private var currentRoute: Route? = null
    private var minTolerance: Int = 500 // default -500m (can be shorter)
    private var maxTolerance: Int = 500 // default +500m (can be longer)
    private var selectedProfile: String = "foot"
    private var locationManager: LocationManager? = null
    private var userLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        routeService = RouteService(this)
        checkLocationPermissions()

        mapManager = MapManager(this, binding.mapView)
        setupMapClickListener()
        setupToleranceSlider()
        initializeLocation()

        binding.btnGenerate.isEnabled = false
        val initStartTime = System.currentTimeMillis()

        val timerRunnable = object : Runnable {
            override fun run() {
                val elapsed = (System.currentTimeMillis() - initStartTime) / 1000
                binding.tvStatus.text = "Preparing routing engine... ${elapsed}s (first run may take 5-15 min)"
                binding.root.postDelayed(this, 1000)
            }
        }
        binding.root.post(timerRunnable)

        Thread {
            val error = routeService.initializeGraphHopperSync()
            runOnUiThread {
                binding.root.removeCallbacks(timerRunnable)
                if (error.isEmpty()) {
                    val elapsed = (System.currentTimeMillis() - initStartTime) / 1000
                    binding.btnGenerate.isEnabled = true
                    binding.tvStatus.text = "Ready (engine loaded in ${elapsed}s)"
                } else {
                    binding.tvStatus.text = "Routing engine failed: $error"
                    Toast.makeText(this, "Map initialization failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        binding.etDistance.setText("10000")

        binding.toggleGroupProfile.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selectedProfile = if (checkedId == R.id.btnProfileRun) "foot" else "bike"
                binding.etDistance.setText(if (checkedId == R.id.btnProfileRun) "10000" else "20000")
            }
        }

        binding.btnGenerate.setOnClickListener { generateRoute() }
        binding.btnNewRoute.setOnClickListener { newRoute() }
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
                            attachStartMarkerDrag()
                            binding.tvStartPoint.text = "Start: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                            isSelectingStart = false
                            binding.tvStatus.text = "Tap map to select end point (or skip). Drag a marker to fine-tune."
                        } else {
                            Toast.makeText(this, "Failed to place marker", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        endMarker?.let { mapManager.removeMarker(it) }
                        endMarker = mapManager.addMarker(latLng, "End Point")
                        if (endMarker != null) {
                            attachEndMarkerDrag()
                            binding.tvEndPoint.text = "End: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
                            binding.tvStatus.text = "Ready to generate route. Drag a marker to fine-tune."
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

    private fun attachStartMarkerDrag() {
        mapManager.makeMarkerDraggable(startMarker, onDragEnd = { latLng ->
            binding.tvStartPoint.text = "Start: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
            onWaypointMoved()
        })
    }

    private fun attachEndMarkerDrag() {
        mapManager.makeMarkerDraggable(endMarker, onDragEnd = { latLng ->
            binding.tvEndPoint.text = "End: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}"
            onWaypointMoved()
        })
    }

    // Called after a start/end marker is dragged to a new position. If a route is already shown,
    // regenerate it from the new points so the displayed route stays in sync with the markers.
    private fun onWaypointMoved() {
        if (currentRoute != null && binding.btnGenerate.isEnabled) {
            binding.tvStatus.text = "Waypoint moved — regenerating route..."
            generateRoute()
        } else {
            binding.tvStatus.text = "Waypoint moved. Tap Generate Route."
        }
    }

    private fun generateRoute() {
        try {
            val startPos = startMarker?.position
            if (startPos == null) {
                Toast.makeText(this, "Please select start point", Toast.LENGTH_SHORT).show()
                return
            }
            val startPoint = LatLng(startPos.latitude, startPos.longitude)

            val endPos = endMarker?.position
            val endPoint = if (endPos != null) LatLng(endPos.latitude, endPos.longitude) else startPoint

            val distanceText = binding.etDistance.text.toString().trim()
            if (distanceText.isEmpty()) {
                Toast.makeText(this, "Please enter distance", Toast.LENGTH_SHORT).show()
                return
            }

            val distance = distanceText.toDoubleOrNull()
            if (distance == null || distance <= 0) {
                Toast.makeText(this, "Distance must be positive number (meters)", Toast.LENGTH_SHORT).show()
                return
            }

            if (distance > 100000) {
                Toast.makeText(this, "Distance too large (max 100000 meters)", Toast.LENGTH_SHORT).show()
                return
            }

            binding.tvStatus.text = "Generating route..."
            binding.btnGenerate.isEnabled = false

            routeService.generateRoute(startPoint, endPoint, distance, selectedProfile, minTolerance, maxTolerance) { route, error ->
                runOnUiThread {
                    binding.btnGenerate.isEnabled = true
                    if (route != null) {
                        displayRoute(route)
                        binding.tvStatus.text = "Route generated (${route.distance.toInt()} m). Ready to export."
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
        mapManager.clearRoute()
        mapManager.addPolyline(route.points)
        binding.tvRouteInfo.text = "Route: ${route.distance.toInt()}m, ${route.points.size} points"
    }

    private fun newRoute() {
        startMarker?.let { mapManager.removeMarker(it) }
        endMarker?.let { mapManager.removeMarker(it) }
        startMarker = null
        endMarker = null
        currentRoute = null
        mapManager.clearRoute()
        isSelectingStart = true
        binding.tvStartPoint.text = "Start: Not set"
        binding.tvEndPoint.text = "End: Not set"
        binding.tvRouteInfo.text = "Route info will appear here"
        binding.tvStatus.text = "Tap map to select start point"
        binding.btnExport.isEnabled = false
    }

    private fun exportRoute() {
        if (currentRoute == null) {
            Toast.makeText(this, "No route generated yet", Toast.LENGTH_SHORT).show()
            return
        }
        val filename = "route_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.gpx"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        saveGpxLauncher.launch(intent)
    }

    private fun saveGpxToUri(uri: Uri) {
        try {
            val gpx = currentRoute?.toGpx() ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(gpx.toByteArray()) }
            Toast.makeText(this, "GPX saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
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

    private fun initializeLocation() {
        try {
            locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) return

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                val lastLocation = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (lastLocation != null) {
                    userLocation = LatLng(lastLocation.latitude, lastLocation.longitude)
                    mapManager.centerOnPoint(userLocation!!, 15.0)
                    setStartPointToCurrentLocation()
                } else {
                    mapManager.centerOnPoint(LatLng(47.5, 19.0), 6.0)
                }
            } else {
                mapManager.centerOnPoint(LatLng(47.5, 19.0), 6.0)
            }
        } catch (e: Exception) {
            mapManager.centerOnPoint(LatLng(47.5, 19.0), 6.0)
        }
    }

    private fun setStartPointToCurrentLocation() {
        if (userLocation != null) {
            startMarker?.let { mapManager.removeMarker(it) }
            startMarker = mapManager.addMarker(userLocation!!, "Start Point (Current Location)")
            attachStartMarkerDrag()
            binding.tvStartPoint.text = "Start: ${String.format("%.4f", userLocation!!.latitude)}, ${String.format("%.4f", userLocation!!.longitude)}"
            isSelectingStart = false
            binding.tvStatus.text = "Start point set to current location. Tap map to select end point (or skip). Drag a marker to fine-tune."
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}
