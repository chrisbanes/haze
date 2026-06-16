# Haze Source Transition Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Draw the last valid source-dependent effect output during short source detach/attach gaps for Blur and LiquidGlass.

**Architecture:** Add an internal retained-output capability in core, let `HazeEffectNode` draw retained output when background source areas are temporarily empty, and implement retention in source-dependent Blur and LiquidGlass delegates. Source layers remain owned by `HazeSourceNode`; only rendered effect output is retained.

**Tech Stack:** Kotlin Multiplatform, Compose UI modifier nodes, common tests, Gradle.

---

### Task 1: Core Retained-Output Hook

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt`
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`
- Test: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt`

- [ ] **Step 1: Add failing tests**

Add two tests to `VisualEffectLifecycleTest`:

```kotlin
@Test
fun visualEffect_retainedOutputDrawnWhenBackgroundAreasDisappear() = runComposeUiTest {
  val hazeState = HazeState()
  val effect = RetainedOutputRecordingVisualEffect()
  val showSource = mutableStateOf(true)

  setContent {
    Box(Modifier.size(100.dp)) {
      if (showSource.value) {
        Spacer(Modifier.size(100.dp).hazeSource(hazeState))
      }
      Spacer(
        Modifier
          .size(100.dp)
          .hazeEffect(hazeState) {
            visualEffect = effect
          },
      )
    }
  }

  waitForIdle()
  assertThat(effect.drawCalls).isGreaterThan(0)
  assertThat(effect.lastDrawAreaCount).isGreaterThan(0)

  val beforeRemovalDraws = effect.drawCalls
  showSource.value = false
  waitForIdle()

  assertThat(effect.drawCalls).isGreaterThan(beforeRemovalDraws)
  assertThat(effect.lastDrawAreaCount).isEqualTo(0)
}

@Test
fun visualEffect_retainedOutputNotDrawnWhenNeverAvailable() = runComposeUiTest {
  val hazeState = HazeState()
  val effect = RetainedOutputRecordingVisualEffect()

  setContent {
    Spacer(
      Modifier
        .size(100.dp)
        .hazeEffect(hazeState) {
          visualEffect = effect
        },
    )
  }

  waitForIdle()

  assertThat(effect.drawCalls).isEqualTo(0)
}
```

Add the helper class near existing test helpers:

```kotlin
internal class RetainedOutputRecordingVisualEffect : VisualEffect, RetainedOutputVisualEffect {
  var drawCalls = 0
  var lastDrawAreaCount = -1
  var retainedOutputAvailable = false
  var clearCalls = 0

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean {
    return retainedOutputAvailable
  }

  override fun clearRetainedOutput() {
    clearCalls++
    retainedOutputAvailable = false
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    drawCalls++
    lastDrawAreaCount = context.areas.size
    if (context.areas.isNotEmpty()) {
      retainedOutputAvailable = true
    }
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.VisualEffectRetainedOutputTest`

Expected: compilation fails because `RetainedOutputVisualEffect` does not exist.

- [ ] **Step 3: Add internal capability and node behavior**

Add to `VisualEffect.kt`:

```kotlin
internal interface RetainedOutputVisualEffect {
  fun canDrawRetainedOutput(context: VisualEffectContext): Boolean
  fun clearRetainedOutput()
}
```

In `HazeEffectNode.draw()` background mode, replace the `areas.isNotEmpty()` check with:

```kotlin
val canDrawRetainedOutput =
  (visualEffect as? RetainedOutputVisualEffect)?.canDrawRetainedOutput(visualEffectContext) == true
if (areas.isNotEmpty() || canDrawRetainedOutput) {
  with(visualEffect) {
    draw(visualEffectContext)
  }
}
```

Add:

```kotlin
private fun clearRetainedOutput() {
  (visualEffect as? RetainedOutputVisualEffect)?.clearRetainedOutput()
}
```

Call `clearRetainedOutput()` when `state`, `size`, or `layerSize` changes, and in `onDetach()`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.VisualEffectRetainedOutputTest`

Expected: PASS.

### Task 2: Blur Retained Output

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurRenderEffectVisualEffect.kt`
- Modify: `haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderScriptBlurVisualEffectDelegate.kt`
- Modify: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectLifecycleTest.kt`

- [ ] **Step 1: Add failing lifecycle test**

Add to `BlurVisualEffectLifecycleTest`:

```kotlin
@Test
fun retainedOutputAvailabilityReflectsDelegate() {
  val effect = BlurVisualEffect()
  val delegate = RetainedTrackingBlurDelegate()
  effect.delegate = delegate

  assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isFalse()

  delegate.retainedOutputAvailable = true

  assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isTrue()

  effect.clearRetainedOutput()

  assertThat(delegate.clearCount).isEqualTo(1)
  assertThat(effect.canDrawRetainedOutput(FakeVisualEffectContext)).isFalse()
}
```

Add:

```kotlin
private class RetainedTrackingBlurDelegate : BlurVisualEffect.Delegate, RetainedOutputDelegate {
  var retainedOutputAvailable = false
  var clearCount = 0

  override fun canDrawRetainedOutput(): Boolean = retainedOutputAvailable

  override fun clearRetainedOutput() {
    clearCount++
    retainedOutputAvailable = false
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze-blur:jvmTest --tests dev.chrisbanes.haze.blur.BlurVisualEffectLifecycleTest`

Expected: compilation fails because `RetainedOutputDelegate` and `BlurVisualEffect.canDrawRetainedOutput` do not exist.

- [ ] **Step 3: Implement Blur retained-output delegation**

In `BlurVisualEffect.kt`, import `dev.chrisbanes.haze.RetainedOutputVisualEffect`, implement it on `BlurVisualEffect`, and add:

```kotlin
internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean
  fun clearRetainedOutput()
}

override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean {
  return (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true
}

override fun clearRetainedOutput() {
  (delegate as? RetainedOutputDelegate)?.clearRetainedOutput()
}
```

Make `RenderEffectBlurVisualEffectDelegate` implement `RetainedOutputDelegate`. Track:

```kotlin
private var retainedOutputAvailable = false
```

Set it to `true` only after `createScaledContentLayer(...)` succeeds while `context.areas.isNotEmpty()`.
When `context.areas.isEmpty()` and retained output is available, skip re-recording and draw the existing
`scaledContentLayer`. Clear it in `clearRetainedOutput()` and `detach()`.

Make `RenderScriptBlurVisualEffectDelegate` implement `RetainedOutputDelegate`. Return `contentLayer.size != IntSize.Zero && retainedOutputAvailable`, skip starting `updateSurface` when areas are empty, and clear retained output by cancelling work and resetting the retained flag.

- [ ] **Step 4: Run Blur tests**

Run: `./gradlew :haze-blur:jvmTest --tests dev.chrisbanes.haze.blur.BlurVisualEffectLifecycleTest`

Expected: PASS.

### Task 3: LiquidGlass Retained Output

**Files:**
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassVisualEffect.kt`
- Modify: `haze-liquidglass/src/commonMain/kotlin/dev/chrisbanes/haze/liquidglass/RuntimeShaderLiquidGlassDelegate.kt`
- Modify: `haze-liquidglass/src/commonTest/kotlin/dev/chrisbanes/haze/liquidglass/LiquidGlassStyleTest.kt`

- [ ] **Step 1: Add failing lifecycle test**

Add to `LiquidGlassStyleTest`:

```kotlin
@Test
fun retainedOutputAvailabilityReflectsDelegate() {
  val effect = LiquidGlassVisualEffect()
  val delegate = RetainedTrackingLiquidGlassDelegate()
  effect.delegate = delegate

  assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()

  delegate.retainedOutputAvailable = true

  assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isTrue()

  effect.clearRetainedOutput()

  assertThat(delegate.clearCount).isEqualTo(1)
  assertThat(effect.canDrawRetainedOutput(FakeLiquidGlassContext)).isFalse()
}
```

Add a fake context and delegate matching the Blur lifecycle test pattern.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze-liquidglass:jvmTest --tests dev.chrisbanes.haze.liquidglass.LiquidGlassStyleTest`

Expected: compilation fails because LiquidGlass does not implement retained-output APIs.

- [ ] **Step 3: Implement LiquidGlass retained-output delegation**

In `LiquidGlassVisualEffect.kt`, import `RetainedOutputVisualEffect`, implement it, and add an internal delegate interface:

```kotlin
internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean
  fun clearRetainedOutput()
}
```

Delegate `canDrawRetainedOutput()` and `clearRetainedOutput()` to the current delegate when it implements this interface.

In `RuntimeShaderLiquidGlassDelegate`, own a reusable `GraphicsLayer`, track retained output availability, skip re-recording on empty-source frames, and draw the retained layer. Clear retained output on detach and through `clearRetainedOutput()`.

- [ ] **Step 4: Run LiquidGlass tests**

Run: `./gradlew :haze-liquidglass:jvmTest --tests dev.chrisbanes.haze.liquidglass.LiquidGlassStyleTest`

Expected: PASS.

### Task 4: Full Verification

**Files:**
- Verify all modified modules.

- [ ] **Step 1: Run focused tests**

Run:

```bash
./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.VisualEffectRetainedOutputTest
./gradlew :haze-blur:jvmTest --tests dev.chrisbanes.haze.blur.BlurVisualEffectLifecycleTest
./gradlew :haze-liquidglass:jvmTest --tests dev.chrisbanes.haze.liquidglass.LiquidGlassStyleTest
```

Expected: all PASS.

- [ ] **Step 2: Run formatting**

Run: `./gradlew spotlessApply`

Expected: exits 0.

- [ ] **Step 3: Run broader checks**

Run: `./gradlew :haze:check :haze-blur:check :haze-liquidglass:check`

Expected: exits 0.
