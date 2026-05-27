package com.routeplanner

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.routeplanner.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var dataManager: DataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataManager = DataManager(this)

        if (dataManager.hasOsmData()) {
            launchMainActivity()
        } else {
            downloadOsmData()
        }
    }

    private fun downloadOsmData() {
        binding.tvStatus.text = "Downloading map data..."
        binding.progressBar.isIndeterminate = false
        binding.progressBar.progress = 0

        dataManager.downloadOsmData(
            onProgress = { progress ->
                binding.progressBar.progress = progress
                binding.tvStatus.text = "Downloading map data... $progress%"
            },
            onComplete = {
                binding.tvStatus.text = "Download complete!"
                launchMainActivity()
            },
            onError = { error ->
                binding.tvStatus.text = "Error: $error. Using offline mode."
                // Still launch but with limited functionality
                launchMainActivity()
            }
        )
    }

    private fun launchMainActivity() {
        binding.root.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 500)
    }
}
