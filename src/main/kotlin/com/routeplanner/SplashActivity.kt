package com.routeplanner

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.routeplanner.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var dataManager: DataManager
    private var isDownloading = false

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
        binding.tvStatus.text = "Select map region"
        binding.spinnerRegion.visibility = android.view.View.VISIBLE
        binding.btnDownload.visibility = android.view.View.VISIBLE
        binding.progressBar.visibility = android.view.View.GONE
        binding.tvDownloadStatus.visibility = android.view.View.GONE

        val regionNames = RegionManager.regions.map { "${it.name} (${it.sizeApprox})" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, regionNames)
        binding.spinnerRegion.adapter = adapter

        val defaultIndex = RegionManager.regions.indexOfFirst { it.name == "Hungary" }
        binding.spinnerRegion.setSelection(defaultIndex)

        binding.btnDownload.setOnClickListener {
            if (!isDownloading) {
                downloadSelectedRegion()
            }
        }
    }

    private fun downloadSelectedRegion() {
        val selectedIndex = binding.spinnerRegion.selectedItemPosition
        val selectedRegion = RegionManager.regions[selectedIndex]

        isDownloading = true
        binding.spinnerRegion.isEnabled = false
        binding.btnDownload.isEnabled = false
        binding.tvStatus.text = "Downloading ${selectedRegion.name}..."
        binding.tvDownloadStatus.visibility = android.view.View.VISIBLE
        binding.progressBar.visibility = android.view.View.VISIBLE
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0

        dataManager.downloadOsmData(
            region = selectedRegion,
            onProgress = { progress ->
                binding.progressBar.progress = progress
                binding.tvDownloadStatus.text = "$progress%"
            },
            onComplete = {
                binding.tvStatus.text = "Download complete!"
                showMainContent()
            },
            onError = { error ->
                isDownloading = false
                binding.spinnerRegion.isEnabled = true
                binding.btnDownload.isEnabled = true
                Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
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
