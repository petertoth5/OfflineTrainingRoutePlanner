# Route Planner

Sports route planning app. Users select start/end points on Google Maps, specify desired distance, app generates optimized GPX route for tracking devices.

## Setup

### Prerequisites
- Android SDK 24+
- Kotlin 1.9+
- Gradle 8.1+

### Google Maps API Key
1. Get API key from [Google Cloud Console](https://console.cloud.google.com/)
2. Enable Maps SDK for Android
3. Add to `AndroidManifest.xml`:
   ```xml
   <meta-data
       android:name="com.google.android.geo.API_KEY"
       android:value="YOUR_API_KEY_HERE" />
   ```

### OSM Data Region
Default downloads Berlin region. Change in `DataManager.kt`:
```kotlin
private const val DOWNLOAD_URL = "https://download.geofabrik.de/europe/germany/berlin-latest.osm.pbf"
```
See [Geofabrik](https://download.geofabrik.de/) for other regions.

## Building

```bash
./gradlew build
./gradlew installDebug
```

## Architecture

- **MainActivity** — Maps UI, marker placement
- **RouteService** — GraphHopper routing, GPX generation
- **DataManager** — OSM data download on first launch
- **SplashActivity** — Download progress screen

## Features

- Tap Google Maps to select start/end points
- Specify distance in km
- Auto-generates route avoiding highways, restricted areas
- Loop detection
- ±500m distance tolerance
- GPX export for GPS devices

## Limitations

- Route extension via detours is basic (can be improved)
- Restricted area filtering needs OSM tag data integration
- No offline maps initially (downloads on first launch)
