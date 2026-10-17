package com.routeplanner

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A downloadable map extract, or a grouping node (url == null) that only has children.
 * [id] is the Geofabrik path without the "-latest.osm.pbf" suffix, e.g. "europe/germany/bayern".
 */
data class Region(
    val id: String,
    val name: String,
    val parentId: String?,
    val url: String?,
    val sizeApprox: String? = null // Only known for the bundled list; otherwise read from the server
)

object RegionManager {
    private const val BASE_URL = "https://download.geofabrik.de/"
    private const val INDEX_URL = "https://download.geofabrik.de/index-v1-nogeom.json"
    private const val INDEX_CACHE_FILE = "region_index.json"
    private const val INDEX_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val PBF_SUFFIX = "-latest.osm.pbf"

    /** Extracts above this size (bytes) get an out-of-memory warning before download. */
    const val LARGE_REGION_BYTES = 300L * 1024 * 1024

    private fun country(slug: String, name: String, size: String) = Region(
        id = "europe/$slug", name = name, parentId = null,
        url = "${BASE_URL}europe/$slug$PBF_SUFFIX", sizeApprox = size
    )

    private fun state(country: String, slug: String, name: String) = Region(
        id = "europe/$country/$slug", name = name, parentId = "europe/$country",
        url = "${BASE_URL}europe/$country/$slug$PBF_SUFFIX"
    )

    /** Used when the Geofabrik index can be neither downloaded nor read from cache. */
    private val bundled: List<Region> = listOf(
        country("hungary", "Hungary", "~190MB"),
        country("austria", "Austria", "~165MB"),
        country("germany", "Germany", "~920MB"),
        country("czech-republic", "Czech Republic", "~165MB"),
        country("slovakia", "Slovakia", "~85MB"),
        country("slovenia", "Slovenia", "~65MB"),
        country("croatia", "Croatia", "~85MB"),
        country("romania", "Romania", "~270MB"),
        country("poland", "Poland", "~600MB"),
        country("france", "France", "~2.7GB"),
        country("italy", "Italy", "~1.1GB"),
        country("spain", "Spain", "~1.3GB"),
        country("switzerland", "Switzerland", "~420MB"),
        state("germany", "baden-wuerttemberg", "Baden-Württemberg"),
        state("germany", "bayern", "Bayern"),
        state("germany", "berlin", "Berlin"),
        state("germany", "brandenburg", "Brandenburg"),
        state("germany", "bremen", "Bremen"),
        state("germany", "hamburg", "Hamburg"),
        state("germany", "hessen", "Hessen"),
        state("germany", "mecklenburg-vorpommern", "Mecklenburg-Vorpommern"),
        state("germany", "niedersachsen", "Niedersachsen"),
        state("germany", "nordrhein-westfalen", "Nordrhein-Westfalen"),
        state("germany", "rheinland-pfalz", "Rheinland-Pfalz"),
        state("germany", "saarland", "Saarland"),
        state("germany", "sachsen", "Sachsen"),
        state("germany", "sachsen-anhalt", "Sachsen-Anhalt"),
        state("germany", "schleswig-holstein", "Schleswig-Holstein"),
        state("germany", "thueringen", "Thüringen")
    )

    /** Current catalogue; starts as the bundled list and is replaced by [loadCatalogue]. */
    @Volatile
    var regions: List<Region> = bundled
        private set

    fun getRegionById(id: String): Region? = regions.find { it.id == id }

    /** Also matches the plain names saved by app versions before sub-regions existed. */
    fun getRegionByIdOrName(key: String): Region? =
        getRegionById(key) ?: regions.firstOrNull { it.parentId == null && it.name == key }

    fun getDefaultRegion(): Region = getRegionById("europe/hungary") ?: regions.first()

    fun childrenOf(parentId: String?): List<Region> =
        regions.filter { it.parentId == parentId }.sortedBy { it.name }

    fun hasChildren(region: Region): Boolean = regions.any { it.parentId == region.id }

    /**
     * Refreshes [regions] from Geofabrik's index (cached for a week). Falls back to a stale
     * cache and then to the bundled list. Blocking — call from a background thread.
     */
    fun loadCatalogue(context: Context) {
        val cache = File(context.filesDir, INDEX_CACHE_FILE)
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < INDEX_MAX_AGE_MS
        if (!fresh) {
            try {
                val text = downloadIndex()
                val parsed = parseIndex(text)
                if (parsed.isNotEmpty()) {
                    cache.writeText(text)
                    regions = parsed
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("RegionManager", "Index download failed: ${e.message}")
            }
        }
        if (cache.exists()) {
            try {
                val parsed = parseIndex(cache.readText())
                if (parsed.isNotEmpty()) {
                    regions = parsed
                    return
                }
            } catch (e: Exception) {
                android.util.Log.w("RegionManager", "Index cache unreadable: ${e.message}")
            }
        }
        regions = bundled
    }

    private fun downloadIndex(): String {
        val conn = URL(INDEX_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Keeps the Europe subtree only; top-level countries get parentId == null. */
    internal fun parseIndex(json: String): List<Region> {
        val features = JSONObject(json).getJSONArray("features")
        val prefix = "${BASE_URL}europe/"
        val raw = ArrayList<Triple<String, String, String>>() // path, name, url
        for (i in 0 until features.length()) {
            val props = features.getJSONObject(i).optJSONObject("properties") ?: continue
            val url = props.optJSONObject("urls")?.optString("pbf").orEmpty()
            if (!url.startsWith(prefix) || !url.endsWith(PBF_SUFFIX)) continue
            val path = url.removePrefix(BASE_URL).removeSuffix(PBF_SUFFIX)
            val name = props.optString("name").ifEmpty { path.substringAfterLast('/') }
            raw.add(Triple(path, name, url))
        }
        val paths = raw.map { it.first }.toSet()
        return raw.map { (path, name, url) ->
            val parent = path.substringBeforeLast('/', "")
            // A sub-region whose parent has no extract of its own is attached to the top level.
            val parentId = if (parent in paths) parent else null
            Region(id = path, name = name, parentId = parentId, url = url)
        }
    }
}
