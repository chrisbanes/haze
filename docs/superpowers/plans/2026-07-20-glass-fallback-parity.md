# Glass Fallback Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android fallback Glass renderer honor resolved specular intensity and the documented centered default light position, with pixel-level regression coverage.

**Architecture:** Keep the fallback renderer intentionally simple: retain its radial highlight, but resolve its center with the same `Offset.takeOrElse { size.center }` contract as the runtime path and scale the existing maximum alpha by the resolved `specularIntensity`. Add API 28 host tests that compare captured pixels so the fallback contract is verified independently of screenshot baselines.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose `DrawScope`, Robolectric API 28, Compose UI host tests, AssertK, Roborazzi.

## Global Constraints

- Preserve the fallback renderer's simplified radial-highlight implementation; do not port the runtime rim shader.
- `specularIntensity = 0f` must produce no static fallback highlight, while `0.5f` and `1f` produce strictly increasing pixel responses.
- An unspecified light position must render identically to an explicit material-center light position.
- Keep the new opt-in interaction-lighting pass unchanged and independent of the static highlight intensity.
- Add no public API and make no documentation change because the public documentation already specifies a centered default.
- Do not commit during plan execution; branch completion remains gated by `finishing-a-development-branch` after review and fresh verification.

---

### Task 1: Lock the fallback lighting contract with API 28 pixel tests

**Files:**
- Create: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassFallbackAndroidTest.kt`

**Interfaces:**
- Consumes: `GlassInvariantSample`, `PixelMap.snapshot()`, and `PixelSnapshot.meanAbsoluteDifference()` from the screenshot-test support code.
- Produces: `GlassFallbackAndroidTest`, covering zero/half/full static highlight response and unspecified/explicit center parity on API 28.

- [x] **Step 1: Add the API 28 regression test class**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

private const val FallbackPixelTolerance = 1f / 255f
private val FallbackSurfaceSize = DpSize(280.dp, 180.dp)
private val FallbackShape = RoundedCornerShape(0.dp)

@Config(sdk = [28], qualifiers = "w393dp-h698dp-440dpi")
class GlassFallbackAndroidTest : ScreenshotTest() {

  @Test
  fun fallback_zeroSpecularIntensityDrawsNoHighlight() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 0f)
    var enabled by mutableStateOf(false)
    setContent {
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          inputScale = HazeInputScale.None,
          shape = FallbackShape,
          enabled = enabled,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val baseline = captureRootPixels().snapshot()
    enabled = true
    waitForIdle()
    val zero = captureRootPixels().snapshot()

    assertThat(zero.meanAbsoluteDifference(baseline))
      .isLessThanOrEqualTo(FallbackPixelTolerance)
  }

  @Test
  fun fallback_specularIntensityResponseIsMonotonic() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 0f)
    setContent {
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          inputScale = HazeInputScale.None,
          shape = FallbackShape,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val zero = captureRootPixels().snapshot()

    effect.specularIntensity = 0.5f
    waitForIdle()
    val half = captureRootPixels().snapshot()

    effect.specularIntensity = 1f
    waitForIdle()
    val full = captureRootPixels().snapshot()

    val halfResponse = half.meanAbsoluteDifference(zero)
    val fullResponse = full.meanAbsoluteDifference(zero)
    assertThat(halfResponse).isGreaterThan(0f)
    assertThat(fullResponse).isGreaterThan(halfResponse)
  }

  @Test
  fun fallback_unspecifiedLightPositionMatchesExplicitCenter() = runScreenshotTest {
    val effect = fallbackEffect(specularIntensity = 1f)
    var materialCenter = Offset.Unspecified
    setContent {
      val density = LocalDensity.current
      SideEffect {
        materialCenter = with(density) {
          Offset(FallbackSurfaceSize.width.toPx() / 2f, FallbackSurfaceSize.height.toPx() / 2f)
        }
      }
      ScreenshotTheme {
        GlassInvariantSample(
          effect = effect,
          inputScale = HazeInputScale.None,
          shape = FallbackShape,
          surfaceSize = FallbackSurfaceSize,
          drawGridLines = false,
        )
      }
    }

    val unspecified = captureRootPixels().snapshot()
    check(materialCenter != Offset.Unspecified)
    effect.lightPosition = materialCenter
    waitForIdle()
    val explicitCenter = captureRootPixels().snapshot()

    assertThat(unspecified.changedPixelRatio(explicitCenter)).isEqualTo(0f)
  }
}

private fun fallbackEffect(specularIntensity: Float): GlassVisualEffect = GlassVisualEffect().apply {
  tint = Color.Transparent
  optics = GlassOptics.Absolute(refractionStrength = 0f, depth = 0f, blurRadius = 0.dp)
  this.specularIntensity = specularIntensity
  ambientResponse = 0f
  edgeSoftness = 0.dp
  shape = FallbackShape
}
```

- [x] **Step 2: Run the new tests and verify the current implementation fails for the intended reasons**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:testAndroidHostTest --tests 'dev.chrisbanes.haze.GlassFallbackAndroidTest'
```

Expected: FAIL. The zero-intensity test observes a non-zero response at `0f`; the monotonic test observes no response change between `0f`, `0.5f`, and `1f`; the light-position test observes changed pixels between the unspecified upper-third fallback and explicit center.

### Task 2: Apply the minimal fallback parity fix

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/FallbackGlassDelegate.kt`
- Generated: `haze-screenshot-tests/screenshots/android/` (only Roborazzi baselines whose API 28 or API 32 fallback rendering changes)

**Interfaces:**
- Consumes: resolved `GlassVisualEffect.specularIntensity`, `GlassVisualEffect.lightPosition`, and Compose geometry `Size.center`.
- Produces: a zero-skipping, intensity-scaled static fallback highlight centered by default; no public API changes.

- [x] **Step 1: Resolve the center and intensity once before drawing**

Add these imports:

```kotlin
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
```

Replace the current fallback center calculation with:

```kotlin
val highlightCenter = effect.lightPosition.takeOrElse { size.center }
val highlightAlpha = 0.25f * effect.specularIntensity.coerceIn(0f, 1f)
val highlightRadius = max(size.minDimension / 2f, edgeSoftnessPx * 4f)
```

- [x] **Step 2: Skip the zero-intensity static highlight and scale all other responses**

Replace the existing `// Specular-ish radial highlight` block with:

```kotlin
// Specular-ish radial highlight
if (highlightAlpha > 0f) {
  val highlightBrush = Brush.radialGradient(
    colors = listOf(Color.White.copy(alpha = highlightAlpha), Color.Transparent),
    center = highlightCenter,
    radius = highlightRadius,
  )
  if (shapePath != null) {
    clipPath(shapePath) {
      drawCircle(brush = highlightBrush, radius = highlightRadius, center = highlightCenter)
    }
  } else {
    drawCircle(
      brush = highlightBrush,
      center = highlightCenter,
      radius = highlightRadius,
    )
  }
}
```

Leave the later `drawInteractionLighting(...)` call unchanged so interactive feedback remains opt-in and separately controlled.

- [x] **Step 3: Run the focused pixel tests and verify they pass**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:testAndroidHostTest --tests 'dev.chrisbanes.haze.GlassFallbackAndroidTest'
```

Expected: PASS for all three API 28 tests.

- [x] **Step 4: Regenerate intentional screenshot baselines**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:recordRoborazzi
```

Expected: SUCCESS. Git changes under `haze-screenshot-tests/screenshots/android/` are limited to API 28 and API 32 Glass fallback images whose static highlight intensity or unspecified center changed; API 35 and desktop images remain unchanged.

- [x] **Step 5: Run formatting and complete screenshot-test verification**

Run:

```bash
rtk ./gradlew spotlessCheck :haze-screenshot-tests:test
```

Expected: BUILD SUCCESSFUL with the new pixel tests and updated Android baselines.

- [x] **Step 6: Inspect the complete issue-scoped diff without committing**

Run:

```bash
rtk git status --short
rtk git diff --check
rtk git diff --stat
```

Expected: only the plan, `FallbackGlassDelegate.kt`, `GlassFallbackAndroidTest.kt`, and intentional API 28/API 32 Glass baseline images are changed; `git diff --check` reports no errors. Do not stage or commit.
