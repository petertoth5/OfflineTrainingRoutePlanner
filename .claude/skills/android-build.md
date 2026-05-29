# Android Build Skill: Gradle, APK Signing, & Optimization

**Purpose**: Build, sign, and troubleshoot debug and release APKs for the Route Planner Android app. Covers Gradle workflow, dependency locking (GraphHopper 6.0), ProGuard optimization, and Windows PowerShell workarounds.

**Applies to**: `build.gradle.kts`, `gradle.properties`, `proguard-rules.pro`, `AndroidManifest.xml`, signed APK distribution.

---

## 1. Gradle Build Process Overview

### Architecture
- **Gradle wrapper** (`gradle-wrapper.jar`, v7.5): Manages build automation, dependency resolution, APK generation.
- **Android Gradle Plugin** (AGP v7.4.2): Compiles Kotlin → DEX, resources → AXML, packages APK.
- **Build config** (`build.gradle.kts`): Declares app namespace, SDK targets, dependencies, signing.
- **Runtime properties** (`gradle.properties`): Sets JVM heap (`-Xmx2048m`), AndroidX compatibility.

### Build Type Variants

| Variant | Signing | ProGuard | Use Case |
|---------|---------|---------|----------|
| **Debug** | Auto (debug keystore) | Off | Development, testing on device/emulator |
| **Release** | Manual (release.jks) | Off (configured, can enable) | Play Store, distribution |

### APK Outputs
- **Debug APK**: `build/outputs/apk/debug/app-debug.apk` — unminified, debuggable
- **Release APK**: `build/outputs/apk/release/app-release.apk` — signed with `release.jks`, ready for distribution

---

## 2. Dependency Management

### Locking GraphHopper Version

**Critical Constraint**: GraphHopper **must stay 6.0**.

**Why**: GraphHopper 7+ require custom Java expression compilation via Janino. Janino generates bytecode at runtime — **incompatible with Android's ART (Android Runtime)**. GH 6.0 supports `weighting=fastest` natively without Janino.

**Check current version**:
```kotlin
// build.gradle.kts
implementation("com.graphhopper:graphhopper-core:6.0")  // ✓ Pinned; do not upgrade
```

**If build fails with `java.lang.ClassNotFoundException: com.sun.codemodel`**: 
- Someone added GH 7+. Revert to `6.0`.
- Run `./gradlew dependencyInsight --dependency graphhopper` to see transitive versions.

### Transitive Dependencies

These arrive via GraphHopper; ProGuard rules keep them safe:

| Dependency | Source | ProGuard Rule |
|------------|--------|---------------|
| Jackson | GH config/JSON | `-keep class com.fasterxml.jackson.** { *; }` |
| Jakarta/javax XML | GH transitive | `-dontwarn jakarta.xml.bind.**` |
| SLF4J | Logging facade | `-keep class org.slf4j.** { *; }` |
| Kotlin Coroutines | Async/concurrency | `-keep class kotlinx.coroutines.** { *; }` |

### Heap Configuration

**Manifest requirement**: `android:largeHeap="true"` in `AndroidManifest.xml`:
```xml
<application
    android:allowBackup="true"
    android:largeHeap="true">
```

**Why**: GraphHopper's routing engine + OSM graph data consume significant memory. Large heap prevents out-of-memory crashes during route generation.

**Build-time JVM heap**: `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m
```
2GB gives Gradle room for DEX, resource compilation, and ProGuard (if enabled).

---

## 3. ProGuard Rules & Code Obfuscation

### When to Enable Minification

By default, minification is **off**:
```kotlin
// build.gradle.kts
release {
    isMinifyEnabled = false  // Safe default; no obfuscation
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

**To enable** (reduces APK size ~20%, but slower build):
```kotlin
release {
    isMinifyEnabled = true  // WARNING: do not enable without testing
    // Rest unchanged
}
```

### Rule Reference (`proguard-rules.pro`)

**GraphHopper** (largest rule set — uses reflection heavily):
```
-keep class com.graphhopper.** { *; }
-keep interface com.graphhopper.** { *; }
-dontwarn com.graphhopper.**
```
Must preserve all classes; minification breaks reflection-based configuration.

**Jackson** (GraphHopper's JSON parser):
```
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
```

**App classes**:
```
-keep class com.routeplanner.** { *; }
```
Preserves all app code (small APK; not worth obfuscating).

**ViewBinding** (Kotlin generates binding classes):
```
-keep class com.routeplanner.databinding.** { *; }
```
Without this rule, `ActivityMainBinding` and other generated classes are stripped.

**Enums & Serialization**:
```
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements java.io.Serializable { ... }
```
Preserves reflection-dependent enum/serializable machinery.

### Troubleshooting ProGuard Issues

**Symptom**: `java.lang.ClassNotFoundException` at runtime after minification.
```
Solution:
1. Check proguard-rules.pro for the missing class package.
2. If present, verify `-keep` syntax (no typos).
3. If absent, add:
   -keep class com.example.MissingClass { *; }
4. Re-build and test.
```

**Symptom**: `java.lang.NoSuchMethodException: ...ViewBinding` after minification.
```
Solution:
Verify -keep rule for ViewBinding:
-keep class com.routeplanner.databinding.** { *; }
```

---

## 4. Debug vs Release APK Signing

### Debug APK (Development)

**Automatic signing** with Android SDK's debug keystore:
```powershell
./gradlew installDebug
```
- No manual keystore setup needed.
- Installs directly to connected device via ADB.
- Debugger fully enabled; slow startup (~500ms).
- Expires yearly; auto-regenerated by Android SDK.

### Release APK (Distribution)

**Manual signing** requires three steps:

#### Step 1: Generate Keystore (One-time)
```powershell
keytool -genkeypair -v -keystore release.jks -alias routeplanner `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass MySecurePass -keypass MySecurePass `
  -dname "CN=RoutePlanner, O=YourOrg, C=HU"
```
- `-validity 10000`: Certificate valid for 27+ years (safe for app lifetime).
- `-keysize 2048`: RSA 2048 bits (minimum for Play Store).
- `-storepass`, `-keypass`: **Save passwords securely** (env vars, password manager).
- **Output**: `release.jks` — **keep safe**; losing it = cannot publish updates.

#### Step 2: Create `keystore.properties` (Gitignored)
```properties
storeFile=release.jks
storePassword=MySecurePass
keyAlias=routeplanner
keyPassword=MySecurePass
```
- **Gitignored** (in `.gitignore`); never commit.
- Read by `build.gradle.kts`:
  ```kotlin
  val keystoreProps = Properties().also { props ->
      val f = rootProject.file("keystore.properties")
      if (f.exists()) props.load(f.inputStream())
  }
  ```
- If missing, build skips signing (release APK unsigned).

#### Step 3: Build Signed Release APK
```powershell
./gradlew assembleRelease
```
- Compiles, packages, signs with `release.jks`.
- **Output**: `build/outputs/apk/release/app-release.apk`
- Ready for Play Store or side-loading.

### Signing Config in Gradle

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(keystoreProps["storeFile"] ?: "release.jks")
        storePassword = keystoreProps["storePassword"] as String?
        keyAlias = keystoreProps["keyAlias"] as String?
        keyPassword = keystoreProps["keyPassword"] as String?
    }
}

buildTypes {
    release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")
    }
}
```

**If `keystore.properties` missing**: Gradle silently skips signing. Always create it before `assembleRelease`.

---

## 5. GraphHopper 6.0 Version Constraint Enforcement

### Verify Current Version
```powershell
./gradlew dependencyTree --dependency graphhopper
```
Output should show `graphhopper-core:6.0` only.

### Lock Version (Preventing Accidental Upgrades)

Gradle allows version range syntax in `build.gradle.kts`. To enforce **exactly** 6.0 and reject 6.1+:

**Current (strict pinning)**:
```kotlin
implementation("com.graphhopper:graphhopper-core:6.0")
```

**If transitive deps pull a newer version**, add resolution rule:
```kotlin
configurations.all {
    resolutionStrategy {
        force("com.graphhopper:graphhopper-core:6.0")
    }
}
```

### Check for Transitive Upgrades
```powershell
./gradlew dependencyInsight --dependency graphhopper
# Shows which dependency pulled it and version
```

**If locked version differs from 6.0**: Modify `build.gradle.kts` or add `-Dorg.gradle.dependency.strict` flag.

---

## 6. Common Compile Errors & Troubleshooting

### Error 1: `java.lang.ClassNotFoundException: com.sun.codemodel`
**Cause**: GraphHopper 7+ imported (depends on Janino).

**Fix**:
```kotlin
// build.gradle.kts
implementation("com.graphhopper:graphhopper-core:6.0")  // Downgrade if 7+ present
```
```powershell
./gradlew clean dependencyInsight --dependency graphhopper
```

---

### Error 2: `OutOfMemoryError: Java heap space`
**Cause**: Gradle JVM heap too small for DEX + resources.

**Fix**:
```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2048m
```
Increase from default 1GB to 2GB (or more if necessary).

---

### Error 3: `error: cannot find symbol: class ActivityMainBinding`
**Cause**: ViewBinding not enabled or ProGuard stripped the generated class.

**Fix** (if not minified):
```kotlin
// build.gradle.kts
buildFeatures {
    viewBinding = true  // Must be true
}
```

**Fix** (if minified):
```
# proguard-rules.pro
-keep class com.routeplanner.databinding.** { *; }
```

---

### Error 4: `No matching variant of com.graphhopper:graphhopper-core:6.0 was found`
**Cause**: Gradle/Maven can't find GH 6.0 in configured repositories.

**Fix**:
```kotlin
// build.gradle.kts (if not already present)
repositories {
    google()
    mavenCentral()
}
```
GH 6.0 is in Maven Central. Ensure `mavenCentral()` is listed.

---

### Error 5: `error: Package android not recognized` / `Cannot find OSM dependency`
**Cause**: AGP or SDK not properly configured.

**Fix**:
```kotlin
// build.gradle.kts
android {
    compileSdk = 34  // Ensure >= 24
    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }
}
```
Also verify `AndroidManifest.xml` exists and is in correct location (`src/main/` or project root).

---

### Error 6: `Cannot generate context-specific metadata for task 'compile*KotlinKt'`
**Cause**: Kotlin plugin version mismatch or missing `jvmTarget`.

**Fix**:
```kotlin
// build.gradle.kts
plugins {
    kotlin("android") version "1.9.22"
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"  // Must match compileSdk
    }
}
```

---

## 7. Build Commands Reference

### Full Build Workflow

```powershell
# ===== DEVELOPMENT (Debug) =====

# 1. Install debug APK to connected device
./gradlew installDebug

# 2. Build only (no install)
./gradlew build

# 3. Clean and rebuild
./gradlew clean build

# 4. Run unit tests
./gradlew test

# 5. Run connected instrumentation tests (on device/emulator)
./gradlew connectedAndroidTest

# ===== DISTRIBUTION (Release) =====

# 1. Generate keystore (one-time; save release.jks safely)
keytool -genkeypair -v -keystore release.jks -alias routeplanner `
  -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass MyPassword -keypass MyPassword `
  -dname "CN=RoutePlanner, O=Org, C=HU"

# 2. Create keystore.properties (gitignored)
# Content:
# storeFile=release.jks
# storePassword=MyPassword
# keyAlias=routeplanner
# keyPassword=MyPassword

# 3. Build signed release APK
./gradlew assembleRelease
# Output: build/outputs/apk/release/app-release.apk

# 4. Verify APK signature
jarsigner -verify -verbose build/outputs/apk/release/app-release.apk

# ===== DEBUGGING =====

# 1. List all tasks
./gradlew tasks

# 2. Inspect dependencies
./gradlew dependencyInsight --dependency graphhopper

# 3. Rebuild dependency tree (text output)
./gradlew dependencyTree

# 4. Check Gradle properties
./gradlew properties

# 5. Sync Gradle (reload build config without building)
./gradlew assemble (without building APK: just sync)

# 6. Force clean Gradle cache
./gradlew clean --refresh-dependencies
```

---

## 8. Windows PowerShell Workaround

### Problem
`gradlew.bat` fails in PowerShell with:
```
Exception in thread "main" java.lang.ClassNotFoundException: org.gradle.launcher.bootstrap.GradleMain
```
**Root cause**: PowerShell's `%CLASSPATH%` expansion is empty when `gradlew.bat` reads it.

### Solution: Invoke Gradle Wrapper JAR Directly

```powershell
# Instead of: ./gradlew build
# Use:

& "c:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" build

# Or for release:
& "c:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
  "-Dorg.gradle.appname=gradlew" `
  -jar ".\gradle\wrapper\gradle-wrapper.jar" assembleRelease
```

**Parameters**:
- `-Xmx64m -Xms64m`: JVM heap (Gradle wrapper only, separate from build heap).
- `-Dorg.gradle.appname=gradlew`: Sets process name in logs.
- `.\gradle\wrapper\gradle-wrapper.jar`: Location of wrapper JAR.
- `build` / `assembleRelease` / `installDebug`: Task name.

### Alias (Optional)
Add to PowerShell profile (`$PROFILE`):
```powershell
function gradlew {
    & "c:\Program Files\Java\jdk-16\bin\java.exe" -Xmx64m -Xms64m `
      "-Dorg.gradle.appname=gradlew" `
      -jar ".\gradle\wrapper\gradle-wrapper.jar" @args
}
```

Then use: `gradlew build` / `gradlew assembleRelease`.

---

## 9. Project-Specific Constraints

### Manifest: Large Heap
```xml
<application android:largeHeap="true">
```
**Required** for GraphHopper's graph data processing.

### Resource Exclusions
```kotlin
// build.gradle.kts
packagingOptions {
    resources {
        excludes += "META-INF/LICENSE.md"
        excludes += "META-INF/NOTICE.md"
        excludes += "META-INF/LICENSE.txt"
        excludes += "META-INF/NOTICE.txt"
    }
}
```
Prevents duplicate LICENSE/NOTICE files in final APK (from multiple dependencies).

### Kotlin + AndroidX
```properties
# gradle.properties
android.useAndroidX=true
android.enableJetifier=true
```
Routes old `android.*` packages to `androidx.*` for compatibility.

---

## 10. Quick Reference Checklist

### Before Building Release APK
- [ ] `keystore.properties` exists with correct `storePassword`, `keyPassword`, `keyAlias`.
- [ ] `release.jks` keystore file exists and is secure.
- [ ] `GraphHopper` dependency is pinned to `6.0` (not 7+).
- [ ] `gradle.properties` has `org.gradle.jvmargs=-Xmx2048m`.
- [ ] `AndroidManifest.xml` has `android:largeHeap="true"`.
- [ ] ProGuard rules in `proguard-rules.pro` include `-keep class com.routeplanner.databinding.** { *; }`.
- [ ] Running on Windows PowerShell? Use JAR invocation (step 8).

### After Build
- [ ] Debug APK: Check `build/outputs/apk/debug/app-debug.apk` exists.
- [ ] Release APK: Verify signature with `jarsigner -verify` before distribution.
- [ ] Test on device/emulator before publishing.

---

## 11. Further Reading

- **Gradle**: https://gradle.org/docs/current/userguide/
- **Android Gradle Plugin**: https://developer.android.com/build/releases/gradle-plugin
- **R8/ProGuard**: https://developer.android.com/build/shrink-code
- **GraphHopper 6.0 Docs**: https://docs.graphhopper.com/web/
- **keytool Ref**: `keytool -help` (in JDK `bin/`)
- **jarsigner Ref**: `jarsigner -help` (in JDK `bin/`)

---

## Support

- Build errors? Check section 6 (Common Compile Errors).
- Windows PowerShell issues? See section 8 (PowerShell Workaround).
- GraphHopper version conflicts? Use section 5 (Version Constraint Enforcement).
- ProGuard config? Review section 3 (ProGuard Rules).
