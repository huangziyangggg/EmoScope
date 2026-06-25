# 心镜首页「安静陪伴」实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让首页在小屏幕上以安静、清晰的层级呈现三个核心入口，同时保持所有现有行为。

**Architecture:** 只调整 `fragment_radar.xml` 的展示层与统一尺寸资源；不改变 `RadarFragment` 回调、`MainActivity` 导航和数据流。用实际模拟器截图验证视觉结果。

**Tech Stack:** Android XML、Material Components、Java、Gradle、ADB。

## Global Constraints

- 用户可见品牌名继续由 `AppBrand` 集中管理；本任务不新增品牌文案硬编码。
- 不修改记录、导出、AI、权限和导航行为。
- 不创建 Git 提交。
- 手写文件仅通过 `apply_patch` 修改。

---

### Task 1: 收敛首页标题区与三入口的视觉节奏

**Files:**
- Modify: `app/src/main/res/layout/fragment_radar.xml`
- Modify: `app/src/main/res/values/dimens.xml`

**Interfaces:**
- Consumes: 现有 `RadarFragment` 的 `tvDateTop`、`tvDynamicGreeting`、`btnTtsToggle`、三个入口 ID。
- Produces: 不改变 ID、回调和视图层级行为的紧凑首页布局。

- [ ] **Step 1: 记录失败前的设备证据**

运行：在 1080×2400 模拟器截图首页，检查标题文字、语音按钮、三张入口卡片和底部导航。

预期：记录当前视觉层级与潜在窄屏冲突位置。

- [ ] **Step 2: 调整 XML 与尺寸资源**

将标题的 `paddingEnd` 设为不小于 `home_action_size + 12dp`，使其不进入语音按钮区域；保持三张入口等高、图标尺寸与间距继续使用 `dimens.xml` 统一资源。

- [ ] **Step 3: 构建资源**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
.\gradlew.bat assembleDebug --no-daemon --gradle-user-home .gradle
```

预期：`BUILD SUCCESSFUL`。

- [ ] **Step 4: 安装并做设备截图复验**

运行：安装 Debug APK，截图首页。

预期：问候不与语音按钮重叠；三入口完整可见；底栏不遮挡内容。

### Task 2: 回归验证

**Files:**
- Test: `app/src/test/java/com/example/emoscope/*Test.java`

- [ ] **Step 1: 执行完整单元测试与 Lint**

```powershell
.\gradlew.bat testDebugUnitTest :app:lintDebug --no-daemon --gradle-user-home .gradle
git diff --check
```

预期：Gradle 任务和差异检查均成功。
