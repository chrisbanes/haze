# Glass Render Budget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bound every runtime Glass retained layer and the complete per-effect layer graph, degrade unsafe requests by lowering input scale before falling back, and release retained Glass resources on effective Android and iOS memory signals.

**Architecture:** Add a pure common-code budget engine that evaluates the real active layer plan at a candidate scale and selects the largest safe scale. `GlassVisualEffect` prepares that decision before platform delegate selection, while the runtime delegate consumes the chosen scale and fallback remains allocation-free. Keep platform memory mechanics thin: Android reuses the existing callback and changes Glass release policy; iOS registers UIKit's memory-warning notification behind the existing common callback boundary.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose `GraphicsLayer`, Compose runtime shaders, Android `ComponentCallbacks2`, iOS UIKit/Foundation notifications, `kotlin.test`, AssertK, Compose UI tests, Gradle, Roborazzi.

## Global Constraints

- Keep all allocation policy internal; add no public API and do not change the accepted `GlassOptics.Absolute.refractionScale` range.
- Maximum retained-layer width or height is exactly `4096 px`.
- Maximum combined retained backing area per Glass effect is exactly `16_777_216` pixels.
- Automatic degradation may not go below `0.25f`; an explicitly requested lower `HazeInputScale.Fixed` value must never be increased.
- Evaluate every active baseline, blur, depth, detail, rim, and interaction layer at its actual planned size.
- Do not select limits from Android `isLowRamDevice()`, poll `MemoryInfo.lowMemory`, or add Canvas capability probing.
- Release Glass retained output when `level == TrimMemoryLevel.UI_HIDDEN` or `level.severity >= TrimMemoryLevel.MODERATE.severity`; preserve `BACKGROUND` alone.
- Preserve pixel output for requests whose requested scale already fits. Do not record new screenshot baselines automatically.
- Keep common APIs semantic and platform mechanics in thin Android/iOS implementations.
- Do not commit during plan execution. The `implement-issue` finisher owns staging, commits, push, pull request, and cleanup after review and verification.

---

## File Structure

- Create `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderBudget.kt`
  - Own fixed limits, retained-layer estimates, overflow-safe pixel accounting, fallback reasons, and bounded scale search.
- Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderBudgetTest.kt`
  - Prove exact limits, stage accounting, scale selection, explicit sub-floor behavior, and invalid-input fallback.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`
  - Move render-parameter construction out of the runtime delegate, canonicalize non-finite resolved values, and build the retained-layer plan from the same keys used by rendering.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
  - Prepare and retain the budget decision before selecting the platform delegate.
- Modify `haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.android.kt`
  - Require both runtime-shader support and a runtime-safe budget decision.
- Modify `haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.skiko.kt`
  - Select runtime only for a runtime-safe budget decision.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
  - Consume the prepared scale, keep budget checks ahead of graphics-layer creation, and use the extracted parameter builder.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/FallbackGlassDelegate.kt`
  - Canonicalize fallback geometry so invalid style values remain draw-safe.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderParamsTest.kt`
  - Cover canonicalized padding and resolved non-finite values.
- Modify `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectLifecycleTest.kt`
  - Cover prepared budget decisions and decision-change invalidation.
- Modify `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegateIntegrationTest.kt`
  - Prove impossible foreground Glass selects fallback before runtime-layer creation.
- Modify `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegateTrimMemoryTest.kt`
  - Cover all eleven layers, `UI_HIDDEN`, preserved `BACKGROUND`, and regeneration after release.
- Delete `haze/src/appleMain/kotlin/dev/chrisbanes/haze/TrimMemoryCallback.apple.kt`
  - Remove the no-op Apple actual.
- Create `haze/src/iosMain/kotlin/dev/chrisbanes/haze/TrimMemoryCallback.ios.kt`
  - Register and dispose UIKit memory-warning observation.
- Create `haze/src/iosTest/kotlin/dev/chrisbanes/haze/TrimMemoryCallbackTest.ios.kt`
  - Post the UIKit notification and verify callback delivery and disposal.

---

### Task 1: Add The Pure Retained-Layer Budget Engine

**Files:**
- Create: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderBudget.kt`
- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderBudgetTest.kt`

**Interfaces:**
- Consumes: Compose `IntSize`; a caller-supplied `(Float) -> GlassRetainedLayerPlan?` that rebuilds the real plan for each candidate scale.
- Produces: `GlassRenderBudgetDecision`, `GlassRenderBudgetDecision.Runtime.scaleFactor`, `GlassRenderBudgetDecision.Runtime.plan`, `GlassRenderBudgetDecision.Fallback.reason`, `GlassRetainedLayerPlan`, and `resolveGlassRenderBudget(...)` for Task 2.

- [ ] **Step 1: Write failing exact-limit and stage-accounting tests**

Create `GlassRenderBudgetTest.kt` with focused tests using a helper that returns named layers. Include these assertions:

```kotlin
@Test
fun exactLimits_fitWithoutChangingRequestedScale() {
  val plan = GlassRetainedLayerPlan(
    listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(4096, 4096))),
  )

  assertThat(resolveGlassRenderBudget(1f) { plan }).isEqualTo(
    GlassRenderBudgetDecision.Runtime(scaleFactor = 1f, plan = plan),
  )
}

@Test
fun everyActiveStage_contributesItsActualPixelCount() {
  val plan = GlassRetainedLayerPlan(
    listOf(
      GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.BlurPrefilter, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.BlurHorizontal, IntSize(500, 500)),
      GlassRetainedLayer(GlassRetainedLayerKind.Blurred, IntSize(500, 500)),
      GlassRetainedLayer(GlassRetainedLayerKind.DepthMixed, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.Optical, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.RefractionDetail, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.Rim, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.InteractionOptical, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.InteractionDetail, IntSize(1000, 1000)),
      GlassRetainedLayer(GlassRetainedLayerKind.InteractionLighting, IntSize(1000, 1000)),
    ),
  )

  assertThat(plan.retainedPixelCountOrNull()).isEqualTo(9_500_000L)
  assertThat(plan.fitsGlassRenderBudget()).isTrue()
}
```

Also add tests named:

```text
dimensionOnePixelOver_doesNotFit
combinedPixelsOneOver_doesNotFit
invalidAndOverflowingDimensions_returnInvalidFallback
```

The combined-pixel overage test must use one `4096 x 4096` layer plus one `1 x 1` layer. Both are
individually dimension-safe and their sum is exactly `16_777_217`, proving the total graph—not only
the source—is bounded.

- [ ] **Step 2: Run the new test and verify the red state**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests dev.chrisbanes.haze.glass.GlassRenderBudgetTest
```

Expected: compilation fails because the budget types and functions do not exist.

- [ ] **Step 3: Implement fixed limits and overflow-safe accounting**

Create `GlassRenderBudget.kt` with these declarations and behavior:

```kotlin
internal const val MAX_GLASS_LAYER_DIMENSION_PX: Int = 4096
internal const val MAX_GLASS_RETAINED_PIXELS: Long = 16_777_216L
internal const val MIN_AUTOMATIC_GLASS_INPUT_SCALE: Float = 0.25f
private const val GLASS_BUDGET_SEARCH_ITERATIONS: Int = 16

internal enum class GlassRetainedLayerKind {
  Source,
  BlurPrefilter,
  BlurHorizontal,
  Blurred,
  DepthMixed,
  Optical,
  RefractionDetail,
  Rim,
  InteractionOptical,
  InteractionDetail,
  InteractionLighting,
}

internal data class GlassRetainedLayer(
  val kind: GlassRetainedLayerKind,
  val size: IntSize,
)

internal data class GlassRetainedLayerPlan(
  val layers: List<GlassRetainedLayer>,
) {
  fun retainedPixelCountOrNull(): Long? {
    var total = 0L
    for (layer in layers) {
      val width = layer.size.width
      val height = layer.size.height
      if (width <= 0 || height <= 0) return null
      val pixels = width.toLong() * height.toLong()
      if (Long.MAX_VALUE - total < pixels) return null
      total += pixels
    }
    return total
  }

  fun fitsGlassRenderBudget(): Boolean =
    layers.isNotEmpty() &&
      layers.all { it.size.width <= MAX_GLASS_LAYER_DIMENSION_PX && it.size.height <= MAX_GLASS_LAYER_DIMENSION_PX } &&
      (retainedPixelCountOrNull()?.let { it <= MAX_GLASS_RETAINED_PIXELS } == true)
}

internal enum class GlassRenderBudgetFallbackReason {
  InvalidGeometry,
  ExceedsLimits,
}

internal sealed interface GlassRenderBudgetDecision {
  data class Runtime(
    val scaleFactor: Float,
    val plan: GlassRetainedLayerPlan,
  ) : GlassRenderBudgetDecision

  data class Fallback(
    val reason: GlassRenderBudgetFallbackReason,
  ) : GlassRenderBudgetDecision
}
```

Implement `resolveGlassRenderBudget(requestedScale, buildPlan)` so it:

1. rejects non-finite or non-positive requested scales as `InvalidGeometry`;
2. evaluates requested scale and returns it unchanged when safe;
3. evaluates `minOf(requestedScale, 0.25f)` as the automatic floor without assuming that retained
   cost is monotonic across the semantic-blur prefilter transition;
4. when that transition is present, isolates its two sides to adjacent representable positive
   `Float` values and searches both continuous-topology intervals;
5. performs exactly 16 budget-search iterations within each eligible interval, retaining the lower
   known-safe candidate and choosing the largest safe result across intervals;
6. returns `ExceedsLimits` only when no interval contains a safe plan;
7. returns the selected lightweight retained-layer plan. Capability-gated exact render-bundle
   construction and final plan validation happen in `GlassVisualEffect` before runtime allocation.

- [ ] **Step 4: Add scale-search and explicit sub-floor tests**

Add tests with a scale-dependent plan builder:

```kotlin
private fun squarePlan(scale: Float, sideAtFullScale: Int): GlassRetainedLayerPlan {
  val side = (sideAtFullScale * scale).roundToInt().coerceAtLeast(1)
  return GlassRetainedLayerPlan(
    listOf(GlassRetainedLayer(GlassRetainedLayerKind.Source, IntSize(side, side))),
  )
}

@Test
fun overBudgetRequest_selectsLargestSafeScale() {
  val result = resolveGlassRenderBudget(1f) { squarePlan(it, 8192) }
  val runtime = assertIs<GlassRenderBudgetDecision.Runtime>(result)

  assertThat(runtime.scaleFactor).isGreaterThanOrEqualTo(0.5f)
  assertThat(runtime.scaleFactor).isLessThanOrEqualTo(4096.5f / 8192f)
  assertThat(runtime.plan.layers.single().size).isEqualTo(IntSize(4096, 4096))
  assertThat(runtime.plan.fitsGlassRenderBudget()).isTrue()
}

@Test
fun automaticScaleFloor_thatStillDoesNotFit_usesFallback() {
  assertThat(resolveGlassRenderBudget(1f) { squarePlan(it, 20_000) })
    .isEqualTo(GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.ExceedsLimits))
}

@Test
fun explicitlyRequestedSubFloorScale_isNeverIncreased() {
  val result = assertIs<GlassRenderBudgetDecision.Runtime>(
    resolveGlassRenderBudget(0.125f) { squarePlan(it, 8192) },
  )

  assertThat(result.scaleFactor).isEqualTo(0.125f)
}
```

- [ ] **Step 5: Run the budget tests green**

Run the same focused JVM command. Expected: all `GlassRenderBudgetTest` tests pass.

- [ ] **Step 6: Validate common behavior on Android host**

Run:

```bash
./gradlew :haze-glass:testAndroidHostTest --tests dev.chrisbanes.haze.glass.GlassRenderBudgetTest
```

Expected: all budget tests pass on Android host.

Do not commit; leave the task diff for the issue workflow's review and finisher.

---

### Task 2: Integrate Budgeting Before Delegate And Layer Selection

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassRenderParams.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Modify: `haze-glass/src/androidMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.android.kt`
- Modify: `haze-glass/src/skikoMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.skiko.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/FallbackGlassDelegate.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassRenderParamsTest.kt`
- Modify: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffectLifecycleTest.kt`
- Modify: `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegateIntegrationTest.kt`

**Interfaces:**
- Consumes: Task 1's `resolveGlassRenderBudget`, `GlassRenderBudgetDecision`, and retained-layer plan types.
- Produces: `GlassVisualEffect.preparedRenderBudget`, top-level `buildGlassRenderParams(...)`, `buildGlassRetainedLayerPlan(...)`, and platform delegate selection that cannot enter runtime preparation for fallback decisions.

- [ ] **Step 1: Write failing render-plan and canonicalization tests**

In `GlassRenderParamsTest.kt`, add tests that construct `GlassRenderParams` with controlled sizes and assert:

```text
retainedPlan_depthZeroOmitsBlurAndDepthLayers
retainedPlan_partialDepthCountsBlurAndDepthLayers
retainedPlan_interactionCountsAllThreeInteractionLayers
retainedPlan_usesBlurWorkingSizeForHorizontalAndVerticalLayers
samplePadding_nonFiniteInputsReturnsFiniteNonNegativeValue
```

The interaction test must assert the exact ordered kinds:

```kotlin
assertThat(plan.layers.map { it.kind }).containsExactly(
  GlassRetainedLayerKind.Source,
  GlassRetainedLayerKind.Optical,
  GlassRetainedLayerKind.RefractionDetail,
  GlassRetainedLayerKind.Rim,
  GlassRetainedLayerKind.InteractionOptical,
  GlassRetainedLayerKind.InteractionDetail,
  GlassRetainedLayerKind.InteractionLighting,
)
```

Use zero blur/depth in that test so no blur layers obscure the interaction assertion.

- [ ] **Step 2: Run the focused parameter tests red**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests dev.chrisbanes.haze.glass.GlassRenderParamsTest
```

Expected: new tests fail because retained-plan construction and canonicalization are absent.

- [ ] **Step 3: Extract parameter building and add retained-plan construction**

Move `RuntimeShaderGlassDelegate.buildRenderParams` to an internal top-level function in
`GlassRenderParams.kt` with this signature:

```kotlin
internal fun buildGlassRenderParams(
  effect: GlassVisualEffect,
  context: VisualEffectContext,
  coordinates: GlassCoordinates,
): GlassRenderParams
```

Add:

```kotlin
internal fun buildGlassRetainedLayerPlan(
  params: GlassRenderParams,
  interaction: GlassInteractionUniforms,
): GlassRetainedLayerPlan
```

Construct its ordered list from the same conditions used by the runtime delegate:

- always source and optical;
- blur prefilter only when `blurEffectKey().plan.requiresPrefilter` and blur is active;
- blur horizontal and blurred at `plan.workingSize` when blur is active;
- depth mix only when active blur has `depth in 0f..1f` excluding endpoints;
- refraction detail only when `activeRefractionDetailEffectKey()` is non-null;
- rim only when `specularIntensity > 0f`;
- interaction optical when `interaction.hasOptics`;
- interaction detail when optics and refraction detail are active;
- interaction lighting when `interaction.hasLighting`.

Canonicalize every non-finite non-sizing shader scalar before clamping with these exact fallbacks:

```text
specularIntensity -> GlassDefaults.specularIntensity
ambientResponse -> GlassDefaults.ambientResponse
chromaticAberrationStrength -> GlassDefaults.chromaticAberrationStrength
contrast -> GlassDefaults.contrast
whitePoint -> GlassDefaults.whitePoint
chromaMultiplier -> GlassDefaults.chromaMultiplier
contentNormalBlend -> GlassDefaults.contentNormalBlend
specularExponent -> GlassDefaults.specularExponent
fresnelExponent -> GlassDefaults.fresnelExponent
interaction lightingIntensity -> 0f
interaction refractionMultiplier -> 1f
interaction whitePointDelta -> 0f
interaction radius fraction -> GlassDefaults.interactionLightRadiusFraction
```

Resolve invalid light and interaction positions to `context.size.center`. Resolve non-finite edge
softness to `GlassDefaults.edgeSoftness` in pixels. Make `calculateGlassSamplePaddingPx` replace any
remaining non-finite contributor with `0f` before non-negative clamping, so
`calculateLayerBounds` cannot return infinite padding.

- [ ] **Step 4: Run parameter tests green**

Run the focused command from Step 2. Expected: all parameter tests pass.

- [ ] **Step 5: Write failing budget-preparation and fallback-selection tests**

In `GlassVisualEffectLifecycleTest.kt`, add a context whose `layerSize` can differ from material
`size`, then assert:

```kotlin
@Test
fun prepareBudget_safeGraphPreservesRequestedScale() {
  val decision = GlassVisualEffect().resolveGlassRenderBudget(
    TrackingVisualEffectContext(effectSize = Size(100f, 100f), layerSize = Size(120f, 120f)),
  )

  assertThat((decision as GlassRenderBudgetDecision.Runtime).scaleFactor).isEqualTo(1f)
}

@Test
fun prepareBudget_maxRefractionUsesFallbackBeforeRuntimePreparation() {
  val effect = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = 1f,
      refractionScale = 16_384f,
      blurRadius = 0.dp,
    )
  }
  val decision = effect.resolveGlassRenderBudget(
    TrackingVisualEffectContext(
      effectSize = Size(100f, 100f),
      layerSize = Size(49_252f, 49_252f),
    ),
  )

  assertThat(decision).isInstanceOf(GlassRenderBudgetDecision.Fallback::class)
}
```

In `RuntimeShaderGlassDelegateIntegrationTest.kt`, add a foreground-content Compose test using
maximum refraction values and assert `effect.delegate is FallbackGlassDelegate` after idle.

- [ ] **Step 6: Run lifecycle and integration tests red**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests dev.chrisbanes.haze.glass.GlassVisualEffectLifecycleTest --tests dev.chrisbanes.haze.glass.RuntimeShaderGlassDelegateIntegrationTest
```

Expected: failures because budget preparation is not connected to delegate selection.

- [ ] **Step 7: Prepare the budget before delegate selection**

In `GlassVisualEffect.kt`, add:

```kotlin
internal var preparedRenderBudget: GlassRenderBudgetDecision =
  GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry)
  private set
```

Add a preparation path that resolves style and interaction inputs once, then rebuilds only
coordinates and the primitive retained-layer plan for every candidate supplied by Task 1's scale
search. It must reject raw or rounded non-drawable material/sample geometry before converting
dimensions to `IntSize`.

Add an internal
`prepareRenderBudget(context, runtimeShaderSupported): GlassRenderBudgetDecision` method. It stores
the lightweight decision for every platform, but constructs `GlassPreparedRender` only when the
decision is runtime-safe and runtime shaders are supported. The exact bundle owns the resolved
parameters, interaction uniforms, shader keys, and actual retained plan; validate that plan against
the limits before exposing it to the runtime delegate. Production `prepareDraw` evaluates the
platform capability once and passes it to this method. Direct-delegate tests must prepare the same
state explicitly.

At the start of `prepareDraw`, set `preparedRenderBudget`, then force platform delegate selection
when the decision changes between `Runtime` and `Fallback`. A scale-only change keeps the runtime
delegate and lets its existing `scaledSize` path release/rebuild layers.

- [ ] **Step 8: Make platform selectors honor the decision**

In both Android and Skiko actual selection, define:

```kotlin
val wantsRuntime =
  preparedRenderBudget is GlassRenderBudgetDecision.Runtime &&
    preparedRender != null
```

Add an internal `isRuntimeShaderGlassSupported()` expect/actual capability boundary: Android
delegates to `isRuntimeShaderRenderEffectSupported()`, while Skiko returns `true`. Preparation and
delegate selection must consume the same capability result rather than querying support twice.
Both selectors return `FallbackGlassDelegate` when `wantsRuntime` is false and preserve the existing
delegate instance when its type already matches.

- [ ] **Step 9: Consume only the prepared safe scale in the runtime delegate**

At the beginning of `RuntimeShaderGlassDelegate.prepareDraw`, consume the exact prepared bundle:

```kotlin
val prepared = effect.preparedRender
if (effect.preparedRenderBudget !is GlassRenderBudgetDecision.Runtime || prepared == null) {
    releaseRetainedResources()
    return
}
```

Use `prepared.params`, `prepared.interactionUniforms`, and its active shader keys directly rather
than rebuilding them. Keep `requireGraphicsContext` and all layer creation after this decision and
the final exact plan check. On topology changes, release every obsolete retained stage before any
replacement `ensure*` call so the old and new graphs cannot overlap beyond the budget.

In `FallbackGlassDelegate`, resolve non-finite edge softness to a finite non-negative value, use the
material center for an invalid light/interaction position, and apply the same defaulting and
clamping to an invalid interaction radius as the runtime path.

- [ ] **Step 10: Run focused lifecycle, integration, and parameter tests green**

Run the commands from Steps 4 and 6. Expected: all selected tests pass; the maximum-refraction
Compose case uses fallback without a runtime delegate.

- [ ] **Step 11: Run the complete Glass unit suites**

Run:

```bash
./gradlew :haze-glass:jvmTest :haze-glass:testAndroidHostTest
```

Expected: both tasks pass without screenshot recording.

Do not commit; leave the task diff for review.

---

### Task 3: Release Every Retained Glass Layer On Effective Trim Signals

**Files:**
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
- Modify: `haze-glass/src/jvmTest/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegateTrimMemoryTest.kt`

**Interfaces:**
- Consumes: existing `TrimMemoryLevel`, `GlassLayers.release`, and Task 2's prepared safe runtime path.
- Produces: Glass-specific release policy `UI_HIDDEN || MODERATE+` and fresh evidence that all eleven layers release and regenerate.

- [ ] **Step 1: Expand the test fixture to all eleven layers**

Update `GlassLayers.populate`, `GlassLayers.allLayers`, and every retained-layer assertion in
`RuntimeShaderGlassDelegateTrimMemoryTest.kt` to include:

```kotlin
interactionOptical = graphicsContext.createGraphicsLayer()
interactionRefractionDetail = graphicsContext.createGraphicsLayer()
interactionLighting = graphicsContext.createGraphicsLayer()
```

Assert `allLayers().size == 11` so future layers cannot silently escape trim coverage.

- [ ] **Step 2: Write failing UI-hidden and recovery tests**

Add:

```kotlin
@Test
fun onTrimMemory_uiHiddenReleasesAllLayersAndInvalidatesDraw() {
  val delegate = RuntimeShaderGlassDelegate(GlassVisualEffect())
  val context = RecordingVisualEffectContext(size = Size(100f, 100f), layerSize = Size(100f, 100f))
  delegate.layers.populate(context.graphicsContext)
  val retainedLayers = delegate.layers.allLayers()
  delegate.setGraphicsContextForTest(context.graphicsContext)

  delegate.onTrimMemory(context, TrimMemoryLevel.UI_HIDDEN)

  assertThat(context.graphicsContext.releasedLayers).containsExactly(*retainedLayers.toTypedArray())
  assertThat(delegate.layers.isEmpty).isTrue()
  assertThat(context.invalidateDrawCalls).isEqualTo(1)
}
```

Keep and strengthen `onTrimMemory_backgroundKeepsRetainedOutputAvailability`. Add a recovery test
that prepares a safe 100-by-100 graph, records the created layer identities, sends `UI_HIDDEN`,
prepares again, and asserts fresh non-released source/optical layers exist and the second set is not
the released set. Update the shared direct-delegate preparation helper, and any test that invokes
`RuntimeShaderGlassDelegate.prepareDraw` without that helper, to call
`effect.prepareRenderBudget(context)` immediately before every `prepareDraw` invocation.

- [ ] **Step 3: Run trim tests red**

Run:

```bash
./gradlew :haze-glass:jvmTest --tests dev.chrisbanes.haze.glass.RuntimeShaderGlassDelegateTrimMemoryTest
```

Expected: the UI-hidden test fails because current policy releases only `MODERATE+`.

- [ ] **Step 4: Implement the semantic release condition**

Replace the current threshold with:

```kotlin
private fun shouldReleaseRetainedGlass(level: TrimMemoryLevel): Boolean =
  level == TrimMemoryLevel.UI_HIDDEN ||
    level.severity >= TrimMemoryLevel.MODERATE.severity
```

Use it in `onTrimMemory`; keep the existing full release, metadata clearing, and draw invalidation.
Do not change the Android raw-level mapping in `TrimMemoryCallback.android.kt`.

- [ ] **Step 5: Run trim and full Glass tests green**

Run the focused trim command, then:

```bash
./gradlew :haze-glass:jvmTest :haze-glass:testAndroidHostTest
```

Expected: `UI_HIDDEN`, `MODERATE`, and `COMPLETE` release; `BACKGROUND` alone preserves; all Glass
tests pass.

Do not commit; leave the task diff for review.

---

### Task 4: Wire iOS Memory Warnings Through The Existing Boundary

**Files:**
- Delete: `haze/src/appleMain/kotlin/dev/chrisbanes/haze/TrimMemoryCallback.apple.kt`
- Create: `haze/src/iosMain/kotlin/dev/chrisbanes/haze/TrimMemoryCallback.ios.kt`
- Create: `haze/src/iosTest/kotlin/dev/chrisbanes/haze/TrimMemoryCallbackTest.ios.kt`

**Interfaces:**
- Consumes: common `registerTrimMemoryCallback(context, callback)` expect function and `TrimMemoryLevel.COMPLETE`.
- Produces: an iOS actual backed by `UIApplicationDidReceiveMemoryWarningNotification` whose observer is removed by `DisposableHandle.dispose()`.

- [ ] **Step 1: Write the failing iOS notification test**

Create `TrimMemoryCallbackTest.ios.kt` with a test that verifies the iOS test runner is on the main
thread, registers a mutable callback list, posts the warning, disposes, then posts again:

```kotlin
// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSThread
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

class TrimMemoryCallbackTest {
  @Test
  fun memoryWarning_deliversCompleteUntilDisposed() {
    assertTrue(NSThread.isMainThread)
    val received = mutableListOf<TrimMemoryLevel>()
    val handle = registerTrimMemoryCallback(
      context = PlatformContext.INSTANCE,
      callback = received::add,
    )

    NSNotificationCenter.defaultCenter.postNotificationName(
      UIApplicationDidReceiveMemoryWarningNotification,
      null,
    )
    assertEquals(listOf(TrimMemoryLevel.COMPLETE), received)

    handle.dispose()
    NSNotificationCenter.defaultCenter.postNotificationName(
      UIApplicationDidReceiveMemoryWarningNotification,
      null,
    )
    assertEquals(listOf(TrimMemoryLevel.COMPLETE), received)
  }
}
```

- [ ] **Step 2: Run the iOS test red**

Run:

```bash
./gradlew :haze:iosSimulatorArm64Test -Phaze.enableAppleTests --tests dev.chrisbanes.haze.TrimMemoryCallbackTest
```

Expected: compilation or assertion failure because the current Apple actual is a no-op.

- [ ] **Step 3: Replace the no-op Apple actual with a thin iOS observer**

Delete the `appleMain` file and create `TrimMemoryCallback.ios.kt`:

```kotlin
// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import kotlinx.coroutines.DisposableHandle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

internal actual fun registerTrimMemoryCallback(
  context: PlatformContext,
  callback: (TrimMemoryLevel) -> Unit,
): DisposableHandle {
  val center = NSNotificationCenter.defaultCenter
  val observer = center.addObserverForName(
    UIApplicationDidReceiveMemoryWarningNotification,
    null,
    NSOperationQueue.mainQueue,
  ) {
    callback(TrimMemoryLevel.COMPLETE)
  }
  return DisposableHandle { center.removeObserver(observer) }
}
```

Keep `context` in the semantic signature even though UIKit registration does not require it.

- [ ] **Step 4: Run iOS test and compile tasks green**

Run:

```bash
./gradlew :haze:iosSimulatorArm64Test -Phaze.enableAppleTests --tests dev.chrisbanes.haze.TrimMemoryCallbackTest
./gradlew :haze:compileKotlinIosArm64 :haze:compileKotlinIosSimulatorArm64
```

Expected: the callback fires once before disposal, not after disposal, and both iOS targets compile.

- [ ] **Step 5: Run common/JVM regression checks for Haze**

Run:

```bash
./gradlew :haze:jvmTest :haze:testAndroidHostTest
```

Expected: both tasks pass.

Do not commit; leave the task diff for review.

---

### Task 5: Format And Verify The Complete Changed Scope

**Files:**
- Verify all files listed in Tasks 1-4.
- Do not modify screenshot baselines unless a separately diagnosed behavioral requirement proves a baseline change is intentional.

**Interfaces:**
- Consumes: completed implementation from Tasks 1-4.
- Produces: fresh focused and cross-platform verification evidence for review.

- [ ] **Step 1: Apply formatting and check whitespace**

Run:

```bash
./gradlew spotlessApply
git diff --check
```

Expected: Spotless completes and `git diff --check` prints no errors.

- [ ] **Step 2: Run complete Haze and Glass unit suites**

Run:

```bash
./gradlew :haze:jvmTest :haze:testAndroidHostTest :haze-glass:jvmTest :haze-glass:testAndroidHostTest
```

Expected: all four tasks pass.

- [ ] **Step 3: Run iOS compile and simulator verification**

Run:

```bash
./gradlew :haze:compileKotlinIosArm64 :haze:compileKotlinIosSimulatorArm64
./gradlew :haze:iosSimulatorArm64Test -Phaze.enableAppleTests
```

Expected: both iOS targets compile and the enabled simulator suite passes.

- [ ] **Step 4: Run unchanged-output screenshot suites**

Run:

```bash
./gradlew :haze-screenshot-tests:jvmTest :haze-screenshot-tests:testAndroidHostTest
```

Expected: both screenshot suites pass against existing baselines. If they fail, stop and diagnose;
do not record replacements.

- [ ] **Step 5: Record final repository evidence**

Run:

```bash
git status --short --branch
git diff --stat origin/main
git diff --check
```

Expected: only issue-1046 design, plan, implementation, and test files are changed; no staged files,
no unrelated changes, and no whitespace errors.

Do not commit, push, or create a pull request. Return control to the `implement-issue` workflow for
complete-scope review, fresh verification, and the user's branch-completion choice.
