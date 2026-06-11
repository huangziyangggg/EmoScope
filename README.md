# EmoScope · 多模态情绪识别与心理评估系统

<p align="center">
  <strong>Multimodal Emotion Recognition and Psychological Assessment System</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0-B794F4?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/minSdk-24-green?style=flat-square" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/targetSdk-36-blue?style=flat-square" alt="Target SDK"/>
  <img src="https://img.shields.io/badge/language-Java%2011-orange?style=flat-square" alt="Language"/>
  <img src="https://img.shields.io/badge/arch-MVVM-purple?style=flat-square" alt="Architecture"/>
  <img src="https://img.shields.io/badge/files-234-silver?style=flat-square" alt="Files"/>
</p>

---

## 概述 · Overview

**EmoScope** 是一个多模态情绪识别与心理健康辅助系统，融合**实时面部微表情分析**、**语音情绪检测**和 **30 天 AI 记忆引擎**，作为你的个人情绪成长伙伴。

EmoScope is a multimodal emotion recognition and mental wellness companion that combines **real-time facial micro-expression analysis**, **voice emotion detection**, and a **30-day AI memory engine** to help you record, understand, and grow through your emotions.

不同于依赖手动输入的传统心情追踪应用，EmoScope 通过 Google MediaPipe 的 **52 个面部 blendshape 系数**主动分析你的情绪状态，并通过 DeepSeek 大语言模型融合语音、语速和环境光照信息，生成个性化解读。

Unlike traditional mood-tracking apps that rely on manual input, EmoScope actively analyzes your emotional state through **52 facial blendshape coefficients** via Google MediaPipe, and fuses this with voice and speech-rate analysis through the DeepSeek large language model.

**这不是一个冷冰冰的检测工具，而是一个真正理解你的 AI。**

**This is not a cold detection tool. It is an AI that understands you.**

---

## 核心理念 · Philosophy

```
记录 Record  →  理解 Understand  →  改善 Improve  →  成长 Grow
语音 Voice      AI 分析 AI         工具 Tools        等级 Levels
面容 Face      记忆 Memory        日记 Journal      徽章 Badges
手动 Manual    洞察 Insights      冥想 Meditate     报告 Reports
```

---

## 核心功能 · Core Features

### 面部情绪分析 · Face Emotion Analysis

| 中文 | English |
|------|---------|
| 52 个 MediaPipe FaceLandmarker blendshape 实时追踪 | 52 MediaPipe FaceLandmarker blendshapes tracked in real-time |
| **10 维情绪空间**：愉悦、悲伤、愤怒、恐惧、惊讶、厌恶、轻蔑、焦虑、疲惫、平静 | **10-dimensional emotion space**: Joy, Sadness, Anger, Fear, Surprise, Disgust, Contempt, Anxiety, Fatigue, Calm |
| **EMA 平滑** (α=0.3) 消除帧间抖动 | **EMA smoothing** (α=0.3) eliminates frame-to-frame jitter |
| **加权打分**：每种情绪分配心理权重（愉悦=95, 恐惧=10），输出 0-100 综合指数 | **Weighted scoring**: each emotion assigned a psychological weight (Joy=95, Fear=10), producing a 0-100 composite |
| **Top-3 显示**：相机叠加层显示主导情绪及精确百分比 | **Top-3 display**: dominant emotions with exact percentages on camera overlay |
| **拍照打分**：一键捕捉当前面部情绪快照并存入数据库 | **Photo capture & score**: one-tap snapshot of current facial emotions saved to database |
| **SOS 自动触发**：消极情绪占比 >35% 时自动预警 | **Auto SOS trigger**: when negative emotions dominate >35% |

### 语音情绪识别 · Voice Emotion Recognition

| 中文 | English |
|------|---------|
| Android 原生 `SpeechRecognizer`，zh-CN 语言 | Android native `SpeechRecognizer` with `zh-CN` language |
| `onPartialResults` 实时转写 | **Real-time transcription** via `onPartialResults` callbacks |
| 语速分析：快 (>4.5 字/秒) = 紧张，慢 (<1.5 字/秒) = 低落 | **Speech rate analysis**: fast (>4.5 char/s) = stressed, slow (<1.5 char/s) = low |
| 自定义 **VoiceWaveView** — 5 根随声音跳动的动画条 | Custom **VoiceWaveView** — 5 animated vocal bars |
| 自定义 **SonarRippleView** — 从麦克风按钮扩散的声纳波纹 | Custom **SonarRippleView** — expanding sonar rings |
| DeepSeek LLM 融合语音文本 + 面部情绪 + 语速 + 环境光照 | DeepSeek LLM fuses voice text + facial emotions + speech rate + ambient light |

### AI 长期记忆引擎 · AI Memory Engine

| 中文 | English |
|------|---------|
| **AiMemoryEngine**：30 天回顾分析，纯本地计算 | **AiMemoryEngine**: 30-day retrospective analysis, fully local |
| **压力来源 Top-3**：30+ 压力关键词词频分析 | **Stress source Top-3**: keyword frequency across 30+ stress keywords |
| **快乐来源 Top-3**：25+ 积极关键词匹配 | **Joy source Top-3**: 25+ happiness keywords matched |
| **情绪低点定位**：最低加权分日期 | **Emotional low-point**: exact date of lowest score |
| **高光时刻**：最高分与最长积极连续记录 | **Highlight moments**: highest scores and longest positive streaks |
| **AI 周报**：Canvas 绘制 PNG，FileProvider 分享 | **Weekly AI report**: Canvas-rendered PNG with FileProvider sharing |

### 成长系统 · Growth System

| 中文 | English |
|------|---------|
| **Lv1-Lv5 等级进阶**：觉察新手 → 记录学徒 → 情绪旅人 → 自我管理者 → 情绪掌控者 | **Lv1-Lv5 progression**: Observer → Recorder → Explorer → Manager → Self-Master |
| **12 枚成就徽章**：记录、连续打卡、日记、冥想、感恩均可解锁 | **12 achievement badges**: unlocked by records, streaks, journals, meditation, gratitude |
| 徽章解锁时 **ConfettiView 粒子彩带** | **ConfettiView particle burst** on badge unlock |
| 自动连续打卡追踪 | Automatic streak tracking |

### 身心健康工坊 · Wellness Workshop

| 中文 | English |
|------|---------|
| **情绪日记**：每日写作引导，SQLite 持久化，草稿自动加载 | **Emotion Journal**: daily writing with prompts, SQLite persistence, draft auto-load |
| **正念冥想**：全屏沉浸式 3/5/10 分钟计时 | **Meditation Timer**: full-screen immersive 3/5/10-minute sessions |
| **感恩清单**：每日 3 件事，SharedPreferences 持久化 | **Gratitude List**: 3 things daily, SharedPreferences persistence |
| **情绪时间线**：Feed 流式浏览，自动日期分组（今天/昨天/日期） | **Emotion Timeline**: feed-style browsing with auto date grouping |

### 每日关怀 · Daily Care

| 中文 | English |
|------|---------|
| **关注目标系统**：可选成长目标（建立记录习惯、减压、睡眠前整理、识别低落周期） | **Focus goal system**: selectable goals (habit building, stress reduction, pre-sleep wind-down, low-mood cycle identification) |
| **首页关怀卡片**：基于当前情绪趋势的动态状态 + 一键操作 | **Homepage care card**: dynamic daily status + one-tap action |
| **隐私中心**：一键清除所有本地数据（DB、SharedPreferences、KeyChain、通知计划） | **Privacy center**: one-tap wipes all local data |

### SOS 紧急干预 · SOS Emergency Intervention

| 中文 | English |
|------|---------|
| **三重触发**：面部消极检测 + 手机摇晃 + 手动按钮 | **Triple trigger**: facial negative detection + phone shake + manual button |
| 3-2-1 倒计时确认弹窗 | 3-2-1 countdown confirmation dialog |
| **BreathingOverlayView**：5 层渐变紫色同心圆 | **BreathingOverlayView**: 5-layer gradient purple concentric rings |
| **双呼吸模式**：盒子呼吸 (4-4-4-4) + 4-7-8 深度放松 | **Dual breathing modes**: Box Breathing + 4-7-8 Deep Relaxation |
| 自动短信发送至预设紧急联系人 | Auto SMS to preset emergency contact |
| **一键拨打** 12355 全国青少年心理援助热线 | **One-tap dial** to national psychological hotline |
| **隐私模式**：FLAG_SECURE 防截屏 | **Privacy mode**: FLAG_SECURE screenshots blocking |

### 数据管理 · Data Management

| 中文 | English |
|------|---------|
| **3 格式导出**：TXT / CSV / Markdown，支持时间范围过滤 | **3-format export**: TXT, CSV, Markdown with time-range filtering |
| **JSON 全量备份/恢复** | **JSON full backup/restore** |
| **智能通知**：基于情绪状态的个性化每日提醒 | **Smart notifications**: mood-based personalized daily reminders |
| AlarmManager + BootReceiver 推送周报 | Weekly report push via AlarmManager + BootReceiver |

### 安全 · Security

| 中文 | English |
|------|---------|
| **生物识别应用锁**：AndroidX Biometric 指纹/面部解锁 | **Biometric lock**: fingerprint/face unlock via AndroidX Biometric |
| **全本地数据**：SQLite + SharedPreferences，零云端依赖 | **All data local**: SQLite + SharedPreferences, zero cloud |
| **SecureStorage**：KeyChain 加密 API Key 与紧急联系人 | **SecureStorage**: KeyChain-encrypted API key and contacts |
| DeepSeek API：HTTPS 加密传输 | DeepSeek API: HTTPS encrypted |

---

## 技术栈 · Tech Stack

| 组件 Component | 技术 Technology |
|-----------|-----------|
| 语言 Language | Java 11 |
| 最低/目标 SDK | 24 / 36 |
| 面部检测 Face Detection | Google MediaPipe FaceLandmarker (52 blendshapes) |
| 语音识别 Voice Recognition | Android SpeechRecognizer (zh-CN) |
| AI 对话 AI Chat | DeepSeek Chat API via OkHttp |
| 相机 Camera | CameraX + ImageAnalysis (RGBA_8888) |
| 数据库 Database | SQLite via SQLiteOpenHelper |
| 本地存储 Storage | SharedPreferences + SecureStorage (Android KeyChain) |
| UI 框架 UI Framework | Material Design 3 (DayNight, custom ShapeAppearance) |
| 架构 Architecture | Single Activity + 4 Fragments, MVVM |
| 通知 Notifications | AlarmManager + BroadcastReceiver |
| 生物识别 Biometrics | AndroidX Biometric |
| 动画 Animations | ObjectAnimator, ValueAnimator, custom Canvas views |

---

## 项目结构 · Project Structure

> `app/src/main/java/com/example/emoscope/` — 30 Java 源文件

### 引擎层 · Engines

| 文件 File | 职责 Role |
|------|------|
| `FaceAnalyzer.java` | 52 MediaPipe blendshape → 10 种情绪 → EMA 平滑 → 0-100 加权打分 |
| `AiMemoryEngine.java` | 30 天回顾 NLP：压力/快乐来源 Top-3、低点/高光检测 |
| `BreathingEngine.java` | 序列呼吸动画：盒子呼吸 & 4-7-8，阶段切换震动反馈 |
| `DeepSeekClient.java` | OkHttp 客户端：指数退避重试、流式打字机效果、结构化 prompt |
| `EmoLineChartView.java` | 自绘 Canvas 图表：情绪区域渐变、虚线参考线、日期 X 轴 |

### 控制器 · Controllers

| 文件 File | 职责 Role |
|------|------|
| `MainActivity.java` | 中枢调度 — Camera/Voice/SOS 委托至独立控制器，Fragment 导航 |
| `CameraEmotionController.java` | CameraX + MediaPipe 生命周期，光照采样，面部推理节流 |
| `VoiceRecognitionController.java` | Android SpeechRecognizer 生命周期与转录回调 |
| `SosInterventionController.java` | SOS 倒计时、紧急短信节流、呼吸遮罩编排 |

### 自定义动画视图 · Custom Animation Views

| 文件 File | 职责 Role |
|------|------|
| `BreathingOverlayView.java` | 5 层渐变紫色同心圆呼吸动画 |
| `VoiceWaveView.java` | 5 根随机高度的录音声波动画条 |
| `SonarRippleView.java` | 从麦克风按钮中心扩散的半透明波纹 |
| `ConfettiView.java` | 60 粒子彩色爆发（徽章解锁） |

### 工具类 · Utilities

| 文件 File | 职责 Role |
|------|------|
| `EmoDatabaseHelper.java` | SQLiteOpenHelper：建表、迁移、统一 `saveRecord()` |
| `MoodDialogHelper.java` | 共享 2×4 表情网格心情选择器 |
| `StreakManager.java` | 连续打卡计算：更新、重置、格式化显示文本 |
| `HistoryAdapter.java` | RecyclerView 双 ViewType 适配器（日期头 + 记录条目） |
| `SecureStorage.java` | Android KeyChain 加密存储 API Key 与联系人 |
| `NotificationHelper.java` | AlarmManager 调度：每日提醒、每周摘要、自定义消息构建 |
| `ReminderReceiver.java` | BroadcastReceiver：闹钟触发 |
| `BootReceiver.java` | 设备重启后重新注册所有闹钟 |
| `LocalDataManager.java` | 集中式本地数据清除 |
| `CrashHandler.java` | 全局未捕获异常处理 |

### Fragment 层 · Fragments (MVVM)

| 文件 File | 标签 Tab | 内容 Content |
|------|-----|---------|
| `RadarFragment.java` | 首页 Home | 情绪分、7 日趋势、每日关怀卡、面容分析入口、AI 观察、语音按钮 |
| `WorkshopFragment.java` | 成长 Growth | AI 洞察、等级卡、日记、冥想、感恩、周报、徽章 |
| `HistoryFragment.java` | 记录 Timeline | 情绪图表、统计胶囊、筛选芯片、时间线 Feed |
| `SettingsFragment.java` | 我的 Profile | 关注目标、隐私中心、数据清除、生物识别锁、通知与引擎配置 |

### ViewModel 层 · ViewModels

| 文件 File | 职责 Role |
|------|------|
| `RadarViewModel.java` | 首页 LiveData：面部/光照/语音/AI 回复/SOS/TTS |
| `HistoryViewModel.java` | 时间线 LiveData：记录条目、统计、筛选、刷新状态 |

### 资源层 · Resource Layer

| 目录 Directory | 内容 Contents |
|-----------|----------|
| `anim/` | 10 个自定义动画 XML |
| `color/` | 底部导航着色选择器 |
| `drawable/` | 139 个矢量图标、渐变形状、卡片背景、装饰条、主题资源 |
| `layout/` | 7 个 XML 布局文件 |
| `menu/` | 4 标签底部导航菜单 |
| `values/` | 色彩 (70+)、主题 (Material 3 DayNight)、形状、字符串 (50+) |
| `values-night/` | 暗色主题覆盖 |
| `xml/` | FileProvider、Shortcuts、备份规则、网络安全配置 |

---

## 关键设计决策 · Key Design Decisions

### 为什么 10 种情绪而不是 6 种？· Why 10 emotions?

Ekman 的 6 种基本情绪（愉悦、悲伤、愤怒、恐惧、惊讶、厌恶）基础上，加入了轻蔑（Ekman 后期工作）、焦虑（临床意义）、疲惫（日常状态）和平静（基线状态）。MediaPipe 的 52 个 blendshape 提供了足够的信号粒度。

Paul Ekman's basic 6 were extended with contempt (Ekman's later work), anxiety (clinical relevance), fatigue (daily life), and calm (baseline). MediaPipe's 52 blendshapes provide sufficient signal granularity.

### 为什么加权打分？· Why weighted scoring?

"愉悦"（权重 95）占主导与"恐惧"（权重 10）占主导是完全不同的心理状态。加权综合分数给出一个直观的 0-100 数字来反映整体心理健康状况。

Dominance of "joy" (weight 95) is very different from "fear" (weight 10). The weighted composite gives a single intuitive 0-100 number reflecting overall well-being.

### 为什么本地优先？· Why local-first?

面部表情数据极其私密。所有数据均存储在本地。AI 记忆引擎完全离线运行。仅用户主动发起的 DeepSeek 调用会经过网络。

Facial expression data is extremely personal. Everything stores locally. The AI memory engine runs fully offline. Only user-initiated DeepSeek calls go over the network.

### 为什么提取控制器？· Why controller extraction?

MainActivity 原本内联了所有相机、语音和 SOS 逻辑（约 1000 行）。提取 `CameraEmotionController`、`VoiceRecognitionController` 和 `SosInterventionController` 实现了单一职责隔离，使每个子系统可独立测试，Activity 保持轻量。

Extracting controllers from a monolithic MainActivity achieves single-responsibility isolation, making each subsystem independently testable.

---

## 快速开始 · Getting Started

### 环境要求 · Prerequisites

- Android Studio Hedgehog 或更新版本
- JDK 11+
- Android SDK 36
- DeepSeek API Key（在应用设置中配置）

### 构建 · Build

```bash
git clone https://github.com/huangziyangggg/EmoScope.git
cd EmoScope

# Debug 构建
./gradlew assembleDebug

# Release 构建 (需要 keystore.properties)
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 模拟器运行 · Run on Emulator

```bash
emulator -avd Medium_Phone &
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.emoscope/.MainActivity
```

### 首次使用 · First Launch

1. 授权相机和麦克风权限 · Grant camera + microphone permissions
2. 首页点击「打开面容分析」· Tap the face analysis entry on homepage
3. 按住底部语音按钮说话 · Hold the voice button to speak
4. 探索成长、记录、我的三个标签 · Explore Growth, Timeline, and Profile tabs

---

## 隐私与合规 · Privacy & Compliance

EmoScope 是情绪记录与自助支持工具，**不提供医疗诊断**，不能替代医生、心理咨询师或紧急救援。

EmoScope is an emotion journaling and self-support tool. It is **not a medical diagnosis product** and does not replace doctors, therapists, or emergency services.

| 数据 Data | 处理方式 Handling |
|-----------|------------------|
| 相机 Camera | 仅用于本地面部情绪分析，不上传画面 |
| 麦克风 Microphone | 通过 Android SpeechRecognizer 将语音转为文本 |
| 短信 SMS | 仅 SOS 流程向预设联系人发送求助消息 |
| AI 分析 AI Analysis | 使用 AI 功能时，文本及必要上下文可能发送至 DeepSeek |
| 本机记录 Local Records | 存储在本地 SQLite 数据库 |
| API Key & 联系人 | 通过 Android Keystore 加密保存 (SecureStorage) |
| 用户控制 User Control | 设置 → 隐私中心查看数据使用说明；设置 → 清除本机数据一键删除 |

---

## 运行要求 · Requirements

- Android 7.0 (API 24) 或更高版本
- 相机 Camera（可选，用于面部分析）
- 麦克风 Microphone（可选，用于语音输入）
- ~78 MB 存储空间
- 网络 Internet（仅 DeepSeek AI 语音分析需要）

---

## 致谢 · License

本项目在 **哈尔滨工业大学** 开发，作为多模态情绪识别与心理评估系统研究项目。

Developed at **Harbin Institute of Technology** as a multimodal emotion recognition and psychological assessment system.

---

## 联系 · Contact

GitHub: [@huangziyangggg](https://github.com/huangziyangggg)

---

<p align="center"><em>
情绪是天气，你是天空。<br>
EmoScope 帮你记录每一片云，<br>
直到你发现——你本就宽广无垠。<br><br>
Emotions are weather; you are the sky.<br>
EmoScope helps you record every cloud,<br>
until you see that you have always been vast.
</em></p>
