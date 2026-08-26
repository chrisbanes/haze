# Repository Guidelines

## Project Structure & Module Organization

The project is a Kotlin Multiplatform library. Core APIs live in `haze`, reusable presets in
`haze-materials`, and library visual verifications in `haze-screenshot-tests`. Glass Gallery
screenshots live in `sample/screenshot-tests`. Auxiliary tooling and shared fixtures are under
`internal/`, while runnable examples reside in `sample/` (Android, Desktop, Web, macOS).
Documentation assets and the MkDocs site configuration are in `docs/` and `site/`.

## Sample Code Guidelines

Code under `sample/` is teaching and reference material intended to be read and copied
independently.

- Prefer explicit, locally complete sample code over shared abstractions.
- Duplication between samples is acceptable when it keeps the demonstrated API and required setup
  visible at the call site.
- Do not extract helpers or parameterize sample composables solely to reduce repetition.
- Share only application infrastructure, fixtures, or abstractions that are themselves part of the
  API being demonstrated.
- During review, do not treat duplication in sample code as a reuse issue unless it creates a
  concrete correctness or maintenance risk.

## Build, Test, and Development Commands

Use `./gradlew build` for a full multi-platform build and verification. Targeted development builds
run faster: `./gradlew assembleDebug testDebug` for library artifacts,
`./gradlew :sample:android:installDebug` to load the Android sample on a connected device, and
`./gradlew :sample:desktop:run` for the desktop demo. Execute
`./gradlew :haze-screenshot-tests:test` for library screenshots or
`./gradlew :sample:screenshot-tests:test` for Glass Gallery screenshots.

## Coding Style & Naming Conventions

All Kotlin sources use the ktlint `intellij_idea` code style with two-space indentation, as
configured in `.editorconfig`, and trailing commas where helpful. Spotless with ktlint enforces
formatting; run `./gradlew spotlessApply` before committing.
Keep public packages under `dev.chrisbanes.haze.*` and follow PascalCase for composables, camelCase
for parameters, and `*Defaults` naming for reusable configuration containers.
For internal or private value types, prefer `@Poko` over `data class`. Configure modules that use
the project annotation with `pokoAnnotation.set("dev/chrisbanes/haze/Poko")`; reserve `data class`
for types that intentionally require generated `copy` or component functions.

## Testing Guidelines

Unit and snapshot tests sit alongside sources (for example, `haze/src/commonTest/kotlin`). Compose
UI tests use Compose semantics and Roborazzi screenshot assertions where appropriate. Use AssertK
for every author-written value, type, collection, boolean, and exception assertion; do not use
assertion functions from `kotlin.test`, JUnit, or Truth. Semantic custom assertion helpers are
allowed when implemented with AssertK. Prefer the most specific semantic assertion available,
such as `isNull()`, `contains()`, or `isInstanceOf()`. Avoid converting conditions into booleans
and asserting `isTrue()` or `isFalse()`; reserve those assertions for APIs whose actual result is
boolean. Prefer descriptive method-level names such as `functionName_emitsExpectedBlur`. Run
`./gradlew check` locally before opening a PR. Regenerate library snapshots with
`./gradlew :haze-screenshot-tests:recordRoborazzi`; regenerate Glass Gallery snapshots with
`./gradlew :sample:screenshot-tests:recordRoborazzi` when intentional UI changes occur.

## VisualEffect Implementation Patterns

When authoring or modifying built-in effect runtimes (for example, `BlurVisualEffect` or the
internal Glass runtime), follow these conventions:

- Annotate the class with `@Stable` for Compose skippability.
- Use a `needsDelegateSelection` flag to defer delegate creation from `update()` to `draw()`,
  avoiding work on frames where no draw occurs.
- Expose a `Local*Style` composition local (e.g., `LocalGlassStyle`). Add a matching
  `*Defaults.style` property only when it represents behavior not already covered by individual
  defaults or named built-in styles.
- Resolve replayable styles in this order: defaults → composition local → explicit style.
- Guard the delegate property setter with `isAttached` to prevent calling `attach()`/`detach()`
  before the effect is node-attached.
- Log mutable internal runtime-property changes via `HazeLogger.d(TAG)` where they aid debugging.
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

## Agent skills

### Issue tracker

Issues and PRDs are tracked in GitHub Issues for `chrisbanes/haze`. See
`docs/agents/issue-tracker.md`.

### Triage labels

Use the default canonical triage labels. See `docs/agents/triage-labels.md`.

### Domain docs

Use a single-context layout with a root `CONTEXT.md` and `docs/adr/`. See
`docs/agents/domain.md`.

### GitHub Project execution

The repository Project binding and execution policy are defined in
`docs/agents/run-github-project.md`.
