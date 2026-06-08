# ── OkHttp / Okio ──────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── MediaPipe Face Landmarker (JNI + protobuf) ──────────────
-keep class com.google.mediapipe.** { *; }
-keep class com.google.auto.value.** { *; }
-dontwarn com.google.auto.value.**
-dontwarn com.google.mediapipe.**

# ── Vosk 离线语音识别 (JNI + native .so) ───────────────────
-keep class org.vosk.** { *; }
-keep class com.alphacephei.** { *; }
-dontwarn org.vosk.**

# ── JNA (Vosk 依赖) ────────────────────────────────────────
-keep class com.sun.jna.** { *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# ── CameraX ─────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── JSON (org.json) ─────────────────────────────────────────
-keep class org.json.** { *; }

# ── 应用自身类 ──────────────────────────────────────────────
-keep class com.example.emoscope.Constants { *; }
-keep class com.example.emoscope.EmoDatabaseHelper { *; }

# ── R8: auto-value 引用的编译期类（运行时不存在） ──────────
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**

# ── 保留行号（调试堆栈） ────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
