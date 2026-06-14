# iOS Liquid Glass Redesign Design

## Goal
Rebuild EmoScope's Android XML UI into a calm, iOS-inspired emotional health experience with liquid-glass surfaces, clearer hierarchy, and fewer visually noisy cards.

## Visual Direction
- Background: soft mist-white canvas with subtle aurora gradients.
- Surfaces: translucent white glass panels with fine borders, low elevation, and generous spacing.
- Typography: large iOS-like page titles, compact labels, restrained metadata text.
- Color: cool neutrals plus soft blue, mint, peach, and violet accents. Purple is no longer the dominant theme.
- Motion: keep existing lightweight entrance animations; avoid heavy custom animation in this pass.

## Information Architecture
- Radar: primary dashboard. One hero mood glass panel, then quick actions and a small insight panel.
- History: analytics view. Large title, glass chart panel, segmented filters, journal-like record list.
- Workshop: care space. Large title, current practice, journal, meditation, gratitude, and AI insight grouped by intent.
- Settings: iOS Settings style. Grouped rows, quiet switches, clear privacy and AI sections.

## Implementation Strategy
Use existing Android XML, MaterialCardView, custom chart views, and current Fragment Java bindings. Add reusable colors and drawable resources, then replace page layouts without changing data contracts. Keep view ids stable wherever Java code depends on them.

## Risks
- Android XML cannot provide true system blur on all devices, so the glass effect will be simulated with translucent fills, gradients, strokes, and shadows.
- Large layout changes can break Java `findViewById` bindings, so ids must be preserved.
- Build verification may require sandbox escalation because Gradle needs local loopback.
