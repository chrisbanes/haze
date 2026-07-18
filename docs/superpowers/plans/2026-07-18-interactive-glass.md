# Interactive Glass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add fully opt-in hover, focus, and press responses directly to `GlassVisualEffect`, with non-consuming input, configurable motion and transforms, and no idle overhead or base glass-pipeline churn.

**Architecture:** Core Haze gains a small internal-capability interface that lets `HazeEffectNode` dynamically delegate pointer observation and apply an optional final draw transform. The glass module owns the public state-response DSL, a retained interaction controller, and separate fallback/runtime rendering paths; active responses resolve per property in fixed focus, hover, then press order.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform Modifier.Node pointer APIs, Compose Foundation `InteractionSource`, Compose Animation Core `Animatable` and `FiniteAnimationSpec`, Android AGSL, Skiko SKSL, assertk, Compose UI tests, and Roborazzi.

## Global Constraints

- Interaction is disabled by default; every response state is opt-in.
- `hovered()`, `focused()`, and `pressed()` install only their own preset; `interactable()` installs all three.
- A custom state block replaces that state's preset and starts from identity.
- State resolution is per property in fixed `focused -> hovered -> pressed` order.
- The feature must not consume, synthesize, or own click, drag, scroll, long-press, focus, or semantics behavior.
- Layout size, placement, semantics bounds, and pointer hit targets must remain unchanged.
- Non-interactive glass must install no pointer delegate, collector, controller, or animation resources.
- Focus-only configuration must not install a pointer delegate and activates only through a configured `InteractionSource`.
- System reduced motion is the default; reduced behavior keeps lighting and optics immediate and forces transform to identity.
- Runtime interaction values must not enter source, blur, depth, base optical, base detail, or base rim cache keys.
- The fallback path supports lighting and both transform targets; interaction optics are intentionally a no-op there.
- Public numeric declarations reject non-finite values and enforce the ranges in the approved design.
- Java 21 is required for Gradle verification.

## File Structure

### Core Haze

- Modify `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt`: define the generic interaction capability and final-draw transform value.
- Modify `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`: dynamically install pointer observation and apply material-plus-content transforms.
- Create `haze/src/commonTest/kotlin/dev/chrisbanes/haze/InteractiveVisualEffectTest.kt`: verify delegation, non-consumption, cancellation, replacement, and draw transforms.

### Glass declarations and controller

- Create `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteraction.kt`: public response scope and enums plus internal compiled response values.
- Create `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteractionController.kt`: signal reduction, response ownership resolution, animation, pointer position, and reduced motion.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`: reusable motion specs and preset constants.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`: direct opt-in methods, shared settings, lifecycle, input forwarding, transforms, and copy behavior.
- Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionDslTest.kt`: public API and validation tests.
- Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt`: resolver, input, source, motion, and lifecycle tests.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectLifecycleTest.kt` and `GlassVisualEffectOverrideTest.kt`: attach/detach and copy coverage.

### Rendering

- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/FallbackGlassDelegate.kt`: localized interaction lighting and material-only transform support.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`: interaction-only static keys and dynamic uniform values.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassLayers.kt`: optional interaction optical, detail, and lighting layers.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassShaders.kt`: localized optical, detail, and lighting shader variants.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`: bypassable interaction stages that reuse retained base inputs.
- Modify `haze-utils/src/commonMain/kotlin/dev/chrisbanes/haze/RuntimeShader.kt`, `haze-utils/src/androidMain/kotlin/dev/chrisbanes/haze/RuntimeShader.android.kt`, and `haze-utils/src/skikoMain/kotlin/dev/chrisbanes/haze/RenderEffect.skiko.kt`: a mutable-uniform runtime-effect wrapper.
- Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/FallbackGlassInteractionTest.kt`: fallback lighting and transform verification.
- Extend `GlassShadersTest.kt`, `GlassStageInvalidationTest.kt`, `GlassRenderEffectKeysTest.kt`, and `RuntimeShaderGlassDelegateIntegrationTest.kt`.

### Integration and publication

- Create `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInteractionScreenshotTest.kt` and corresponding Android/Desktop baselines.
- Create `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/InteractiveGlassSample.kt` and register it in `Samples.kt`: demonstrate `pressed()`, `interactable()`, and a customized scale-only press.
- Modify `docs/effects/glass.md`: document opt-in semantics, source sharing, response precedence, clearing, reduced motion, and fallback limitations.
- Modify `CHANGELOG.md`: record the new opt-in capability under `Unreleased / Added`.
- Regenerate `haze/api/api.txt` and `haze-glass/api/api.txt`.

---

### Task 1: Add the generic interactive visual-effect capability

**Files:**

- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt:3-149`
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt:35-680`
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/InteractiveVisualEffectTest.kt`

**Interfaces:**

- Consumes: existing `VisualEffect` lifecycle and `VisualEffectContext`.
- Produces: `InteractiveVisualEffect`, `VisualEffectTransform`, dynamic final-pass pointer forwarding, and final group scaling for later glass tasks.

- [ ] **Step 1: Write failing pointer-delegation and transform tests**

Create `InteractiveVisualEffectTest.kt` with a retained fake effect and these cases:

```kotlin
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class InteractiveVisualEffectTest : ContextTest() {
  @Test
  fun pointerObservation_isInstalledOnlyWhileRequested() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect()
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .hazeEffect { visualEffect = effect },
      )
    }

    onNodeWithTag("glass").performTouchInput { click(center) }
    assertThat(effect.pointerEvents).isEmpty()

    effect.observes = true
    waitForIdle()
    onNodeWithTag("glass").performTouchInput { click(center) }
    assertThat(effect.pointerEvents).isNotEmpty()

    effect.observes = false
    waitForIdle()
    val count = effect.pointerEvents.size
    onNodeWithTag("glass").performTouchInput { click(center) }
    assertThat(effect.pointerEvents.size).isEqualTo(count)
    assertThat(effect.sawConsumedChange).isFalse()
  }

  @Test
  fun pointerObservation_cancelledWhenNodeLeavesComposition() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect().apply { observes = true }
    var shown by mutableStateOf(true)
    setContent {
      if (shown) {
        Box(
          Modifier
            .size(100.dp)
            .testTag("glass")
            .hazeEffect { visualEffect = effect },
        )
      }
    }

    onNodeWithTag("glass").performTouchInput { down(center) }
    shown = false
    waitForIdle()

    assertThat(effect.cancelCalls).isEqualTo(1)
  }

  @Test
  fun materialAndContentTransform_scalesFinalGroupWithoutChangingBounds() = runComposeUiTest {
    val effect = RecordingInteractiveVisualEffect().apply {
      transform = VisualEffectTransform(0.5f, 0.5f, Offset(50f, 50f))
    }
    setContent {
      Box(Modifier.size(100.dp).background(Color.Black)) {
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect {
              drawContentBehind = true
              visualEffect = effect
            }
            .background(Color.Red),
        )
      }
    }

    val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
    assertThat(pixels[5, 5]).isEqualTo(Color.Black)
    onNodeWithTag("glass").assertWidthIsEqualTo(100.dp).assertHeightIsEqualTo(100.dp)
  }
}

private class RecordingInteractiveVisualEffect : InteractiveVisualEffect {
  var observes by mutableStateOf(false)
  var transform: VisualEffectTransform = VisualEffectTransform.Identity
  val pointerEvents = mutableListOf<PointerEvent>()
  var cancelCalls = 0
  var sawConsumedChange = false

  override val observesPointerEvents: Boolean get() = observes

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
    pointerEvents += event
    sawConsumedChange = sawConsumedChange || event.changes.any { it.isConsumed }
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    cancelCalls++
  }

  override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform {
    return transform
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean = true

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
```

- [ ] **Step 2: Run the tests and confirm the capability is missing**

Run:

```bash
./gradlew :haze:jvmTest --tests 'dev.chrisbanes.haze.InteractiveVisualEffectTest'
```

Expected: compilation fails because `InteractiveVisualEffect` and `VisualEffectTransform` do not exist.

- [ ] **Step 3: Add the capability types**

Add these declarations to `VisualEffect.kt`:

```kotlin
@InternalHazeApi
public interface InteractiveVisualEffect : VisualEffect {
  public val observesPointerEvents: Boolean

  public fun onPointerEvent(
    event: PointerEvent,
    context: VisualEffectContext,
  )

  public fun onCancelPointerInput(context: VisualEffectContext)

  public fun currentContentTransform(
    context: VisualEffectContext,
  ): VisualEffectTransform = VisualEffectTransform.Identity
}

@InternalHazeApi
public data class VisualEffectTransform(
  public val scaleX: Float,
  public val scaleY: Float,
  public val pivot: Offset,
) {
  init {
    require(scaleX.isFinite() && scaleX > 0f) { "scaleX must be finite and greater than zero" }
    require(scaleY.isFinite() && scaleY > 0f) { "scaleY must be finite and greater than zero" }
    require(pivot.x.isFinite() && pivot.y.isFinite()) { "pivot must be finite" }
  }

  public companion object {
    public val Identity: VisualEffectTransform = VisualEffectTransform(
      scaleX = 1f,
      scaleY = 1f,
      pivot = Offset.Zero,
    )
  }
}
```

Import `Offset` and `PointerEvent` from Compose UI.

- [ ] **Step 4: Dynamically delegate pointer events**

Change `HazeEffectNode` to inherit from `DelegatingNode()`. Add a nullable delegated child and synchronize it after effect replacement, after `visualEffect.update(...)`, and before detach:

```kotlin
private var pointerInputDelegate: HazeEffectPointerInputNode? = null

private fun syncPointerInputDelegate() {
  val interactive = visualEffect as? InteractiveVisualEffect
  val required = interactive?.observesPointerEvents == true
  when {
    required && pointerInputDelegate == null -> {
      pointerInputDelegate = delegate(
        HazeEffectPointerInputNode(
          interactiveEffect = { visualEffect as? InteractiveVisualEffect },
          context = { visualEffectContext },
        ),
      )
    }
    !required && pointerInputDelegate != null -> {
      val current = pointerInputDelegate ?: return
      current.cancel(interactive)
      undelegate(current)
      pointerInputDelegate = null
    }
  }
}

private class HazeEffectPointerInputNode(
  private val interactiveEffect: () -> InteractiveVisualEffect?,
  private val context: () -> VisualEffectContext,
) : Modifier.Node(), PointerInputModifierNode {
  private var cancellationDelivered = false

  override fun onPointerEvent(
    pointerEvent: PointerEvent,
    pass: PointerEventPass,
    bounds: IntSize,
  ) {
    if (pass == PointerEventPass.Final) {
      cancellationDelivered = false
      interactiveEffect()?.onPointerEvent(pointerEvent, context())
    }
  }

  override fun onCancelPointerInput() {
    cancel(interactiveEffect())
  }

  fun cancel(effect: InteractiveVisualEffect?) {
    if (!cancellationDelivered) {
      cancellationDelivered = true
      effect?.onCancelPointerInput(context())
    }
  }
}
```

Call `syncPointerInputDelegate()` immediately after each successful effect update and after the `visualEffect` field changes. On node detach, call `onCancelPointerInput` and undelegate the child before detaching the visual effect. Do not consume any `PointerInputChange`.

Before replacing `visualEffect`, call `pointerInputDelegate?.cancel(oldInteractiveEffect)` while the
old effect still owns the context. Then assign the new effect and run `syncPointerInputDelegate()`.
The child's guard prevents `undelegate` from delivering a duplicate cancellation. Add a test that
starts a press, replaces the effect with `VisualEffect.Empty`, and asserts the old effect receives
exactly one cancellation and no later events.

- [ ] **Step 5: Wrap only the final visible draw group**

Add this helper:

```kotlin
private inline fun ContentDrawScope.withVisualEffectTransform(
  block: ContentDrawScope.() -> Unit,
) {
  val transform = (visualEffect as? InteractiveVisualEffect)
    ?.currentContentTransform(visualEffectContext)
    ?: VisualEffectTransform.Identity
  if (transform == VisualEffectTransform.Identity) {
    block()
  } else {
    scale(
      scaleX = transform.scaleX,
      scaleY = transform.scaleY,
      pivot = transform.pivot,
      block = block,
    )
  }
}
```

In background mode, run `prepareDraw` before the helper, then wrap `draw`, `drawContentSafely`, and `drawForeground` together. In foreground mode, record `contentLayer` and run `prepareDraw` before the helper, then wrap the visible content-layer draw, effect draw, and foreground draw. Leave measurement, source recording, geometry calculation, and pointer hit testing outside the transform.

- [ ] **Step 6: Run core tests**

Run:

```bash
./gradlew :haze:jvmTest --tests 'dev.chrisbanes.haze.InteractiveVisualEffectTest' --tests 'dev.chrisbanes.haze.VisualEffectLifecycleTest' --tests 'dev.chrisbanes.haze.VisualEffectDrawPreparationTest'
```

Expected: all selected tests pass, click input remains unconsumed, and existing draw ordering is unchanged.

- [ ] **Step 7: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/InteractiveVisualEffectTest.kt
git commit -m "Add interactive visual effect capability"
```

---

### Task 2: Add the direct glass interaction DSL and opt-in slots

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `haze-glass/build.gradle.kts`
- Create: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteraction.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionDslTest.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectOverrideTest.kt`

**Interfaces:**

- Consumes: `FiniteAnimationSpec<Float>`, `FiniteAnimationSpec<Offset>`, and Foundation `InteractionSource`.
- Produces: the complete public configuration surface and internal `GlassInteractionSlot` values consumed by the controller.

- [ ] **Step 1: Write failing DSL, preset, replacement, copy, and validation tests**

Create tests covering these exact assertions:

```kotlin
@Test
fun noResponses_isNotInteractive() {
  val effect = GlassVisualEffect()
  assertThat(effect.hoveredSlot).isEqualTo(null)
  assertThat(effect.focusedSlot).isEqualTo(null)
  assertThat(effect.pressedSlot).isEqualTo(null)
  assertThat(effect.observesPointerEvents).isFalse()
}

@Test
fun interactable_matchesIndividualPresets() {
  val individual = GlassVisualEffect().apply {
    hovered()
    focused()
    pressed()
  }
  val shortcut = GlassVisualEffect().apply { interactable() }

  assertThat(shortcut.hoveredSlot?.response).isEqualTo(individual.hoveredSlot?.response)
  assertThat(shortcut.focusedSlot?.response).isEqualTo(individual.focusedSlot?.response)
  assertThat(shortcut.pressedSlot?.response).isEqualTo(individual.pressedSlot?.response)
}

@Test
fun customPressed_replacesPresetAndStartsFromIdentity() {
  val effect = GlassVisualEffect().apply {
    interactable()
    pressed { scale(0.97f) }
  }

  val response = checkNotNull(effect.pressedSlot?.response)
  assertThat(response.scaleX?.value).isEqualTo(0.97f)
  assertThat(response.scaleY?.value).isEqualTo(0.97f)
  assertThat(response.lightingIntensity).isEqualTo(null)
  assertThat(response.refractionMultiplier).isEqualTo(null)
  assertThat(response.whitePointDelta).isEqualTo(null)
  assertThat(effect.hoveredSlot).isNotNull()
  assertThat(effect.focusedSlot).isNotNull()
}

@Test
fun declarations_areLastWriteWins() {
  val effect = GlassVisualEffect().apply {
    pressed {
      scale(0.99f)
      scale(0.96f, 0.97f)
      lightingIntensity(0.2f)
      lightingIntensity(0.8f)
    }
  }

  val response = checkNotNull(effect.pressedSlot?.response)
  assertThat(response.scaleX?.value).isEqualTo(0.96f)
  assertThat(response.scaleY?.value).isEqualTo(0.97f)
  assertThat(response.lightingIntensity?.value).isEqualTo(0.8f)
}

@Test
fun clearingFinalPointerSlot_disablesPointerObservation() {
  val effect = GlassVisualEffect().apply { pressed() }
  assertThat(effect.observesPointerEvents).isTrue()
  effect.clearPressed()
  assertThat(effect.observesPointerEvents).isFalse()
}

@Test
fun focusOnly_doesNotObservePointers() {
  val effect = GlassVisualEffect().apply { focused() }
  assertThat(effect.observesPointerEvents).isFalse()
}

@Test
fun invalidValues_failDuringResponseCompilation() {
  assertFailsWith<IllegalArgumentException> {
    GlassVisualEffect().pressed { scale(Float.NaN) }
  }
  assertFailsWith<IllegalArgumentException> {
    GlassVisualEffect().pressed { lightingIntensity(1.1f) }
  }
  assertFailsWith<IllegalArgumentException> {
    GlassVisualEffect().pressed { refractionMultiplier(2.1f) }
  }
  assertFailsWith<IllegalArgumentException> {
    GlassVisualEffect().pressed { whitePointDelta(-1.1f) }
  }
}
```

Extend the existing copy-constructor test to assert all three slots, `interactionSource`, radius, transform target, pivot, position spec, and reduced-motion policy are copied.

- [ ] **Step 2: Run the tests and confirm the API is missing**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionDslTest' --tests 'dev.chrisbanes.haze.glass.GlassVisualEffectOverrideTest'
```

Expected: compilation fails on the new interaction methods and types.

- [ ] **Step 3: Add the animation-core dependency**

Add this alias:

```toml
compose-animation-core = { module = "org.jetbrains.compose.animation:animation-core", version.ref = "compose-multiplatform" }
```

Add this public dependency to `haze-glass` `commonMain` because animation specs appear in public signatures:

```kotlin
api(libs.compose.animation.core)
```

- [ ] **Step 4: Create the response declarations**

Create `GlassInteraction.kt` with these public types and internal compiled representation:

```kotlin
@ExperimentalHazeApi
public enum class GlassTransformTarget {
  MaterialOnly,
  MaterialAndContent,
}

@ExperimentalHazeApi
public enum class GlassTransformPivot {
  Pointer,
  Center,
}

@ExperimentalHazeApi
public enum class GlassReducedMotionPolicy {
  System,
  Reduced,
  Full,
}

@ExperimentalHazeApi
public interface GlassInteractionScope {
  public fun lightingIntensity(intensity: Float)
  public fun refractionMultiplier(multiplier: Float)
  public fun whitePointDelta(delta: Float)

  public fun scale(scale: Float) {
    scale(scaleX = scale, scaleY = scale)
  }

  public fun scale(scaleX: Float, scaleY: Float)

  public fun animate(
    toSpec: FiniteAnimationSpec<Float>,
    fromSpec: FiniteAnimationSpec<Float>,
    block: GlassInteractionScope.() -> Unit,
  )
}

internal data class GlassResponseValue(
  val value: Float,
  val toSpec: FiniteAnimationSpec<Float>?,
  val fromSpec: FiniteAnimationSpec<Float>?,
)

internal data class GlassInteractionResponse(
  val lightingIntensity: GlassResponseValue? = null,
  val refractionMultiplier: GlassResponseValue? = null,
  val whitePointDelta: GlassResponseValue? = null,
  val scaleX: GlassResponseValue? = null,
  val scaleY: GlassResponseValue? = null,
)

internal data class GlassInteractionSlot(
  val revision: Long,
  val response: GlassInteractionResponse,
)

internal class GlassInteractionScopeImpl : GlassInteractionScope {
  private var toSpec: FiniteAnimationSpec<Float>? = null
  private var fromSpec: FiniteAnimationSpec<Float>? = null
  private var lighting: GlassResponseValue? = null
  private var refraction: GlassResponseValue? = null
  private var whitePoint: GlassResponseValue? = null
  private var xScale: GlassResponseValue? = null
  private var yScale: GlassResponseValue? = null

  override fun lightingIntensity(intensity: Float) {
    requireFiniteInRange("lightingIntensity", intensity, 0f..1f)
    lighting = value(intensity)
  }

  override fun refractionMultiplier(multiplier: Float) {
    requireFiniteInRange("refractionMultiplier", multiplier, 0f..2f)
    refraction = value(multiplier)
  }

  override fun whitePointDelta(delta: Float) {
    requireFiniteInRange("whitePointDelta", delta, -1f..1f)
    whitePoint = value(delta)
  }

  override fun scale(scaleX: Float, scaleY: Float) {
    requireFiniteScale("scaleX", scaleX)
    requireFiniteScale("scaleY", scaleY)
    xScale = value(scaleX)
    yScale = value(scaleY)
  }

  override fun animate(
    toSpec: FiniteAnimationSpec<Float>,
    fromSpec: FiniteAnimationSpec<Float>,
    block: GlassInteractionScope.() -> Unit,
  ) {
    val previousTo = this.toSpec
    val previousFrom = this.fromSpec
    this.toSpec = toSpec
    this.fromSpec = fromSpec
    try {
      block()
    } finally {
      this.toSpec = previousTo
      this.fromSpec = previousFrom
    }
  }

  fun build(): GlassInteractionResponse = GlassInteractionResponse(
    lightingIntensity = lighting,
    refractionMultiplier = refraction,
    whitePointDelta = whitePoint,
    scaleX = xScale,
    scaleY = yScale,
  )

  private fun value(value: Float): GlassResponseValue {
    return GlassResponseValue(value = value, toSpec = toSpec, fromSpec = fromSpec)
  }
}

internal fun buildGlassInteractionResponse(
  block: GlassInteractionScope.() -> Unit,
): GlassInteractionResponse = GlassInteractionScopeImpl().apply(block).build()

private fun requireFiniteInRange(name: String, value: Float, range: ClosedFloatingPointRange<Float>) {
  require(value.isFinite() && value in range) { "$name must be finite and in range" }
}

private fun requireFiniteScale(name: String, value: Float) {
  require(value.isFinite() && value > 0f && value <= 1f) {
    "$name must be finite, greater than zero, and at most one"
  }
}
```

- [ ] **Step 5: Add presets and reusable motion specs**

Add to `GlassDefaults`:

```kotlin
public const val interactionLightRadiusFraction: Float = 0.7f

public val hoverAnimationSpec: FiniteAnimationSpec<Float> = spring(
  dampingRatio = 1f,
  stiffness = Spring.StiffnessMediumLow,
)

public val pressAnimationSpec: FiniteAnimationSpec<Float> = spring(
  dampingRatio = 0.82f,
  stiffness = Spring.StiffnessMedium,
)

public val releaseAnimationSpec: FiniteAnimationSpec<Float> = spring(
  dampingRatio = 0.72f,
  stiffness = Spring.StiffnessMediumLow,
)

public val positionAnimationSpec: FiniteAnimationSpec<Offset> = spring(
  dampingRatio = 1f,
  stiffness = Spring.StiffnessMedium,
)
```

Compile presets through the same builder used by custom blocks:

```kotlin
internal fun defaultHoverResponse(): GlassInteractionResponse = buildGlassInteractionResponse {
  animate(GlassDefaults.hoverAnimationSpec, GlassDefaults.releaseAnimationSpec) {
    lightingIntensity(0.35f)
    refractionMultiplier(1.02f)
    whitePointDelta(0.01f)
    scale(1f)
  }
}

internal fun defaultFocusResponse(): GlassInteractionResponse = defaultHoverResponse()

internal fun defaultPressResponse(): GlassInteractionResponse = buildGlassInteractionResponse {
  animate(GlassDefaults.pressAnimationSpec, GlassDefaults.releaseAnimationSpec) {
    lightingIntensity(1f)
    refractionMultiplier(1.08f)
    whitePointDelta(0.04f)
    scale(0.98f)
  }
}
```

- [ ] **Step 6: Add retained slots and direct configuration to `GlassVisualEffect`**

Add internal snapshot-backed slots with a monotonically increasing revision. Task 4 promotes the
pointer-observation property to the `InteractiveVisualEffect` override when it adds event forwarding:

```kotlin
private var nextInteractionRevision: Long = 0L

internal var hoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
  private set
internal var focusedSlot: GlassInteractionSlot? by mutableStateOf(null)
  private set
internal var pressedSlot: GlassInteractionSlot? by mutableStateOf(null)
  private set

internal val observesPointerEvents: Boolean
  get() = hoveredSlot != null || pressedSlot != null

public fun hovered() = setHovered(defaultHoverResponse())
public fun hovered(block: GlassInteractionScope.() -> Unit) =
  setHovered(buildGlassInteractionResponse(block))

public fun focused() = setFocused(defaultFocusResponse())
public fun focused(block: GlassInteractionScope.() -> Unit) =
  setFocused(buildGlassInteractionResponse(block))

public fun pressed() = setPressed(defaultPressResponse())
public fun pressed(block: GlassInteractionScope.() -> Unit) =
  setPressed(buildGlassInteractionResponse(block))

public fun interactable() {
  hovered()
  focused()
  pressed()
}

public fun clearHovered() {
  hoveredSlot = null
  onInteractionConfigurationChanged()
}

public fun clearFocused() {
  focusedSlot = null
  onInteractionConfigurationChanged()
}

public fun clearPressed() {
  pressedSlot = null
  onInteractionConfigurationChanged()
}

public fun clearInteractions() {
  hoveredSlot = null
  focusedSlot = null
  pressedSlot = null
  onInteractionConfigurationChanged()
}

private fun setHovered(response: GlassInteractionResponse) {
  hoveredSlot = GlassInteractionSlot(++nextInteractionRevision, response)
  onInteractionConfigurationChanged()
}

private fun setFocused(response: GlassInteractionResponse) {
  focusedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
  onInteractionConfigurationChanged()
}

private fun setPressed(response: GlassInteractionResponse) {
  pressedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
  onInteractionConfigurationChanged()
}
```

Add direct properties with logged setters:

```kotlin
public var interactionSource: InteractionSource? by mutableStateOf(null)
public var interactionLightRadiusFraction: Float by mutableStateOf(
  GlassDefaults.interactionLightRadiusFraction,
)
public var interactionTransformTarget: GlassTransformTarget by mutableStateOf(
  GlassTransformTarget.MaterialOnly,
)
public var interactionTransformPivot: GlassTransformPivot by mutableStateOf(
  GlassTransformPivot.Pointer,
)
public var interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> by mutableStateOf(
  GlassDefaults.positionAnimationSpec,
)
public var interactionReducedMotionPolicy: GlassReducedMotionPolicy by mutableStateOf(
  GlassReducedMotionPolicy.System,
)
```

Replace delegated setters with explicit setters so `interactionLightRadiusFraction` rejects non-finite values outside `0f..2f` and every property logs through `HazeLogger.d(TAG)`. Implement configuration notification without an attached context:

```kotlin
private var interactionConfigurationVersion by mutableIntStateOf(0)

private fun onInteractionConfigurationChanged() {
  interactionConfigurationVersion++
}
```

Read `interactionConfigurationVersion` in `update` so the node observes it. Task 3 uses that update
to retarget the controller; direct slot fields remain the retained source of truth.

Copy all slots and shared properties in the copy constructor. Copy compiled responses, not a controller or live jobs.

- [ ] **Step 7: Run the DSL tests**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionDslTest' --tests 'dev.chrisbanes.haze.glass.GlassVisualEffectOverrideTest'
```

Expected: all tests pass; an untouched effect has three null slots and does not observe pointers.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml haze-glass/build.gradle.kts haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteraction.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassDefaults.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionDslTest.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectOverrideTest.kt
git commit -m "Add opt-in glass interaction DSL"
```

---

### Task 3: Resolve state ownership and animate interaction values

**Files:**

- Create: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteractionController.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectLifecycleTest.kt`

**Interfaces:**

- Consumes: the three `GlassInteractionSlot` instances from Task 2 and the effect-node `CoroutineScope`.
- Produces: `GlassInteractionRenderState` with animated lighting, optics, scale, and position for both rendering paths.

Use `Animatable` here because the motion is pointer-driven, interruptible, and owned outside
composition. Composition-owned `animate*AsState` or `rememberTransition` would put frame-rate state
in the wrong lifecycle and cannot express per-property owner replacement.

- [ ] **Step 1: Write failing pure resolver tests**

Start `GlassInteractionControllerTest.kt` with direct resolver tests:

```kotlin
@Test
fun resolver_usesFixedPrecedencePerProperty() {
  val slots = testSlots(
    focused = response {
      lightingIntensity(0.2f)
      scale(0.99f)
    },
    hovered = response {
      lightingIntensity(0.4f)
      refractionMultiplier(1.03f)
    },
    pressed = response {
      scale(0.96f)
    },
  )

  val result = resolveGlassInteractionTargets(
    slots = slots,
    signals = GlassInteractionSignals(
      rawHovered = true,
      sourceFocused = true,
      rawPressed = true,
    ),
  )

  assertThat(result.lightingIntensity.value).isEqualTo(0.4f)
  assertThat(result.refractionMultiplier.value).isEqualTo(1.03f)
  assertThat(result.scaleX.value).isEqualTo(0.96f)
  assertThat(result.scaleY.value).isEqualTo(0.96f)
  assertThat(result.whitePointDelta.value).isEqualTo(0f)
}

@Test
fun resolver_customScaleOnlyPressRetainsHoverLightingAndOptics() {
  val effect = GlassVisualEffect().apply {
    hovered()
    pressed { scale(0.97f) }
  }

  val result = resolveGlassInteractionTargets(
    slots = effect.interactionSlots,
    signals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
  )

  assertThat(result.lightingIntensity.value).isEqualTo(0.35f)
  assertThat(result.refractionMultiplier.value).isEqualTo(1.02f)
  assertThat(result.whitePointDelta.value).isEqualTo(0.01f)
  assertThat(result.scaleX.value).isEqualTo(0.97f)
}

@Test
fun resolver_hiddenStateChangeDoesNotChangeOwner() {
  val effect = GlassVisualEffect().apply {
    hovered { lightingIntensity(0.4f) }
    pressed { lightingIntensity(0.8f) }
  }

  val pressedOnly = resolveGlassInteractionTargets(
    effect.interactionSlots,
    GlassInteractionSignals(rawPressed = true),
  )
  val pressedAndHovered = resolveGlassInteractionTargets(
    effect.interactionSlots,
    GlassInteractionSignals(rawHovered = true, rawPressed = true),
  )

  assertThat(pressedAndHovered.lightingIntensity.owner)
    .isEqualTo(pressedOnly.lightingIntensity.owner)
}

@Test
fun transitionSpec_usesEnteringToSpecAndDepartingFromSpec() {
  val hoverTo = tween<Float>(100)
  val hoverFrom = tween<Float>(200)
  val pressTo = tween<Float>(300)
  val pressFrom = tween<Float>(400)
  val effect = GlassVisualEffect().apply {
    hovered { animate(hoverTo, hoverFrom) { scale(0.99f) } }
    pressed { animate(pressTo, pressFrom) { scale(0.96f) } }
  }
  val idle = resolveGlassInteractionTargets(effect.interactionSlots, GlassInteractionSignals())
  val hover = resolveGlassInteractionTargets(
    effect.interactionSlots,
    GlassInteractionSignals(rawHovered = true),
  )
  val press = resolveGlassInteractionTargets(
    effect.interactionSlots,
    GlassInteractionSignals(rawHovered = true, rawPressed = true),
  )

  assertThat(
    selectTransitionSpec(
      previous = idle.scaleX,
      next = hover.scaleX,
      previousSlots = GlassInteractionSlots(),
      nextSlots = effect.interactionSlots,
      previousSignals = GlassInteractionSignals(),
      nextSignals = GlassInteractionSignals(rawHovered = true),
    ),
  ).isSameInstanceAs(hoverTo)
  assertThat(
    selectTransitionSpec(
      previous = hover.scaleX,
      next = press.scaleX,
      previousSlots = effect.interactionSlots,
      nextSlots = effect.interactionSlots,
      previousSignals = GlassInteractionSignals(rawHovered = true),
      nextSignals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
    ),
  ).isSameInstanceAs(pressTo)
  assertThat(
    selectTransitionSpec(
      previous = press.scaleX,
      next = hover.scaleX,
      previousSlots = effect.interactionSlots,
      nextSlots = effect.interactionSlots,
      previousSignals = GlassInteractionSignals(rawHovered = true, rawPressed = true),
      nextSignals = GlassInteractionSignals(rawHovered = true),
    ),
  ).isSameInstanceAs(pressFrom)
}
```

- [ ] **Step 2: Run the resolver tests and confirm failure**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest'
```

Expected: compilation fails because the signal, target, and resolver types do not exist.

- [ ] **Step 3: Implement fixed-order per-property resolution**

Create these core types in `GlassInteractionController.kt`:

```kotlin
internal data class GlassInteractionSlots(
  val focused: GlassInteractionSlot? = null,
  val hovered: GlassInteractionSlot? = null,
  val pressed: GlassInteractionSlot? = null,
)

internal enum class GlassInteractionState {
  Focused,
  Hovered,
  Pressed,
}

internal data class GlassInteractionSignals(
  val rawHovered: Boolean = false,
  val sourceHovered: Boolean = false,
  val sourceFocused: Boolean = false,
  val rawPressed: Boolean = false,
  val sourcePressed: Boolean = false,
) {
  val hovered: Boolean get() = rawHovered || sourceHovered
  val focused: Boolean get() = sourceFocused
  val pressed: Boolean get() = rawPressed || sourcePressed

  fun isActive(state: GlassInteractionState): Boolean = when (state) {
    GlassInteractionState.Focused -> focused
    GlassInteractionState.Hovered -> hovered
    GlassInteractionState.Pressed -> pressed
  }
}

internal data class OwnedGlassResponseValue(
  val state: GlassInteractionState?,
  val owner: GlassInteractionSlot?,
  val declaration: GlassResponseValue?,
  val identity: Float,
) {
  val value: Float get() = declaration?.value ?: identity
}

internal data class GlassInteractionTargets(
  val lightingIntensity: OwnedGlassResponseValue,
  val refractionMultiplier: OwnedGlassResponseValue,
  val whitePointDelta: OwnedGlassResponseValue,
  val scaleX: OwnedGlassResponseValue,
  val scaleY: OwnedGlassResponseValue,
)

internal fun resolveGlassInteractionTargets(
  slots: GlassInteractionSlots,
  signals: GlassInteractionSignals,
): GlassInteractionTargets {
  fun resolve(
    identity: Float,
    property: (GlassInteractionResponse) -> GlassResponseValue?,
  ): OwnedGlassResponseValue {
    var result = OwnedGlassResponseValue(null, null, null, identity)
    fun apply(
      state: GlassInteractionState,
      active: Boolean,
      slot: GlassInteractionSlot?,
    ) {
      if (!active || slot == null) return
      property(slot.response)?.let { declaration ->
        result = OwnedGlassResponseValue(state, slot, declaration, identity)
      }
    }
    apply(GlassInteractionState.Focused, signals.focused, slots.focused)
    apply(GlassInteractionState.Hovered, signals.hovered, slots.hovered)
    apply(GlassInteractionState.Pressed, signals.pressed, slots.pressed)
    return result
  }

  return GlassInteractionTargets(
    lightingIntensity = resolve(0f, GlassInteractionResponse::lightingIntensity),
    refractionMultiplier = resolve(1f, GlassInteractionResponse::refractionMultiplier),
    whitePointDelta = resolve(0f, GlassInteractionResponse::whitePointDelta),
    scaleX = resolve(1f, GlassInteractionResponse::scaleX),
    scaleY = resolve(1f, GlassInteractionResponse::scaleY),
  )
}

internal fun selectTransitionSpec(
  previous: OwnedGlassResponseValue,
  next: OwnedGlassResponseValue,
  previousSlots: GlassInteractionSlots,
  nextSlots: GlassInteractionSlots,
  previousSignals: GlassInteractionSignals,
  nextSignals: GlassInteractionSignals,
): FiniteAnimationSpec<Float>? {
  if (previous == next) return null

  fun GlassInteractionSlots.slot(state: GlassInteractionState?): GlassInteractionSlot? =
    when (state) {
      GlassInteractionState.Focused -> focused
      GlassInteractionState.Hovered -> hovered
      GlassInteractionState.Pressed -> pressed
      null -> null
    }

  val nextStateEntered = next.state != null &&
    !previousSignals.isActive(next.state) &&
    nextSignals.isActive(next.state)
  val nextResponseIsNew = next.owner != null &&
    previousSlots.slot(next.state)?.revision != next.owner.revision &&
    nextSlots.slot(next.state)?.revision == next.owner.revision

  return when {
    nextStateEntered || nextResponseIsNew -> next.declaration?.toSpec
    previous.declaration != null -> previous.declaration.fromSpec
    else -> next.declaration?.toSpec
  }
}
```

Expose a read-only `interactionSlots` property on `GlassVisualEffect` that constructs `GlassInteractionSlots`. Do not expose reusable public response values.

- [ ] **Step 4: Add failing animation and reduced-behavior tests**

Add controller tests through a real attached haze node so `Animatable` inherits the node's frame
clock and motion-duration context:

```kotlin
@Test
fun controller_animatesFromCurrentValueWithSelectedSpec() = runComposeUiTest {
  val effect = GlassVisualEffect().apply {
    pressed {
      animate(tween(100), tween(200)) {
        lightingIntensity(1f)
        scale(0.9f)
      }
    }
  }
  setContent {
    Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
  }
  waitForIdle()
  val controller = checkNotNull(effect.interactionControllerForTest)

  controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
  controller.updateSignals(GlassInteractionSignals(rawPressed = true))
  mainClock.advanceTimeBy(50)

  assertThat(controller.renderState.lightingIntensity).isBetween(0f, 1f)
  assertThat(controller.renderState.scaleX).isBetween(0.9f, 1f)
}

@Test
fun controller_reducedMotionSnapsLightingAndOpticsButKeepsIdentityTransform() =
  runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.2f)
        whitePointDelta(0.2f)
        scale(0.9f)
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
    }
    waitForIdle()
    val controller = checkNotNull(effect.interactionControllerForTest)

    controller.updateConfiguration(effect.controllerConfiguration(systemMotionScale = 1f))
    controller.updateSignals(GlassInteractionSignals(rawPressed = true))
    waitForIdle()

    assertThat(controller.renderState).isEqualTo(
      GlassInteractionRenderState(
        position = Offset(50f, 50f),
        lightingIntensity = 1f,
        refractionMultiplier = 1.2f,
        whitePointDelta = 0.2f,
        scaleX = 1f,
        scaleY = 1f,
      ),
    )
  }
```

Add three configuration-transition tests: replacing an active response uses the replacement's
`toSpec`; clearing an active pressed slot while hover remains configured uses the removed press
response's `fromSpec`; and clearing the final slot disposes the controller and exposes identity on
the next read. Add one declaration outside `animate` and assert it snaps immediately.

- [ ] **Step 5: Implement the retained animated controller**

Add the render and configuration values:

```kotlin
internal data class GlassInteractionRenderState(
  val position: Offset,
  val lightingIntensity: Float = 0f,
  val refractionMultiplier: Float = 1f,
  val whitePointDelta: Float = 0f,
  val scaleX: Float = 1f,
  val scaleY: Float = 1f,
) {
  val hasLighting: Boolean get() = lightingIntensity > 0f
  val hasOptics: Boolean get() = refractionMultiplier != 1f || whitePointDelta != 0f
  val hasTransform: Boolean get() = scaleX != 1f || scaleY != 1f
}

internal data class GlassInteractionControllerConfiguration(
  val slots: GlassInteractionSlots,
  val positionAnimationSpec: FiniteAnimationSpec<Offset>,
  val reducedMotion: Boolean,
  val forceFullMotion: Boolean,
)
```

Build that value in `GlassVisualEffect`:

```kotlin
internal fun controllerConfiguration(
  systemMotionScale: Float,
): GlassInteractionControllerConfiguration {
  val (reduced, forceFull) = reducedMotion(
    policy = interactionReducedMotionPolicy,
    systemScale = systemMotionScale,
  )
  return GlassInteractionControllerConfiguration(
    slots = interactionSlots,
    positionAnimationSpec = interactionPositionAnimationSpec,
    reducedMotion = reduced,
    forceFullMotion = forceFull,
  )
}

private fun systemMotionScale(context: VisualEffectContext): Float {
  return context.currentValueOf(LocalMotionDurationScale).scaleFactor
}

private fun reducedMotion(
  policy: GlassReducedMotionPolicy,
  systemScale: Float,
): Pair<Boolean, Boolean> = when (policy) {
  GlassReducedMotionPolicy.System -> (systemScale == 0f) to false
  GlassReducedMotionPolicy.Reduced -> true to false
  GlassReducedMotionPolicy.Full -> false to true
}
```

Task 4 adds the source collector and verifies all three policies through composition-local values.

Implement one `AnimatedFloatChannel` per response property. Each channel owns one `Animatable<Float, AnimationVector1D>`, its current `OwnedGlassResponseValue`, and its current `Job`:

```kotlin
private class AnimatedFloatChannel(
  identity: Float,
  private val scope: CoroutineScope,
  private val invalidateDraw: () -> Unit,
) {
  private val value = Animatable(identity)
  private var owner = OwnedGlassResponseValue(null, null, null, identity)
  private var job: Job? = null

  val currentValue: Float get() = value.value

  fun retarget(
    target: OwnedGlassResponseValue,
    previousSlots: GlassInteractionSlots,
    nextSlots: GlassInteractionSlots,
    previousSignals: GlassInteractionSignals,
    nextSignals: GlassInteractionSignals,
    reducedMotion: Boolean,
    forceFullMotion: Boolean,
  ) {
    if (target == owner) return
    val previous = owner
    owner = target
    val spec = selectTransitionSpec(
      previous = previous,
      next = target,
      previousSlots = previousSlots,
      nextSlots = nextSlots,
      previousSignals = previousSignals,
      nextSignals = nextSignals,
    )
    job?.cancel()
    job = scope.launch(if (forceFullMotion) FullMotionDurationScale else EmptyCoroutineContext) {
      if (reducedMotion || spec == null) {
        value.snapTo(target.value)
        invalidateDraw()
      } else {
        value.animateTo(target.value, spec) {
          invalidateDraw()
        }
      }
    }
  }

  fun cancel() {
    job?.cancel()
    job = null
  }
}

private object FullMotionDurationScale : MotionDurationScale {
  override val scaleFactor: Float = 1f
}
```

`GlassInteractionController` owns five channels and an
`Animatable(Offset.Zero, Offset.VectorConverter)` for position. It retains the previous slots and
signals. `updateConfiguration` and `updateSignals` both call one `retarget()` method that resolves
targets once and passes the previous/next slots and signals to every channel. This makes an entering
press use the press `toSpec`, while an exiting press uses the press `fromSpec` to reveal an already
active hover response. Position uses `positionAnimationSpec`, snaps when reduced, and invalidates
each frame. Its `renderState` reads current channel values; when reduced motion is active it always
returns `1f` for both scales.

`dispose()` must cancel the source collector, all channel jobs, and position animation. The effect discards the controller when the last slot is cleared, so its next draw reads `GlassInteractionRenderState` identity without awaiting a coroutine.

- [ ] **Step 6: Create and destroy the controller from effect lifecycle**

In `GlassVisualEffect`:

```kotlin
private var attachedContext: VisualEffectContext? = null
private var interactionController: GlassInteractionController? = null

override fun attach(context: VisualEffectContext) {
  if (!isAttached) {
    isAttached = true
    attachedContext = context
    syncInteractionController(context)
    delegate.attach()
  }
}

override fun detach(context: VisualEffectContext) {
  if (isAttached) {
    interactionController?.dispose()
    interactionController = null
    attachedContext = null
    isAttached = false
    delegate.detach()
  }
}

private fun syncInteractionController(context: VisualEffectContext) {
  if (hoveredSlot == null && focusedSlot == null && pressedSlot == null) {
    interactionController?.dispose()
    interactionController = null
    context.invalidateDraw()
    return
  }
  val controller = interactionController ?: GlassInteractionController(context).also {
    interactionController = it
  }
  controller.updateConfiguration(controllerConfiguration(systemMotionScale(context)))
}
```

Add internal read-only test seams; they are excluded from the Metalava public surface:

```kotlin
internal val interactionControllerForTest: GlassInteractionController?
  get() = interactionController

internal val attachedContextForTest: VisualEffectContext?
  get() = attachedContext
```

Call `syncInteractionController(context)` from `update` after reading the composition-local motion scale. Ensure a never-configured effect does not allocate a controller during attach or update.

- [ ] **Step 7: Run controller and lifecycle tests**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest' --tests 'dev.chrisbanes.haze.glass.GlassVisualEffectLifecycleTest'
```

Expected: resolver, animation, reduced behavior, no-allocation, and dispose tests pass.

- [ ] **Step 8: Commit**

```bash
git add haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteractionController.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectLifecycleTest.kt
git commit -m "Animate glass interaction responses"
```

---

### Task 4: Merge raw pointer and `InteractionSource` signals

**Files:**

- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteractionController.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt`

**Interfaces:**

- Consumes: `PointerEvent` forwarded by Task 1 and optional Foundation `InteractionSource.interactions`.
- Produces: independent raw/source hover, focus, and press signals plus a validated primary pointer position.

- [ ] **Step 1: Add failing raw-input and source-merging tests**

Add Compose UI tests for:

```kotlin
@Test
fun rawInput_usesFirstPointerAndRetainsLastPositionThroughRelease() = runComposeUiTest {
  val effect = GlassVisualEffect().apply {
    pressed()
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Box(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect { visualEffect = effect },
    )
  }

  onNodeWithTag("glass").performMultiModalInput {
    touch {
      down(0, Offset(20f, 30f))
      down(1, Offset(80f, 70f))
      moveTo(1, Offset(90f, 90f))
      up(1)
      up(0)
    }
  }
  assertThat(effect.currentInteractionState.position).isEqualTo(Offset(20f, 30f))
}

@Test
fun rawAndSourcePress_mergeWithoutDoubleStrengthOrPrematureRelease() = runComposeUiTest {
  val source = MutableInteractionSource()
  val press = PressInteraction.Press(Offset(60f, 40f))
  val effect = GlassVisualEffect().apply {
    pressed()
    interactionSource = source
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Box(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect { visualEffect = effect },
    )
  }

  source.tryEmit(press)
  onNodeWithTag("glass").performTouchInput {
    down(Offset(20f, 20f))
    up()
  }
  waitForIdle()
  assertThat(effect.currentInteractionState.lightingIntensity).isEqualTo(1f)

  source.tryEmit(PressInteraction.Release(press))
  waitForIdle()
  assertThat(effect.currentInteractionState.lightingIntensity).isEqualTo(0f)
}

@Test
fun focusAndSourceOnlyPress_useNodeCenter() = runComposeUiTest {
  val source = MutableInteractionSource()
  val focus = FocusInteraction.Focus()
  val press = PressInteraction.Press(Offset.Unspecified)
  val effect = GlassVisualEffect().apply {
    focused()
    pressed()
    interactionSource = source
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Box(Modifier.size(100.dp).hazeEffect { visualEffect = effect })
  }

  source.tryEmit(focus)
  source.tryEmit(press)
  waitForIdle()

  assertThat(effect.currentInteractionState.position).isEqualTo(Offset(50f, 50f))
  assertThat(effect.currentInteractionState.lightingIntensity).isEqualTo(1f)
}
```

Also add a separate primary-pointer test that moves from `Offset(20f, 20f)` to
`Offset(120f, -10f)`, releases in the same injection block, and asserts the retained position is
`Offset(100f, 0f)`. Add tests for mouse/stylus enter-move-exit, consumed primary-pointer movement
cancelling only the raw press, non-finite positions falling back to the last valid position,
zero-size geometry ignoring positions, source replacement cancelling the old collector, and
pointer cancellation clearing raw signals. Add policy tests proving `System` snaps when the local
duration scale is zero, `System` animates when it is nonzero, and `Full` animates even when the
local duration scale is zero.

- [ ] **Step 2: Run the tests and confirm signal handling is absent**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest'
```

Expected: the new tests fail because pointer/source signals are not yet fed into the controller.

- [ ] **Step 3: Implement primary-pointer and hover reduction**

Add to `GlassInteractionController`:

```kotlin
private var primaryPointerId: PointerId? = null
private var rawHovered = false
private var rawPressed = false
private var rawPosition: Offset? = null
private var hoverPosition: Offset? = null

fun onPointerEvent(event: PointerEvent, size: Size) {
  if (!size.isDrawable()) return

  val primaryChange = primaryPointerId?.let { id ->
    event.changes.firstOrNull { it.id == id }
  }
  if (primaryChange?.isConsumed == true && primaryChange.positionChanged()) {
    cancelRawPress()
  }

  if (event.type == PointerEventType.Enter || event.type == PointerEventType.Move) {
    event.changes
      .lastOrNull { it.type == PointerType.Mouse || it.type == PointerType.Stylus }
      ?.position
      ?.validOrNull()
      ?.let { position ->
        rawHovered = position.x in 0f..size.width && position.y in 0f..size.height
        if (rawHovered) hoverPosition = position
      }
  }
  if (event.type == PointerEventType.Exit) {
    rawHovered = false
  }

  if (primaryPointerId == null) {
    event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }?.let { change ->
      primaryPointerId = change.id
      rawPressed = true
      rawPosition = change.position.validOrNull()?.clampTo(size)
    }
  } else {
    event.changes.firstOrNull { it.id == primaryPointerId }?.let { change ->
      change.position.validOrNull()?.let { rawPosition = it.clampTo(size) }
      if (change.changedToUpIgnoreConsumed()) {
        primaryPointerId = null
        rawPressed = false
      }
    }
  }

  updateSignalsAndPosition(size)
}

fun cancelPointerInput(size: Size) {
  rawHovered = false
  cancelRawPress()
  updateSignalsAndPosition(size)
}

private fun cancelRawPress() {
  primaryPointerId = null
  rawPressed = false
}

private fun Offset.validOrNull(): Offset? =
  takeIf { x.isFinite() && y.isFinite() }

private fun Offset.clampTo(size: Size): Offset = Offset(
  x = x.coerceIn(0f, size.width),
  y = y.coerceIn(0f, size.height),
)

private fun Size.isDrawable(): Boolean =
  width.isFinite() && height.isFinite() && width > 0f && height > 0f
```

Keep the last valid `rawPosition` after up. Ignore every secondary pointer until the primary ends or is cancelled.

Expose deterministic internal test hooks without changing the public API:

```kotlin
internal val currentSignals: GlassInteractionSignals
  get() = GlassInteractionSignals(
    rawHovered = rawHovered,
    sourceHovered = sourceHovers.isNotEmpty(),
    sourceFocused = sourceFocuses.isNotEmpty(),
    rawPressed = rawPressed,
    sourcePressed = sourcePresses.isNotEmpty(),
  )

internal fun setRawPressedForTest(pressed: Boolean, position: Offset, size: Size) {
  rawPressed = pressed
  primaryPointerId = null
  position.validOrNull()?.let { rawPosition = it.clampTo(size) }
  updateSignalsAndPosition(size)
}
```

- [ ] **Step 4: Collect `InteractionSource` with independent sets**

Add source state and replacement:

```kotlin
private var interactionSource: InteractionSource? = null
private var sourceJob: Job? = null
private val sourcePresses = mutableSetOf<PressInteraction.Press>()
private val sourceHovers = mutableSetOf<HoverInteraction.Enter>()
private val sourceFocuses = mutableSetOf<FocusInteraction.Focus>()
private var sourcePosition: Offset? = null

fun updateInteractionSource(source: InteractionSource?, size: Size) {
  if (source === interactionSource) return
  sourceJob?.cancel()
  sourceJob = null
  interactionSource = source
  sourcePresses.clear()
  sourceHovers.clear()
  sourceFocuses.clear()
  sourcePosition = null
  updateSignalsAndPosition(size)

  if (source != null) {
    sourceJob = scope.launch {
      source.interactions.collect { interaction ->
        when (interaction) {
          is PressInteraction.Press -> {
            sourcePresses += interaction
            sourcePosition = interaction.pressPosition.validOrNull()
          }
          is PressInteraction.Release -> sourcePresses -= interaction.press
          is PressInteraction.Cancel -> sourcePresses -= interaction.press
          is HoverInteraction.Enter -> sourceHovers += interaction
          is HoverInteraction.Exit -> sourceHovers -= interaction.enter
          is FocusInteraction.Focus -> sourceFocuses += interaction
          is FocusInteraction.Unfocus -> sourceFocuses -= interaction.focus
        }
        updateSignalsAndPosition(sizeProvider())
      }
    }
  }
}
```

`updateSignalsAndPosition` constructs `GlassInteractionSignals` with independent booleans, then chooses position in this order: active raw pointer, latest in-bounds hover, specified source press, last valid raw position, node center. A focus-only response never reads raw input because Task 1 does not install a pointer delegate for it.

- [ ] **Step 5: Wire the effect and reduced-motion policy**

Change the class declaration to implement `InteractiveVisualEffect`, change the existing internal
`observesPointerEvents` property to `override val`, and forward the capability methods:

```kotlin
override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
  interactionController?.onPointerEvent(event, context.size)
}

override fun onCancelPointerInput(context: VisualEffectContext) {
  interactionController?.cancelPointerInput(context.size)
}

internal val currentInteractionSignals: GlassInteractionSignals
  get() = interactionController?.currentSignals ?: GlassInteractionSignals()

internal fun setPressedForTest(position: Offset, pressed: Boolean = true) {
  val context = attachedContext ?: return
  interactionController?.setRawPressedForTest(pressed, position, context.size)
}
```

During `update`, read `LocalMotionDurationScale.current.scaleFactor` through
`VisualEffectContext.currentValueOf` and pass it to the `controllerConfiguration` mapping created in
Task 3. System values greater than zero remain in the coroutine context so Compose scales
configured specs normally; `Full` adds `FullMotionDurationScale` to animation jobs.

- [ ] **Step 6: Run input/controller tests**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest'
```

Expected: all pointer, source, geometry, merging, replacement, cancellation, and reduced-motion tests pass.

- [ ] **Step 7: Commit**

```bash
git add haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassInteractionController.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt
git commit -m "Observe glass interaction signals"
```

---

### Task 5: Apply transforms and fallback interaction lighting

**Files:**

- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/FallbackGlassDelegate.kt`
- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/FallbackGlassInteractionTest.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt`

**Interfaces:**

- Consumes: `GlassInteractionRenderState` from Task 3.
- Produces: material-only drawing inside `GlassVisualEffect`, material-plus-content transforms through Task 1, and localized fallback lighting.

- [ ] **Step 1: Add failing transform-target and pivot tests**

Add these deterministic tests with reduced motion:

```kotlin
@Test
fun materialOnlyTransform_isNotExposedToHazeNode() = runComposeUiTest {
  val effect = GlassVisualEffect().apply {
    pressed { scale(0.9f, 0.8f) }
    interactionTransformTarget = GlassTransformTarget.MaterialOnly
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Box(Modifier.size(100.dp, 80.dp).hazeEffect { visualEffect = effect })
  }
  waitForIdle()
  effect.setPressedForTest(position = Offset(20f, 30f))
  waitForIdle()
  val context = checkNotNull(effect.attachedContextForTest)

  assertThat(effect.currentContentTransform(context))
    .isEqualTo(VisualEffectTransform.Identity)
  assertThat(effect.currentMaterialTransform(context))
    .isEqualTo(VisualEffectTransform(0.9f, 0.8f, Offset(20f, 30f)))
}

@Test
fun materialAndContentTransform_usesConfiguredPivot() = runComposeUiTest {
  val effect = GlassVisualEffect().apply {
    pressed { scale(0.9f) }
    interactionTransformTarget = GlassTransformTarget.MaterialAndContent
    interactionTransformPivot = GlassTransformPivot.Center
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Box(Modifier.size(100.dp, 80.dp).hazeEffect { visualEffect = effect })
  }
  waitForIdle()
  effect.setPressedForTest(position = Offset(20f, 30f))
  waitForIdle()
  val context = checkNotNull(effect.attachedContextForTest)

  assertThat(effect.currentContentTransform(context))
    .isEqualTo(VisualEffectTransform(0.9f, 0.9f, context.size.center))
  assertThat(effect.currentMaterialTransform(context))
    .isEqualTo(VisualEffectTransform.Identity)
}

@Test
fun invalidGeometry_returnsIdentityTransform() = runComposeUiTest {
  val effect = interactiveScaledEffect()
  setContent {
    Box(Modifier.size(0.dp).hazeEffect { visualEffect = effect })
  }
  waitForIdle()
  effect.setPressedForTest(position = Offset.Zero)
  waitForIdle()
  val context = checkNotNull(effect.attachedContextForTest)

  assertThat(effect.currentContentTransform(context))
    .isEqualTo(VisualEffectTransform.Identity)
}
```

- [ ] **Step 2: Run transform tests and confirm failure**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest'
```

Expected: tests fail because the effect does not yet expose or apply resolved transforms.

- [ ] **Step 3: Resolve transform target and pivot**

Add these helpers to `GlassVisualEffect`:

```kotlin
internal val currentInteractionState: GlassInteractionRenderState
  get() = interactionController?.renderState ?: GlassInteractionRenderState(Offset.Zero)

private fun resolveTransform(
  context: VisualEffectContext,
  target: GlassTransformTarget,
): VisualEffectTransform {
  if (interactionTransformTarget != target) return VisualEffectTransform.Identity
  val state = currentInteractionState
  val size = context.size
  if (!state.hasTransform || !size.isDrawable()) return VisualEffectTransform.Identity
  val pivot = when (interactionTransformPivot) {
    GlassTransformPivot.Pointer -> state.position.clampTo(size)
    GlassTransformPivot.Center -> size.center
  }
  return VisualEffectTransform(state.scaleX, state.scaleY, pivot)
}

override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform {
  return resolveTransform(context, GlassTransformTarget.MaterialAndContent)
}

internal fun currentMaterialTransform(context: VisualEffectContext): VisualEffectTransform {
  return resolveTransform(context, GlassTransformTarget.MaterialOnly)
}
```

Wrap the delegate's `draw` and `drawForeground` calls with the same material transform:

```kotlin
private inline fun DrawScope.withMaterialTransform(
  context: VisualEffectContext,
  block: DrawScope.() -> Unit,
) {
  val transform = currentMaterialTransform(context)
  if (transform == VisualEffectTransform.Identity) {
    block()
  } else {
    scale(transform.scaleX, transform.scaleY, transform.pivot, block)
  }
}
```

Do not transform `prepareDraw`, source capture, layer geometry, or cache keys.

- [ ] **Step 4: Write a failing fallback-lighting pixel test**

Create `FallbackGlassInteractionTest.kt`. Render a forced-fallback effect at `Offset(20f, 20f)` with reduced motion, then compare equal-sized regions around the pointer and the opposite corner:

```kotlin
@Test
fun fallback_pressedLighting_isLocalizedAtPointer() = runComposeUiTest {
  val effect = GlassVisualEffect().apply {
    pressed { lightingIntensity(1f) }
    interactionLightRadiusFraction = 0.4f
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
  }
  setContent {
    Box(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect {
          visualEffect = effect
        },
    )
  }

  onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
  waitForIdle()
  val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()

  assertThat(pixels[20, 20].luminance()).isGreaterThan(pixels[80, 80].luminance())
}
```

Select `FallbackGlassDelegate` directly in the test through the existing internal delegate seam so the assertion does not depend on the host's runtime-shader support.

- [ ] **Step 5: Draw localized fallback lighting**

Keep the existing base tint, highlight, and edge passes unchanged. After them, draw the interaction highlight only when `state.hasLighting`:

```kotlin
private fun DrawScope.drawInteractionLighting(
  effect: GlassVisualEffect,
  state: GlassInteractionRenderState,
  shapePath: Path?,
) {
  if (!state.hasLighting) return
  val center = state.position
  val radius = size.minDimension * effect.interactionLightRadiusFraction
  if (radius <= 0f) return
  val brush = Brush.radialGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.32f * state.lightingIntensity),
      Color.Transparent,
    ),
    center = center,
    radius = radius,
  )
  val drawHighlight: DrawScope.() -> Unit = {
    drawCircle(brush = brush, center = center, radius = radius)
  }
  if (shapePath != null) {
    clipPath(shapePath, block = drawHighlight)
  } else {
    drawHighlight()
  }
}
```

Call it inside the existing `withAlpha` block. Do not read interaction refraction or white-point values in the fallback delegate.

- [ ] **Step 6: Run fallback and controller tests**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.FallbackGlassInteractionTest' --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest'
```

Expected: material-only and material-plus-content transforms, center/pointer pivots, invalid geometry, and localized fallback lighting pass.

- [ ] **Step 7: Commit**

```bash
git add haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/FallbackGlassDelegate.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/FallbackGlassInteractionTest.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionControllerTest.kt
git commit -m "Render glass interaction transforms"
```

---

### Task 6: Add bypassable runtime-shader interaction stages

**Files:**

- Modify: `haze-utils/src/commonMain/kotlin/dev/chrisbanes/haze/RuntimeShader.kt`
- Modify: `haze-utils/src/androidMain/kotlin/dev/chrisbanes/haze/RuntimeShader.android.kt`
- Modify: `haze-utils/src/skikoMain/kotlin/dev/chrisbanes/haze/RenderEffect.skiko.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassLayers.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassShaders.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassShadersTest.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStageInvalidationTest.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectKeysTest.kt`
- Modify: `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegateIntegrationTest.kt`

**Interfaces:**

- Consumes: base `GlassRenderParams` and `GlassInteractionRenderState`.
- Produces: mutable-uniform optical, detail, and lighting effects whose dynamic values never alter base stage keys.

- [ ] **Step 1: Write failing key-isolation and shader-contract tests**

Add tests that construct the same base params with two different interaction states:

```kotlin
@Test
fun interactionValues_doNotChangeBaseStageKeys() {
  val params = testRenderParams()
  val idle = params.interactionUniforms(
    GlassInteractionRenderState(position = Offset(20f, 20f)),
    radiusFraction = 0.7f,
  )
  val pressed = params.interactionUniforms(
    GlassInteractionRenderState(
      position = Offset(80f, 60f),
      lightingIntensity = 1f,
      refractionMultiplier = 1.08f,
      whitePointDelta = 0.04f,
      scaleX = 0.98f,
      scaleY = 0.98f,
    ),
    radiusFraction = 0.7f,
  )

  assertThat(params.blurEffectKey()).isEqualTo(params.blurEffectKey())
  assertThat(params.opticalEffectKey()).isEqualTo(params.opticalEffectKey())
  assertThat(params.refractionDetailEffectKey()).isEqualTo(params.refractionDetailEffectKey())
  assertThat(params.rimEffectKey()).isEqualTo(params.rimEffectKey())
  assertThat(idle).isNotEqualTo(pressed)
}

@Test
fun interactionShader_usesLocalizedDynamicUniforms() {
  val optical = GlassShaders.buildOptical(interactive = true)
  val detail = GlassShaders.buildRefractionDetail(interactive = true)
  val lighting = GlassShaders.buildInteractionLighting()

  listOf(optical, detail, lighting).forEach { shader ->
    assertThat(shader).contains("uniform float2 interactionPosition;")
    assertThat(shader).contains("uniform float interactionRadius;")
    assertThat(shader).contains("interactionFalloff")
  }
  assertThat(optical).contains("uniform float interactionRefractionMultiplier;")
  assertThat(optical).contains("uniform float interactionWhitePointDelta;")
  assertThat(detail).contains("uniform float interactionRefractionMultiplier;")
}
```

Extend `GlassStageInvalidationTest` so changing only `GlassInteractionUniforms` produces no `source`, `blur`, `depth`, `optical`, `detail`, or `rim` invalidation in `calculateRequiredStageInvalidation`.

- [ ] **Step 2: Run rendering tests and confirm failure**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassShadersTest' --tests 'dev.chrisbanes.haze.glass.GlassStageInvalidationTest' --tests 'dev.chrisbanes.haze.glass.GlassRenderEffectKeysTest'
```

Expected: compilation fails because interaction uniform/key and shader variants are absent.

- [ ] **Step 3: Add a mutable-uniform runtime effect wrapper**

Add this common contract to `haze-utils`:

```kotlin
@InternalHazeApi
public interface MutableRuntimeShaderRenderEffect {
  public fun updateUniforms(
    uniforms: RuntimeShaderUniformProvider.() -> Unit,
  ): PlatformRenderEffect
}

@InternalHazeApi
public expect fun createMutableRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
): MutableRuntimeShaderRenderEffect
```

Android creates one `RuntimeShader` and one `RenderEffect` and mutates that shader on every call:

```kotlin
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AndroidMutableRuntimeShaderRenderEffect(
  effect: PlatformRuntimeEffect,
  shaderNames: Array<String>,
  inputs: Array<PlatformRenderEffect?>,
) : MutableRuntimeShaderRenderEffect {
  private val shader = RuntimeShader(effect.sksl)
  private val provider = AndroidRuntimeShaderUniformProvider(shader)
  private val renderEffect = createAndroidRuntimeShaderRenderEffect(shader, shaderNames, inputs)

  override fun updateUniforms(
    uniforms: RuntimeShaderUniformProvider.() -> Unit,
  ): PlatformRenderEffect {
    uniforms(provider)
    return renderEffect
  }
}
```

Extract the existing Android shader-name/input validation and effect chaining into `createAndroidRuntimeShaderRenderEffect` so both factory paths share it.

Skiko retains the compiled `RuntimeEffect`, names, and inputs but creates a fresh builder/filter for each update:

```kotlin
private class SkikoMutableRuntimeShaderRenderEffect(
  private val effect: RuntimeEffect,
  private val shaderNames: Array<String>,
  private val inputs: Array<ImageFilter?>,
) : MutableRuntimeShaderRenderEffect {
  override fun updateUniforms(
    uniforms: RuntimeShaderUniformProvider.() -> Unit,
  ): PlatformRenderEffect {
    val builder = RuntimeShaderBuilder(effect)
    uniforms(SkikoRuntimeShaderUniformProvider(builder))
    return ImageFilter.makeRuntimeShader(builder, shaderNames, inputs)
  }
}
```

- [ ] **Step 4: Define static keys and dynamic uniforms**

Add to `GlassRenderParams.kt`:

```kotlin
internal data class GlassInteractionOpticalKey(
  val base: GlassOpticalEffectKey,
)

internal data class GlassInteractionDetailKey(
  val base: GlassRefractionDetailEffectKey,
)

internal data class GlassInteractionLightingKey(
  val coordinates: GlassCoordinates,
  val edgeSoftnessPx: Float,
  val cornerRadii: CornerRadii,
)

internal data class GlassInteractionUniforms(
  val position: Offset,
  val radiusPx: Float,
  val lightingIntensity: Float,
  val refractionMultiplier: Float,
  val whitePointDelta: Float,
) {
  val hasLighting: Boolean get() = lightingIntensity > 0f && radiusPx > 0f
  val hasOptics: Boolean get() =
    radiusPx > 0f && (refractionMultiplier != 1f || whitePointDelta != 0f)
}

internal fun GlassRenderParams.interactionUniforms(
  state: GlassInteractionRenderState,
  radiusFraction: Float,
): GlassInteractionUniforms = GlassInteractionUniforms(
  position = state.position * coordinates.scaleFactor + coordinates.materialOrigin,
  radiusPx = coordinates.materialSize.minDimension * radiusFraction,
  lightingIntensity = state.lightingIntensity.coerceIn(0f, 1f),
  refractionMultiplier = state.refractionMultiplier.coerceIn(0f, 2f),
  whitePointDelta = state.whitePointDelta.coerceIn(-1f, 1f),
)
```

The static keys contain geometry and base material values only. Never add `GlassInteractionUniforms` to `GlassStageInputs` or the existing base keys.

- [ ] **Step 5: Add interaction shader variants**

Parameterize `buildOptical` and `buildRefractionDetail` with `interactive: Boolean = false`. The default output must remain byte-for-byte semantically equivalent. For the interactive variants add:

```glsl
uniform float2 interactionPosition;
uniform float interactionRadius;
uniform float interactionRefractionMultiplier;
uniform float interactionWhitePointDelta;

float interactionFalloff(vec2 coord) {
  float normalized = distance(coord, interactionPosition) / max(interactionRadius, 0.0001);
  return 1.0 - smootherstep(clamp(normalized, 0.0, 1.0));
}
```

In the optical main function compute:

```glsl
float interactionWeight = interactionFalloff(coord);
float localizedRefractionMultiplier =
  mix(1.0, interactionRefractionMultiplier, interactionWeight);
float localizedWhitePoint = clamp(
  whitePoint + interactionWhitePointDelta * interactionWeight,
  -1.0,
  1.0
);
```

Pass `localizedRefractionMultiplier` into `refractionDisplacement` and `localizedWhitePoint` into `applyColorGrading`. Change those helpers to accept the values explicitly; the non-interactive shader passes `1.0` and the base `whitePoint`.

Use the same falloff and multiplier in the detail shader's displacement calculation. Add a standalone lighting shader:

```glsl
uniform shader content;
uniform float2 materialOrigin;
uniform float2 materialSize;
uniform vec4 cornerRadii;
uniform float edgeSoftness;
uniform float2 interactionPosition;
uniform float interactionRadius;
uniform float interactionLightingIntensity;

vec4 main(vec2 coord) {
  vec2 localCoord = coord - materialOrigin;
  vec2 halfSize = materialSize * 0.5;
  vec2 centeredCoord = localCoord - halfSize;
  float radius = radiusAt(centeredCoord, cornerRadii);
  float sd = sdRoundedRect(centeredCoord, halfSize, radius);
  if (sd > 0.0) return vec4(0.0);
  float shapeMask = edgeSoftness <= 0.0
    ? 1.0
    : smootherstep(clamp(max(-sd, 0.0) / max(edgeSoftness, 0.0001), 0.0, 1.0));
  float light = interactionFalloff(coord) * interactionLightingIntensity * shapeMask;
  float contentAlpha = content.eval(coord).a;
  float alpha = light * 0.32 * contentAlpha;
  return vec4(vec3(alpha), alpha);
}
```

Reuse the existing SDF helpers. Record an opaque black rect into the lighting layer, so the child
sample's alpha is `1f` and keeps the Android content-shader binding live without changing the light
color.

- [ ] **Step 6: Add optional interaction layers**

Extend `GlassLayers` with `interactionOptical`, `interactionRefractionDetail`, and `interactionLighting` plus matching ensure/release helpers. Include them in `isEmpty` and `release`.

Add a single preparation method:

```kotlin
fun prepareInteraction(
  optics: Boolean,
  detail: Boolean,
  lighting: Boolean,
  graphicsContext: GraphicsContext,
) {
  if (optics) {
    interactionOptical = ensureLayer(interactionOptical, graphicsContext)
  } else {
    releaseLayer(interactionOptical, graphicsContext)
    interactionOptical = null
  }
  if (detail) {
    interactionRefractionDetail = ensureLayer(interactionRefractionDetail, graphicsContext)
  } else {
    releaseLayer(interactionRefractionDetail, graphicsContext)
    interactionRefractionDetail = null
  }
  if (lighting) {
    interactionLighting = ensureLayer(interactionLighting, graphicsContext)
  } else {
    releaseLayer(interactionLighting, graphicsContext)
    interactionLighting = null
  }
}
```

Make the file-level `releaseLayer` helper internal so this method can share it.

- [ ] **Step 7: Compose interaction stages over retained base inputs**

In `RuntimeShaderGlassDelegate`:

1. Build all existing base effects and `GlassStageInputs` exactly as before.
2. Resolve `GlassInteractionUniforms` after `buildRenderParams`.
3. If `hasOptics`, update/reuse mutable optical and detail effects and point the interaction layers at the already-retained `depthInput` and `source`.
4. If `hasLighting`, update/reuse the lighting effect and a layer containing a static black rect.
5. Draw interaction optical/detail instead of base optical/detail; draw interaction lighting in `drawForeground` before the existing rim.
6. When both flags are false, release or bypass every interaction layer and draw the existing retained output directly.

Cache each mutable wrapper by its static key:

```kotlin
private var interactionOpticalKey: GlassInteractionOpticalKey? = null
private var interactionOpticalEffect: MutableRuntimeShaderRenderEffect? = null

private fun updateInteractionOpticalEffect(
  key: GlassInteractionOpticalKey,
  uniforms: GlassInteractionUniforms,
): PlatformRenderEffect {
  if (key != interactionOpticalKey || interactionOpticalEffect == null) {
    interactionOpticalKey = key
    interactionOpticalEffect = createMutableRuntimeShaderRenderEffect(
      effect = GLASS_INTERACTION_OPTICAL_EFFECT,
      shaderNames = arrayOf("content"),
      inputs = arrayOf(null),
    )
  }
  return checkNotNull(interactionOpticalEffect).updateUniforms {
    setOpticalUniforms(key.base)
    setInteractionUniforms(uniforms)
  }
}
```

Apply the same pattern to detail and lighting. Track whether the retained input layer or static key changed; only then re-record the interaction graphics layer. A dynamic-uniform-only frame updates `renderEffect` and redraws without re-recording source, blur, depth, or base optical/detail.

Increment internal `baseOpticalEffectCreationCount` and `sourceRecordCount` counters only at their existing creation/record sites so the integration test can prove they remain stable during interaction frames.

- [ ] **Step 8: Add runtime integration assertions**

Extend `RuntimeShaderGlassDelegateIntegrationTest`:

```kotlin
@Test
fun interactionFrames_doNotRebuildBaseEffectsOrRecordSource() = runComposeUiTest {
  val effect = runtimeInteractiveEffect()
  setContent { RuntimeGlassTestContent(effect, tag = "glass") }
  waitForIdle()
  val delegate = effect.delegate as RuntimeShaderGlassDelegate
  val opticalBuilds = delegate.baseOpticalEffectCreationCount
  val sourceRecords = delegate.sourceRecordCount

  onNodeWithTag("glass").performTouchInput {
    down(Offset(20f, 20f))
    moveTo(Offset(80f, 60f))
  }
  mainClock.advanceTimeBy(500)

  assertThat(delegate.baseOpticalEffectCreationCount).isEqualTo(opticalBuilds)
  assertThat(delegate.sourceRecordCount).isEqualTo(sourceRecords)
  assertThat(delegate.interactionFrameCount).isGreaterThan(0)
}
```

Also assert the idle path leaves `interactionFrameCount` unchanged and does not allocate interaction layers before the first active signal.

- [ ] **Step 9: Run runtime rendering tests**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassShadersTest' --tests 'dev.chrisbanes.haze.glass.GlassStageInvalidationTest' --tests 'dev.chrisbanes.haze.glass.GlassRenderEffectKeysTest' --tests 'dev.chrisbanes.haze.glass.RuntimeShaderGlassDelegateIntegrationTest'
```

Expected: shader contracts, key isolation, idle bypass, and no-base-churn tests pass.

- [ ] **Step 10: Commit**

```bash
git add haze-utils/src/commonMain/kotlin/dev/chrisbanes/haze/RuntimeShader.kt haze-utils/src/androidMain/kotlin/dev/chrisbanes/haze/RuntimeShader.android.kt haze-utils/src/skikoMain/kotlin/dev/chrisbanes/haze/RenderEffect.skiko.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassLayers.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassShaders.kt haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassShadersTest.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassStageInvalidationTest.kt haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderEffectKeysTest.kt haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegateIntegrationTest.kt
git commit -m "Render dynamic glass interaction"
```

---

### Task 7: Prove input interoperability and visual behavior end to end

**Files:**

- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionIntegrationTest.kt`
- Create: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInteractionScreenshotTest.kt`
- Create: generated files under `haze-screenshot-tests/screenshots/android/`
- Create: generated files under `haze-screenshot-tests/screenshots/desktop/`

**Interfaces:**

- Consumes: the complete input, controller, fallback, transform, and runtime implementations.
- Produces: behavioral and visual regression coverage for the approved success criteria.

- [ ] **Step 1: Write failing click, scroll, hover, and recomposition tests**

Create `GlassInteractionIntegrationTest.kt` with these cases:

```kotlin
@Test
fun interactiveGlass_doesNotBlockClick() = runComposeUiTest {
  val source = MutableInteractionSource()
  var clicks = 0
  setContent {
    Box(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect {
          glassEffect {
            pressed()
            interactionSource = source
          }
        }
        .clickable(
          interactionSource = source,
          indication = null,
          onClick = { clicks++ },
        ),
    )
  }

  onNodeWithTag("glass").performClick()
  assertThat(clicks).isEqualTo(1)
}

@Test
fun scrollConsumesMovementAndCancelsOnlyRawPress() = runComposeUiTest {
  val scroll = ScrollState(0)
  val effect = GlassVisualEffect().apply {
    pressed()
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Column(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect { visualEffect = effect }
        .verticalScroll(scroll),
    ) {
      Spacer(Modifier.height(400.dp))
    }
  }

  onNodeWithTag("glass").performTouchInput {
    down(center)
    moveBy(Offset(0f, -60f))
    up()
  }
  waitForIdle()

  assertThat(scroll.value).isGreaterThan(0)
  assertThat(effect.currentInteractionSignals.rawPressed).isFalse()
}

@Test
fun mouseHoverTracksPointerAndEndsOnExit() = runComposeUiTest {
  val effect = GlassVisualEffect().apply {
    hovered()
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }
  setContent {
    Box(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect { visualEffect = effect },
    )
  }

  onNodeWithTag("glass").performMouseInput {
    enter(Offset(10f, 20f))
    moveTo(Offset(70f, 60f))
  }
  waitForIdle()
  assertThat(effect.currentInteractionState.position).isEqualTo(Offset(70f, 60f))
  assertThat(effect.currentInteractionSignals.rawHovered).isTrue()

  onNodeWithTag("glass").performMouseInput { exit() }
  waitForIdle()
  assertThat(effect.currentInteractionSignals.rawHovered).isFalse()
}

@Test
fun interactionFrames_doNotRecomposeConfiguration() = runComposeUiTest {
  val effect = GlassVisualEffect().apply { pressed() }
  var compositions = 0
  setContent {
    SideEffect { compositions++ }
    Box(
      Modifier
        .size(100.dp)
        .testTag("glass")
        .hazeEffect { visualEffect = effect },
    )
  }
  waitForIdle()
  val initial = compositions

  onNodeWithTag("glass").performTouchInput {
    down(Offset(20f, 20f))
    moveTo(Offset(80f, 80f))
    up()
  }
  mainClock.advanceTimeBy(1_000)

  assertThat(compositions).isEqualTo(initial)
}
```

Add a source-driven keyboard/D-pad case that emits focus and press interactions, asserts center position, then releases/unfocuses to identity. The test need not synthesize click semantics because the owning focusable/clickable component remains responsible for them.

- [ ] **Step 2: Run integration tests and confirm any remaining failures**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionIntegrationTest'
```

Expected: new tests initially expose any incorrect pass handling, consumption cancellation, hover exit, or composition-state reads.

- [ ] **Step 3: Make only the minimal interoperability fixes**

Keep pointer delivery on `PointerEventPass.Final`. Cancel raw press only when the primary pointer has consumed position movement; do not treat the owning clickable's consumed down/up as scroll cancellation. Ensure frame-rate values live in `Animatable` and ordinary controller fields, never `mutableStateOf` read by composition. Re-run Step 2 until all cases pass.

- [ ] **Step 4: Add deterministic interaction screenshot scenes**

Create one common screenshot scene with a patterned source and three glass panels:

- lighting-only `pressed { lightingIntensity(1f) }`;
- optics-only `pressed { refractionMultiplier(1.08f); whitePointDelta(0.04f) }`;
- scale-only `pressed { scale(0.9f, 0.96f) }`.

Use `GlassReducedMotionPolicy.Reduced` for deterministic target-state captures. Add tests that capture:

```kotlin
@Test
fun glassInteraction_idleHoverPressAndRelease() = runScreenshotTest {
  setContent { ScreenshotTheme { GlassInteractionScene() } }
  captureRoot("idle")

  onNodeWithTag("combined").performMouseInput { enter(Offset(24f, 32f)) }
  waitForIdle()
  captureRoot("hover_top_left")

  onNodeWithTag("combined").performTouchInput { down(Offset(132f, 72f)) }
  waitForIdle()
  captureRoot("press_bottom_right")

  onNodeWithTag("combined").performTouchInput { up() }
  waitForIdle()
  captureRoot("release")
}

@Test
fun glassInteraction_channelsAndTransformTargets() = runScreenshotTest {
  setContent { ScreenshotTheme { GlassInteractionChannelScene() } }
  pressAllTaggedPanels()
  waitForIdle()
  captureRoot("channels")
}

@Test
fun glassInteraction_pointerAndCenterPivots() = runScreenshotTest {
  setContent { ScreenshotTheme { GlassInteractionPivotScene() } }
  pressAllTaggedPanels(at = Offset(24f, 24f))
  waitForIdle()
  captureRoot("pivots")
}
```

Include both `MaterialOnly` and `MaterialAndContent` panels, pointer and center pivots, and a forced-reduced panel that proves transform remains identity while lighting/optics reach their targets. The common suite exercises runtime shaders on Desktop/current Android and fallback lighting on Android API levels without runtime-shader support.

- [ ] **Step 5: Record the new Android and Desktop baselines**

Run:

```bash
./gradlew :haze-screenshot-tests:recordRoborazzi
```

Expected: new `GlassInteractionScreenshotTest` WebP files appear under both platform baseline directories. Inspect every image for localization, clipping, pivot, content-target, and reduced-motion correctness before accepting them.

- [ ] **Step 6: Verify screenshot and integration suites**

Run:

```bash
./gradlew :haze-glass:jvmTest :haze-screenshot-tests:test
```

Expected: all interaction integration tests and screenshot comparisons pass.

- [ ] **Step 7: Commit**

```bash
git add haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassInteractionIntegrationTest.kt haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInteractionScreenshotTest.kt haze-screenshot-tests/screenshots/android haze-screenshot-tests/screenshots/desktop
git commit -m "Test interactive glass behavior"
```

---

### Task 8: Publish the API, sample, and documentation

**Files:**

- Create: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/InteractiveGlassSample.kt`
- Modify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/CreditCardSamplesTest.kt`
- Modify: `docs/effects/glass.md`
- Modify: `CHANGELOG.md`
- Modify: `haze/api/api.txt`
- Modify: `haze-glass/api/api.txt`

**Interfaces:**

- Consumes: the finalized public API.
- Produces: runnable examples, user-facing guidance, and checked API signatures.

- [ ] **Step 1: Add a failing sample smoke test**

Register `Sample.InteractiveGlass` in `CommonSamples` and add a smoke case beside the existing credit-card sample test:

```kotlin
@Test
fun interactiveGlassSample_renders() = runComposeUiTest {
  setContent {
    SamplesTheme {
      InteractiveGlassSample(navController = rememberNavController())
    }
  }
  waitForIdle()
  onNodeWithText("Pressed only").assertExists()
  onNodeWithText("All presets").assertExists()
  onNodeWithText("Custom press").assertExists()
}
```

- [ ] **Step 2: Run the sample test and confirm the sample is missing**

Run:

```bash
./gradlew :sample:shared:jvmTest --tests 'dev.chrisbanes.haze.sample.CreditCardSamplesTest'
```

Expected: compilation fails because `InteractiveGlassSample` does not exist.

- [ ] **Step 3: Build the interactive sample**

Create three controls over a shared patterned `HazeState`. Each control must own a retained `MutableInteractionSource` and use the same source for the behavior modifier and glass effect.

Use these exact glass configurations:

```kotlin
// Pressed only: the smallest opt-in.
glassEffect {
  pressed()
  shape = ControlShape
}

// Every preset.
glassEffect {
  interactionSource = allPresetsSource
  interactable()
  shape = ControlShape
}

// Scale-only custom press with content transform and explicit motion policy.
glassEffect {
  interactionSource = customSource
  interactionTransformTarget = GlassTransformTarget.MaterialAndContent
  interactionTransformPivot = GlassTransformPivot.Pointer
  interactionPositionAnimationSpec = GlassDefaults.positionAnimationSpec
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.System
  hovered()
  focused()
  pressed {
    animate(
      toSpec = GlassDefaults.pressAnimationSpec,
      fromSpec = GlassDefaults.releaseAnimationSpec,
    ) {
      scale(0.98f)
    }
  }
  shape = ControlShape
}
```

Apply `clickable(interactionSource = source, indication = null)` and `focusable(interactionSource = source)` outside the glass configuration. Keep all labels visible so the smoke test can find them.

- [ ] **Step 4: Document the opt-in API**

Add an “Interaction” section to `docs/effects/glass.md` with:

```kotlin
Modifier.hazeEffect(hazeState) {
  glassEffect {
    pressed()
  }
}
```

Then document:

- `hovered()`, `focused()`, `pressed()`, and `interactable()`;
- custom blocks replacing presets from identity;
- fixed focus, hover, press per-property precedence;
- `animate(toSpec, fromSpec)` ownership semantics;
- `interactionSource` sharing for focus and keyboard/D-pad activation;
- transform targets and pivots;
- `clearHovered()`, `clearFocused()`, `clearPressed()`, and `clearInteractions()`;
- `System`, `Reduced`, and `Full` motion policies;
- the fact that these APIs add visual response only, not click/focus/semantics behavior; and
- fallback optics being a no-op while lighting and transforms still work.

Add this `CHANGELOG.md` entry under `Unreleased / Added`:

```markdown
- Added opt-in hover, focus, and press responses to `GlassVisualEffect`, including localized lighting and optics, configurable transforms and motion, and an `interactable()` preset shortcut.
```

- [ ] **Step 5: Format and run sample/docs-adjacent tests**

Run:

```bash
./gradlew spotlessApply :sample:shared:jvmTest
```

Expected: formatting completes and all shared sample tests pass.

- [ ] **Step 6: Regenerate API signatures**

Run:

```bash
./gradlew :haze:metalavaGenerateSignature :haze-glass:metalavaGenerateSignature
```

Inspect the generated signatures. `haze/api/api.txt` must contain only `InteractiveVisualEffect` and
`VisualEffectTransform` additions. `haze-glass/api/api.txt` must contain the direct
`GlassVisualEffect` methods/properties, `GlassInteractionScope`, the three enums, and
`GlassDefaults` motion specs; it must not contain a standalone class named exactly
`GlassInteraction`, `then`, a default named class, Compose `Style` types, or an enabled flag.

- [ ] **Step 7: Run API, module, and full verification**

Run:

```bash
./gradlew :haze:metalavaCheckCompatibility :haze-glass:metalavaCheckCompatibility
./gradlew :haze:jvmTest :haze-glass:jvmTest :haze-screenshot-tests:test :sample:shared:jvmTest
./gradlew check
git diff --check
```

Expected: every command exits successfully. The final diff check prints no output.

- [ ] **Step 8: Commit**

```bash
git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/InteractiveGlassSample.kt sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/Samples.kt sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/CreditCardSamplesTest.kt docs/effects/glass.md CHANGELOG.md haze/api/api.txt haze-glass/api/api.txt
git commit -m "Document interactive glass"
```

- [ ] **Step 9: Verify the final branch state**

Run:

```bash
git status --short
git log --oneline --max-count=8
```

Expected: the worktree is clean and the eight focused implementation commits are visible in task order.
