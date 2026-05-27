# Route Planner

Sports route planning app. Users select start/end points on OpenStreetMap, specify desired distance, app generates optimized GPX route for tracking devices.

**No API keys required** — uses free osmdroid + OpenStreetMap tiles.

## Setup

### Prerequisites
- Android SDK 24+
- Kotlin 1.9+
- Gradle 8.1+

## Building

```bash
./gradlew build
./gradlew installDebug
```

## Architecture

- **MainActivity** — osmdroid map UI, marker placement
- **RouteService** — GraphHopper routing, GPX generation
- **DataManager** — OSM data download on first launch (14 regions)
- **MapManager** — osmdroid wrapper
- **SplashActivity** — Region selector + download progress

## Features

- Tap map to select start/end points (osmdroid, no API key)
- 14 European regions to choose from (Hungary default)
- Specify distance in km
- Auto-generates route avoiding highways
- Loop detection
- ±500m distance tolerance
- GPX export to Downloads folder
- Delete OSM data
- Change region anytime

## Limitations

- Route extension via detours is basic (can be improved)
- Restricted area filtering needs OSM tag data integration
- osmdroid tiles cached locally (disk space required)
