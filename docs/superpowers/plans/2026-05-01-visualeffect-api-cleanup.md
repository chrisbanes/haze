# VisualEffect API Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify the experimental `VisualEffect` API by removing internal-only hooks from the public surface, clarifying lifecycle and ownership semantics, and updating tests and docs to match the intended extension model.

**Architecture:** Keep `VisualEffect` as the single public extension point, but make it smaller and more intentional. Move blur-specific scaling and helper behavior into `haze-blur` internals, keep `VisualEffectContext` as a pure host abstraction, and let `HazeEffectNode` own effect lifecycle and single-owner enforcement.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose runtime/UI modifier nodes, Gradle, kotlin.test, assertk

---

## File Map

- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt`
  - Shrink the public interface, remove `@Stable`, rename clipping hooks, add `detach(context)`, and document attach-time geometry behavior.
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt`
  - Remove `visualEffect`, tighten KDoc, and keep the context focused on host-provided state/services.
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`
  - Update lifecycle calls to `detach(context)`, rename policy-hook call sites, and enforce single-owner attachment semantics.
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt`
  - Update `Modifier.hazeEffect` KDoc/examples to prefer `blurEffect {}` instead of allocating a fresh `BlurVisualEffect()` in the update block.
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`
  - Adapt to the new `VisualEffect` contract, move delegate updates out of `shouldDrawContentBehind`, and replace generic policy hooks with renamed ones.
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt`
  - Remove `context.visualEffect` usage and make helpers take explicit blur-specific inputs.
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurRenderEffectVisualEffect.kt`
  - Replace generic input-scale and clipping calls with blur-specific internal helpers.
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectUtils.kt`
  - Stop relying on `VisualEffect.calculateInputScaleFactor()` and call blur-specific resolution instead.
- Modify: `haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderScriptBlurVisualEffectDelegate.kt`
  - Remove `context.visualEffect` and generic scale/clip hooks.
- Modify: `haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderEffectBlurVisualEffectDelegate.android.kt`
  - Replace generic scale-factor hook usage with blur-specific resolution.
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt`
  - Cover `detach(context)`, attach-before-geometry behavior, and double-attach failure.
- Modify: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectLifecycleTest.kt`
  - Cover blur delegate update behavior and blur-specific scale-factor behavior.

## Task 1: Shrink The Public VisualEffect Surface

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt`
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt`
- Test: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt`

- [ ] **Step 1: Write the failing lifecycle test updates**

Add/adjust the test scaffolding in `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt` so it expects the new API shape:

```kotlin
internal class RecordingVisualEffect : VisualEffect {
  var attachCalls = 0
  var detachCalls = 0
  var updateCalls = 0
  var drawCalls = 0
  var trimMemoryCalls = 0
  var attachSawUnspecifiedSize = false
  var lastDetachContext: VisualEffectContext? = null

  override fun attach(context: VisualEffectContext) {
    attachCalls++
    attachSawUnspecifiedSize = context.size == Size.Unspecified || context.size == Size.Zero
  }

  override fun update(context: VisualEffectContext) {
    updateCalls++
  }

  override fun detach(context: VisualEffectContext) {
    detachCalls++
    lastDetachContext = context
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    drawCalls++
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    trimMemoryCalls++
  }
}

@Test
fun visualEffect_attachMayRunBeforeGeometryResolved() = runComposeUiTest {
  val hazeState = HazeState()
  val effect = RecordingVisualEffect()

  setContent {
    Box(Modifier.size(100.dp).hazeSource(hazeState)) {
      Spacer(Modifier.size(100.dp).hazeEffect(hazeState) { visualEffect = effect })
    }
  }

  waitForIdle()
  assertThat(effect.attachSawUnspecifiedSize).isEqualTo(true)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze:test --tests dev.chrisbanes.haze.VisualEffectLifecycleTest`

Expected: FAIL because `VisualEffect.detach()` currently has no context parameter and the new test/assertions do not compile yet.

- [ ] **Step 3: Update the public API minimally**

Edit `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt` to this shape:

```kotlin
@ExperimentalHazeApi
public interface VisualEffect {
  public fun DrawScope.draw(context: VisualEffectContext)

  public fun attach(context: VisualEffectContext): Unit = Unit
  public fun update(context: VisualEffectContext): Unit = Unit
  public fun detach(context: VisualEffectContext): Unit = Unit

  public fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel): Unit = Unit

  public fun shouldDrawContentBehind(context: VisualEffectContext): Boolean = false

  public fun shouldClipToNodeBounds(): Boolean = false

  public fun shouldPreferClipToAreaBounds(): Boolean = false

  public fun calculateLayerBounds(rect: Rect, density: Density): Rect = rect

  public companion object {
    public val Empty: VisualEffect get() = EmptyVisualEffect
  }
}

private object EmptyVisualEffect : VisualEffect {
  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
```

Also update the KDoc in that file to state:

```kotlin
/**
 * Called when this effect is attached to a context.
 *
 * Geometry may not be resolved yet at this point. Implementations must tolerate
 * [VisualEffectContext.position], [VisualEffectContext.size], [VisualEffectContext.layerSize],
 * and [VisualEffectContext.layerOffset] being unspecified or zero during attach.
 */
```

Then edit `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt` to remove the back-reference:

```kotlin
public interface VisualEffectContext {
  public val position: Offset
  public val size: Size
  public val layerSize: Size
  public val layerOffset: Offset
  public val rootBounds: Rect
  public val inputScale: HazeInputScale
  public val windowId: Any?
  public val areas: List<HazeArea>
  public val state: HazeState?
  public fun requireDensity(): Density
  public fun <T> currentValueOf(local: CompositionLocal<T>): T
  public fun requireGraphicsContext(): GraphicsContext
  public val coroutineScope: CoroutineScope
  public fun invalidateDraw()
}
```

And remove this implementation line from `HazeEffectNodeVisualEffectContext`:

```kotlin
override val visualEffect: VisualEffect get() = node.visualEffect
```

- [ ] **Step 4: Update tests to the new names and signatures**

In `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt`, update assertions like this:

```kotlin
assertThat(empty.shouldClipToNodeBounds()).isEqualTo(false)
assertThat(empty.requireInvalidation()).isEqualTo(false)
assertThat(empty.shouldPreferClipToAreaBounds()).isEqualTo(false)
assertThat(effect.lastDetachContext).isNotNull()
```

And remove `visualEffect` from `FakeVisualEffectContext`:

```kotlin
internal data object FakeVisualEffectContext : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = Size.Zero
  override val layerSize: Size = Size.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)
  override fun requireDensity(): Density = error("Fake")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Fake")
  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Fake")
  override fun invalidateDraw() = Unit
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :haze:test --tests dev.chrisbanes.haze.VisualEffectLifecycleTest`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt
git commit -m "Refine VisualEffect lifecycle API"
```

## Task 2: Update HazeEffectNode To Match The New Contract

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`
- Test: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt`

- [ ] **Step 1: Write the failing single-owner test**

Add this test to `haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt`:

```kotlin
@Test
fun visualEffect_reusingOneInstanceAcrossNodesThrows() = runComposeUiTest {
  val hazeState = HazeState()
  val effect = RecordingVisualEffect()

  kotlin.test.assertFailsWith<IllegalStateException> {
    setContent {
      Box(Modifier.size(100.dp).hazeSource(hazeState)) {
        Spacer(Modifier.size(40.dp).hazeEffect(hazeState) { visualEffect = effect })
        Spacer(Modifier.size(40.dp).hazeEffect(hazeState) { visualEffect = effect })
      }
    }
    waitForIdle()
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze:test --tests dev.chrisbanes.haze.VisualEffectLifecycleTest.visualEffect_reusingOneInstanceAcrossNodesThrows`

Expected: FAIL because the same effect instance can currently be attached twice.

- [ ] **Step 3: Update HazeEffectNode lifecycle calls and ownership guard**

In `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`, make these exact changes:

1. Pass context on detach:

```kotlin
field.detach(visualEffectContext)
```

and:

```kotlin
visualEffect.detach(visualEffectContext)
```

2. Change the draw-order call site:

```kotlin
if (drawContentBehind || visualEffect.shouldDrawContentBehind(visualEffectContext)) {
  drawLayer(contentLayer)
}
```

3. Rename clipping policy calls:

```kotlin
} else if (state == null && size.isSpecified && !visualEffect.shouldClipToNodeBounds() && shouldExpandLayer()) {
```

and:

```kotlin
return visualEffect.shouldPreferClipToAreaBounds()
```

4. Add a small attachment registry near the bottom of the file:

```kotlin
private val attachedEffects = mutableMapOf<VisualEffect, HazeEffectNode>()

private fun HazeEffectNode.attachVisualEffect(effect: VisualEffect) {
  val current = attachedEffects[effect]
  check(current == null || current === this) {
    "VisualEffect instances are single-owner and cannot be shared across multiple hazeEffect nodes."
  }
  attachedEffects[effect] = this
  effect.attach(visualEffectContext)
}

private fun HazeEffectNode.detachVisualEffect(effect: VisualEffect) {
  effect.detach(visualEffectContext)
  attachedEffects.remove(effect, this)
}
```

Then use those helpers from the property setter and `onAttach`/`onDetach`.

- [ ] **Step 4: Update the lifecycle test expectations**

Assert the replacement path still works with the new detach signature:

```kotlin
assertThat(effect1.lastDetachContext).isNotNull()
assertThat(effect2.detachCalls).isEqualTo(0)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :haze:test --tests dev.chrisbanes.haze.VisualEffectLifecycleTest`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/VisualEffectLifecycleTest.kt
git commit -m "Enforce single-owner VisualEffect lifecycle"
```

## Task 3: Remove Blur’s Dependence On Generic Public Hooks

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurRenderEffectVisualEffect.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectUtils.kt`
- Modify: `haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderScriptBlurVisualEffectDelegate.kt`
- Modify: `haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderEffectBlurVisualEffectDelegate.android.kt`
- Test: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectLifecycleTest.kt`

- [ ] **Step 1: Write the failing blur tests**

Add these tests to `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectLifecycleTest.kt`:

```kotlin
@Test
fun shouldDrawContentBehind_reflectsCurrentDelegateWithoutMutatingIt() {
  val effect = BlurVisualEffect()
  effect.delegate = ScrimBlurVisualEffectDelegate(effect)

  assertThat(effect.shouldDrawContentBehind(FakeVisualEffectContext)).isEqualTo(true)
}

@Test
fun resolveInputScaleFactor_autoUsesBlurSpecificRules() {
  val effect = BlurVisualEffect().apply {
    blurRadius = 20.dp
  }

  assertThat(effect.resolveInputScaleFactor(HazeInputScale.Auto)).isEqualTo(0.3334f)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :haze-blur:test --tests dev.chrisbanes.haze.blur.BlurVisualEffectLifecycleTest`

Expected: FAIL because `resolveInputScaleFactor()` does not exist yet and `shouldDrawContentBehind()` still relies on draw-scope/update side effects.

- [ ] **Step 3: Add blur-specific internal helpers**

In `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`, replace the public generic hook overrides with blur-internal helpers:

```kotlin
internal fun resolveInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
  is HazeInputScale.None -> 1f
  is HazeInputScale.Fixed -> scale.scale
  HazeInputScale.Auto -> {
    val blurRadius = blurRadius.takeOrElse { 0.dp }
    when {
      blurRadius < 7.dp -> 1f
      progressive != null -> 0.5f
      mask != null -> 0.5f
      else -> 0.3334f
    }
  }
}

override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
  return delegate is ScrimBlurVisualEffectDelegate
}

override fun shouldClipToNodeBounds(): Boolean = blurredEdgeTreatment.shape != null

override fun shouldPreferClipToAreaBounds(): Boolean {
  return backgroundColor.isSpecified && backgroundColor.alpha < 0.9f
}

override fun detach(context: VisualEffectContext) {
  if (isAttached) {
    isAttached = false
    delegate.detach()
  }
}
```

Also move delegate refreshing into `update(context)` so it is explicit:

```kotlin
override fun update(context: VisualEffectContext) {
  compositionLocalStyle = context.currentValueOf(LocalHazeBlurStyle)
  delegate = updateDelegate(context)

  if (dirtyTracker.any(BlurDirtyFields.InvalidateFlags)) {
    context.invalidateDraw()
  }
}
```

and change the platform expect/actual signature to return the delegate:

```kotlin
internal expect fun BlurVisualEffect.updateDelegate(context: VisualEffectContext): Delegate
```

- [ ] **Step 4: Replace generic helper usage with explicit blur inputs**

Edit `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt` so helper APIs no longer read `context.visualEffect`:

```kotlin
internal fun DrawScope.createAndDrawScaledContentLayer(
  context: VisualEffectContext,
  scaleFactor: Float,
  clipToNodeBounds: Boolean,
  backgroundColor: Color,
  releaseLayerOnExit: Boolean = true,
  block: DrawScope.(GraphicsLayer) -> Unit,
) {
  val graphicsContext = context.requireGraphicsContext()

  val layer = createScaledContentLayer(
    context = context,
    scaleFactor = scaleFactor,
    layerSize = context.layerSize,
    layerOffset = context.layerOffset,
    backgroundColor = backgroundColor,
  )

  if (layer != null) {
    layer.clip = clipToNodeBounds
    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clipToNodeBounds,
    ) {
      block(layer)
    }

    if (releaseLayerOnExit) {
      graphicsContext.releaseGraphicsLayer(layer)
    }
  }
}
```

Then update callers in:

- `BlurRenderEffectVisualEffect.kt`
- `BlurVisualEffectUtils.kt`
- `RenderScriptBlurVisualEffectDelegate.kt`
- `RenderEffectBlurVisualEffectDelegate.android.kt`

using this pattern:

```kotlin
val scaleFactor = blurVisualEffect.resolveInputScaleFactor(context.inputScale)
val clip = blurVisualEffect.shouldClipToNodeBounds()
```

and replace every `context.visualEffect.shouldClip()` / `calculateInputScaleFactor()` call with the explicit blur-specific values.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :haze-blur:test --tests dev.chrisbanes.haze.blur.BlurVisualEffectLifecycleTest`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurRenderEffectVisualEffect.kt haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectUtils.kt haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderScriptBlurVisualEffectDelegate.kt haze-blur/src/androidMain/kotlin/dev/chrisbanes/haze/blur/RenderEffectBlurVisualEffectDelegate.android.kt haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectLifecycleTest.kt
git commit -m "Move blur policy out of VisualEffect API"
```

## Task 4: Update Docs And Public Examples

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt`
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt`
- Modify: `docs/migrating-2.0.md`
- Verify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/*.kt`

- [ ] **Step 1: Update the failing examples in docs/KDoc**

Change the `Modifier.hazeEffect` KDoc example in `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt` from:

```kotlin
Modifier.hazeEffect(state, effect = effect) {
  visualEffect = BlurVisualEffect().apply {
    blurRadius = 20.dp
    colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.5f)))
  }
}
```

to:

```kotlin
Modifier.hazeEffect(state) {
  blurEffect {
    blurRadius = 20.dp
    colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.5f)))
  }
}
```

Update the blur class example in `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt` the same way.

- [ ] **Step 2: Update migration docs**

Search `docs/migrating-2.0.md` for any examples that imply allocating a fresh effect inside `hazeEffect {}` and convert them to `blurEffect {}`.

Use this shape where relevant:

```kotlin
Modifier.hazeEffect(state) {
  blurEffect {
    style = HazeMaterials.thin()
  }
}
```

- [ ] **Step 3: Inspect sample code for the discouraged pattern**

Run: `rg "visualEffect = BlurVisualEffect\(|BlurVisualEffect\(\)\.apply" sample haze docs`

Expected: no remaining public-facing examples that allocate a new blur effect inside the `hazeEffect {}` update block.

- [ ] **Step 4: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffect.kt haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffect.kt docs/migrating-2.0.md
git commit -m "Update VisualEffect docs and examples"
```

## Task 5: Run Full Verification

**Files:**
- Verify only

- [ ] **Step 1: Run focused module tests**

Run: `./gradlew :haze:test :haze-blur:test`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run the full verification command**

Run: `./gradlew check`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Inspect worktree before final handoff**

Run: `git status --short`

Expected: only the intended API, test, and doc changes are present.

- [ ] **Step 4: Confirm the last task commit still represents the verified code**

Run: `git log --oneline -1`

Expected: the latest commit is the final task commit for this cleanup, and no additional fixes were introduced during verification.
