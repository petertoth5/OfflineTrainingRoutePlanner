# Android UI Design Skill

## Overview
This skill provides practical guidelines for designing Android UIs for the Offline Training Route Planner app. The app targets Android 7.0+ (minSdk 24, compileSdk 34) with portrait-only orientation, high contrast accessibility, and Samsung Galaxy optimization.

## 1. Material Design 3 Implementation

### Core Principles
- Use Material Design Components (MDC) v1.10+ for consistency
- Follow Material Design 3 color system with high contrast
- Implement proper elevation and shadow hierarchy
- Use responsive typography scale

### Theme Configuration (res/values/themes.xml)
```xml
<resources>
    <style name="Theme.RoutePlanner" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <!-- Primary Color: Strong, high-contrast brand color -->
        <item name="colorPrimary">@color/primary_600</item>
        <item name="colorPrimaryVariant">@color/primary_900</item>
        
        <!-- Surface and Container Colors: High contrast backgrounds -->
        <item name="colorSurface">@color/surface_100</item>
        <item name="colorOnSurface">@color/surface_900</item>
        
        <!-- Secondary: Accent color for maps and CTAs -->
        <item name="colorSecondary">@color/secondary_600</item>
        <item name="colorSecondaryVariant">@color/secondary_800</item>
        
        <!-- Error State: High contrast red -->
        <item name="colorError">@color/error_600</item>
        
        <!-- Status Bar -->
        <item name="android:statusBarColor">?attr/colorPrimaryVariant</item>
        <item name="android:windowLightStatusBar">false</item>
        
        <!-- Text Appearance for accessibility -->
        <item name="textAppearanceHeadlineSmall">@style/TextAppearance.RoutePlanner.HeadlineSmall</item>
        <item name="textAppearanceBodyLarge">@style/TextAppearance.RoutePlanner.BodyLarge</item>
        <item name="textAppearanceBodyMedium">@style/TextAppearance.RoutePlanner.BodyMedium</item>
        <item name="textAppearanceBodySmall">@style/TextAppearance.RoutePlanner.BodySmall</item>
    </style>

    <!-- High Contrast Text Styles -->
    <style name="TextAppearance.RoutePlanner.HeadlineSmall" parent="TextAppearance.MaterialComponents.HeadlineSmall">
        <item name="android:textSize">20sp</item>
        <item name="android:textColor">@color/text_primary</item>
        <item name="android:textStyle">bold</item>
    </style>

    <style name="TextAppearance.RoutePlanner.BodyLarge" parent="TextAppearance.MaterialComponents.BodyLarge">
        <item name="android:textSize">16sp</item>
        <item name="android:textColor">@color/text_primary</item>
    </style>

    <style name="TextAppearance.RoutePlanner.BodyMedium" parent="TextAppearance.MaterialComponents.BodyMedium">
        <item name="android:textSize">14sp</item>
        <item name="android:textColor">@color/text_primary</item>
    </style>

    <style name="TextAppearance.RoutePlanner.BodySmall" parent="TextAppearance.MaterialComponents.BodySmall">
        <item name="android:textSize">12sp</item>
        <item name="android:textColor">@color/text_secondary</item>
    </style>
</resources>
```

### Color Palette (res/values/colors.xml) - High Contrast
```xml
<resources>
    <!-- Primary Brand Colors (High Contrast Blue) -->
    <color name="primary_50">#f0f4ff</color>
    <color name="primary_100">#e0e9ff</color>
    <color name="primary_200">#c2d7ff</color>
    <color name="primary_300">#a3c4ff</color>
    <color name="primary_400">#84b1ff</color>
    <color name="primary_500">#6699ff</color>
    <color name="primary_600">#1e40af</color>
    <color name="primary_700">#1e3a8a</color>
    <color name="primary_900">#0c1650</color>

    <!-- Secondary Colors (High Contrast Teal/Green) -->
    <color name="secondary_50">#f0fdfa</color>
    <color name="secondary_100">#ccfbf1</color>
    <color name="secondary_200">#99f6e4</color>
    <color name="secondary_600">#0d9488</color>
    <color name="secondary_700">#0f766e</color>
    <color name="secondary_800">#115e59</color>

    <!-- Error/Alert (High Contrast Red) -->
    <color name="error_50">#fef2f2</color>
    <color name="error_100">#fee2e2</color>
    <color name="error_600">#dc2626</color>
    <color name="error_700">#b91c1c</color>

    <!-- Neutral/Surface (Minimalist) -->
    <color name="surface_50">#fafafa</color>
    <color name="surface_100">#f5f5f5</color>
    <color name="surface_200">#e5e5e5</color>
    <color name="surface_300">#d4d4d4</color>
    <color name="surface_700">#404040</color>
    <color name="surface_800">#262626</color>
    <color name="surface_900">#171717</color>

    <!-- Semantic Colors -->
    <color name="text_primary">@color/surface_900</color>
    <color name="text_secondary">@color/surface_700</color>
    <color name="text_tertiary">@color/surface_600</color>
    <color name="text_disabled">@color/surface_400</color>
    
    <!-- Map/Location Colors -->
    <color name="location_start">#10b981</color>
    <color name="location_end">#ef4444</color>
    <color name="location_waypoint">#f59e0b</color>
    <color name="route_line">#6699ff</color>
    <color name="route_line_alt">#0d9488</color>
</resources>
```

## 2. Portrait Orientation Lock Patterns

### AndroidManifest.xml Configuration
All activities must explicitly lock to portrait:
```xml
<activity
    android:name=".MainActivity"
    android:exported="false"
    android:screenOrientation="portrait"
    android:configChanges="keyboardHidden|orientation"
    android:windowSoftInputMode="adjustResize" />

<activity
    android:name=".SplashActivity"
    android:exported="true"
    android:screenOrientation="portrait" />
```

### Handling Configuration Changes in Code
```kotlin
override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // Prevent orientation changes - portrait only
    if (newConfig.orientation != Configuration.ORIENTATION_PORTRAIT) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
```

### Programmatic Portrait Lock Pattern
```kotlin
// In onCreate() of activity
requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

// For map view specifically
mapView.setOnConfigurationChangedListener {
    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
}
```

## 3. Responsive Layout Patterns (Phones & Tablets)

### Layout Architecture
Use ConstraintLayout as base for all layouts - provides responsive behavior across device sizes:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">
    
    <!-- Content here -->
</androidx.constraintlayout.widget.ConstraintLayout>
```

### Samsung Galaxy Optimization (Standard Phones)
Target screen sizes: 5.0"-6.1" (Galaxy A/S series)

**Safe Margins & Padding:**
- Top padding: 16dp (status bar consideration)
- Side margins: 12dp-16dp
- Bottom padding: 16dp (navigation bar consideration)
- Element spacing: 8dp, 12dp, 16dp

**Font Sizes by Purpose:**
- Headlines: 18-20sp
- Body text: 14-16sp
- Labels: 12sp
- Hint text: 12sp (lighter color)

### Tablet-Ready Layout (res/layout-sw600dp)
For devices 7"+ or landscape orientation (if future):
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout 
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="24dp">

    <!-- Use larger text sizes and more generous spacing -->
    <org.osmdroid.views.MapView
        android:id="@+id/mapView"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toStartOf="@id/controlPanel"
        app:layout_constraintBottom_toBottomOf="parent" />

    <!-- Control panel on right for tablets -->
    <FrameLayout
        android:id="@+id/controlPanel"
        android:layout_width="320dp"
        android:layout_height="0dp"
        android:background="@color/surface_100"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintBottom_toBottomOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

### Dimension Resources (res/values/dimens.xml)
```xml
<resources>
    <!-- Standard spacing -->
    <dimen name="spacing_xs">4dp</dimen>
    <dimen name="spacing_sm">8dp</dimen>
    <dimen name="spacing_md">12dp</dimen>
    <dimen name="spacing_lg">16dp</dimen>
    <dimen name="spacing_xl">24dp</dimen>
    <dimen name="spacing_xxl">32dp</dimen>

    <!-- Text sizes (phone) -->
    <dimen name="text_headline_small">18sp</dimen>
    <dimen name="text_body_large">16sp</dimen>
    <dimen name="text_body_medium">14sp</dimen>
    <dimen name="text_body_small">12sp</dimen>
    <dimen name="text_label">12sp</dimen>

    <!-- Component heights -->
    <dimen name="button_height_default">44dp</dimen>
    <dimen name="button_height_small">36dp</dimen>
    <dimen name="icon_size_default">24dp</dimen>
    <dimen name="icon_size_large">32dp</dimen>
</resources>
```

### Tablet-Specific Dimensions (res/values-sw600dp/dimens.xml)
```xml
<resources>
    <dimen name="spacing_lg">24dp</dimen>
    <dimen name="spacing_xl">32dp</dimen>
    <dimen name="text_headline_small">24sp</dimen>
    <dimen name="text_body_large">18sp</dimen>
    <dimen name="text_body_medium">16sp</dimen>
    <dimen name="text_body_small">14sp</dimen>
    <dimen name="button_height_default">48dp</dimen>
    <dimen name="icon_size_default">28dp</dimen>
</resources>
```

## 4. High Contrast Accessibility Guidelines

### WCAG 2.1 AA Compliance Standards
- **Text Contrast Ratio:** Minimum 4.5:1 for body text, 3:1 for large text (18sp+)
- **Interactive Elements:** Minimum 4.5:1 contrast with adjacent colors
- **Disabled State:** Minimum 3:1 contrast with background

### Color Accessibility Checklist
```
Primary Text (#171717) on Light Background (#fafafa): 18:1 ✓
Secondary Text (#404040) on Light Background (#fafafa): 12:1 ✓
Primary Button (#1e40af) Text (white): 7.8:1 ✓
Error Red (#dc2626) on white: 5.8:1 ✓
Secondary Teal (#0d9488) on white: 4.8:1 ✓
```

### Accessible Component Implementation

**High Contrast Buttons:**
```xml
<com.google.android.material.button.MaterialButton
    android:id="@+id/btnGenerate"
    style="@style/Widget.MaterialComponents.Button"
    android:layout_width="match_parent"
    android:layout_height="@dimen/button_height_default"
    android:text="Generate Route"
    android:textSize="@dimen/text_body_medium"
    android:textColor="@color/text_primary"
    android:contentDescription="Generate route with selected parameters"
    app:backgroundTint="@color/primary_600"
    app:rippleColor="@color/primary_700" />
```

**Accessible EditText:**
```xml
<com.google.android.material.textfield.TextInputLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Distance (meters)"
    app:counterEnabled="true"
    app:counterMaxLength="6">

    <com.google.android.material.textfield.TextInputEditText
        android:id="@+id/etDistance"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="numberDecimal"
        android:textSize="@dimen/text_body_medium"
        android:textColor="@color/text_primary"
        android:contentDescription="Enter desired route distance in meters"
        android:maxLength="6" />
</com.google.android.material.textfield.TextInputLayout>
```

**Accessible SeekBar with Labels:**
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_marginVertical="@dimen/spacing_md">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Shorter:"
            android:textSize="@dimen/text_label"
            android:textColor="@color/text_secondary"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tvMinToleranceValue"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="-500m"
            android:textSize="@dimen/text_body_medium"
            android:textColor="@color/text_primary"
            android:textStyle="bold"
            android:layout_marginStart="@dimen/spacing_md"
            android:contentDescription="Shorter distance tolerance: -500 meters" />
    </LinearLayout>

    <androidx.appcompat.widget.AppCompatSeekBar
        android:id="@+id/seekBarMinTolerance"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:max="2000"
        android:progress="500"
        android:layout_marginTop="@dimen/spacing_sm"
        android:contentDescription="Adjust shorter distance tolerance"
        android:minHeight="48dp" />
</LinearLayout>
```

### Dark Mode Support
```xml
<!-- res/values/colors.xml (Light) -->
<color name="text_primary">#171717</color>
<color name="surface_bg">#fafafa</color>

<!-- res/values-night/colors.xml (Dark) -->
<color name="text_primary">#f5f5f5</color>
<color name="surface_bg">#1a1a1a</color>
```

### Accessibility Announcements in Code
```kotlin
// For dynamic content changes
binding.tvRouteInfo.announceForAccessibility("Route generated: 8.5 km, 45 minutes")

// For state changes
binding.btnGenerate.contentDescription = 
    "Generate route. Start: ${startPoint}, End: ${endPoint}"
```

## 5. App Icon Design Standards

### Icon Specifications
- **Safe Area Diameter:** 66dp (physical Android spec requires 72dp minimum canvas, 12dp margin)
- **Versions:** ic_launcher (adaptive, API 26+) + ic_launcher_round (API 25-)
- **File Formats:** PNG 24-bit with alpha channel, optimized
- **Naming Convention:** ic_launcher.png, ic_launcher_round.png

### File Structure
```
res/
├── mipmap-hdpi/
│   ├── ic_launcher.png (72x72)
│   └── ic_launcher_round.png (72x72)
├── mipmap-xhdpi/
│   ├── ic_launcher.png (96x96)
│   └── ic_launcher_round.png (96x96)
├── mipmap-xxhdpi/
│   ├── ic_launcher.png (144x144)
│   └── ic_launcher_round.png (144x144)
├── mipmap-xxxhdpi/
│   ├── ic_launcher.png (192x192)
│   └── ic_launcher_round.png (192x192)
├── mipmap-anydpi-v26/
│   └── ic_launcher.xml (adaptive icon)
└── mipmap-anydpi-v33/
    └── ic_launcher.xml (themed icon for Android 13+)
```

### Adaptive Icon XML (res/mipmap-anydpi-v26/ic_launcher.xml)
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/primary_600" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <!-- Optional monochrome for themed icons (API 33+) -->
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

### Foreground Design (res/drawable/ic_launcher_foreground.xml)
High-contrast design representing route/navigation:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    
    <!-- Route path line - bold, high contrast -->
    <path
        android:pathData="M54,20L54,88"
        android:strokeColor="#ffffff"
        android:strokeWidth="8"
        android:strokeLineCap="round" />
    
    <!-- Start marker -->
    <circle
        android:cx="54"
        android:cy="25"
        android:r="6"
        android:fillColor="#10b981" />
    
    <!-- End marker -->
    <circle
        android:cx="54"
        android:cy="83"
        android:r="6"
        android:fillColor="#ef4444" />
</vector>
```

### Icon Accessibility
In AndroidManifest.xml:
```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round"
    android:label="@string/app_name">
```

## 6. Layout XML Best Practices

### Layout Hierarchy Rules
1. **Root Container:** ConstraintLayout (preferred) or LinearLayout
2. **Map View:** Takes flex space (0dp height with weight or constraints)
3. **Control Panel:** Fixed height, minimal scrolling
4. **Nested Layouts:** Minimize depth (max 3 levels)

### Example: Optimized Main Layout
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <!-- MAP VIEW: Takes all available space -->
    <org.osmdroid.views.MapView
        android:id="@+id/mapView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <!-- CONTROL PANEL: Scrollable bottom sheet -->
    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:maxHeight="400dp"
        android:background="@color/surface_100"
        android:padding="@dimen/spacing_lg">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical">

            <!-- Status section -->
            <TextView
                android:id="@+id/tvStatus"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="?attr/textAppearanceHeadlineSmall"
                android:textColor="@color/text_primary"
                android:layout_marginBottom="@dimen/spacing_md" />

            <!-- Point indicators -->
            <TextView
                android:id="@+id/tvStartPoint"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:textAppearance="?attr/textAppearanceBodySmall"
                android:textColor="@color/text_secondary"
                android:layout_marginBottom="@dimen/spacing_sm" />

            <!-- Action buttons group -->
            <com.google.android.material.button.MaterialButtonToggleGroup
                android:id="@+id/toggleGroupProfile"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginVertical="@dimen/spacing_md"
                app:singleSelection="true"
                app:selectionRequired="true">

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnProfileRun"
                    style="?attr/materialButtonOutlinedStyle"
                    android:layout_width="0dp"
                    android:layout_height="@dimen/button_height_default"
                    android:layout_weight="1"
                    android:text="Running" />

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnProfileBike"
                    style="?attr/materialButtonOutlinedStyle"
                    android:layout_width="0dp"
                    android:layout_height="@dimen/button_height_default"
                    android:layout_weight="1"
                    android:text="Biking" />
            </com.google.android.material.button.MaterialButtonToggleGroup>

            <!-- Input fields -->
            <com.google.android.material.textfield.TextInputLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:hint="Distance (meters)"
                android:layout_marginBottom="@dimen/spacing_lg">

                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/etDistance"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:inputType="numberDecimal"
                    android:textSize="@dimen/text_body_medium"
                    android:textColor="@color/text_primary" />
            </com.google.android.material.textfield.TextInputLayout>

            <!-- Primary CTA -->
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnGenerate"
                android:layout_width="match_parent"
                android:layout_height="@dimen/button_height_default"
                android:text="Generate Route"
                android:textSize="@dimen/text_body_medium"
                app:backgroundTint="@color/primary_600"
                android:layout_marginBottom="@dimen/spacing_lg" />
        </LinearLayout>
    </androidx.core.widget.NestedScrollView>
</LinearLayout>
```

### Critical Layout Best Practices
1. **Use match_parent sparingly** - Explicitly size containers
2. **Weight-based sizing** - For flexible components, use `layout_weight`
3. **Margins vs Padding** - Use margins between elements, padding inside containers
4. **Text measurement** - Account for text size variation across locales
5. **Touch target size** - All interactive elements minimum 44dp x 44dp
6. **Constraint chains** - For complex layouts, use ConstraintLayout chains instead of nested layouts
7. **Resource references** - Always use @dimen and @color for values, never hardcode

### Portrait-Specific Layout Considerations
```xml
<!-- Ensure 1:1 aspect ratio for square components -->
<ImageView
    android:layout_width="100dp"
    android:layout_height="100dp" />

<!-- Account for on-screen keyboard -->
<LinearLayout
    android:windowSoftInputMode="adjustResize"
    android:fitsSystemWindows="true" />

<!-- Scroll bottom sheet when keyboard appears -->
<androidx.core.widget.NestedScrollView
    android:layout_height="wrap_content"
    android:maxHeight="400dp" />
```

## Implementation Checklist

- [ ] Color palette defined in res/values/colors.xml with high contrast
- [ ] Theme applied in AndroidManifest.xml
- [ ] All activities have android:screenOrientation="portrait"
- [ ] Dimensions defined in res/values/dimens.xml
- [ ] Tablet layout variants in res/layout-sw600dp/
- [ ] Material Components (MDC) dependency added (com.google.android.material:material)
- [ ] Text sizes use @dimen references
- [ ] Colors use @color references
- [ ] All interactive elements 44dp+ (touch target)
- [ ] Text contrast ratios verified (minimum 4.5:1)
- [ ] App icons in all required mipmap densities
- [ ] Adaptive icon XML configured (API 26+)
- [ ] NestedScrollView wraps control panels
- [ ] ConstraintLayout used for complex layouts
- [ ] Dark mode colors defined in res/values-night/colors.xml
- [ ] Content descriptions added to all images/interactive elements

## Samsung Galaxy Optimization Notes
- Test on Galaxy A12 (6.5" screen): Layout should be readable without scaling
- SeekBars need explicit height (minHeight="48dp") for touch accuracy
- Button text size 12-14sp optimal (not smaller)
- Margins/padding should use consistent 16dp spacing
- Map view interaction should not interfere with scroll gestures in control panel
