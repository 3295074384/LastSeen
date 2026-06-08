# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep Amap SDK classes
-keep class com.amap.api.** { *; }
-keep class com.amap.api.maps.** { *; }
-keep class com.amap.api.location.** { *; }

# Keep Room entity classes
-keep class com.tom_crayon.lastseen.data.** { *; }
