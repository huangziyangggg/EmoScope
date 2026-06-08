# EmoScope — AI Emotion Growth Companion

<p align="center">
  <strong>记录情绪 · 理解情绪 · 改善情绪 · 长期成长</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-2.0-7C5CFC?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/minSdk-24-green?style=flat-square" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/targetSdk-36-blue?style=flat-square" alt="Target SDK"/>
  <img src="https://img.shields.io/badge/language-Java%2011-orange?style=flat-square" alt="Language"/>
  <img src="https://img.shields.io/badge/architecture-MVVM-purple?style=flat-square" alt="Architecture"/>
  <img src="https://img.shields.io/badge/UI-Material%20Design%203-7C5CFC?style=flat-square" alt="UI"/>
</p>

---

## Overview

EmoScope is a **multimodal emotion recognition and mental wellness system** that combines **real-time facial micro-expression analysis**, **voice emotion detection**, and a **30-day AI memory engine** to serve as your personal emotion growth companion.

Unlike traditional mood-tracking apps that rely on manual input, EmoScope actively analyzes your emotional state through **52 facial blendshape coefficients** via Google MediaPipe, and fuses this with voice speech rate analysis through DeepSeek's large language model.

**This is not a cold detection tool. It is an AI that truly understands you.**

---

## Philosophy

```
Record  →  Understand  →  Improve  →  Grow
Voice       AI             Tools        Levels
Face        Memory         Journal      Badges
Manual      Insights       Meditate     Reports
```

---

## Core Features

### Face Emotion Analysis
- **52 MediaPipe FaceLandmarker blendshapes** tracked in real-time
- **10-dimensional emotion space**: Joy, Sadness, Anger, Fear, Surprise, Disgust, Contempt, Anxiety, Fatigue, Calm
- **EMA smoothing** (alpha=0.3) eliminates frame-to-frame jitter
- **Weighted scoring**: each emotion assigned a psychological weight (Joy=95, Fear=10), producing a 0-100 composite index
- **Top-3 display**: three dominant emotions shown with exact percentages on camera overlay
- **Auto SOS trigger**: when negative emotions dominate >35%

### Voice Emotion Recognition
- Android native `SpeechRecognizer` with `zh-CN` language
- **Real-time transcription** via `onPartialResults` callbacks
- **Speech rate analysis**: fast (>4.5 char/s) = stressed, slow (<1.5 char/s) = low
- Custom **VoiceWaveView** — 5 animated bars dancing to your voice
- Custom **SonarRippleView** — expanding purple rings from the mic button
- DeepSeek LLM fuses voice text + facial emotions + speech rate + ambient light

### AI Long-Term Memory Engine
- **AiMemoryEngine**: 30-day retrospective analysis, fully local computation
- **Stress source Top-3**: keyword frequency across 30+ stress keywords
- **Joy source Top-3**: 25+ happiness keywords matched against all records
- **Emotional low-point localization**: exact date of lowest weighted score
- **Highlight moments**: highest scores and longest positive streaks
- **Weekly AI report**: Canvas-rendered PNG with FileProvider sharing

### Growth System
- **Lv1-Lv5 level progression**: Observer, Recorder, Explorer, Manager, Self-Master
- **12 achievement badges**: unlocked by records, streaks, journals, meditation, gratitude
- **ConfettiView particle burst** on badge unlock
- **Streak tracking**: automatic daily record detection

### Wellness Workshop
- **Emotion Journal**: daily writing with prompts, SQLite persistence, draft auto-load
- **Meditation Timer**: full-screen immersive 3/5/10-minute sessions
- **Gratitude List**: 3 things daily, SharedPreferences persistence
- **Emotion Timeline**: feed-style browsing with auto date grouping (Today/Yesterday/date)

### SOS Emergency Intervention
- **Triple trigger**: facial negative detection + phone shake + manual button
- **3-2-1 countdown** confirmation dialog
- **BreathingOverlayView**: 5-layer gradient purple concentric rings
- **Dual breathing modes**: Box Breathing (4-4-4-4) + 4-7-8 Deep Relaxation
- **Auto SMS** to preset emergency contact
- **One-tap dial** to 12355 National Youth Psychological Hotline
- **Privacy mode**: FLAG_SECURE screenshots blocking

### Data Management
- **3-format export**: TXT, CSV, Markdown with time-range filtering
- **JSON full backup/restore**
- **Smart notifications**: daily reminders with mood-based personalization
- **Weekly report push** via AlarmManager + BootReceiver

### Security
- **Biometric lock**: fingerprint/face unlock via AndroidX Biometric
- **All data local**: SQLite + SharedPreferences, zero cloud dependency
- **SecureStorage**: KeyChain-encrypted API key and contacts
- **DeepSeek API**: HTTPS encrypted

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 11 |
| Min/Target SDK | 24 / 36 |
| Face Detection | Google MediaPipe FaceLandmarker (52 blendshapes) |
| Voice Recognition | Android SpeechRecognizer (zh-CN) |
| AI Chat | DeepSeek Chat API via OkHttp |
| Camera | CameraX + ImageAnalysis (RGBA_8888) |
| Database | SQLite via SQLiteOpenHelper |
| Storage | SharedPreferences + SecureStorage (KeyChain) |
| UI | Material Design 3 (DayNight, custom shapes) |
| Architecture | Single Activity + 4 Fragments, MVVM |
| Notifications | AlarmManager + BroadcastReceiver |
| Biometrics | AndroidX Biometric |
| Animations | ObjectAnimator, ValueAnimator, custom Canvas |

---

## Project Structure

```
EmoScope/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── assets/face_landmarker.task        # MediaPipe model (3.6MB)
│   ├── java/com/example/emoscope/
│   │   ├── MainActivity.java              # Central hub
│   │   ├── Constants.java                 # Global constants
│   │   │
│   │   ├── # Core Engines
│   │   ├── FaceAnalyzer.java              # 52 blendshape to 10 emotions
│   │   ├── AiMemoryEngine.java            # 30-day NLP analysis
│   │   ├── BreathingEngine.java           # Breath animation engine
│   │   ├── DeepSeekClient.java            # OkHttp AI client
│   │   ├── EmoLineChartView.java          # Custom Canvas chart
│   │   │
│   │   ├── # Custom Animation Views
│   │   ├── BreathingOverlayView.java      # 5-layer breathing rings
│   │   ├── VoiceWaveView.java             # 5 animated vocal bars
│   │   ├── SonarRippleView.java           # Expanding sonar ripples
│   │   ├── ConfettiView.java              # Particle burst
│   │   │
│   │   ├── # Utilities
│   │   ├── EmoDatabaseHelper.java         # SQLite + saveRecord()
│   │   ├── MoodDialogHelper.java          # Shared mood picker
│   │   ├── StreakManager.java             # Streak calculation
│   │   ├── HistoryAdapter.java            # Timeline RecyclerView
│   │   ├── SecureStorage.java             # KeyChain encryption
│   │   ├── NotificationHelper.java        # AlarmManager scheduling
│   │   ├── ReminderReceiver.java          # BroadcastReceiver
│   │   ├── BootReceiver.java              # Re-register on reboot
│   │   ├── CrashHandler.java              # Global crash handler
│   │   │
│   │   ├── fragments/
│   │   │   ├── RadarFragment.java         # Homepage
│   │   │   ├── WorkshopFragment.java      # Growth + tools
│   │   │   ├── HistoryFragment.java       # Timeline + chart
│   │   │   └── SettingsFragment.java      # Settings
│   │   │
│   │   └── viewmodels/
│   │       ├── RadarViewModel.java
│   │       └── HistoryViewModel.java
│   │
│   └── res/
│       ├── anim/          # 10 animation XMLs
│       ├── drawable/      # 60+ vector drawables & gradients
│       ├── layout/        # 6 XML layouts
│       ├── menu/          # bottom_nav_menu.xml
│       ├── values/        # colors, themes, shapes, strings
│       ├── values-night/  # Dark theme
│       └── xml/           # FileProvider, shortcuts, backup
│
├── gradle/libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## Key Design Decisions

### Why 10 emotions instead of 6?
Paul Ekman's basic 6 emotions (joy, sadness, anger, fear, surprise, disgust) were extended with contempt (Ekman's later work), anxiety (clinical relevance), fatigue (daily life), and calm (baseline state). MediaPipe's 52 blendshapes provide enough signal granularity.

### Why weighted scoring?
A dominance of "joy" (weight 95) is very different from "fear" (weight 10). The weighted composite gives a single intuitive 0-100 number reflecting overall well-being.

### Why local-first?
Facial expression data is extremely personal. Everything stores locally. The AI memory engine runs fully offline. Only the user-initiated DeepSeek call goes over the network.

### Why remove Vosk?
Vosk's Chinese small model had reliability issues and added 50MB. Android's built-in SpeechRecognizer is more accurate for Chinese, works offline on modern devices, and adds zero dependencies.

### Why Material 3 with custom shapes?
Unified `shapeAppearance`: Small=12dp, Medium=16dp, Large=24dp. All MaterialCardView instances use these tokens, ensuring visual consistency across all 60+ cards.

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- JDK 11+
- Android SDK 36
- A DeepSeek API key (set in app Settings)

### Build

```bash
git clone https://github.com/huangziyangggg/EmoScope.git
cd EmoScope

# Debug
./gradlew assembleDebug

# Release (requires keystore.properties)
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### Run on Emulator

```bash
emulator -avd Medium_Phone &
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.emoscope/.MainActivity
```

### First Launch
1. Grant camera + microphone permissions
2. Tap "Start Face Analysis" on the homepage
3. Hold the voice button at the bottom to speak
4. Explore Growth, Timeline, and Profile tabs

---

## Requirements

- Android 7.0 (API 24) or higher
- Camera (optional, for face analysis)
- Microphone (optional, for voice input)
- ~78 MB storage
- Internet (for DeepSeek AI voice analysis only)

---

## License

Developed at **Harbin Institute of Technology** as a multimodal emotion recognition and psychological assessment system.

---

## Contact

GitHub: [@huangziyangggg](https://github.com/huangziyangggg)

---

<p align="center"><em>
Emotions are weather; you are the sky.<br>
EmoScope helps you record every cloud,<br>
until you see that you have always been vast.
</em></p>
