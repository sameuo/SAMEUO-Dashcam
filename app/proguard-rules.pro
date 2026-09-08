# SAMEUO Dashcam ProGuard / R8 rules

# Keep model/data classes used by reflection-free parsing is not required,
# but keep public API of media3 and okhttp.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
# A resource is loaded with a relative path so the package of this class must be preserved.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Media3
-keep class androidx.media3.** { *; }

# Application
-keep class com.sameuo.dashcam.data.protocol.model.** { *; }
-keep class com.sameuo.dashcam.data.gps.** { *; }
