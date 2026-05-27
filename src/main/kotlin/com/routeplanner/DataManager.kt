package com.routeplanner

import android.content.Context
import android.content.Context.MODE_PRIVATE
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URL

class DataManager(private val context: Context) {

    companion object {
        private const val OSM_DATA_FILE = "map.osm.pbf"
        private const val PREFS_NAME = "route_planner_prefs"
        private const val PREFS_CURRENT_REGION = "current_region"
    }

    private val dataDir = File(context.cacheDir, "osm_data")
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    init {
        dataDir.mkdirs()
    }

    fun getOsmDataFile(): File = File(dataDir, OSM_DATA_FILE)

    fun hasOsmData(): Boolean = getOsmDataFile().exists()

    fun getCurrentRegion(): Region {
        val regionName = prefs.getString(PREFS_CURRENT_REGION, null)
        return if (regionName != null) {
            RegionManager.getRegionByName(regionName) ?: RegionManager.getDefaultRegion()
        } else {
            RegionManager.getDefaultRegion()
        }
    }

    fun setCurrentRegion(region: Region) {
        prefs.edit().putString(PREFS_CURRENT_REGION, region.name).apply()
    }

    fun downloadOsmData(
        region: Region,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = getOsmDataFile()

                // Validate URL
                if (region.url.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onError("Invalid region URL")
                    }
                    return@launch
                }

                val url = try {
                    URL(region.url)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("Invalid URL format: ${e.message}")
                    }
                    return@launch
                }

                val connection = try {
                    url.openConnection().apply {
                        connectTimeout = 30000 // 30 seconds
                        readTimeout = 30000
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError("Connection error: ${e.message}")
                    }
                    return@launch
                }

                val fileLength = connection.contentLength
                if (fileLength <= 0) {
                    withContext(Dispatchers.Main) {
                        onError("Invalid file size from server")
                    }
                    return@launch
                }

                // Check available space (rough estimate: need 2x file size)
                val availableSpace = file.parentFile?.freeSpace ?: 0
                if (availableSpace < fileLength * 2) {
                    withContext(Dispatchers.Main) {
                        onError("Not enough storage space (need ~${fileLength / (1024*1024*1024)} GB)")
                    }
                    return@launch
                }

                try {
                    url.openStream().use { input ->
                        FileOutputStream(file).use { output ->
                            val data = ByteArray(8192)
                            var downloaded = 0L
                            var count: Int

                            while (input.read(data).also { count = it } != -1) {
                                downloaded += count
                                output.write(data, 0, count)

                                val progress = ((downloaded * 100) / fileLength).toInt()
                                onProgress(progress)
                            }
                        }
                    }
                } catch (e: InterruptedIOException) {
                    file.delete()
                    withContext(Dispatchers.Main) {
                        onError("Download interrupted")
                    }
                    return@launch
                }

                // Verify file
                if (!file.exists() || file.length() < fileLength * 0.9) { // Allow 10% tolerance
                    file.delete()
                    withContext(Dispatchers.Main) {
                        onError("Downloaded file incomplete or corrupted")
                    }
                    return@launch
                }

                setCurrentRegion(region)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    onError("Permission denied: cannot write file")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Download failed")
                }
            }
        }
    }

    fun deleteOsmData(): Boolean {
        return try {
            getOsmDataFile().delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getDataSizeKb(): Long {
        return try {
            getOsmDataFile().length() / 1024
        } catch (e: Exception) {
            0L
        }
    }

    fun cleanCache() {
        dataDir.deleteRecursively()
    }
}
