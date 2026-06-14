# iOS Liquid Glass Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current heavy Material-card UI with an iOS-inspired liquid-glass visual system across the main EmoScope screens.

**Architecture:** Keep the existing Fragment and ViewModel contracts. Rebuild resources and XML layouts around reusable glass drawables, stable ids, and a shared page composition pattern.

**Tech Stack:** Android XML layouts, Material Components, existing Java fragments, vector/drawable XML resources.

---

### Task 1: Visual System

**Files:**
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/values/shapes.xml`
- Create: reusable `app/src/main/res/drawable/glass_*.xml` resources

- [ ] Add liquid glass color tokens with neutral, blue, mint, peach, rose, and violet accents.
- [ ] Add glass panel drawables with translucent fill, 1dp white/outline strokes, and soft rounded radii.
- [ ] Add a page aurora background drawable.

### Task 2: Shell And Navigation

**Files:**
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/menu/bottom_nav_menu.xml`

- [ ] Apply the glass page background to the app shell.
- [ ] Make bottom navigation feel like a floating iOS tab bar while preserving current menu ids.

### Task 3: Radar Dashboard

**Files:**
- Modify: `app/src/main/res/layout/fragment_radar.xml`

- [ ] Rebuild top area as large title plus compact controls.
- [ ] Replace stacked feature cards with one mood hero panel, quick-action glass buttons, and care insight panel.
- [ ] Preserve all ids referenced by `RadarFragment` and `MainActivity`.

### Task 4: Secondary Screens

**Files:**
- Modify: `app/src/main/res/layout/fragment_history.xml`
- Modify: `app/src/main/res/layout/fragment_workshop.xml`
- Modify: `app/src/main/res/layout/fragment_settings.xml`
- Modify: `app/src/main/res/layout/item_history_record.xml`

- [ ] Convert History into glass analytics panels and journal-like records.
- [ ] Convert Workshop into grouped care modules.
- [ ] Convert Settings into iOS-style grouped rows.
- [ ] Keep current ids stable for Java bindings.

### Task 5: Verification

**Files:**
- Read changed XML and Java bindings.

- [ ] Run resource/id grep checks for required ids.
- [ ] Run `git diff --check`.
- [ ] Attempt `:app:assembleDebug` with JDK 21; if sandbox loopback blocks Gradle, report that accurately.
