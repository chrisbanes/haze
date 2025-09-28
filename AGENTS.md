# Repository Guidelines

## Project Structure & Module Organization

The project is a Kotlin Multiplatform library. Core APIs live in `haze`, reusable presets in
`haze-materials`, and visual verifications in `haze-screenshot-tests`. Auxiliary tooling and shared
fixtures are under `internal/`, while runnable examples reside in `sample/` (Android, Desktop, Web,
macOS). Documentation assets and the MkDocs site configuration are in `docs/` and `site/`.

## Build, Test, and Development Commands

Use `./gradlew build` for a full multi-platform build and verification. Targeted development builds
run faster: `./gradlew :haze:assemble` for library artifacts,
`./gradlew :sample:android:installDebug` to load the Android sample on a connected device, and
`./gradlew :sample:desktop:run` for the desktop demo. Execute
`./gradlew :haze-screenshot-tests:test` to validate the screenshot suite.

## Coding Style & Naming Conventions

All Kotlin sources use the default JetBrains style (four-space indentation, trailing commas where
helpful). Spotless with Ktlint enforces formatting; run `./gradlew spotlessApply` before committing.
Keep public packages under `dev.chrisbanes.haze.*` and follow PascalCase for composables, camelCase
for parameters, and `*Defaults` naming for reusable configuration containers.

## Testing Guidelines

Unit and snapshot tests sit alongside sources (for example, `haze/src/commonTest/kotlin`). Compose
UI tests leverage `assertk`, `kotlin.test`, and Roborazzi-based screenshot assertions. Prefer
descriptive method-level names such as `functionName_emitsExpectedBlur`. Run `./gradlew check`
locally before opening a PR, and regenerate snapshots with
`./gradlew :haze-screenshot-tests:recordRoborazzi` when intentional UI changes occur.

## Commit & Pull Request Guidelines

Commit history favors imperative subjects with optional scope notes and auto-linked PR numbers (
e.g., “Update plugin … (#772)”). Keep commits focused, include configuration updates when they
affect generated artifacts, and ensure Spotless has been applied. Pull requests should describe
motivation, mention affected modules, link GitHub issues when relevant, and attach updated
screenshots for UI-facing changes.

## Security & Configuration Notes

Gradle convention plugins expect Java 21; verify your local toolchain matches `gradle/build-logic`.
Secrets are not required for local builds, but Android sample runs need a connected device or
emulator with API level ≥34, matching the raised compile SDK settings.
