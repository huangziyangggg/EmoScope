# EmoScope — 多模态情绪识别与情绪支持系统

<p align="center">
  <strong>记录情绪 · 看见线索 · 照顾自己 · 温和成长</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/version-5.3-B794F4?style=flat-square" alt="Version"/>
  <img src="https://img.shields.io/badge/minSdk-24-green?style=flat-square" alt="Min SDK"/>
  <img src="https://img.shields.io/badge/targetSdk-36-blue?style=flat-square" alt="Target SDK"/>
  <img src="https://img.shields.io/badge/language-Java%2011-orange?style=flat-square" alt="Language"/>
  <img src="https://img.shields.io/badge/arch-MVVM-purple?style=flat-square" alt="Architecture"/>
</p>

> 📖 [English Documentation](README.md)

---

## 概述

心镜（EmoScope）是一个隐私优先的情绪支持工具，融合**实时面部分析**、**语音转写**和**30 天本地记忆引擎**，帮助用户记录、回看和理解自己的情绪变化。

不同于依赖手动输入的传统心情追踪应用，心镜通过 Google MediaPipe 的 **52 个面部 blendshape 系数**提供情绪线索，并在用户明确同意后，通过 DeepSeek 大语言模型提供温和的陪伴式回应。

**心镜不是诊断工具，而是帮你看见自己的节奏。**

---

## 核心理念

```
记录  →  回顾  →  支持
语音     趋势       AI 解读
面容     统计       成长工坊
手动     30天回顾    SOS 守护
```

**从识别情绪到支持一个人。** 心镜的核心闭环不是"检测→报告"，而是"记录→回顾→支持"。每一步都由用户控制。

---

## 核心功能

### 面部情绪线索
- **52 个 MediaPipe FaceLandmarker blendshape** 实时追踪
- **10 维情绪空间**：愉悦、悲伤、愤怒、恐惧、惊讶、厌恶、轻蔑、焦虑、疲惫、平静
- **EMA 平滑**（α=0.3）减少帧间抖动
- **加权参考分**：0-100 参考指数（不作为诊断依据）
- **Top-3 显示**：相机叠加层展示主导情绪线索
- **拍照存档**：一键捕捉当前面部快照存入本地数据库
- **SOS 提示**：当情绪线索显示明显消极倾向时，出现"需要帮助"入口（用户可选择忽略）

### 语音记录
- Android 原生 `SpeechRecognizer`，zh-CN 中文
- `onPartialResults` **实时转写**
- **语速线索**：快/慢/正常，配有温和的解读文字
- 自定义 **VoiceWaveView** — 5 根随声音跳动的动画条
- 自定义 **SonarRippleView** — 从麦克风按钮扩散的声纳波纹
- 用户主动选择后，DeepSeek 可结合语音文本提供陪伴式回应

### AI 本地记忆引擎
- **AiMemoryEngine**：30 天回顾分析，纯本地计算，无需联网
- **你在意的**：关键词频率分析（考试、工作、失眠等 30+ 关键词）
- **让你开心的事**：积极关键词匹配（游戏、运动、美食等 25+ 关键词）
- **情绪低点日期**：值得关注的时刻
- **高光时刻**：记录中最平稳的日子
- **AI 周报**：Canvas 绘制 PNG 图片，通过 FileProvider 分享

### 成长系统
- **Lv1-Lv5 等级**：情绪观察者 → 情绪记录者 → 内心探索者 → 情绪管理者 → 自我掌控者
- **12 枚成就徽章**：通过记录、日记、冥想、感恩解锁
- 徽章解锁时触发 **ConfettiView 彩带效果**
- 自动连续记录追踪（不施压，只是看到自己的坚持）

### 照顾自己 · 工坊
- **情绪日记**：每日写作引导，SQLite 持久化
- **正念冥想**：全屏沉浸式 3/5/10 分钟计时器
- **感恩清单**：每日记录值得感谢的小事，SharedPreferences 持久化
- **情绪时间线**：自动日期分组（今天/昨天/日期）

### SOS 安全守护
- **三重触发**：面部提示 + 摇一摇 + 手动按钮
- **3-2-1 倒计时**确认弹窗（可随时取消）
- **BreathingOverlayView**：5 层渐变紫色同心圆呼吸动画
- **双呼吸模式**：盒子呼吸法 (4-4-4-4) + 4-7-8 深度放松法
- **用户确认后**才发送短信至预设联系人（不会自动发送）
- **一键拨打** 12355 全国青少年心理援助热线

### 数据管理
- **3 种格式导出**：TXT / CSV / Markdown，支持时间范围筛选
- **JSON 全量备份/恢复**
- **智能通知**：温和的每日提醒 + 每周回顾推送
- AlarmManager + BootReceiver 调度

### 安全与隐私
- **生物识别应用锁**：AndroidX Biometric 指纹/面部解锁
- **全本地数据**：SQLite + SharedPreferences，零云端依赖
- **SecureStorage**：Android KeyChain AES-256-GCM 加密 API Key 与紧急联系人
- DeepSeek API HTTPS 加密传输，用户需明确确认后才启用

---

## 技术栈

| 组件 | 技术 |
|------|------|
| 编程语言 | Java 11 |
| 最低/目标 SDK | 24 / 36 |
| 面部检测 | Google MediaPipe FaceLandmarker（52 blendshape） |
| 语音识别 | Android SpeechRecognizer（中文 zh-CN） |
| AI 对话 | DeepSeek Chat API（用户可选），通过 OkHttp 调用 |
| 相机 | CameraX + ImageAnalysis（RGBA_8888） |
| 数据库 | SQLite（SQLiteOpenHelper） |
| 安全存储 | SharedPreferences + Android Keystore（AES-256-GCM） |
| UI 框架 | Material Design 3（DayNight 深色模式适配） |
| 架构 | 单 Activity + 4 Fragment，MVVM |
| 通知 | AlarmManager + BroadcastReceiver |
| 生物识别 | AndroidX Biometric |
| 动画 | ObjectAnimator、ValueAnimator、自定义 Canvas |

---

## 项目结构

### 引擎层
| 文件 | 职责 |
|------|------|
| `FaceAnalyzer.java` | 52 blendshape → 10 情绪 → EMA 平滑 → 参考分 |
| `AiMemoryEngine.java` | 30 天本地回顾：关键词分析、情绪趋势 |
| `BreathingEngine.java` | 呼吸动画序列：盒子呼吸 & 4-7-8 |
| `DeepSeekClient.java` | OkHttp 客户端：指数退避、结构化 prompt、诊断约束 |
| `EmoLineChartView.java` | 自绘 Canvas 分段趋势图表 |

### 控制器层
| 文件 | 职责 |
|------|------|
| `MainActivity.java` | 中枢调度 |
| `CameraEmotionController.java` | CameraX + MediaPipe 生命周期 |
| `VoiceRecognitionController.java` | SpeechRecognizer 生命周期 |
| `SosInterventionController.java` | SOS 倒计时、紧急短信节流 |

### Fragment 层
| 文件 | 标签 | 内容 |
|------|------|------|
| `RadarFragment.java` | 首页 | 快速记录 / 语音 / 面容分析 三个入口 + AI 陪伴 |
| `WorkshopFragment.java` | 成长 | AI 洞察、等级卡、日记、冥想、感恩、周报、徽章 |
| `HistoryFragment.java` | 记录 | 趋势图表、统计、筛选、时间线、30天回顾、导出 |
| `SettingsFragment.java` | 我的 | 私人档案、隐私中心、SOS 配置、通知、引擎 |

---

## 关键设计决策

### 为什么坚持本地优先？
面部数据极其私密。所有情绪记录存储在本地 SQLite。AI 记忆引擎完全离线。仅在用户明确确认后，DeepSeek 调用才经过网络传输。

### 为什么不声称"准确率"？
当前阶段是工程验证，不是临床验证。表情和语音线索受光线、遮挡、语音识别误差和个人表达习惯影响。心镜的定位是"辅助观察"而非"精确诊断"。

### 为什么提取独立控制器？
MainActivity 原本内联了所有逻辑（约 1000 行）。提取控制器实现了单一职责隔离，使每个子系统可独立测试。

---

## 隐私声明

心镜是情绪记录与自助支持工具，**不提供医疗诊断**，不能替代医生、心理咨询师或紧急救援。

| 数据 | 处理方式 |
|------|---------|
| 相机 | 仅用于本地面部分析，不上传画面 |
| 麦克风 | Android 系统语音转文字 |
| 短信 | 仅在 SOS 流程中用户明确确认后发送 |
| AI 解读 | 用户确认后，文本及上下文可能发送至 DeepSeek |
| 本机记录 | 本地 SQLite，默认不参与云备份 |
| API Key | Android Keystore 加密保存 |
| 控制权 | 可随时导出、恢复或彻底清除本机数据 |

---

## 运行要求

- Android 7.0 (API 24) 或更高版本
- 相机（可选，用于面部情绪分析）
- 麦克风（可选，用于语音输入）
- 约 78 MB 存储空间
- 网络连接（仅 DeepSeek AI 功能需要）

---

## 致谢

本项目在**哈尔滨工业大学**开发，作为大一年度项目——多模态情绪识别与情绪支持系统。

---

## 联系

GitHub: [@huangziyangggg](https://github.com/huangziyangggg)

---

<p align="center"><em>
情绪是天气，你是天空。<br>
心镜帮你记录每一片云，<br>
直到你发现——你本就宽广无垠。
</em></p>
