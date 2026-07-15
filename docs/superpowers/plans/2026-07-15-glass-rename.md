# Glass Rename Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename Haze's unpublished Liquid Glass feature to Glass across its module, Kotlin API, samples, tests, documentation, goldens, and iOS reference tooling without changing behavior or image bytes.

**Architecture:** Perform one clean source break: move the Gradle module and Kotlin package, mechanically rename Haze-owned symbols, then update each consumer. Keep the iOS reference pipeline and accepted fixtures, but distinguish generic Haze Glass names from explicit references to Apple's iOS Liquid Glass material.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Gradle, Metalava, Roborazzi, SwiftUI/Xcode, POSIX shell.

---

## File Structure

- `haze-glass/`: renamed effect module; all production and test packages live under `dev.chrisbanes.haze.glass`.
- `sample/shared/`: Glass sample entry points and sample UI tests.
- `haze-screenshot-tests/`: Glass screenshot fixtures, invariant helpers, iOS calibration resources, and platform screenshot tests.
- `internal/ios-glass-reference-capture/`: renamed native iOS reference producer and shell self-test.
- `docs/effects/glass.md`, `docs/architecture.md`, `docs/migrating-2.0.md`, `CHANGELOG.md`, and `AGENTS.md`: current documentation and repository guidance.
- `docs/superpowers/`: completed internal design/plan records updated where stale names would point at removed files; this rename design and plan retain old names only to document the migration.
- `kotlin-js-store/package-lock.json` and `kotlin-js-store/wasm/package-lock.json`: generated workspace names for the renamed Gradle project.

### Canonical Rename Map

| Old | New |
| --- | --- |
| `:haze-liquidglass` | `:haze-glass` |
| `projects.hazeLiquidglass` | `projects.hazeGlass` |
| `dev.chrisbanes.haze.liquidglass` | `dev.chrisbanes.haze.glass` |
| `LiquidGlassVisualEffect` | `GlassVisualEffect` |
| `LiquidGlassStyle` | `GlassStyle` |
| `LiquidGlassOptics` | `GlassOptics` |
| `LiquidGlassLighting` | `GlassLighting` |
| `LiquidGlassColor` | `GlassColor` |
| `LiquidGlassRendering` | `GlassRendering` |
| `LiquidGlassDefaults` | `GlassDefaults` |
| `LocalLiquidGlassStyle` | `LocalGlassStyle` |
| `liquidGlassEffect` | `glassEffect` |
| Haze-owned `LiquidGlass*` / `liquidGlass*` internals | `Glass*` / `glass*` |

`SurfaceProfile` and `ChromaticAberrationMode` retain their names and only move packages.

### Task 1: Establish The Rename Baseline

**Files:**
- Read: `docs/superpowers/specs/2026-07-15-glass-rename-design.md`
- Read: `haze-liquidglass/api/api.txt`
- Read: `haze-screenshot-tests/screenshots/android/`
- Read: `haze-screenshot-tests/screenshots/desktop/`
- Read: `haze-screenshot-tests/src/commonTest/resources/liquid-glass/ios26/`

- [ ] **Step 1: Confirm the worktree and baseline project compile**

Run:

```bash
git status --short
./gradlew :haze-liquidglass:allTests :haze-liquidglass:metalavaCheckCompatibility
```

Expected: the status contains only intentional pre-existing work and the new design/plan documents; both Gradle tasks pass.

- [ ] **Step 2: Run the current consumer suites**

Run:

```bash
./gradlew :sample:shared:allTests :haze-screenshot-tests:test
```

Expected: all sample and screenshot tests pass before any rename.

- [ ] **Step 3: Record content-only checksums for every affected PNG**

Run:

```bash
git ls-files 'haze-screenshot-tests/screenshots/*LiquidGlass*.png' 'haze-screenshot-tests/screenshots/*liquidGlass*.png' 'haze-screenshot-tests/src/commonTest/resources/liquid-glass/ios26/*.png' \
  | xargs shasum -a 256 \
  | cut -d ' ' -f 1 \
  | sort > /tmp/haze-glass-rename-before.sha256
wc -l /tmp/haze-glass-rename-before.sha256
```

Expected: the checksum file is non-empty and its line count equals the number of affected committed PNGs.

### Task 2: Rename The Gradle Module And Kotlin API

**Files:**
- Move: `haze-liquidglass/` to `haze-glass/`
- Move: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/` to `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/`
- Move: `haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/liquidglass/` to `haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass/`
- Move: `haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/liquidglass/` to `haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass/`
- Move: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/` to `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/`
- Move: `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/liquidglass/` to `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/`
- Modify: `settings.gradle.kts`
- Modify: `haze-glass/build.gradle.kts`
- Modify: `haze-screenshot-tests/build.gradle.kts`
- Modify: `sample/shared/build.gradle.kts`
- Regenerate: `haze-glass/api/api.txt`

- [ ] **Step 1: Move the module and package directories**

Run:

```bash
git mv haze-liquidglass haze-glass
git mv haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass
git mv haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/liquidglass haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass
git mv haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/liquidglass haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass
git mv haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass
git mv haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/liquidglass haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass
```

Expected: Git reports directory/file renames and no `haze-liquidglass/` directory remains.

- [ ] **Step 2: Rename every Haze-owned Kotlin file with a LiquidGlass prefix**

Apply the canonical map to production and test filenames. The resulting production files include:

```text
GlassDefaults.kt
GlassDirtyFields.kt
GlassLayers.kt
GlassProgressive.kt
GlassRenderParams.kt
GlassShaders.kt
GlassStageInvalidation.kt
GlassStyle.kt
GlassVisualEffect.kt
FallbackGlassDelegate.kt
RuntimeShaderGlassDelegate.kt
GlassRenderEffectFactory.android.kt
GlassVisualEffect.android.kt
GlassRenderEffectFactory.skiko.kt
GlassVisualEffect.skiko.kt
```

Rename test files with the same rule, including `GlassStyleTest.kt`, `GlassVisualEffectLifecycleTest.kt`, `GlassRenderParamsTest.kt`, `RuntimeShaderGlassDelegateIntegrationTest.kt`, and `RuntimeShaderGlassDelegateTrimMemoryTest.kt`. Keep generic files such as `Canvas.kt`, `CornerRadii.kt`, `SemanticBlurKernel.kt`, `RequiredRetainedStageTest.kt`, and `SurfaceProfile.kt` unchanged.

- [ ] **Step 3: Rename package declarations and Kotlin symbols**

Update all files under `haze-glass/src/` to package `dev.chrisbanes.haze.glass`. Apply the canonical public map and the same prefix rule to internal symbols, including:

```kotlin
internal var delegate: Delegate = FallbackGlassDelegate(this)

internal expect fun GlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): GlassVisualEffect.Delegate
```

Rename internal shader/effect keys and helpers consistently, for example `GlassBlurEffectKey`, `GlassOpticalEffectKey`, `createGlassBlurRenderEffects`, `calculateGlassSamplePaddingPx`, and `RuntimeShaderGlassDelegate`. Do not alter equations, constants, branches, defaults, or test expectations.

- [ ] **Step 4: Update Gradle project wiring**

Change `settings.gradle.kts` to include `":haze-glass"`. In `haze-glass/build.gradle.kts`, set:

```kotlin
namespace = "dev.chrisbanes.haze.glass"
```

Change both consumer dependencies to:

```kotlin
api(projects.hazeGlass)
```

Expected: no `projects.hazeLiquidglass` accessor remains.

- [ ] **Step 5: Compile the renamed module to expose missed names**

Run:

```bash
./gradlew :haze-glass:compileKotlinJvm :haze-glass:compileTestKotlinJvm
```

Expected: PASS. Any unresolved LiquidGlass symbol indicates an incomplete mechanical rename; fix only that naming mismatch.

- [ ] **Step 6: Generate and inspect the renamed API signature**

Run:

```bash
./gradlew :haze-glass:metalavaGenerateSignature
```

Expected: `haze-glass/api/api.txt` contains package `dev.chrisbanes.haze.glass`, the Glass public symbols from the canonical map, and no compatibility aliases.

- [ ] **Step 7: Run all module tests**

Run:

```bash
./gradlew :haze-glass:allTests :haze-glass:metalavaCheckCompatibility
```

Expected: PASS with unchanged behavioral assertions.

- [ ] **Step 8: Commit the module/API rename**

```bash
git add settings.gradle.kts haze-glass haze-screenshot-tests/build.gradle.kts sample/shared/build.gradle.kts
git commit -m "Rename Liquid Glass module and API to Glass"
```

### Task 3: Rename Samples And Current Documentation

**Files:**
- Move: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/LiquidGlassSample.kt` to `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassSample.kt`
- Move: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/LiquidGlassDebugSample.kt` to `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassDebugSample.kt`
- Modify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/SamplesTest.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/CreditCardSamplesTest.kt`
- Move: `docs/effects/liquid-glass.md` to `docs/effects/glass.md`
- Modify: `docs/architecture.md`
- Modify: `docs/migrating-2.0.md`
- Modify: `CHANGELOG.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-06-07-v2-api-cleanup-design.md`
- Modify: `docs/superpowers/plans/2026-06-07-v2-api-cleanup.md`
- Move: `docs/superpowers/specs/2026-07-01-android-liquid-glass-depth-spike-design.md` to `docs/superpowers/specs/2026-07-01-android-glass-depth-spike-design.md`
- Move: `docs/superpowers/plans/2026-07-01-android-liquid-glass-depth-spike.md` to `docs/superpowers/plans/2026-07-01-android-glass-depth-spike.md`

- [ ] **Step 1: Change the sample expectation first**

In `SamplesTest.kt`, rename the test and expected sample:

```kotlin
@Test
fun samplesList_keepsBlurAndGlassAsSeparateEntries() = runComposeUiTest {
  setContent {
    SamplesList(
      samples = listOf(Sample.CreditCard, Sample.Glass),
      onSampleClick = {},
    )
  }

  onNodeWithTag("Glass").assertIsDisplayed()
}
```

Run:

```bash
./gradlew :sample:shared:allTests
```

Expected: compilation fails because `Sample.Glass` does not exist yet.

- [ ] **Step 2: Rename sample files, composables, and entries**

Use `GlassCreditCardSample`, `GlassDebugSample`, `Sample.Glass`, and `Sample.GlassDebug`. Import `dev.chrisbanes.haze.glass.glassEffect`, and type debug configuration lambdas as:

```kotlin
config: dev.chrisbanes.haze.glass.GlassVisualEffect.() -> Unit
```

Set visible titles to `"Glass"` and `"Glass (Debug)"`. Rename the credit-card test to `glassSample_keepsBenchmarkCardTag` and call `GlassCreditCardSample`.

- [ ] **Step 3: Run sample tests**

Run:

```bash
./gradlew :sample:shared:allTests
```

Expected: PASS.

- [ ] **Step 4: Rename current product documentation**

Move the effects page to `docs/effects/glass.md`. Use `Glass` for Haze's feature, module, symbols, and prose. Keep `Liquid Glass` only when explicitly qualified as Apple's iOS material, for example:

```markdown
A refraction-driven Glass effect calibrated against Apple's iOS Liquid Glass Regular material.
```

Update `docs/architecture.md`, `docs/migrating-2.0.md`, `CHANGELOG.md`, and `AGENTS.md` to use `haze-glass`, Glass symbols, and `glassEffect`. Because this module was never published, update earlier changelog references rather than documenting aliases that never existed.

- [ ] **Step 5: Repair internal records that point at renamed code**

Apply the canonical map to the two completed v2 cleanup records. Rename the Android depth-spike files and update their titles, paths, symbols, and commands to Glass names. Do not alter their historical technical conclusions.

The current rename design and this plan intentionally retain old names in rename tables and are excluded from the final stale-name assertion.

- [ ] **Step 6: Commit samples and documentation**

```bash
git add sample/shared docs CHANGELOG.md AGENTS.md
git commit -m "Rename Liquid Glass samples and documentation"
```

### Task 4: Rename Screenshot Tests, Goldens, And iOS Fixtures

**Files:**
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassContentScreenshotTest.kt` to `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassContentScreenshotTest.kt`
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassDepthComparisonSample.kt` to `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassDepthComparisonSample.kt`
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassImageMetrics.kt` to `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassImageMetrics.kt`
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassImageMetricsTest.kt` to `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassImageMetricsTest.kt`
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassInvariantSample.kt` to `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInvariantSample.kt`
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/LiquidGlassScreenshotTest.kt` to `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassScreenshotTest.kt`
- Move: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/LiquidGlassDepthAndroidScreenshotTest.kt` to `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt`
- Move: `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/LiquidGlassDepthDesktopScreenshotTest.kt` to `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassDepthDesktopScreenshotTest.kt`
- Move: `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/LiquidGlassIos26ReferenceMetricsTest.kt` to `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassIos26ReferenceMetricsTest.kt`
- Move: `haze-screenshot-tests/src/commonTest/resources/liquid-glass/ios26/` to `haze-screenshot-tests/src/commonTest/resources/glass/ios26/`
- Move: affected PNGs under `haze-screenshot-tests/screenshots/android/` and `haze-screenshot-tests/screenshots/desktop/`
- Modify: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/HazeScreenshotTest.kt`

- [ ] **Step 1: Move Kotlin test/support files and rename identifiers**

Use `Glass`/`glass` for all Haze-owned classes, helpers, methods, and imports. Examples:

```kotlin
class GlassScreenshotTest : ScreenshotTest()

internal fun ScreenshotUiTest.assertGlassBlurInvariant()

internal val Ios26RegularReferenceMetrics: Map<GlassReferenceKey, GlassOpticalMetrics>
```

Keep `Ios26` in reference names because it identifies the calibration source. Update the resource loader to:

```kotlin
val path = "/glass/ios26/$fileName"
```

- [ ] **Step 2: Move the accepted iOS fixture bundle**

Run:

```bash
mkdir -p haze-screenshot-tests/src/commonTest/resources/glass
git mv haze-screenshot-tests/src/commonTest/resources/liquid-glass/ios26 haze-screenshot-tests/src/commonTest/resources/glass/ios26
```

Expected: the same manifest and four PNG files exist under the new path.

- [ ] **Step 3: Rename screenshot basenames without recording new images**

For every tracked PNG under `haze-screenshot-tests/screenshots/android/` and `haze-screenshot-tests/screenshots/desktop/`, replace `LiquidGlass` with `Glass` and `liquidGlass` with `glass` in the filename using `git mv`. Do not run `recordRoborazzi`.

- [ ] **Step 4: Prove all PNG bytes are unchanged**

Run:

```bash
git ls-files 'haze-screenshot-tests/screenshots/*Glass*.png' 'haze-screenshot-tests/screenshots/*glass*.png' 'haze-screenshot-tests/src/commonTest/resources/glass/ios26/*.png' \
  | xargs shasum -a 256 \
  | cut -d ' ' -f 1 \
  | sort > /tmp/haze-glass-rename-after.sha256
diff -u /tmp/haze-glass-rename-before.sha256 /tmp/haze-glass-rename-after.sha256
```

Expected: `diff` prints nothing and exits 0.

- [ ] **Step 5: Run metric and screenshot tests**

Run:

```bash
./gradlew :haze-screenshot-tests:jvmTest --tests dev.chrisbanes.haze.GlassImageMetricsTest --tests dev.chrisbanes.haze.GlassIos26ReferenceMetricsTest
./gradlew :haze-screenshot-tests:test
```

Expected: PASS without generating or changing PNG contents.

- [ ] **Step 6: Commit test and golden renames**

```bash
git add haze-screenshot-tests
git commit -m "Rename Liquid Glass screenshot coverage to Glass"
```

### Task 5: Rename The iOS Reference Producer

**Files:**
- Move: `internal/ios-liquid-glass-reference-capture/` to `internal/ios-glass-reference-capture/`
- Move: `internal/ios-glass-reference-capture/LiquidGlassReferenceCapture.xcodeproj/` to `internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj/`
- Modify: `internal/ios-glass-reference-capture/project.yml`
- Modify: `internal/ios-glass-reference-capture/Configuration/Config.xcconfig`
- Modify: `internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj/project.pbxproj`
- Modify: `internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj/xcshareddata/xcschemes/CaptureApp.xcscheme`
- Modify: `internal/ios-glass-reference-capture/scripts/capture-references.sh`
- Modify: `internal/ios-glass-reference-capture/scripts/capture-references-test.sh`
- Modify: `internal/ios-glass-reference-capture/README.md`
- Modify: `internal/ios-glass-reference-capture/App/CaptureApp.swift`

- [ ] **Step 1: Change self-test expectations to the new Haze-owned names**

Update `capture-references-test.sh` to expect:

```text
dev.chrisbanes.haze.glassreferencecapture
GlassReferenceCapture.xcodeproj
Glass Reference Capture.app
Haze Glass Reference
haze-screenshot-tests/src/commonTest/resources/glass/ios26
```

Run:

```bash
internal/ios-liquid-glass-reference-capture/scripts/capture-references-test.sh
```

Expected: FAIL because the producer still emits the old bundle ID, project/product names, simulator name, and import path.

- [ ] **Step 2: Move the producer and Xcode project**

Run:

```bash
git mv internal/ios-liquid-glass-reference-capture internal/ios-glass-reference-capture
git mv internal/ios-glass-reference-capture/LiquidGlassReferenceCapture.xcodeproj internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj
```

- [ ] **Step 3: Update producer-owned identity consistently**

Use these exact values across `project.yml`, `Config.xcconfig`, the checked-in `.pbxproj`, the scheme, and both scripts:

```text
Project: GlassReferenceCapture
Product: Glass Reference Capture
Bundle ID: dev.chrisbanes.haze.glassreferencecapture
Simulator: Haze Glass Reference
Import destination: haze-screenshot-tests/src/commonTest/resources/glass/ios26
```

Update all `ReferencedContainer` entries in `CaptureApp.xcscheme` to `container:GlassReferenceCapture.xcodeproj`. Keep SwiftUI's `.glassEffect(...)` spelling unchanged.

- [ ] **Step 4: Keep Apple terminology only as qualified provenance**

The README may call the captured target “Apple's iOS Liquid Glass Regular material.” Rename Haze-owned headings, paths, simulator/product names, and generic status messages to Glass. Make the failure message explicit rather than generic branding:

```swift
let message = "iOS Liquid Glass reference capture failed: \(error)\n"
```

- [ ] **Step 5: Run the shell self-test**

Run:

```bash
internal/ios-glass-reference-capture/scripts/capture-references-test.sh
```

Expected: PASS. This test uses fake simulator/build commands and does not perform a native capture.

- [ ] **Step 6: Validate the checked-in Xcode project when Xcode is available**

Run:

```bash
xcodebuild -list -project internal/ios-glass-reference-capture/GlassReferenceCapture.xcodeproj
```

Expected: Xcode lists the `CaptureApp` scheme and targets. If the pinned Xcode toolchain is unavailable, record this as an environment limitation; do not regenerate the project with a different Xcode/XcodeGen version.

- [ ] **Step 7: Commit the producer rename**

```bash
git add internal/ios-glass-reference-capture
git commit -m "Rename iOS Glass reference producer"
```

### Task 6: Regenerate Workspace Metadata And Verify The Clean Break

**Files:**
- Modify: `kotlin-js-store/package-lock.json`
- Modify: `kotlin-js-store/wasm/package-lock.json`
- Verify: all files changed by Tasks 2-5

- [ ] **Step 1: Update generated JS workspace project names**

Regenerate the Kotlin JS/Wasm package metadata through Gradle:

```bash
./gradlew kotlinNpmInstall kotlinWasmNpmInstall
```

Expected: both package locks replace `haze-root-haze-liquidglass` and `haze-root-haze-liquidglass-test` with `haze-root-haze-glass` and `haze-root-haze-glass-test`, with no unrelated dependency upgrades.

- [ ] **Step 2: Apply formatting and API verification**

Run:

```bash
./gradlew spotlessApply
./gradlew :haze-glass:metalavaCheckCompatibility
```

Expected: PASS; Spotless changes formatting only.

- [ ] **Step 3: Check for stale Haze-owned concatenated names**

Run:

```bash
git grep -nE 'LiquidGlass|liquidGlass|liquidglass|liquid-glass' -- . \
  ':(exclude)docs/superpowers/specs/2026-07-15-glass-rename-design.md' \
  ':(exclude)docs/superpowers/plans/2026-07-15-glass-rename.md'
```

Expected: no matches. The two excluded rename records intentionally contain old-to-new mappings.

- [ ] **Step 4: Inspect remaining spaced Apple terminology**

Run:

```bash
git grep -nEi 'liquid glass' -- . \
  ':(exclude)docs/superpowers/specs/2026-07-15-glass-rename-design.md' \
  ':(exclude)docs/superpowers/plans/2026-07-15-glass-rename.md'
```

Expected: every match explicitly identifies Apple's/iOS Liquid Glass reference material or provenance. Replace any generic Haze branding match with Glass.

- [ ] **Step 5: Recheck immutable PNG content**

Run:

```bash
diff -u /tmp/haze-glass-rename-before.sha256 /tmp/haze-glass-rename-after.sha256
git diff --numstat -- '*.png'
```

Expected: checksum diff is empty. `git diff --numstat` shows binary renames only, not modified image content.

- [ ] **Step 6: Run targeted verification**

Run:

```bash
./gradlew :haze-glass:allTests :haze-glass:metalavaCheckCompatibility :sample:shared:allTests :haze-screenshot-tests:test spotlessCheck
internal/ios-glass-reference-capture/scripts/capture-references-test.sh
```

Expected: all tasks and the script pass.

- [ ] **Step 7: Run the full repository build**

Run:

```bash
./gradlew build
```

Expected: PASS. A failure must be resolved as a missed project/package/name reference unless evidence shows an unrelated pre-existing environment failure.

- [ ] **Step 8: Review the final diff**

Run:

```bash
git status --short
git diff --check
git diff --stat
git diff --summary
```

Expected: only intended Glass rename files, metadata, and design/plan documents appear; file moves are recognized as renames; no whitespace errors exist.

- [ ] **Step 9: Commit final metadata and formatting**

```bash
git add kotlin-js-store/package-lock.json kotlin-js-store/wasm/package-lock.json
git add -u
git commit -m "Finish Glass rename metadata"
```
