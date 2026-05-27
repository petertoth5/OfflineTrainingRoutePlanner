package com.routeplanner

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DataManager(private val context: Context) {

    companion object {
        private const val OSM_DATA_FILE = "map.osm.pbf"
        private const val DOWNLOAD_URL = "https://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf"
        // Smaller test data - adjust URL based on target region
        // Available: https://download.geofabrik.de/
    }

    private val dataDir = File(context.cacheDir, "osm_data")

    init {
        dataDir.mkdirs()
    }

    fun getOsmDataFile(): File = File(dataDir, OSM_DATA_FILE)

    fun hasOsmData(): Boolean = getOsmDataFile().exists()

    fun downloadOsmData(
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = getOsmDataFile()
                val url = URL(DOWNLOAD_URL)
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

    fun cleanCache() {
        dataDir.deleteRecursively()
    }
}
