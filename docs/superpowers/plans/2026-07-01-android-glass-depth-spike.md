# Android Glass Depth Spike Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Test whether Android can draw one retained Glass content layer twice with two different render effects to recover depth-like original-content plus blurred-content composition.

**Architecture:** Keep source capture, parameter resolution, and retained-output ownership in `RuntimeShaderGlassDelegate`. Replace the single platform render effect with a small bundle containing an optional underlay effect and a required overlay effect. Android returns both effects and draws two passes; Skiko returns only the current dual-input overlay.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose `GraphicsLayer`, Android `RenderEffect.createRuntimeShaderEffect`, AGSL/SKSL shader generation, Robolectric/Roborazzi screenshot tests.

---

## File Structure

- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
  - Add a common `GlassRenderEffects` bundle.
  - Cache render-effect bundles instead of one `RenderEffect`.
  - Draw optional underlay first, then overlay.
  - Rename the platform factory expect function to return the bundle.
- Modify `haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectFactory.android.kt`
  - Build a masked blurred underlay effect.
  - Build an original-content overlay effect using `OverlayWithExternalUnderlay`.
  - Return both effects.
- Modify `haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectFactory.skiko.kt`
  - Wrap the current dual-input render effect as the overlay effect.
  - Keep `underlay = null`.
- Modify `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassScreenshotTest.kt`
  - Make `GlassBlurRadiusSample` internal so Android-host tests can reuse the high-contrast scene.
- Create `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt`
  - Add one targeted Android API 35 screenshot test with `depth = 0f`, `0.5f`, and `1f`.
- Generate screenshots under `haze-screenshot-tests/screenshots/android/`
  - Expected files:
    - `GlassDepthAndroidScreenshotTest.glass_depthProgression_0.png`
    - `GlassDepthAndroidScreenshotTest.glass_depthProgression_50.png`
    - `GlassDepthAndroidScreenshotTest.glass_depthProgression_100.png`

---

## Task 1: Add The Android Depth Screenshot Probe

**Files:**
- Modify: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassScreenshotTest.kt`
- Create: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt`

- [ ] **Step 1: Expose the high-contrast Glass fixture to Android-host tests**

In `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassScreenshotTest.kt`, change this function declaration:

```kotlin
@Composable
private fun GlassBlurRadiusSample(
```

to:

```kotlin
@Composable
internal fun GlassBlurRadiusSample(
```

- [ ] **Step 2: Add the Android API 35 depth progression test**

Create `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt` with this exact content:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class GlassDepthAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun glass_depthProgression() = runScreenshotTest(relaxedTolerance = true) {
    val shape = RoundedCornerShape(28.dp)
    val visualEffect = GlassVisualEffect().apply {
      tint = Color.White.copy(alpha = 0.08f)
      refractionStrength = 0.35f
      depth = 0f
      blurRadius = 32.dp
      specularIntensity = 0f
      ambientResponse = 0f
      edgeSoftness = 0.dp
      this.shape = shape
    }

    setContent {
      ScreenshotTheme {
        GlassBlurRadiusSample(
          visualEffect = visualEffect,
          shape = shape,
        )
      }
    }

    captureRoot("0")

    visualEffect.depth = 0.5f
    waitForIdle()
    captureRoot("50")

    visualEffect.depth = 1f
    waitForIdle()
    captureRoot("100")
  }
}
```

- [ ] **Step 3: Run the new screenshot test before implementation**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest --tests dev.chrisbanes.haze.GlassDepthAndroidScreenshotTest
```

Expected: FAIL because the reference screenshots do not exist yet, or because the current Android single-input path does not produce the desired depth progression. Confirm the test compiles and reaches screenshot comparison.

---

## Task 2: Add The Render Effect Bundle And Two-Pass Draw

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
- Modify: `haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectFactory.android.kt`
- Modify: `haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectFactory.skiko.kt`

- [ ] **Step 1: Replace the single cached render effect with a bundle**

In `RuntimeShaderGlassDelegate.kt`, remove this import:

```kotlin
import androidx.compose.ui.graphics.RenderEffect
```

Replace this field:

```kotlin
  private var renderEffect: RenderEffect? = null
```

with:

```kotlin
  private var renderEffects: GlassRenderEffects? = null
```

- [ ] **Step 2: Replace the one-pass draw block with two-pass application**

In `RuntimeShaderGlassDelegate.kt`, replace this block inside `drawRetainedLayer`:

```kotlin
      if (params != lastParams || renderEffect == null) {
        renderEffect = buildRenderEffect(params)
        lastParams = params
      }

      layer.renderEffect = renderEffect
      layer.alpha = effect.alpha
      drawLayer(layer)
```

with:

```kotlin
      if (params != lastParams || renderEffects == null) {
        renderEffects = buildRenderEffects(params)
        lastParams = params
      }

      val currentRenderEffects = renderEffects ?: return@drawScaledContent

      currentRenderEffects.underlay
        ?.takeIf { params.depth > 0f }
        ?.let { underlay ->
          layer.renderEffect = underlay.asComposeRenderEffect()
          layer.alpha = effect.alpha * params.depth
          drawLayer(layer)
        }

      layer.renderEffect = currentRenderEffects.overlay.asComposeRenderEffect()
      layer.alpha = effect.alpha
      drawLayer(layer)
```

- [ ] **Step 3: Rename the builder and platform factory call**

In `RuntimeShaderGlassDelegate.kt`, replace:

```kotlin
  private fun buildRenderEffect(params: RenderParams): RenderEffect {
    return createGlassRenderEffect(params) {
```

with:

```kotlin
  private fun buildRenderEffects(params: RenderParams): GlassRenderEffects {
    return createGlassRenderEffects(params) {
```

Keep the existing uniform-setting body unchanged.

- [ ] **Step 4: Add the common render-effect bundle and rename the expect function**

At the bottom of `RuntimeShaderGlassDelegate.kt`, replace:

```kotlin
@OptIn(InternalHazeApi::class)
internal expect fun createGlassRenderEffect(
  params: RuntimeShaderGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): PlatformRenderEffect
```

with:

```kotlin
@OptIn(InternalHazeApi::class)
internal data class GlassRenderEffects(
  val overlay: PlatformRenderEffect,
  val underlay: PlatformRenderEffect? = null,
)

@OptIn(InternalHazeApi::class)
internal expect fun createGlassRenderEffects(
  params: RuntimeShaderGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): GlassRenderEffects
```

- [ ] **Step 5: Update Android to return an underlay plus overlay**

In `GlassRenderEffectFactory.android.kt`, replace the existing `createGlassRenderEffect` function with:

```kotlin
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@InternalHazeApi
internal actual fun createGlassRenderEffects(
  params: RuntimeShaderGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): GlassRenderEffects {
  val blurEffect = params.createBlurRenderEffect()

  val blurredUnderlay = createRuntimeShaderRenderEffect(
    effect = GLASS_OUTPUT_MASK_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(blurEffect),
  ) {
    setMaskUniforms(params)
  }

  val overlay = createRuntimeShaderRenderEffect(
    effect = GLASS_OVERLAY_EFFECT,
    shaderNames = arrayOf("content"),
    inputs = arrayOf(null),
    uniforms = uniforms,
  )

  return GlassRenderEffects(
    overlay = overlay,
    underlay = blurredUnderlay,
  )
}
```

Then replace the Android runtime effect property:

```kotlin
private val GLASS_RUNTIME_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(
    GlassShaders.build(
      contentMode = GlassShaders.ContentMode.SingleBlurredInput,
    ),
  )
}
```

with:

```kotlin
private val GLASS_OVERLAY_EFFECT by lazy(LazyThreadSafetyMode.NONE) {
  createRuntimeEffect(
    GlassShaders.build(
      contentMode = GlassShaders.ContentMode.OverlayWithExternalUnderlay,
    ),
  )
}
```

- [ ] **Step 6: Update Skiko to wrap the existing effect**

In `GlassRenderEffectFactory.skiko.kt`, replace the existing `createGlassRenderEffect` function with:

```kotlin
@InternalHazeApi
internal actual fun createGlassRenderEffects(
  params: RuntimeShaderGlassDelegate.RenderParams,
  uniforms: RuntimeShaderUniformProvider.() -> Unit,
): GlassRenderEffects {
  val blurEffect = params.createBlurRenderEffect()

  val overlay = createRuntimeShaderRenderEffect(
    effect = GLASS_RUNTIME_EFFECT,
    shaderNames = arrayOf("content", "blurredContent"),
    inputs = arrayOf(null, blurEffect),
    uniforms = uniforms,
  )

  return GlassRenderEffects(overlay = overlay)
}
```

- [ ] **Step 7: Compile the changed modules**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest --tests dev.chrisbanes.haze.GlassDepthAndroidScreenshotTest
```

Expected: either PASS after screenshots are recorded or FAIL only because screenshots have not been recorded yet. Kotlin compilation errors must be fixed before moving on.

---

## Task 3: Record, Compare, And Decide Whether The Spike Passed

**Files:**
- Create/update screenshots in `haze-screenshot-tests/screenshots/android/`
- Commit all production, test, and screenshot changes if the visual evidence is acceptable.

- [ ] **Step 1: Record the Android API 35 depth screenshots**

Run:

```bash
./gradlew :haze-screenshot-tests:recordRoborazziAndroidHostTest --tests dev.chrisbanes.haze.GlassDepthAndroidScreenshotTest
```

Expected: PASS and new files under `haze-screenshot-tests/screenshots/android/` for suffixes `0`, `50`, and `100`.

- [ ] **Step 2: Compare the recorded screenshots**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest --tests dev.chrisbanes.haze.GlassDepthAndroidScreenshotTest
```

Expected: PASS.

- [ ] **Step 3: Inspect the generated files**

Open these files and compare them visually:

```text
haze-screenshot-tests/screenshots/android/GlassDepthAndroidScreenshotTest.glass_depthProgression_0.png
haze-screenshot-tests/screenshots/android/GlassDepthAndroidScreenshotTest.glass_depthProgression_50.png
haze-screenshot-tests/screenshots/android/GlassDepthAndroidScreenshotTest.glass_depthProgression_100.png
```

Expected visual result:

- `0` shows mostly original high-contrast stripes through the glass.
- `50` shows visibly more blur contribution than `0`.
- `100` shows the strongest blurred underlay contribution.
- All three remain clipped to the rounded shape without a visible double-alpha halo outside the shape.

- [ ] **Step 4: Run the existing Glass screenshot class**

Run:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest --tests dev.chrisbanes.haze.GlassScreenshotTest
```

Expected: PASS after any intentional screenshot changes have been recorded. If this fails because existing screenshots changed, inspect the diffs before accepting them; the spike should not create obvious clipping or alpha regressions in unrelated Glass cases.

- [ ] **Step 5: Capture the spike decision in the commit message**

If the screenshots satisfy the expected visual result, commit:

```bash
git add haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt \
  haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectFactory.android.kt \
  haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectFactory.skiko.kt \
  haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassScreenshotTest.kt \
  haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassDepthAndroidScreenshotTest.kt \
  haze-screenshot-tests/screenshots/android/GlassDepthAndroidScreenshotTest.glass_depthProgression_0.png \
  haze-screenshot-tests/screenshots/android/GlassDepthAndroidScreenshotTest.glass_depthProgression_50.png \
  haze-screenshot-tests/screenshots/android/GlassDepthAndroidScreenshotTest.glass_depthProgression_100.png
git commit -m "Spike Android Glass depth composition"
```

If the screenshots fail the pass criteria, do not commit the production change as a fix. Either leave it as an explicit spike commit with the observed failure in the message, or revert the production code and update issue #1009 with the limitation evidence.

---

## Self-Review

- Spec coverage: The plan covers Android-only two-pass drawing, unchanged Skiko behavior, depth screenshots at `0f`, `0.5f`, and `1f`, API 33+ verification through API 35 Robolectric, and pass/fail visual criteria.
- Placeholder scan: No placeholders remain. Each task lists concrete files, code snippets, commands, and expected outcomes.
- Type consistency: The common bundle is named `GlassRenderEffects`, and both platform actual functions are named `createGlassRenderEffects`.
