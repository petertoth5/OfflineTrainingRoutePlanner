package com.routeplanner

import android.content.Context
import android.content.Context.MODE_PRIVATE
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class DataManager(private val context: Context) {

    companion object {
        private const val OSM_DATA_FILE = "map.osm.pbf"
        private const val PREFS_NAME = "route_planner_prefs"
        private const val PREFS_CURRENT_REGION = "current_region"
        private const val PREFS_PART_REGION = "part_region"
        private const val SPACE_FACTOR = 4L // pbf + graph (~2-3x pbf) + headroom
    }

    private val dataDir = File(context.cacheDir, "osm_data")
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    init {
        dataDir.mkdirs()
    }

    fun getOsmDataFile(): File = File(dataDir, OSM_DATA_FILE)

    fun hasOsmData(): Boolean = getOsmDataFile().exists()

    fun getCurrentRegion(): Region {
        val key = prefs.getString(PREFS_CURRENT_REGION, null)
        return (key?.let { RegionManager.getRegionByIdOrName(it) }) ?: RegionManager.getDefaultRegion()
    }

    fun setCurrentRegion(region: Region) {
        prefs.edit().putString(PREFS_CURRENT_REGION, region.id).apply()
    }

    /** Size of the extract in bytes from a HEAD request, or -1 if unknown. Blocking. */
    fun fetchRemoteSize(region: Region): Long {
        val url = region.url ?: return -1
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                if (conn.responseCode == HttpURLConnection.HTTP_OK) conn.contentLengthLong else -1
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Free space needed for the download plus the GraphHopper graph built from it
     * (the graph is typically 2-3x the size of the pbf).
     */
    fun requiredSpaceBytes(pbfBytes: Long): Long = pbfBytes * SPACE_FACTOR

    fun freeSpaceBytes(): Long = dataDir.freeSpace

    fun downloadOsmData(
        region: Region,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = getOsmDataFile()
                val part = File(dataDir, "$OSM_DATA_FILE.part")

                val regionUrl = region.url
                if (regionUrl.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) { onError("Invalid region URL") }
                    return@launch
                }

                // A partial file can only be resumed for the same region
                if (prefs.getString(PREFS_PART_REGION, null) != region.id) part.delete()
                prefs.edit().putString(PREFS_PART_REGION, region.id).apply()

                var existing = if (part.exists()) part.length() else 0L
                val connection = (URL(regionUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 30000
                    if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
                }

                try {
                    val code = connection.responseCode
                    if (code == HttpURLConnection.HTTP_OK) {
                        existing = 0L // Server ignored the range request: start over
                    } else if (code != HttpURLConnection.HTTP_PARTIAL) {
                        if (code == 416) part.delete() // Stale partial file
                        withContext(Dispatchers.Main) { onError("Server returned HTTP $code") }
                        return@launch
                    }

                    val remaining = connection.contentLengthLong
                    if (remaining <= 0) {
                        withContext(Dispatchers.Main) { onError("Invalid file size from server") }
                        return@launch
                    }
                    val totalLength = existing + remaining

                    val needed = requiredSpaceBytes(totalLength) - existing
                    if (dataDir.freeSpace < needed) {
                        withContext(Dispatchers.Main) {
                            onError("Not enough storage space (need ~${needed / (1024 * 1024)} MB free). Try a smaller region.")
                        }
                        return@launch
                    }

                    try {
                        connection.inputStream.use { input ->
                            FileOutputStream(part, existing > 0).use { output ->
                                val data = ByteArray(8192)
                                var downloaded = existing
                                var lastProgress = -1
                                var count: Int

                                while (input.read(data).also { count = it } != -1) {
                                    downloaded += count
                                    output.write(data, 0, count)

                                    val progress = ((downloaded * 100) / totalLength).toInt()
                                    if (progress != lastProgress) {
                                        lastProgress = progress
                                        withContext(Dispatchers.Main) { onProgress(progress) }
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        // Keep the .part file so the next attempt can resume
                        withContext(Dispatchers.Main) {
                            onError("Download interrupted - tap Download to resume")
                        }
                        return@launch
                    }

                    if (part.length() != totalLength) {
                        part.delete()
                        withContext(Dispatchers.Main) { onError("Downloaded file incomplete or corrupted") }
                        return@launch
                    }
                } finally {
                    connection.disconnect()
                }

                // The routing graph is built from the pbf: a graph of the previous region must not be reused
                clearGraphCache()
                file.delete()
                if (!part.renameTo(file)) {
                    part.delete()
                    withContext(Dispatchers.Main) { onError("Could not store downloaded file") }
                    return@launch
                }
                prefs.edit().remove(PREFS_PART_REGION).apply()

                setCurrentRegion(region)
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) { onError("Permission denied: cannot write file") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "Download failed") }
            }
        }
    }

    private fun clearGraphCache() {
        File(context.cacheDir, "gh").deleteRecursively()
    }

    fun deleteOsmData(): Boolean {
        return try {
            clearGraphCache()
            File(dataDir, "$OSM_DATA_FILE.part").delete()
            prefs.edit().remove(PREFS_PART_REGION).apply()
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
