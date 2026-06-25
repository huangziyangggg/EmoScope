# 心镜 Architecture Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor 心镜 into smaller, testable modules without changing user-visible behavior.

**Architecture:** Keep the current Android Java app and package name stable. Extract pure logic first, then controller/service classes, and leave Fragment/Activity classes as thin UI coordinators. Every extraction must preserve behavior and add or reuse tests before production code changes.

**Tech Stack:** Android Java, Material Components, JUnit 4, Gradle Android Plugin, minSdk 24, targetSdk 36.

## Global Constraints

- Do not rename the Java package `com.example.emoscope` during this refactor.
- Do not reset existing local data keys such as `EmoScopePrefs`; compatibility matters more than cosmetic internal names.
- Do not rewrite the app from scratch.
- Each task must pass `.\gradlew.bat testDebugUnitTest`, `.\gradlew.bat :app:lintDebug`, and `.\gradlew.bat assembleDebug` with `JAVA_HOME=C:\Program Files\Java\jdk-21`.
- Keep UI behavior and navigation stable unless a task explicitly states a behavior change.

---

### Task 1: Centralize Brand And Export Naming

**Files:**
- Create: `app/src/main/java/com/example/emoscope/AppBrand.java`
- Create: `app/src/test/java/com/example/emoscope/AppBrandTest.java`
- Modify: `app/src/main/java/com/example/emoscope/fragments/HistoryFragment.java`
- Modify: `app/src/main/java/com/example/emoscope/fragments/WorkshopFragment.java`
- Modify: `app/src/main/java/com/example/emoscope/LocalDataManager.java`

**Interfaces:**
- Produces: `AppBrand.APP_NAME`, `AppBrand.EXPORT_DIRECTORY`, `AppBrand.LEGACY_EXPORT_DIRECTORY`, `AppBrand.androidSourceLabel()`, `AppBrand.researchFileName(String, boolean)`, `AppBrand.reportFileName(String, String)`, `AppBrand.backupFileName(String)`, `AppBrand.weeklyReportFileName(String)`, `AppBrand.localJsonExportFileName(String)`.
- Consumes: existing timestamp strings formatted as `yyyyMMdd_HHmmss`.

- [ ] **Step 1: Write failing tests**

```java
package com.example.emoscope;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppBrandTest {
    @Test
    public void exposesChineseProductNameAndCompatibleDirectories() {
        assertEquals("心镜", AppBrand.APP_NAME);
        assertEquals("心镜", AppBrand.EXPORT_DIRECTORY);
        assertEquals("EmoScope", AppBrand.LEGACY_EXPORT_DIRECTORY);
        assertEquals("心镜 Android", AppBrand.androidSourceLabel());
    }

    @Test
    public void buildsUserVisibleExportFileNames() {
        assertEquals("心镜_Research_20260619_140000.json",
                AppBrand.researchFileName("20260619_140000", true));
        assertEquals("心镜_Research_20260619_140000.csv",
                AppBrand.researchFileName("20260619_140000", false));
        assertEquals("心镜_20260619_140000.md",
                AppBrand.reportFileName("20260619_140000", "md"));
        assertEquals("心镜_Backup_20260619_140000.json",
                AppBrand.backupFileName("20260619_140000"));
        assertEquals("心镜_Weekly_20260619_140000.png",
                AppBrand.weeklyReportFileName("20260619_140000"));
        assertEquals("xinjing_export_20260619_140000.json",
                AppBrand.localJsonExportFileName("20260619_140000"));
    }
}
```

- [ ] **Step 2: Run red test**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.emoscope.AppBrandTest`
Expected: compile failure because `AppBrand` does not exist.

- [ ] **Step 3: Implement `AppBrand`**

```java
package com.example.emoscope;

public final class AppBrand {
    public static final String APP_NAME = "心镜";
    public static final String EXPORT_DIRECTORY = APP_NAME;
    public static final String LEGACY_EXPORT_DIRECTORY = "EmoScope";

    private AppBrand() {}

    public static String androidSourceLabel() {
        return APP_NAME + " Android";
    }

    public static String researchFileName(String timestamp, boolean json) {
        return APP_NAME + "_Research_" + timestamp + (json ? ".json" : ".csv");
    }

    public static String reportFileName(String timestamp, String extension) {
        return APP_NAME + "_" + timestamp + "." + extension;
    }

    public static String backupFileName(String timestamp) {
        return APP_NAME + "_Backup_" + timestamp + ".json";
    }

    public static String weeklyReportFileName(String timestamp) {
        return APP_NAME + "_Weekly_" + timestamp + ".png";
    }

    public static String localJsonExportFileName(String timestamp) {
        return "xinjing_export_" + timestamp + ".json";
    }
}
```

- [ ] **Step 4: Replace scattered brand/export literals**

Use `AppBrand` in export source labels, export file names, backup file names, weekly report file names, and compatible import directories.

- [ ] **Step 5: Verify**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest --tests com.example.emoscope.AppBrandTest --no-daemon --gradle-user-home .gradle
.\gradlew.bat testDebugUnitTest --no-daemon --gradle-user-home .gradle
.\gradlew.bat :app:lintDebug --no-daemon --gradle-user-home .gradle
.\gradlew.bat assembleDebug --no-daemon --gradle-user-home .gradle
```

Expected: all commands exit 0.

### Task 2: Extract History Export Coordination

**Files:**
- Create: `app/src/main/java/com/example/emoscope/history/HistoryExportRepository.java`
- Create: `app/src/test/java/com/example/emoscope/history/HistoryExportRepositoryTest.java`
- Modify: `app/src/main/java/com/example/emoscope/fragments/HistoryFragment.java`

**Interfaces:**
- Produces: `HistoryExportRepository.ExportRows` as a small data result for text, CSV, Markdown, research JSON, research CSV, backup JSON.
- Consumes: `EmoDatabaseHelper`, `ResearchDataExporter`, `AppBrand`.

- [ ] **Step 1: Add tests around pure formatting and path selection**
- [ ] **Step 2: Extract database read and file naming out of `HistoryFragment`**
- [ ] **Step 3: Keep `HistoryFragment` responsible only for menus, dialogs, and share intents**
- [ ] **Step 4: Run full verification commands from Task 1**

### Task 3: Split MainActivity Controllers

**Files:**
- Create: `app/src/main/java/com/example/emoscope/controllers/PermissionCoordinator.java`
- Create: `app/src/main/java/com/example/emoscope/controllers/SosUiCoordinator.java`
- Modify: `app/src/main/java/com/example/emoscope/MainActivity.java`

**Interfaces:**
- Produces: small coordinators with explicit constructor dependencies and no hidden global state.
- Consumes: existing `SosInterventionController`, `BreathingEngine`, Android permission APIs.

- [ ] **Step 1: Extract permission request constants and rationale decisions behind `PermissionCoordinator`**
- [ ] **Step 2: Extract SOS overlay binding and close/hotline actions behind `SosUiCoordinator`**
- [ ] **Step 3: Keep `MainActivity` as navigation and lifecycle orchestrator only**
- [ ] **Step 4: Run full verification commands from Task 1**

### Task 4: Normalize UI Layout Resources

**Files:**
- Create: `app/src/main/res/values/dimens.xml`
- Modify: key fragment layouts to replace repeated `24dp`, `156dp`, card heights, and icon sizes with named dimens.

**Interfaces:**
- Produces: names like `@dimen/page_horizontal_padding`, `@dimen/bottom_nav_safe_padding`, `@dimen/home_entry_height`.

- [ ] **Step 1: Add dimens for repeated layout constants**
- [ ] **Step 2: Replace one page at a time and run resource build after each page**
- [ ] **Step 3: Run lint and assemble**

## Self-Review

- Spec coverage: covers the agreed “保功能，换骨架” direction and starts with low-risk pure logic.
- Placeholder scan: no TBD markers; later tasks are intentionally higher-level but still name files, boundaries, and verification commands.
- Type consistency: Task 1 has exact method names and signatures; later tasks will be expanded before execution.
