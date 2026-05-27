package com.routeplanner

data class Region(
    val name: String,
    val url: String,
    val sizeApprox: String // Approximate download size
)

object RegionManager {
    val regions = listOf(
        Region("Hungary", "https://download.geofabrik.de/europe/hungary-latest.osm.pbf", "~190MB"),
        Region("Austria", "https://download.geofabrik.de/europe/austria-latest.osm.pbf", "~165MB"),
        Region("Germany", "https://download.geofabrik.de/europe/germany-latest.osm.pbf", "~920MB"),
        Region("Czech Republic", "https://download.geofabrik.de/europe/czech-republic-latest.osm.pbf", "~165MB"),
        Region("Slovakia", "https://download.geofabrik.de/europe/slovakia-latest.osm.pbf", "~85MB"),
        Region("Slovenia", "https://download.geofabrik.de/europe/slovenia-latest.osm.pbf", "~65MB"),
        Region("Croatia", "https://download.geofabrik.de/europe/croatia-latest.osm.pbf", "~85MB"),
        Region("Romania", "https://download.geofabrik.de/europe/romania-latest.osm.pbf", "~270MB"),
        Region("Poland", "https://download.geofabrik.de/europe/poland-latest.osm.pbf", "~600MB"),
        Region("France", "https://download.geofabrik.de/europe/france-latest.osm.pbf", "~2.7GB"),
        Region("Italy", "https://download.geofabrik.de/europe/italy-latest.osm.pbf", "~1.1GB"),
        Region("Spain", "https://download.geofabrik.de/europe/spain-latest.osm.pbf", "~1.3GB"),
        Region("Switzerland", "https://download.geofabrik.de/europe/switzerland-latest.osm.pbf", "~420MB"),
        Region("Berlin", "https://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf", "~70MB")
    )

    fun getRegionByName(name: String): Region? = regions.find { it.name == name }

    fun getDefaultRegion(): Region = regions.find { it.name == "Hungary" } ?: regions.first()
}
