# Repository Guidelines

## Project Structure & Module Organization

The project is a Kotlin Multiplatform library. Core APIs live in `haze`, reusable presets in
`haze-materials`, and library visual verifications in `haze-screenshot-tests`. Glass Gallery
screenshots live in `sample/screenshot-tests`. Auxiliary tooling and shared fixtures are under
`internal/`, while runnable examples reside in `sample/` (Android, Desktop, Web, macOS).
Documentation assets and the MkDocs site configuration are in `docs/` and `site/`.

## Build, Test, and Development Commands

Use `./gradlew build` for a full multi-platform build and verification. Targeted development builds
run faster: `./gradlew assembleDebug testDebug` for library artifacts,
`./gradlew :sample:android:installDebug` to load the Android sample on a connected device, and
`./gradlew :sample:desktop:run` for the desktop demo. Execute
`./gradlew :haze-screenshot-tests:test` for library screenshots or
`./gradlew :sample:screenshot-tests:test` for Glass Gallery screenshots.

## Coding Style & Naming Conventions

All Kotlin sources use the default JetBrains style (four-space indentation, trailing commas where
helpful). Spotless with ktlint enforces formatting; run `./gradlew spotlessApply` before committing.
Keep public packages under `dev.chrisbanes.haze.*` and follow PascalCase for composables, camelCase
for parameters, and `*Defaults` naming for reusable configuration containers.

## Testing Guidelines

Unit and snapshot tests sit alongside sources (for example, `haze/src/commonTest/kotlin`). Compose
UI tests leverage `assertk`, `kotlin.test`, and Roborazzi-based screenshot assertions. Prefer
descriptive method-level names such as `functionName_emitsExpectedBlur`. Run `./gradlew check`
locally before opening a PR. Regenerate library snapshots with
`./gradlew :haze-screenshot-tests:recordRoborazzi`; regenerate Glass Gallery snapshots with
`./gradlew :sample:screenshot-tests:recordRoborazzi` when intentional UI changes occur.

## VisualEffect Implementation Patterns

When authoring or modifying `VisualEffect` implementations (e.g., `BlurVisualEffect`,
`GlassVisualEffect`), follow these conventions:

- Annotate the class with `@Stable` for Compose skippability.
- Use a `needsDelegateSelection` flag to defer delegate creation from `update()` to `draw()`,
  avoiding work on frames where no draw occurs.
- Expose a `Local*Style` composition local (e.g., `LocalGlassStyle`) and a matching
  `*Defaults.style` property.
- Resolve style properties with three-tier precedence: direct property value → `style` parameter
  → composition local → defaults.
- Guard the delegate property setter with `isAttached` to prevent calling `attach()`/`detach()`
  before the effect is node-attached.
- Log property changes via `HazeLogger.d(TAG)` in every public setter for debugging.
- Make platform-specific `updateDelegate` functions return the new `Delegate` instance rather than
  mutating the property as a side-effect.

## Commit & Pull Request Guidelines

Commit history favors imperative subjects with optional scope notes and auto-linked PR numbers (
e.g., "Update plugin \u2026 (#772)"). Keep commits focused, include configuration updates when they
affect generated artifacts, and ensure Spotless has been applied. Pull requests should describe
motivation, mention affected modules, link GitHub issues when relevant, and attach updated
screenshots for UI-facing changes.

## Changelog Maintenance

The project maintains `CHANGELOG.md` following the [Keep a Changelog](https://keepachangelog.com)
format. When preparing releases, update the changelog with entries from GitHub releases. Changes
should be categorized under standard headings: Added, Changed, Fixed, Deprecated, Removed, and
Security. Each version entry includes the release date in `YYYY-MM-DD` format, links to pull
requests, and contributor acknowledgments. The changelog can be regenerated or updated using the
GitHub REST API to fetch release notes: `curl -s
"https://api.github.com/repos/chrisbanes/haze/releases?per_page=100"`.

## Security & Configuration Notes

Gradle convention plugins expect Java 21; verify your local toolchain matches `gradle/build-logic`.
Secrets are not required for local builds, but Android sample runs need a connected device or
emulator with API level ≥34, matching the raised compile SDK settings.
