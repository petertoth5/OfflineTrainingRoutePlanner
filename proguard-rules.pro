# GraphHopper — uses heavy reflection internally; keep everything
-keep class com.graphhopper.** { *; }
-keep interface com.graphhopper.** { *; }
-dontwarn com.graphhopper.**

# Jackson — GraphHopper uses it for config/JSON deserialization
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# SLF4J
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**

# Jakarta/javax XML (transitive deps from GH)
-dontwarn javax.xml.bind.**
-dontwarn jakarta.xml.bind.**
-dontwarn jakarta.activation.**

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Google Maps LatLng (used as data class only)
-keep class com.google.android.gms.maps.model.LatLng { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# App classes
-keep class com.routeplanner.** { *; }

# ViewBinding generated classes
-keep class com.routeplanner.databinding.** { *; }

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
