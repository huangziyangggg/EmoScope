# EmoScope — 多模态情绪识别与心理评估系统

<p align="center">
  <strong>记录情绪 · 理解情绪 · 改善情绪 · 长期成长</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-1.0-B794F4?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/minSdk-24-green?style=flat-square" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/targetSdk-36-blue?style=flat-square" alt="Target SDK"/>
  <img src="https://img.shields.io/badge/language-Java%2011-orange?style=flat-square" alt="Language"/>
  <img src="https://img.shields.io/badge/arch-MVVM-purple?style=flat-square" alt="Architecture"/>
  <img src="https://img.shields.io/badge/files-234-silver?style=flat-square" alt="Files"/>
</p>

> 📖 [English Documentation](README.md)

---

## 概述

EmoScope 是一个多模态情绪识别与心理健康辅助系统，融合**实时面部微表情分析**、**语音情绪检测**和 **30 天 AI 记忆引擎**，作为你的个人情绪成长伙伴。

不同于依赖手动输入的传统心情追踪应用，EmoScope 通过 Google MediaPipe 的 **52 个面部 blendshape 系数**主动分析你的情绪状态，并通过 DeepSeek 大语言模型融合语音、语速和环境光照信息，生成个性化解读。

**这不是一个冷冰冰的检测工具，而是一个真正理解你的 AI。**

---

## 核心理念

```
记录  →  理解  →  改善  →  成长
语音      AI       工具       等级
面容      记忆      日记       徽章
手动      洞察      冥想       报告
```

---

## 核心功能

### 面部情绪分析
- **52 个 MediaPipe FaceLandmarker blendshape** 实时追踪
- **10 维情绪空间**：愉悦、悲伤、愤怒、恐惧、惊讶、厌恶、轻蔑、焦虑、疲惫、平静
- **EMA 平滑**（α=0.3）消除帧间抖动
- **加权打分**：每种情绪分配心理权重（愉悦=95, 恐惧=10），输出 0-100 综合指数
- **Top-3 显示**：相机叠加层展示主导情绪及精确百分比
- **拍照打分**：一键捕捉当前面部情绪快照并存入数据库
- **SOS 自动触发**：消极情绪占比超过 35% 时自动预警

### 语音情绪识别
- Android 原生 `SpeechRecognizer`，zh-CN 中文语言
- `onPartialResults` **实时转写**
- **语速分析**：快（>4.5 字/秒）= 紧张，慢（<1.5 字/秒）= 低落
- 自定义 **VoiceWaveView** — 5 根随声音跳动的动画条
- 自定义 **SonarRippleView** — 从麦克风按钮扩散的声纳波纹
- DeepSeek 大模型融合语音文本 + 面部情绪 + 语速 + 环境光照

### AI 长期记忆引擎
- **AiMemoryEngine**：30 天回顾分析，纯本地计算，无需联网
- **压力来源 Top-3**：30+ 压力关键词词频分析
- **快乐来源 Top-3**：25+ 积极关键词匹配
- **情绪低点定位**：最低加权分出现的具体日期
- **高光时刻**：最高分数与最长的积极连续记录
- **AI 周报**：Canvas 绘制 PNG 图片，通过 FileProvider 分享

### 成长系统
- **Lv1-Lv5 等级进阶**：觉察新手 → 记录学徒 → 情绪旅人 → 自我管理者 → 情绪掌控者
- **12 枚成就徽章**：通过记录、连续打卡、日记、冥想、感恩解锁
- 徽章解锁时触发 **ConfettiView 粒子彩带效果**
- 自动连续打卡追踪

### 身心健康工坊
- **情绪日记**：每日写作引导，SQLite 持久化，草稿自动加载
- **正念冥想**：全屏沉浸式 3/5/10 分钟计时器
- **感恩清单**：每日记录 3 件值得感谢的事，SharedPreferences 持久化
- **情绪时间线**：Feed 流式浏览，自动日期分组（今天/昨天/日期）

### 每日关怀
- **关注目标系统**：可选个人成长目标（建立记录习惯、减压、睡眠前整理、识别低落周期）
- **首页关怀卡片**：基于当前情绪趋势的动态状态 + 一键操作
- **隐私中心**：一键清除所有本地数据（数据库、偏好设置、KeyChain 密钥、通知计划）

### SOS 紧急干预
- **三重触发机制**：面部消极检测 + 手机摇晃 + 手动按钮
- **3-2-1 倒计时**确认弹窗
- **BreathingOverlayView**：5 层渐变紫色同心圆呼吸动画
- **双呼吸模式**：盒子呼吸法 (4-4-4-4) + 4-7-8 深度放松法
- **自动短信**发送至预设紧急联系人
- **一键拨打** 12355 全国青少年心理援助热线
- **隐私模式**：FLAG_SECURE 阻止截图

### 数据管理
- **3 种格式导出**：TXT / CSV / Markdown，支持时间范围筛选
- **JSON 全量备份/恢复**
- **智能通知**：基于情绪状态的个性化每日提醒
- AlarmManager + BootReceiver 推送**每周报告**

### 安全与隐私
- **生物识别应用锁**：通过 AndroidX Biometric 实现指纹/面部解锁
- **全本地数据**：SQLite + SharedPreferences，零云端依赖
- **SecureStorage**：基于 Android KeyChain 加密存储 API Key 与紧急联系人
- DeepSeek API 采用 HTTPS 加密传输

---

## 技术栈

| 组件 | 技术 |
|------|------|
| 编程语言 | Java 11 |
| 最低/目标 SDK | 24 / 36 |
| 面部检测 | Google MediaPipe FaceLandmarker（52 个 blendshape） |
| 语音识别 | Android SpeechRecognizer（中文 zh-CN） |
| AI 对话 | DeepSeek Chat API，通过 OkHttp 调用 |
| 相机 | CameraX + ImageAnalysis（RGBA_8888 格式） |
| 数据库 | SQLite，通过 SQLiteOpenHelper 管理 |
| 本地存储 | SharedPreferences + SecureStorage（Android KeyChain） |
| UI 框架 | Material Design 3（DayNight 日夜间主题，自定义 ShapeAppearance） |
| 架构模式 | 单 Activity + 4 Fragment，MVVM |
| 通知 | AlarmManager + BroadcastReceiver |
| 生物识别 | AndroidX Biometric |
| 动画 | ObjectAnimator、ValueAnimator、自定义 Canvas 视图 |

---

## 项目结构

> 234 个文件 · 30 个 Java 源文件 · 139 个 drawable 资源 · 7 个布局文件

### 核心层 — `app/src/main/java/com/example/emoscope/`

**引擎 Engines**

| 文件 | 职责 |
|------|------|
| `FaceAnalyzer.java` | 52 个 MediaPipe blendshape → 10 种情绪 → EMA 平滑 → 0-100 加权打分 |
| `AiMemoryEngine.java` | 30 天回顾 NLP：压力/快乐来源 Top-3、低点/高光检测 |
| `BreathingEngine.java` | 序列呼吸动画：盒子呼吸 & 4-7-8，阶段切换震动反馈 |
| `DeepSeekClient.java` | OkHttp 客户端：指数退避重试、流式打字机效果、结构化 prompt |
| `EmoLineChartView.java` | 自绘 Canvas 图表：情绪区域渐变、虚线参考线、日期标注 X 轴 |

**控制器 Controllers**

| 文件 | 职责 |
|------|------|
| `MainActivity.java` | 中枢调度 — Camera/Voice/SOS 委托至独立控制器，Fragment 导航 |
| `CameraEmotionController.java` | CameraX + MediaPipe 生命周期管理，光照采样，面部推理节流 |
| `VoiceRecognitionController.java` | Android SpeechRecognizer 生命周期与转录回调 |
| `SosInterventionController.java` | SOS 倒计时、紧急短信节流、呼吸遮罩编排 |

**自定义动画视图 Animation Views**

| 文件 | 职责 |
|------|------|
| `BreathingOverlayView.java` | 5 层渐变紫色同心圆呼吸动画 |
| `VoiceWaveView.java` | 5 根随机高度的录音声波动画条 |
| `SonarRippleView.java` | 从麦克风按钮中心扩散的半透明波纹 |
| `ConfettiView.java` | 60 粒子彩色爆发（徽章解锁时触发） |

**工具类 Utilities**

| 文件 | 职责 |
|------|------|
| `EmoDatabaseHelper.java` | SQLiteOpenHelper：建表、迁移、统一 `saveRecord()` 方法 |
| `MoodDialogHelper.java` | 共享 2×4 表情网格心情选择器 |
| `StreakManager.java` | 连续打卡计算：更新、重置、格式化显示文本 |
| `HistoryAdapter.java` | RecyclerView 双 ViewType 适配器（日期头 + 记录条目） |
| `SecureStorage.java` | Android KeyChain 加密存储 API Key 与联系人 |
| `NotificationHelper.java` | AlarmManager 调度：每日提醒、每周摘要 |
| `ReminderReceiver.java` | BroadcastReceiver 闹钟触发 |
| `BootReceiver.java` | 设备重启后重新注册所有闹钟 |
| `LocalDataManager.java` | 集中式本地数据清除 |
| `CrashHandler.java` | 全局未捕获异常处理 |

**Fragment 层（MVVM）**

| 文件 | 标签 | 内容 |
|------|------|------|
| `RadarFragment.java` | 首页 | 情绪分、7 日趋势、每日关怀卡、面容分析入口、AI 观察、语音按钮 |
| `WorkshopFragment.java` | 成长 | AI 洞察、等级卡、情绪日记、正念冥想、感恩清单、周报、徽章 |
| `HistoryFragment.java` | 记录 | 情绪图表、统计胶囊、筛选芯片、时间线 Feed |
| `SettingsFragment.java` | 我的 | 关注目标、隐私中心、数据清除、生物识别锁、通知与引擎配置 |

**ViewModel 层**

| 文件 | 职责 |
|------|------|
| `RadarViewModel.java` | 首页 LiveData：面部/光照/语音/AI 回复/SOS/TTS |
| `HistoryViewModel.java` | 时间线 LiveData：记录条目、统计、筛选、刷新状态 |

### 资源层 — `app/src/main/res/`

| 目录 | 内容 |
|------|------|
| `anim/` | 10 个自定义动画 XML |
| `color/` | 底部导航着色选择器 |
| `drawable/` | 139 个矢量图标、渐变形状、卡片背景、装饰条、主题资源 |
| `layout/` | 7 个 XML 布局文件 |
| `menu/` | 4 标签底部导航菜单 |
| `values/` | 色彩（70+）、主题（Material 3 DayNight）、形状、字符串（50+） |
| `values-night/` | 暗色主题覆盖 |
| `xml/` | FileProvider、Shortcuts、备份规则、网络安全配置 |

---

## 关键设计决策

### 为什么选择 10 种情绪而非 6 种？
在 Ekman 的 6 种基本情绪（愉悦、悲伤、愤怒、恐惧、惊讶、厌恶）基础上，加入了轻蔑（Ekman 后期研究成果）、焦虑（临床心理学意义）、疲惫（日常生活状态）和平静（情绪基线）。MediaPipe 的 52 个 blendshape 提供了足够的信号粒度来支撑这一扩展。

### 为什么采用加权打分？
"愉悦"（权重 95）占主导与"恐惧"（权重 10）占主导代表完全不同的心理状态。加权综合分数将复杂的多维情绪数据浓缩为一个直观的 0-100 数字，便于用户理解整体心理健康状况。

### 为什么坚持本地优先？
面部表情数据极其私密。所有情绪记录存储在本地 SQLite 数据库中。AI 记忆引擎完全离线运行。仅用户主动发起的 DeepSeek 调用会经过网络传输。

### 为什么提取独立控制器？
MainActivity 原本内联了所有相机、语音和 SOS 逻辑（约 1000 行代码）。提取 `CameraEmotionController`、`VoiceRecognitionController` 和 `SosInterventionController` 实现了单一职责隔离，使每个子系统可独立测试，Activity 保持轻量。

---

## 快速开始

### 环境要求
- Android Studio Hedgehog 或更新版本
- JDK 11+
- Android SDK 36
- DeepSeek API Key（在应用设置中配置）

### 构建

```bash
git clone https://github.com/huangziyangggg/EmoScope.git
cd EmoScope

# Debug 构建
./gradlew assembleDebug

# Release 构建（需要 keystore.properties）
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### 模拟器运行

```bash
emulator -avd Medium_Phone &
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n com.example.emoscope/.MainActivity
```

### 首次使用
1. 授权相机和麦克风权限
2. 首页点击面容分析入口
3. 按住底部语音按钮说话
4. 探索成长、记录、我的三个标签页

---

## 隐私与合规

EmoScope 是情绪记录与自助支持工具，**不提供医疗诊断**，不能替代医生、心理咨询师或紧急救援服务。

| 数据类型 | 处理方式 |
|---------|---------|
| 相机 | 仅用于本地面部情绪分析，不上传画面 |
| 麦克风 | 通过 Android SpeechRecognizer 将语音转为文本 |
| 短信 | 仅在 SOS 流程中向预设紧急联系人发送求助消息 |
| AI 分析 | 使用 AI 功能时，文本及必要上下文可能发送至 DeepSeek 服务 |
| 本机记录 | 存储在本地 SQLite 数据库 |
| API Key 及联系人 | 通过 Android Keystore 加密保存（SecureStorage） |
| 用户控制 | 设置 → 隐私中心查看数据使用说明；设置 → 清除本机数据一键删除 |

---

## 运行要求

- Android 7.0 (API 24) 或更高版本
- 相机（可选，用于面部情绪分析）
- 麦克风（可选，用于语音输入）
- 约 78 MB 存储空间
- 网络连接（仅 DeepSeek AI 语音分析需要）

---

## 致谢

本项目在**哈尔滨工业大学**开发，作为多模态情绪识别与心理评估系统研究项目。

---

## 联系

GitHub: [@huangziyangggg](https://github.com/huangziyangggg)

---

<p align="center"><em>
情绪是天气，你是天空。<br>
EmoScope 帮你记录每一片云，<br>
直到你发现——你本就宽广无垠。
</em></p>
