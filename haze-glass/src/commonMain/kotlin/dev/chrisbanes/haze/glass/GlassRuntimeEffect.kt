// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererInteraction
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.trace
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private class GlassPreparedRenderCacheKey(
  val style: ResolvedGlassStyle,
  val coordinates: GlassCoordinates,
  val interaction: ResolvedGlassInteraction,
  val interactionTopology: GlassInteractionTopology,
)

private class GlassRenderBudgetCacheKey(
  val style: ResolvedGlassStyle,
  val requestedScale: Float,
  val layerSize: Size,
  val materialSize: Size,
  val interactionTopology: GlassInteractionTopology,
  val interactionRadiusFraction: Float,
)

private class GlassPreparedDrawCacheKey(
  val dirtyTrackerVersion: Int,
  val size: Size,
  val layerSize: Size,
  val layerOffset: Offset,
  val sampling: HazeSampling,
  val density: Density,
  val layoutDirection: LayoutDirection,
  val style: ResolvedGlassStyle?,
  val interactionState: GlassInteractionRenderState,
  val interactionTopology: GlassInteractionTopology,
)

@Poko
private class GlassAdaptiveUpdateKey(
  val inputSnapshot: HazeEffectInputSnapshot?,
  val dirtyTrackerVersion: Int,
  val materialSize: Size,
  val layerSize: Size,
  val layerOffset: Offset,
  val interactionState: GlassInteractionRenderState,
)

private fun ResolvedGlassStyle.hasSameRenderParams(other: ResolvedGlassStyle): Boolean =
  resolvedOptics == other.resolvedOptics &&
    specularIntensity == other.specularIntensity &&
    ambientResponse == other.ambientResponse &&
    tint == other.tint &&
    edgeSoftnessPx == other.edgeSoftnessPx &&
    lightPosition == other.lightPosition &&
    chromaticAberrationStrength == other.chromaticAberrationStrength &&
    surfaceProfile == other.surfaceProfile &&
    chromaticAberrationMode == other.chromaticAberrationMode &&
    contrast == other.contrast &&
    whitePoint == other.whitePoint &&
    chromaMultiplier == other.chromaMultiplier &&
    contentNormalBlend == other.contentNormalBlend &&
    specularExponent == other.specularExponent &&
    fresnelExponent == other.fresnelExponent &&
    cornerRadii == other.cornerRadii

private fun ResolvedGlassStyle.hasSameBudgetParams(other: ResolvedGlassStyle): Boolean =
  requiresGlassGroupAlpha(alpha) == requiresGlassGroupAlpha(other.alpha) &&
    resolvedOptics.blurRadiusPx == other.resolvedOptics.blurRadiusPx &&
    resolvedOptics.depth == other.resolvedOptics.depth &&
    (resolvedOptics.progressive == null) == (other.resolvedOptics.progressive == null) &&
    resolvedOptics.refractionStrength == other.resolvedOptics.refractionStrength &&
    resolvedOptics.refractionScalePx == other.resolvedOptics.refractionScalePx &&
    resolvedOptics.refractionHeightPx == other.resolvedOptics.refractionHeightPx &&
    resolvedOptics.refractionDetailIntensity == other.resolvedOptics.refractionDetailIntensity &&
    chromaticAberrationStrength == other.chromaticAberrationStrength &&
    edgeSoftnessPx == other.edgeSoftnessPx &&
    (specularIntensity > 0f) == (other.specularIntensity > 0f)

private val IdleInteractionState = GlassInteractionRenderState(Offset.Zero)
private val IdleInteractionSignals = GlassInteractionSignals()

/**
 * Node-owned runtime for rendering a translucent refractive glass material.
 *
 * Style evaluation, attachment state, delegates, caches, controllers, and platform resources all
 * belong to this instance.
 */
@ExperimentalHazeApi
@Stable
@OptIn(InternalHazeApi::class)
internal class GlassRuntimeEffect() :
  GlassRuntimeState(),
  HazeEffectRenderer<GlassNodeConfiguration>,
  HazeEffectRendererLifecycle<GlassNodeConfiguration>,
  HazeEffectRendererDrawHooks<GlassNodeConfiguration>,
  HazeEffectRendererRetainedOutput,
  HazeEffectRendererInteraction {

  constructor(configuration: GlassNodeConfiguration) : this() {
    applyConfiguration(configuration)
  }

  init {
    onConfigurationChanged = ::onRuntimeConfigurationChanged
  }

  private fun applyConfiguration(configuration: GlassNodeConfiguration) {
    style = configuration.style
    interactionSource = configuration.interactionSource
    interactionTransformTarget = configuration.interactionTransformTarget
    interactionTransformPivot = configuration.interactionTransformPivot
    interactionReducedMotionPolicy = configuration.interactionReducedMotionPolicy
  }

  private var isAttached: Boolean = false

  private var attachedContext: HazeEffectLifecycleScope? = null

  private fun onRuntimeConfigurationChanged(fields: Int) {
    markDirty(fields)
    val context = attachedContext ?: return
    if ((fields and GlassDirtyFields.Interaction) != 0) {
      syncInteractionController(context)
    }
    if ((fields and GlassDirtyFields.LayerBoundsFlags) != 0) {
      context.invalidateLayerBounds()
    }
    if ((fields and GlassDirtyFields.InvalidateFlags) != 0) {
      context.invalidateDraw()
    }
  }

  private var interactionController: GlassInteractionController? = null

  internal val interactionControllerForTest: GlassInteractionController?
    get() = interactionController

  internal val attachedContextForTest: HazeEffectLifecycleScope?
    get() = attachedContext

  internal val currentInteractionState: GlassInteractionRenderState
    get() = interactionController?.renderState ?: IdleInteractionState

  internal val currentInteractionSignals: GlassInteractionSignals
    get() = interactionController?.currentSignals ?: IdleInteractionSignals

  private var needsDelegateSelection: Boolean = true

  internal var runtimeShaderIncompatible: Boolean = false
    private set

  internal var preparedRenderBudget: GlassRenderBudgetDecision =
    GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry)
    private set

  internal var preparedRender: GlassPreparedRender? = null
    private set

  private var preparedRenderCacheKey: GlassPreparedRenderCacheKey? = null
  private var preparedRenderCache: GlassPreparedRender? = null

  private var budgetCacheKey: GlassRenderBudgetCacheKey? = null
  private var budgetCacheDecision: GlassRenderBudgetDecision? = null
  private val inputScalePolicy = GlassInputScalePolicy()
  private var resolvedInputScale: Float = 1f

  private var preparedDrawCacheKey: GlassPreparedDrawCacheKey? = null

  private var dirtyTrackerVersion: Int by mutableIntStateOf(0)
  internal var dirtyTracker: Bitmask = Bitmask()
    private set

  private var resolvedStyleCache: ResolvedGlassStyle? = null
  private var resolvedStyleCacheSize: Size = Size.Unspecified
  private var resolvedStyleCacheDensity: Density? = null

  internal val resolvedStyleCacheDensityForTest: Density?
    get() = resolvedStyleCacheDensity

  private var resolvedStyleCacheLayoutDirection: LayoutDirection? = null

  private var geometrySnapshotObserver: SnapshotStateObserver? = null
  private var geometryObserverGeneration: Int = 0
  private var geometryInvalidationJob: Job? = null
  private val styleObservationScope = Any()
  private val clipObservationScope = Any()
  private val onObservedStyleChanged: (Any) -> Unit = {
    scheduleObservedStyleInvalidation()
  }

  private var coordinatesCache: GlassCoordinates? = null
  private var coordinatesCacheLayerSize: Size = Size.Unspecified
  private var coordinatesCacheLayerOffset: Offset = Offset.Unspecified
  private var coordinatesCacheMaterialSize: Size = Size.Unspecified
  private var coordinatesCacheScaleFactor: Float = Float.NaN

  private var interactionRenderStateCache: GlassInteractionRenderState? = null
  private var interactionRadiusFractionCache: Float = Float.NaN
  private var resolvedInteractionCache: ResolvedGlassInteraction? = null
  private var idleInteractionRenderStateSize: Size = Size.Unspecified
  private var idleInteractionRenderState: GlassInteractionRenderState = IdleInteractionState

  private var shouldClipToNodeBoundsCacheValid: Boolean = false
  private var shouldClipToNodeBoundsCache: Boolean = false

  private val renderPreparation = GlassRenderPreparation(
    decision = GlassRenderBudgetDecision.Fallback(
      GlassRenderBudgetFallbackReason.InvalidGeometry,
    ),
    prepared = null,
  )

  internal val interactionSlots: GlassInteractionSlots
    get() = resolvedInteractionSlots

  private val interactionTopologySnapshot: GlassInteractionTopology
    get() = resolvedInteractionTopology

  override val observesPointerEvents: Boolean
    get() = super.observesPointerEvents

  internal var delegate: Delegate = FallbackGlassDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        val old = field
        field = value
        if (isAttached) {
          old.release()
          value.attach()
        }
      }
    }

  override fun attach(scope: HazeEffectLifecycleScope) {
    val context = scope
    if (!isAttached) {
      isAttached = true
      attachedContext = context
      val observerGeneration = ++geometryObserverGeneration
      geometrySnapshotObserver = SnapshotStateObserver { command ->
        context.coroutineScope.launch {
          if (
            isAttached &&
            attachedContext === context &&
            geometryObserverGeneration == observerGeneration
          ) {
            command()
          }
        }
      }.also { it.start() }
      resolvedStyleCache = null
      shouldClipToNodeBoundsCacheValid = false
      syncInteractionController(context)
      delegate.attach()
    }
  }

  override fun detach() {
    if (isAttached) {
      interactionController?.dispose()
      interactionController = null
      geometryObserverGeneration++
      geometryInvalidationJob?.cancel()
      geometryInvalidationJob = null
      geometrySnapshotObserver?.stop()
      geometrySnapshotObserver = null
      attachedContext = null
      isAttached = false
      delegate.release()
    }
    runtimeShaderIncompatible = false
    needsDelegateSelection = true
    inputScalePolicy.reset()
    resolvedInputScale = GlassInputScalePolicy.FULL_RESOLUTION_SCALE
    preparedRender = null
    clearPreparedRenderCache()
  }

  private fun scheduleObservedStyleInvalidation() {
    if (geometryInvalidationJob?.isActive == true) return
    val context = attachedContext ?: return
    val observerGeneration = geometryObserverGeneration
    geometryInvalidationJob = context.coroutineScope.launch {
      yield()
      if (
        isAttached &&
        attachedContext === context &&
        geometryObserverGeneration == observerGeneration
      ) {
        resolvedStyleCache = null
        shouldClipToNodeBoundsCacheValid = false
        context.invalidateLayerBounds()
        context.invalidateDraw()
      }
    }
  }

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: GlassNodeConfiguration,
    sampling: HazeSampling,
  ) {
    val context = scope
    applyConfiguration(style)
    dirtyTrackerVersion
    compositionLocalStyle = context.currentValueOf(LocalGlassStyle)
    updateStyleInteractionSlots()
    syncInteractionController(context)

    if (dirtyTracker.any(GlassDirtyFields.LayerBoundsFlags)) {
      context.invalidateLayerBounds()
    }
    if (dirtyTracker.any(GlassDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      context.invalidateDraw()
    }
  }

  override fun onPointerEvent(event: PointerEvent, scope: HazeEffectLifecycleScope) {
    interactionController?.onPointerEvent(event, scope.modifierSize)
  }

  override fun onCancelPointerInput(scope: HazeEffectLifecycleScope) {
    interactionController?.cancelPointerInput(scope.modifierSize)
  }

  internal fun setPressedForTest(position: Offset, pressed: Boolean = true) {
    val context = attachedContext ?: return
    interactionController?.setRawPressedForTest(pressed, position, context.modifierSize)
  }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
    val context = this
    trace(GlassTraceSection.Prepare) {
      val workloadWeightChanged = if (context.sampling === HazeSampling.Adaptive) {
        inputScalePolicy.observeUpdate(
          GlassAdaptiveUpdateKey(
            inputSnapshot = context.inputSnapshot,
            dirtyTrackerVersion = dirtyTrackerVersion,
            materialSize = context.modifierSize,
            layerSize = context.layerSize,
            layerOffset = context.layerOffset,
            interactionState = interactionRenderState(context.modifierSize),
          ),
        )
      } else {
        inputScalePolicy.reset()
        false
      }
      if (!workloadWeightChanged && canReusePreparedDraw(context)) return@trace
      val previousBudget = preparedRenderBudget
      trace(GlassTraceSection.PrepareBudget) {
        prepareRenderBudget(context, runtimeShaderSupported = isRuntimeShaderGlassSupported())
      }
      if (previousBudget::class != preparedRenderBudget::class) {
        needsDelegateSelection = true
      }
      trace(GlassTraceSection.SelectDelegate) {
        selectDelegateForDraw(context)
      }
      trace(GlassTraceSection.DelegatePrepare) {
        val selectedDelegate = delegate
        try {
          with(selectedDelegate) { prepareDraw(context) }
        } catch (failure: RuntimeShaderRenderEffectException) {
          if (selectedDelegate !is RuntimeShaderGlassDelegate) throw failure
          downgradeRuntimeDelegate(context, failure)
        }
      }
      cachePreparedDrawInputs(context)
    }
  }

  override fun shouldPrepareDraw(style: GlassNodeConfiguration): Boolean {
    if (alpha != 0f) return true
    resetDirtyTracker()
    return false
  }

  private fun canReusePreparedDraw(context: HazeEffectRuntimeDrawScope): Boolean {
    val key = preparedDrawCacheKey ?: return false
    val style = resolvedStyleCache ?: return false
    val interactionState = interactionRenderState(context.modifierSize)
    return preparedRender != null &&
      (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true &&
      key.dirtyTrackerVersion == dirtyTrackerVersion &&
      key.size == context.modifierSize &&
      key.layerSize == context.layerSize &&
      key.layerOffset == context.layerOffset &&
      key.sampling == context.sampling &&
      key.density == context.requireDensity() &&
      key.layoutDirection == context.currentValueOf(LocalLayoutDirection) &&
      key.style === style &&
      key.interactionState === interactionState &&
      key.interactionTopology === interactionTopologySnapshot
  }

  private fun cachePreparedDrawInputs(context: HazeEffectRuntimeDrawScope) {
    preparedDrawCacheKey = GlassPreparedDrawCacheKey(
      dirtyTrackerVersion = dirtyTrackerVersion,
      size = context.modifierSize,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      sampling = context.sampling,
      density = context.requireDensity(),
      layoutDirection = context.currentValueOf(LocalLayoutDirection),
      style = resolvedStyleCache,
      interactionState = interactionRenderState(context.modifierSize),
      interactionTopology = interactionTopologySnapshot,
    )
  }

  override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
    val context = this as HazeEffectRuntimeDrawScope
    try {
      selectDelegateForDraw(context)
      val selectedDelegate = delegate
      try {
        withMaterialTransform(context) {
          with(selectedDelegate) { draw(context) }
        }
      } catch (failure: RuntimeShaderRenderEffectException) {
        if (selectedDelegate !is RuntimeShaderGlassDelegate) throw failure
        downgradeRuntimeDelegate(context, failure)
      }
    } finally {
      resetDirtyTracker()
    }
  }

  private fun DrawScope.downgradeRuntimeDelegate(
    context: HazeEffectRuntimeDrawScope,
    failure: RuntimeShaderRenderEffectException,
  ) {
    runtimeShaderIncompatible = true
    HazeLogger.d(TAG) {
      "Runtime shader construction failed; using fallback until reattachment: $failure"
    }
    delegate = FallbackGlassDelegate(this@GlassRuntimeEffect)
    with(delegate) { prepareDraw(context) }
    context.invalidateDraw()
  }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: GlassNodeConfiguration) {
    val context = this
    withMaterialTransform(context) {
      with(delegate) { drawForeground(context) }
    }
  }

  override fun currentContentTransform(): HazeEffectContentTransform {
    val size = attachedContext?.modifierSize ?: return HazeEffectContentTransform.Identity
    return resolveTransform(size, GlassTransformTarget.MaterialAndContent)
  }

  internal fun currentMaterialTransform(size: Size): HazeEffectContentTransform {
    return resolveTransform(size, GlassTransformTarget.MaterialOnly)
  }

  override fun shouldDrawContentBehind(): Boolean {
    return delegate is FallbackGlassDelegate
  }

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

  internal fun interactionRenderState(size: Size): GlassInteractionRenderState {
    interactionController?.let { return it.renderState }
    if (idleInteractionRenderStateSize != size) {
      idleInteractionRenderStateSize = size
      idleInteractionRenderState = GlassInteractionRenderState(position = size.center)
    }
    return idleInteractionRenderState
  }

  private fun resolveTransform(
    size: Size,
    target: GlassTransformTarget,
  ): HazeEffectContentTransform {
    if (interactionTransformTarget != target) return HazeEffectContentTransform.Identity
    val state = currentInteractionState
    if (!state.hasTransform || !size.isDrawable()) return HazeEffectContentTransform.Identity
    val pivot = when (interactionTransformPivot) {
      GlassTransformPivot.Pointer -> state.position.clampTo(size)
      GlassTransformPivot.Center -> size.center
    }
    return HazeEffectContentTransform(state.scaleX, state.scaleY, pivot)
  }

  private inline fun DrawScope.withMaterialTransform(
    context: HazeEffectRuntimeDrawScope,
    block: DrawScope.() -> Unit,
  ) {
    val transform = currentMaterialTransform(context.modifierSize)
    if (transform == HazeEffectContentTransform.Identity) {
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

  private fun syncInteractionController(context: HazeEffectLifecycleScope) {
    if (
      interactionSlots.hovered == null &&
      interactionSlots.focused == null &&
      interactionSlots.pressed == null
    ) {
      val controller = interactionController ?: return
      controller.dispose()
      interactionController = null
      context.invalidateDraw()
      return
    }
    val controller = interactionController ?: GlassInteractionController(context).also {
      interactionController = it
    }
    controller.updateConfiguration(controllerConfiguration(systemMotionScale(context)))
    controller.updateInteractionSource(interactionSource, context.modifierSize)
  }

  private fun systemMotionScale(context: HazeEffectLifecycleScope): Float {
    return context.coroutineScope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
  }

  override fun onTrimMemory(level: TrimMemoryLevel) {
    attachedContext?.let { delegate.onTrimMemory(it, level) }
  }

  override fun canDrawRetainedOutput(): Boolean {
    return (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true
  }

  override fun shouldDrawRetainedOutput(): Boolean {
    return (delegate as? RetainedOutputDelegate)?.shouldDrawRetainedOutput() == true
  }

  override fun clearRetainedOutput() {
    (delegate as? RetainedOutputDelegate)?.clearRetainedOutput()
  }

  override fun shouldClipToNodeBounds(): Boolean {
    if (!shouldClipToNodeBoundsCacheValid) {
      val observer = geometrySnapshotObserver
      if (observer != null) {
        observer.observeReads(clipObservationScope, onObservedStyleChanged) {
          shouldClipToNodeBoundsCache = edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()
        }
      } else {
        shouldClipToNodeBoundsCache = edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()
      }
      shouldClipToNodeBoundsCacheValid = true
    }
    return shouldClipToNodeBoundsCache
  }

  internal fun resolveGlassRenderBudget(context: HazeEffectRuntimeDrawScope): GlassRenderBudgetDecision {
    return resolveGlassRenderPreparation(context, runtimeShaderSupported = true).decision
  }

  private fun resolvePreparedStyle(context: HazeEffectRuntimeDrawScope): ResolvedGlassStyle {
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    resolvedStyleCache?.takeIf {
      resolvedStyleCacheSize == context.modifierSize &&
        resolvedStyleCacheDensity == density &&
        resolvedStyleCacheLayoutDirection == layoutDirection
    }?.let { return it }

    val resolvedStyle = geometrySnapshotObserver?.let { observer ->
      lateinit var observedStyle: ResolvedGlassStyle
      observer.observeReads(styleObservationScope, onObservedStyleChanged) {
        observedStyle = resolveGlassStyle(this, context.modifierSize, density, layoutDirection)
      }
      observedStyle
    } ?: resolveGlassStyle(this, context.modifierSize, density, layoutDirection)

    return resolvedStyle.also {
      resolvedStyleCache = it
      resolvedStyleCacheSize = context.modifierSize
      resolvedStyleCacheDensity = density
      resolvedStyleCacheLayoutDirection = layoutDirection
    }
  }

  private fun resolvePreparedInteraction(
    context: HazeEffectRuntimeDrawScope,
  ): ResolvedGlassInteraction {
    val state = interactionRenderState(context.modifierSize)
    resolvedInteractionCache?.takeIf {
      interactionRenderStateCache === state &&
        interactionRadiusFractionCache == interactionLightRadiusFraction
    }?.let { return it }

    return resolveGlassInteraction(
      state = state,
      radiusFraction = interactionLightRadiusFraction,
    ).also {
      interactionRenderStateCache = state
      interactionRadiusFractionCache = interactionLightRadiusFraction
      resolvedInteractionCache = it
    }
  }

  private fun resolvePreparedCoordinates(
    layerSize: Size,
    layerOffset: Offset,
    materialSize: Size,
    scaleFactor: Float,
  ): GlassCoordinates {
    coordinatesCache?.takeIf {
      coordinatesCacheLayerSize == layerSize &&
        coordinatesCacheLayerOffset == layerOffset &&
        coordinatesCacheMaterialSize == materialSize &&
        coordinatesCacheScaleFactor == scaleFactor
    }?.let { return it }

    return resolveGlassCoordinates(
      layerSize = layerSize,
      layerOffset = layerOffset,
      materialSize = materialSize,
      scaleFactor = scaleFactor,
    ).withRoundedSampleSize().also {
      coordinatesCache = it
      coordinatesCacheLayerSize = layerSize
      coordinatesCacheLayerOffset = layerOffset
      coordinatesCacheMaterialSize = materialSize
      coordinatesCacheScaleFactor = scaleFactor
    }
  }

  private fun resolveGlassRenderPreparation(
    context: HazeEffectRuntimeDrawScope,
    runtimeShaderSupported: Boolean,
  ): GlassRenderPreparation {
    if (
      !context.modifierSize.isDrawable() || !context.layerSize.isDrawable()
    ) {
      clearPreparedRenderCache()
      return updateRenderPreparation(
        GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry),
        null,
      )
    }
    val style = resolvePreparedStyle(context)
    val interaction = resolvePreparedInteraction(context)
    val interactionTopology = interactionTopologySnapshot
    val optics = style.resolvedOptics
    val allowMultiscaleBlur = optics.progressive == null
    val buildPlan: (Float) -> GlassRetainedLayerPlan = buildPlan@{ scaleFactor ->
      val rawCoordinates = resolveGlassCoordinates(
        layerSize = context.layerSize,
        layerOffset = context.layerOffset,
        materialSize = context.modifierSize,
        scaleFactor = scaleFactor,
      )
      if (!rawCoordinates.materialSize.isDrawable() || !rawCoordinates.sampleSize.isDrawable()) {
        return@buildPlan GlassRetainedLayerPlan(emptyList())
      }
      val coordinates = rawCoordinates.withRoundedSampleSize()
      if (!coordinates.materialSize.isDrawable() || !coordinates.sampleSize.isDrawable()) {
        return@buildPlan GlassRetainedLayerPlan(emptyList())
      }
      val outputSize = context.modifierSize.roundToIntSize()
      val interactionPatchSize = calculateGlassInteractionPatchSize(
        buildGlassRenderParams(style, coordinates),
        radiusFraction = interaction.radiusFraction,
        topology = interactionTopology,
      )
      val interactionLayersActive =
        interactionPatchSize.width > 0 && interactionPatchSize.height > 0
      buildGlassBudgetLayerPlan(
        sampleSize = coordinates.sampleSize.roundToIntSize(),
        groupCompositeSize = resolveGlassGroupCompositeSize(
          outputSize = outputSize,
          alpha = style.alpha,
          interactionLayersActive = interactionLayersActive,
          interactionTopology = interactionTopology,
        ),
        blurRadiusPx = optics.blurRadiusPx * scaleFactor,
        depth = optics.depth,
        allowMultiscaleBlur = allowMultiscaleBlur,
        refractionDetailActive = isGlassRefractionDetailActive(
          refractionStrength = optics.refractionStrength,
          refractionScalePx = optics.refractionScalePx * scaleFactor,
          refractionHeightPx = optics.refractionHeightPx * scaleFactor,
          edgeSoftnessPx = style.edgeSoftnessPx * scaleFactor,
          sampleStepPx = 2f * scaleFactor,
          detailIntensity = optics.refractionDetailIntensity,
        ),
        rimActive = style.specularIntensity > 0f,
        interactionPatchSize = interactionPatchSize,
        interactionOpticsActive = interactionTopology.hasOptics,
        interactionLightingActive = interactionTopology.hasLighting,
      )
    }
    val balancedPlan = if (context.sampling === HazeSampling.Adaptive) {
      buildPlan(GlassInputScalePolicy.BALANCED_SCALE)
    } else {
      null
    }
    val requestedScale = inputScalePolicy.resolve(
      sampling = context.sampling,
      balancedPlan = balancedPlan,
    )
    if (requestedScale != resolvedInputScale) {
      HazeLogger.d(TAG) {
        "Glass input scale changed from $resolvedInputScale to $requestedScale"
      }
    }
    resolvedInputScale = requestedScale
    if (!requestedScale.isFinite() || requestedScale <= 0f) {
      clearPreparedRenderCache()
      return updateRenderPreparation(
        GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry),
        null,
      )
    }
    val budgetKey = budgetCacheKey
    val decision = if (
      budgetKey != null &&
      budgetKey.style.hasSameBudgetParams(style) &&
      budgetKey.requestedScale == requestedScale &&
      budgetKey.layerSize == context.layerSize &&
      budgetKey.materialSize == context.modifierSize &&
      budgetKey.interactionTopology === interactionTopology &&
      budgetKey.interactionRadiusFraction == interaction.radiusFraction
    ) {
      checkNotNull(budgetCacheDecision)
    } else {
      resolveGlassRenderBudget(
        requestedScale = requestedScale,
        requestedPlan = balancedPlan.takeIf {
          requestedScale == GlassInputScalePolicy.BALANCED_SCALE
        },
        buildPlan = buildPlan,
      ).also { resolvedDecision ->
        budgetCacheKey = GlassRenderBudgetCacheKey(
          style = style,
          requestedScale = requestedScale,
          layerSize = context.layerSize,
          materialSize = context.modifierSize,
          interactionTopology = interactionTopology,
          interactionRadiusFraction = interaction.radiusFraction,
        )
        budgetCacheDecision = resolvedDecision
      }
    }
    if (decision !is GlassRenderBudgetDecision.Runtime) {
      clearPreparedRenderCache()
      return updateRenderPreparation(decision, null)
    }
    if (!runtimeShaderSupported) {
      return updateRenderPreparation(decision, null)
    }
    val coordinates = resolvePreparedCoordinates(
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      materialSize = context.size,
      scaleFactor = decision.scaleFactor,
    )
    val preparedCacheKey = preparedRenderCacheKey
    if (
      preparedCacheKey != null &&
      style === preparedCacheKey.style &&
      coordinates === preparedCacheKey.coordinates &&
      interaction === preparedCacheKey.interaction &&
      interactionTopology === preparedCacheKey.interactionTopology
    ) {
      return updateRenderPreparation(decision, checkNotNull(preparedRenderCache))
    }
    val previousStyle = preparedCacheKey?.style
    val previousCoordinates = preparedCacheKey?.coordinates
    val previousInteraction = preparedCacheKey?.interaction
    val previousPrepared = preparedRenderCache
    val params = if (
      previousStyle != null && previousCoordinates != null && previousPrepared != null &&
      coordinates === previousCoordinates &&
      style.hasSameRenderParams(previousStyle)
    ) {
      previousPrepared.params
    } else {
      buildGlassRenderParams(style, coordinates)
    }
    val interactionUniforms = if (
      previousCoordinates != null && previousInteraction != null && previousPrepared != null &&
      coordinates === previousCoordinates &&
      interaction === previousInteraction
    ) {
      previousPrepared.interactionUniforms
    } else {
      interaction.uniforms(coordinates)
    }
    val exactPrepared = buildGlassPreparedRender(
      params = params,
      interactionUniforms = interactionUniforms,
      interactionTopology = interactionTopology,
      interactionRadiusFraction = interaction.radiusFraction,
      alpha = style.alpha,
      outputSize = context.modifierSize.roundToIntSize(),
      previous = previousPrepared,
    )
    if (!exactPrepared.plan.fitsGlassRenderBudget()) {
      val fallback = GlassRenderBudgetDecision.Fallback(
        GlassRenderBudgetFallbackReason.ExceedsLimits,
      )
      budgetCacheDecision = fallback
      clearPreparedRenderCache()
      return updateRenderPreparation(fallback, null)
    }
    val validatedDecision = if (decision.plan == exactPrepared.plan) {
      decision
    } else {
      GlassRenderBudgetDecision.Runtime(decision.scaleFactor, exactPrepared.plan)
        .also { budgetCacheDecision = it }
    }
    val prepared = if (exactPrepared.plan === validatedDecision.plan) {
      exactPrepared
    } else {
      exactPrepared.copy(plan = validatedDecision.plan)
    }
    preparedRenderCacheKey = GlassPreparedRenderCacheKey(
      style = style,
      coordinates = coordinates,
      interaction = interaction,
      interactionTopology = interactionTopology,
    )
    preparedRenderCache = prepared
    return updateRenderPreparation(validatedDecision, prepared)
  }

  private fun updateRenderPreparation(
    decision: GlassRenderBudgetDecision,
    prepared: GlassPreparedRender?,
  ): GlassRenderPreparation {
    renderPreparation.decision = decision
    renderPreparation.prepared = prepared
    return renderPreparation
  }

  private fun clearPreparedRenderCache() {
    preparedRenderCacheKey = null
    preparedRenderCache = null
  }

  internal fun prepareRenderBudget(
    context: HazeEffectRuntimeDrawScope,
    runtimeShaderSupported: Boolean,
  ): GlassRenderBudgetDecision {
    val previousBudget = preparedRenderBudget
    val preparation = resolveGlassRenderPreparation(
      context = context,
      runtimeShaderSupported = runtimeShaderSupported,
    )
    preparedRenderBudget = preparation.decision
    preparedRender = preparation.prepared
    if (previousBudget != preparation.decision) {
      when (val decision = preparation.decision) {
        is GlassRenderBudgetDecision.Fallback -> HazeLogger.d(TAG) {
          "Glass render budget selected fallback: ${decision.reason}"
        }
        is GlassRenderBudgetDecision.Runtime -> {
          if (decision.scaleFactor < resolvedInputScale) {
            HazeLogger.d(TAG) {
              "Glass render budget reduced scale from $resolvedInputScale to ${decision.scaleFactor}"
            }
          }
        }
      }
    }
    return preparation.decision
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: GlassNodeConfiguration): Rect {
    return calculateLayerBounds(modifierBounds, this)
  }

  internal fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val resolvedStyle = resolveGlassStyle(
      effect = this@GlassRuntimeEffect,
      materialSizePx = rect.size,
      density = density,
      layoutDirection = LayoutDirection.Ltr,
    )
    val resolved = resolvedStyle.resolvedOptics
    val paddingPx = calculateGlassSamplePaddingPx(
      blurRadiusPx = if (resolved.depth <= 0f) 0f else resolved.blurRadiusPx,
      refractionScale = resolved.refractionScalePx,
      refractionStrength = (
        resolved.refractionStrength * maximumInteractionRefractionMultiplier()
        ).coerceIn(0f, 1f),
      chromaticAberrationStrength = resolvedStyle.chromaticAberrationStrength,
      edgeSoftnessPx = resolvedStyle.edgeSoftnessPx,
      foregroundOutsetPx = 0f,
    )
    return rect.inflate(paddingPx)
  }

  override fun shouldPreferClipToInputBounds(): Boolean = !shouldClipToNodeBounds()

  private fun maximumInteractionRefractionMultiplier(): Float =
    interactionTopologySnapshot.maxRefractionMultiplier

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.prepareDraw(context: HazeEffectRuntimeDrawScope) = Unit
    fun DrawScope.draw(context: HazeEffectRuntimeDrawScope)
    fun DrawScope.drawForeground(context: HazeEffectRuntimeDrawScope) = Unit
    fun detach() = Unit
    fun release() = detach()
    fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) = Unit
  }

  internal fun release() {
    delegate.release()
  }

  internal fun resetDirtyTracker() {
    dirtyTracker = Bitmask()
  }

  private fun markDirty(fields: Int) {
    val updated = dirtyTracker + fields
    if (updated != dirtyTracker) {
      dirtyTracker = updated
      dirtyTrackerVersion++
    }
    if (fields and GlassDirtyFields.StyleResolutionFlags != 0) {
      resolvedStyleCache = null
    }
    if (fields and GlassDirtyFields.ClipDecisionFlags != 0) {
      shouldClipToNodeBoundsCacheValid = false
    }
  }

  private fun DrawScope.selectDelegateForDraw(context: HazeEffectRuntimeDrawScope) {
    if (needsDelegateSelection) {
      delegate = updateDelegate()
      needsDelegateSelection = false
    }
  }

  internal companion object {
    const val TAG = "GlassRuntimeEffect"
  }
}

private class GlassRenderPreparation(
  var decision: GlassRenderBudgetDecision,
  var prepared: GlassPreparedRender?,
)

internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean

  fun shouldDrawRetainedOutput(): Boolean = canDrawRetainedOutput()

  fun clearRetainedOutput()
}

internal fun GlassRuntimeEffect.updateDelegate(): GlassRuntimeEffect.Delegate {
  val wantsRuntime =
    preparedRenderBudget is GlassRenderBudgetDecision.Runtime &&
      preparedRender != null &&
      !runtimeShaderIncompatible
  return when {
    wantsRuntime &&
      (
        delegate !is RuntimeShaderGlassDelegate ||
          GlassDirtyFields.RuntimeEffectFactory in dirtyTracker
        ) ->
      RuntimeShaderGlassDelegate(this, runtimeEffectFactory)
    !wantsRuntime && delegate !is FallbackGlassDelegate -> FallbackGlassDelegate(this)
    else -> delegate
  }
}

internal expect fun isRuntimeShaderGlassSupported(): Boolean

internal fun RoundedCornerShape.hasZeroCornerRadii(): Boolean {
  // Use unit values to check if all corner sizes resolve to zero.
  val unitSize = androidx.compose.ui.geometry.Size(1f, 1f)
  val unitDensity = androidx.compose.ui.unit.Density(1f)
  return topStart.toPx(unitSize, unitDensity) == 0f &&
    topEnd.toPx(unitSize, unitDensity) == 0f &&
    bottomEnd.toPx(unitSize, unitDensity) == 0f &&
    bottomStart.toPx(unitSize, unitDensity) == 0f
}

internal inline fun Float.takeOrElse(default: () -> Float): Float {
  return if (this.isNaN()) default() else this
}

internal fun Float.hasSameOverrideValueAs(other: Float): Boolean =
  this == other || isNaN() && other.isNaN()

internal inline fun Float.hasSameNormalizedOverrideValueAs(
  other: Float,
  normalize: (Float) -> Float,
): Boolean = hasSameOverrideValueAs(other) ||
  isFinite() && other.isFinite() && normalize(this) == normalize(other)

internal fun reducedMotion(
  policy: GlassReducedMotionPolicy,
  systemScale: Float,
): Pair<Boolean, Boolean> = when (policy) {
  GlassReducedMotionPolicy.System -> (systemScale == 0f) to false
  GlassReducedMotionPolicy.Reduced -> true to false
  GlassReducedMotionPolicy.Full -> false to true
}
