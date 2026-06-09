# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# MediaPipe Face Landmarker uses generated task/protobuf/JNI entry points that R8
# should not rename or strip.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.auto.value.** { *; }
-dontwarn com.google.auto.value.**
-dontwarn com.google.mediapipe.**

# CameraX runtime classes are used through AndroidX camera providers.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# org.json is referenced from app code and platform variants differ by API level.
-keep class org.json.** { *; }

# App classes accessed reflectively or from platform callbacks.
-keep class com.example.emoscope.Constants { *; }
-keep class com.example.emoscope.EmoDatabaseHelper { *; }

# Compile-time-only references pulled by generated libraries.
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**

# Keep useful release crash line numbers.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
