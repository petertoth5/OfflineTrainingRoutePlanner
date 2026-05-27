package com.routeplanner

import android.content.Context
import android.content.Context.MODE_PRIVATE
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
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
                val url = URL(region.url)
                val connection = url.openConnection()
                val fileLength = connection.contentLength

                url.openStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val data = ByteArray(8192)
                        var downloaded = 0L
                        var count: Int

                        while (input.read(data).also { count = it } != -1) {
                            downloaded += count
                            output.write(data, 0, count)

                            val progress = if (fileLength > 0) {
                                ((downloaded * 100) / fileLength).toInt()
                            } else {
                                0
                            }
                            onProgress(progress)
                        }
                    }
                }

                setCurrentRegion(region)
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
