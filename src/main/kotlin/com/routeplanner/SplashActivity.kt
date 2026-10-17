package com.routeplanner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.routeplanner.databinding.ActivitySplashBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var dataManager: DataManager
    private var isDownloading = false
    private var selectedRegion: Region? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataManager = DataManager(this)

        if (dataManager.hasOsmData()) {
            showMainContent()
        } else {
            showRegionSelector()
        }
    }

    private fun showRegionSelector() {
        binding.tvStatus.text = "Loading regions..."
        binding.btnSelectRegion.visibility = View.VISIBLE
        binding.btnSelectRegion.isEnabled = false
        binding.btnDownload.visibility = View.VISIBLE
        binding.btnDownload.isEnabled = false
        binding.progressBar.visibility = View.GONE
        binding.tvDownloadStatus.visibility = View.GONE

        // Refresh the region list in the background; the bundled list is used if offline
        CoroutineScope(Dispatchers.IO).launch {
            RegionManager.loadCatalogue(applicationContext)
            withContext(Dispatchers.Main) {
                if (isFinishing) return@withContext
                selectedRegion = RegionManager.getDefaultRegion()
                binding.tvStatus.text = "Select map region - smaller regions download faster and need less memory"
                binding.btnSelectRegion.isEnabled = true
                updateSelectedRegion()
            }
        }

        binding.btnSelectRegion.setOnClickListener {
            if (!isDownloading) {
                RegionPicker.show(this) { region ->
                    selectedRegion = region
                    updateSelectedRegion()
                }
            }
        }

        binding.btnDownload.setOnClickListener {
            if (!isDownloading) confirmDownload()
        }
    }

    private fun updateSelectedRegion() {
        val region = selectedRegion ?: return
        val size = region.sizeApprox?.let { " ($it)" } ?: ""
        binding.btnSelectRegion.text = "${region.name}$size"
        binding.btnDownload.isEnabled = region.url != null
    }

    /** Looks up the real download size first, then asks the user to confirm. */
    private fun confirmDownload() {
        val region = selectedRegion ?: return
        binding.btnDownload.isEnabled = false
        binding.btnSelectRegion.isEnabled = false
        binding.tvStatus.text = "Checking download size..."

        CoroutineScope(Dispatchers.IO).launch {
            val size = dataManager.fetchRemoteSize(region)
            withContext(Dispatchers.Main) {
                if (isFinishing) return@withContext
                binding.tvStatus.text = "Select map region"
                binding.btnDownload.isEnabled = true
                binding.btnSelectRegion.isEnabled = true
                showConfirmDialog(region, size)
            }
        }
    }

    private fun showConfirmDialog(region: Region, sizeBytes: Long) {
        val message = StringBuilder()
        if (sizeBytes > 0) {
            message.append("Download size: ${formatMb(sizeBytes)}\n")
            message.append("Free space needed: ~${formatMb(dataManager.requiredSpaceBytes(sizeBytes))}")
            val free = dataManager.freeSpaceBytes()
            if (free < dataManager.requiredSpaceBytes(sizeBytes)) {
                message.append("\n\nOnly ${formatMb(free)} is free. Choose a smaller region or free up storage.")
            }
            if (sizeBytes > RegionManager.LARGE_REGION_BYTES) {
                message.append("\n\nThis is a large region. Preparing it for routing may run out of memory on some devices - a smaller sub-region is safer.")
            }
        } else {
            message.append("The download size could not be determined (no connection?).")
        }

        AlertDialog.Builder(this)
            .setTitle("Download ${region.name}?")
            .setMessage(message.toString())
            .setPositiveButton("Download") { _, _ -> startDownload(region) }
            .setNeutralButton("Choose another") { _, _ -> binding.btnSelectRegion.performClick() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatMb(bytes: Long): String =
        if (bytes >= 1024L * 1024 * 1024) String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        else "${bytes / (1024 * 1024)} MB"

    private fun startDownload(selectedRegion: Region) {
        isDownloading = true
        binding.btnSelectRegion.isEnabled = false
        binding.btnDownload.isEnabled = false
        binding.tvStatus.text = "Downloading ${selectedRegion.name}..."
        binding.tvDownloadStatus.visibility = View.VISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0

        dataManager.downloadOsmData(
            region = selectedRegion,
            onProgress = { progress ->
                if (progress in 0..100) {
                    binding.progressBar.progress = progress
                    binding.tvDownloadStatus.text = "$progress%"
                }
            },
            onComplete = {
                if (isDownloading) {
                    binding.tvStatus.text = "Download complete!"
                    showMainContent()
                }
            },
            onError = { error ->
                isDownloading = false
                binding.btnSelectRegion.isEnabled = true
                binding.btnDownload.isEnabled = true
                Toast.makeText(this, error.ifEmpty { "Unknown error occurred" }, Toast.LENGTH_LONG).show()
                binding.tvStatus.text = "Select map region"
            }
        )
    }

    private fun showMainContent() {
        binding.root.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 500)
    }
}
