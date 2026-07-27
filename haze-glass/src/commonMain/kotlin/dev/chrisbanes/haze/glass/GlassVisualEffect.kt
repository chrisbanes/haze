// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.takeOrElse
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InteractiveVisualEffect
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.VisualEffectTransform
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
  val inputScale: HazeInputScale,
  val density: Density,
  val layoutDirection: LayoutDirection,
  val style: ResolvedGlassStyle?,
  val interactionState: GlassInteractionRenderState,
  val interactionTopology: GlassInteractionTopology,
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
 * A [VisualEffect] implementation that renders a translucent refractive glass material with
 * refraction, depth layering, specular highlights, and a soft tint.
 *
 * Use one instance per `hazeEffect` node. A Glass effect retains node-specific layers and
 * interaction state, so sharing an instance between nodes is unsupported. Use the
 * `GlassVisualEffect(other)` copy constructor to duplicate configuration for another node.
 */
@ExperimentalHazeApi
@Stable
@OptIn(InternalHazeApi::class)
public class GlassVisualEffect() : VisualEffect, RetainedOutputVisualEffect, InteractiveVisualEffect {

  /** Creates a new [GlassVisualEffect] copying all properties from [other]. */
  public constructor(other: GlassVisualEffect) : this() {
    _optics = other._optics
    _specularIntensity = other._specularIntensity
    _ambientResponse = other._ambientResponse
    _tint = other._tint
    _edgeSoftness = other._edgeSoftness
    _lightPosition = other._lightPosition
    _chromaticAberrationStrength = other._chromaticAberrationStrength
    _surfaceProfile = other._surfaceProfile
    _chromaticAberrationMode = other._chromaticAberrationMode
    _shape = other._shape
    _alpha = other._alpha
    _contrast = other._contrast
    _whitePoint = other._whitePoint
    _chromaMultiplier = other._chromaMultiplier
    _contentNormalBlend = other._contentNormalBlend
    _specularExponent = other._specularExponent
    _fresnelExponent = other._fresnelExponent
    compositionLocalStyle = other.compositionLocalStyle
    style = other.style
    nextInteractionRevision = other.nextInteractionRevision
    hoveredSlot = other.hoveredSlot
    focusedSlot = other.focusedSlot
    pressedSlot = other.pressedSlot
    interactionSource = other.interactionSource
    interactionLightRadiusFraction = other.interactionLightRadiusFraction
    interactionTransformTarget = other.interactionTransformTarget
    interactionTransformPivot = other.interactionTransformPivot
    interactionPositionAnimationSpec = other.interactionPositionAnimationSpec
    interactionReducedMotionPolicy = other.interactionReducedMotionPolicy
    refreshInteractionSnapshots()
  }

  private var isAttached: Boolean = false

  private var attachedContext: VisualEffectContext? = null

  private var interactionController: GlassInteractionController? = null

  internal val interactionControllerForTest: GlassInteractionController?
    get() = interactionController

  internal val attachedContextForTest: VisualEffectContext?
    get() = attachedContext

  internal val currentInteractionState: GlassInteractionRenderState
    get() = interactionController?.renderState ?: IdleInteractionState

  internal val currentInteractionSignals: GlassInteractionSignals
    get() = interactionController?.currentSignals ?: IdleInteractionSignals

  private var needsDelegateSelection: Boolean = true

  internal var preparedRenderBudget: GlassRenderBudgetDecision =
    GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry)
    private set

  internal var preparedRender: GlassPreparedRender? = null
    private set

  private var preparedRenderCacheKey: GlassPreparedRenderCacheKey? = null
  private var preparedRenderCache: GlassPreparedRender? = null

  private var budgetCacheKey: GlassRenderBudgetCacheKey? = null
  private var budgetCacheDecision: GlassRenderBudgetDecision? = null

  private var preparedDrawCacheKey: GlassPreparedDrawCacheKey? = null

  private var dirtyTrackerVersion: Int by mutableIntStateOf(0)
  internal var dirtyTracker: Bitmask = Bitmask()
    private set

  private var resolvedStyleCache: ResolvedGlassStyle? = null
  private var resolvedStyleCacheSize: Size = Size.Unspecified
  private var resolvedStyleCacheDensity: Density? = null
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

  private var nextInteractionRevision: Long = 0L

  internal var hoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var focusedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var pressedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  private var interactionSlotsSnapshot: GlassInteractionSlots = GlassInteractionSlots()
  private var interactionTopologySnapshot: GlassInteractionTopology =
    interactionSlotsSnapshot.resolveInteractionTopology()

  internal val interactionSlots: GlassInteractionSlots
    get() = interactionSlotsSnapshot

  private class InteractionSlotTransaction(effect: GlassVisualEffect) {
    var hovered: GlassInteractionResponse? = effect.hoveredSlot?.response
    var focused: GlassInteractionResponse? = effect.focusedSlot?.response
    var pressed: GlassInteractionResponse? = effect.pressedSlot?.response
  }

  private var interactionSlotTransaction: InteractionSlotTransaction? = null

  override val observesPointerEvents: Boolean
    get() = hoveredSlot != null || pressedSlot != null

  private var _interactionSource: InteractionSource? by mutableStateOf(
    null,
    referentialEqualityPolicy(),
  )

  public var interactionSource: InteractionSource?
    get() = _interactionSource
    set(value) {
      if (_interactionSource !== value) {
        HazeLogger.d(TAG) { "interactionSource changed. Current: $_interactionSource. New: $value" }
        _interactionSource = value
        onInteractionConfigurationChanged()
      }
    }

  private var _interactionLightRadiusFraction: Float by mutableStateOf(
    GlassDefaults.interactionLightRadiusFraction,
  )

  public var interactionLightRadiusFraction: Float
    get() = _interactionLightRadiusFraction
    set(value) {
      require(value.isFinite() && value in 0f..2f) {
        "interactionLightRadiusFraction must be finite and in range"
      }
      if (_interactionLightRadiusFraction != value) {
        HazeLogger.d(TAG) { "interactionLightRadiusFraction changed. Current: $_interactionLightRadiusFraction. New: $value" }
        _interactionLightRadiusFraction = value
        onInteractionConfigurationChanged()
      }
    }

  private var _interactionTransformTarget: GlassTransformTarget by mutableStateOf(
    GlassTransformTarget.MaterialOnly,
  )

  public var interactionTransformTarget: GlassTransformTarget
    get() = _interactionTransformTarget
    set(value) {
      if (_interactionTransformTarget != value) {
        HazeLogger.d(TAG) { "interactionTransformTarget changed. Current: $_interactionTransformTarget. New: $value" }
        _interactionTransformTarget = value
        onInteractionConfigurationChanged()
      }
    }

  private var _interactionTransformPivot: GlassTransformPivot by mutableStateOf(
    GlassTransformPivot.Pointer,
  )

  public var interactionTransformPivot: GlassTransformPivot
    get() = _interactionTransformPivot
    set(value) {
      if (_interactionTransformPivot != value) {
        HazeLogger.d(TAG) { "interactionTransformPivot changed. Current: $_interactionTransformPivot. New: $value" }
        _interactionTransformPivot = value
        onInteractionConfigurationChanged()
      }
    }

  private var _interactionPositionAnimationSpec: FiniteAnimationSpec<Offset> by mutableStateOf(
    GlassDefaults.positionAnimationSpec,
  )

  public var interactionPositionAnimationSpec: FiniteAnimationSpec<Offset>
    get() = _interactionPositionAnimationSpec
    set(value) {
      if (_interactionPositionAnimationSpec != value) {
        HazeLogger.d(TAG) { "interactionPositionAnimationSpec changed. Current: $_interactionPositionAnimationSpec. New: $value" }
        _interactionPositionAnimationSpec = value
        onInteractionConfigurationChanged()
      }
    }

  private var _interactionReducedMotionPolicy: GlassReducedMotionPolicy by mutableStateOf(
    GlassReducedMotionPolicy.System,
  )

  public var interactionReducedMotionPolicy: GlassReducedMotionPolicy
    get() = _interactionReducedMotionPolicy
    set(value) {
      if (_interactionReducedMotionPolicy != value) {
        HazeLogger.d(TAG) { "interactionReducedMotionPolicy changed. Current: $_interactionReducedMotionPolicy. New: $value" }
        _interactionReducedMotionPolicy = value
        onInteractionConfigurationChanged()
      }
    }

  public fun hovered() {
    setHovered(defaultHoverResponse())
  }

  public fun hovered(block: GlassInteractionScope.() -> Unit) {
    setHovered(buildGlassInteractionResponse(block))
  }

  public fun focused() {
    setFocused(defaultFocusResponse())
  }

  public fun focused(block: GlassInteractionScope.() -> Unit) {
    setFocused(buildGlassInteractionResponse(block))
  }

  public fun pressed() {
    setPressed(defaultPressResponse())
  }

  public fun pressed(block: GlassInteractionScope.() -> Unit) {
    setPressed(buildGlassInteractionResponse(block))
  }

  public fun interactable() {
    hovered()
    focused()
    pressed()
  }

  public fun clearHovered() {
    interactionSlotTransaction?.let {
      it.hovered = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  public fun clearFocused() {
    interactionSlotTransaction?.let {
      it.focused = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    focusedSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  public fun clearPressed() {
    interactionSlotTransaction?.let {
      it.pressed = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    pressedSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  public fun clearInteractions() {
    interactionSlotTransaction?.let {
      it.hovered = null
      it.focused = null
      it.pressed = null
      return
    }
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredSlot = null
    focusedSlot = null
    pressedSlot = null
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setHovered(response: GlassInteractionResponse) {
    interactionSlotTransaction?.let {
      it.hovered = response
      return
    }
    if (hoveredSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    hoveredSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setFocused(response: GlassInteractionResponse) {
    interactionSlotTransaction?.let {
      it.focused = response
      return
    }
    if (focusedSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    focusedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun setPressed(response: GlassInteractionResponse) {
    interactionSlotTransaction?.let {
      it.pressed = response
      return
    }
    if (pressedSlot?.response == response) return
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    pressedSlot = GlassInteractionSlot(++nextInteractionRevision, response)
    onInteractionConfigurationChanged(previousRefractionMultiplier)
  }

  private fun onInteractionConfigurationChanged(previousRefractionMultiplier: Float? = null) {
    refreshInteractionSnapshots()
    markDirty(GlassDirtyFields.Interaction)
    if (
      previousRefractionMultiplier != null &&
      previousRefractionMultiplier != maximumInteractionRefractionMultiplier()
    ) {
      markDirty(GlassDirtyFields.InteractionLayerBounds)
    }
    if (hoveredSlot == null && focusedSlot == null && pressedSlot == null) {
      attachedContext?.let(::syncInteractionController)
    }
  }

  private fun refreshInteractionSnapshots() {
    val slots = GlassInteractionSlots(
      focused = focusedSlot,
      hovered = hoveredSlot,
      pressed = pressedSlot,
    )
    if (slots != interactionSlotsSnapshot) {
      interactionSlotsSnapshot = slots
      interactionTopologySnapshot = slots.resolveInteractionTopology()
    }
  }

  @PublishedApi
  internal fun beginInteractionSlotTransaction(): Boolean {
    if (interactionSlotTransaction != null) return false
    interactionSlotTransaction = InteractionSlotTransaction(this)
    return true
  }

  @PublishedApi
  internal fun commitInteractionSlotTransaction(ownsTransaction: Boolean) {
    if (!ownsTransaction) return
    val transaction = checkNotNull(interactionSlotTransaction)
    interactionSlotTransaction = null
    commitInteractionSlots(transaction)
  }

  @PublishedApi
  internal fun rollbackInteractionSlotTransaction(ownsTransaction: Boolean) {
    if (!ownsTransaction) return
    interactionSlotTransaction = null
  }

  private fun commitInteractionSlots(transaction: InteractionSlotTransaction) {
    val previousRefractionMultiplier = maximumInteractionRefractionMultiplier()
    var changed = false
    if (hoveredSlot?.response != transaction.hovered) {
      hoveredSlot = transaction.hovered?.let { GlassInteractionSlot(++nextInteractionRevision, it) }
      changed = true
    }
    if (focusedSlot?.response != transaction.focused) {
      focusedSlot = transaction.focused?.let { GlassInteractionSlot(++nextInteractionRevision, it) }
      changed = true
    }
    if (pressedSlot?.response != transaction.pressed) {
      pressedSlot = transaction.pressed?.let { GlassInteractionSlot(++nextInteractionRevision, it) }
      changed = true
    }
    if (changed) {
      onInteractionConfigurationChanged(previousRefractionMultiplier)
    }
  }

  internal var delegate: Delegate = FallbackGlassDelegate(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "delegate changed. Current $field. New: $value" }
        val old = field
        field = value
        if (isAttached) {
          old.detach()
          value.attach()
        }
      }
    }

  override fun attach(context: VisualEffectContext) {
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

  override fun detach(context: VisualEffectContext) {
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
      delegate.detach()
    }
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

  override fun update(context: VisualEffectContext) {
    dirtyTrackerVersion
    compositionLocalStyle = context.currentValueOf(LocalGlassStyle)
    syncInteractionController(context)

    if (dirtyTracker.any(GlassDirtyFields.LayerBoundsFlags)) {
      context.invalidateLayerBounds()
    }
    if (dirtyTracker.any(GlassDirtyFields.InvalidateFlags)) {
      needsDelegateSelection = true
      context.invalidateDraw()
    }
  }

  override fun onPointerEvent(event: PointerEvent, context: VisualEffectContext) {
    interactionController?.onPointerEvent(event, context.size)
  }

  override fun onCancelPointerInput(context: VisualEffectContext) {
    interactionController?.cancelPointerInput(context.size)
  }

  internal fun setPressedForTest(position: Offset, pressed: Boolean = true) {
    val context = attachedContext ?: return
    interactionController?.setRawPressedForTest(pressed, position, context.size)
  }

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    trace(GlassTraceSection.Prepare) {
      if (canReusePreparedDraw(context)) return@trace
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
        with(delegate) { prepareDraw(context) }
      }
      cachePreparedDrawInputs(context)
    }
  }

  private fun canReusePreparedDraw(context: VisualEffectContext): Boolean {
    val key = preparedDrawCacheKey ?: return false
    val style = resolvedStyleCache ?: return false
    val interactionState = interactionRenderState(context)
    return preparedRender != null &&
      (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true &&
      key.dirtyTrackerVersion == dirtyTrackerVersion &&
      key.size == context.size &&
      key.layerSize == context.layerSize &&
      key.layerOffset == context.layerOffset &&
      key.inputScale == context.inputScale &&
      key.density == context.requireDensity() &&
      key.layoutDirection == context.currentValueOf(LocalLayoutDirection) &&
      key.style === style &&
      key.interactionState === interactionState &&
      key.interactionTopology === interactionTopologySnapshot
  }

  private fun cachePreparedDrawInputs(context: VisualEffectContext) {
    preparedDrawCacheKey = GlassPreparedDrawCacheKey(
      dirtyTrackerVersion = dirtyTrackerVersion,
      size = context.size,
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      inputScale = context.inputScale,
      density = context.requireDensity(),
      layoutDirection = context.currentValueOf(LocalLayoutDirection),
      style = resolvedStyleCache,
      interactionState = interactionRenderState(context),
      interactionTopology = interactionTopologySnapshot,
    )
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    try {
      selectDelegateForDraw(context)
      withMaterialTransform(context) {
        with(delegate) { draw(context) }
      }
    } finally {
      resetDirtyTracker()
    }
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    withMaterialTransform(context) {
      with(delegate) { drawForeground(context) }
    }
  }

  override fun currentContentTransform(context: VisualEffectContext): VisualEffectTransform {
    return resolveTransform(context, GlassTransformTarget.MaterialAndContent)
  }

  internal fun currentMaterialTransform(context: VisualEffectContext): VisualEffectTransform {
    return resolveTransform(context, GlassTransformTarget.MaterialOnly)
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
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

  internal fun interactionRenderState(context: VisualEffectContext): GlassInteractionRenderState {
    interactionController?.let { return it.renderState }
    if (idleInteractionRenderStateSize != context.size) {
      idleInteractionRenderStateSize = context.size
      idleInteractionRenderState = GlassInteractionRenderState(position = context.size.center)
    }
    return idleInteractionRenderState
  }

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

  private inline fun DrawScope.withMaterialTransform(
    context: VisualEffectContext,
    block: DrawScope.() -> Unit,
  ) {
    val transform = currentMaterialTransform(context)
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

  private fun syncInteractionController(context: VisualEffectContext) {
    if (hoveredSlot == null && focusedSlot == null && pressedSlot == null) {
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
    controller.updateInteractionSource(interactionSource, context.size)
  }

  private fun systemMotionScale(context: VisualEffectContext): Float {
    return context.coroutineScope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    delegate.onTrimMemory(context, level)
  }

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean {
    return (delegate as? RetainedOutputDelegate)?.canDrawRetainedOutput() == true
  }

  override fun shouldDrawRetainedOutput(context: VisualEffectContext): Boolean {
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

  internal fun resolveInputScaleFactor(scale: HazeInputScale): Float = when {
    scale === HazeInputScale.Auto -> 0.75f
    scale is HazeInputScale.Fixed -> scale.scale
    else -> 1f
  }

  internal fun resolveGlassRenderBudget(context: VisualEffectContext): GlassRenderBudgetDecision {
    return resolveGlassRenderPreparation(context, runtimeShaderSupported = true).decision
  }

  private fun resolvePreparedStyle(context: VisualEffectContext): ResolvedGlassStyle {
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    resolvedStyleCache?.takeIf {
      resolvedStyleCacheSize == context.size &&
        resolvedStyleCacheDensity == density &&
        resolvedStyleCacheLayoutDirection == layoutDirection
    }?.let { return it }

    val resolvedStyle = geometrySnapshotObserver?.let { observer ->
      lateinit var observedStyle: ResolvedGlassStyle
      observer.observeReads(styleObservationScope, onObservedStyleChanged) {
        observedStyle = resolveGlassStyle(this, context.size, density, layoutDirection)
      }
      observedStyle
    } ?: resolveGlassStyle(this, context.size, density, layoutDirection)

    return resolvedStyle.also {
      resolvedStyleCache = it
      resolvedStyleCacheSize = context.size
      resolvedStyleCacheDensity = density
      resolvedStyleCacheLayoutDirection = layoutDirection
    }
  }

  private fun resolvePreparedInteraction(
    context: VisualEffectContext,
  ): ResolvedGlassInteraction {
    val state = interactionRenderState(context)
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
    context: VisualEffectContext,
    runtimeShaderSupported: Boolean,
  ): GlassRenderPreparation {
    val requestedScale = resolveInputScaleFactor(context.inputScale)
    if (
      !requestedScale.isFinite() || requestedScale <= 0f ||
      !context.size.isDrawable() || !context.layerSize.isDrawable()
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
    val budgetKey = budgetCacheKey
    val decision = if (
      budgetKey != null &&
      budgetKey.style.hasSameBudgetParams(style) &&
      budgetKey.requestedScale == requestedScale &&
      budgetKey.layerSize == context.layerSize &&
      budgetKey.materialSize == context.size &&
      budgetKey.interactionTopology === interactionTopology &&
      budgetKey.interactionRadiusFraction == interaction.radiusFraction
    ) {
      checkNotNull(budgetCacheDecision)
    } else {
      val allowMultiscaleBlur = optics.progressive == null
      resolveGlassRenderBudget(requestedScale) { scaleFactor ->
        val rawCoordinates = resolveGlassCoordinates(
          layerSize = context.layerSize,
          layerOffset = context.layerOffset,
          materialSize = context.size,
          scaleFactor = scaleFactor,
        )
        if (!rawCoordinates.materialSize.isDrawable() || !rawCoordinates.sampleSize.isDrawable()) {
          return@resolveGlassRenderBudget GlassRetainedLayerPlan(emptyList())
        }
        val coordinates = rawCoordinates.withRoundedSampleSize()
        if (!coordinates.materialSize.isDrawable() || !coordinates.sampleSize.isDrawable()) {
          return@resolveGlassRenderBudget GlassRetainedLayerPlan(emptyList())
        }
        val outputSize = context.size.roundToIntSize()
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
      }.also { resolvedDecision ->
        budgetCacheKey = GlassRenderBudgetCacheKey(
          style = style,
          requestedScale = requestedScale,
          layerSize = context.layerSize,
          materialSize = context.size,
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
      outputSize = context.size.roundToIntSize(),
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
    context: VisualEffectContext,
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
          val requestedScale = resolveInputScaleFactor(context.inputScale)
          if (decision.scaleFactor < requestedScale) {
            HazeLogger.d(TAG) {
              "Glass render budget reduced scale from $requestedScale to ${decision.scaleFactor}"
            }
          }
        }
      }
    }
    return preparation.decision
  }

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
    val resolvedStyle = resolveGlassStyle(
      effect = this,
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

  override fun shouldPreferClipToAreaBounds(): Boolean = !shouldClipToNodeBounds()

  private fun maximumInteractionRefractionMultiplier(): Float =
    interactionTopologySnapshot.maxRefractionMultiplier

  private var _optics: GlassOptics? = null

  /**
   * Complete optical configuration for this effect.
   *
   * A direct value takes precedence over [style], [LocalGlassStyle], and [GlassDefaults]. Call
   * [clearOpticsOverride] to restore the next complete inherited value.
   */
  public var optics: GlassOptics
    get() = _optics ?: style.optics ?: compositionLocalStyle.optics ?: GlassDefaults.optics
    set(value) {
      if (value != _optics) {
        HazeLogger.d(TAG) { "optics changed. Current: $_optics. New: $value" }
        _optics = value
        markDirty(GlassDirtyFields.Optics)
      }
    }

  /**
   * Clears the direct [optics] override and restores inherited values from [style] and
   * [LocalGlassStyle].
   */
  public fun clearOpticsOverride() {
    if (_optics != null) {
      HazeLogger.d(TAG) { "optics override cleared. Current: $_optics" }
      _optics = null
      markDirty(GlassDirtyFields.Optics)
    }
  }

  /**
   * Intensity of specular highlights, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.specularIntensity] value set in [style], if specified.
   *  - [GlassStyle.specularIntensity] value set in the [LocalGlassStyle] composition local.
   */
  private var _specularIntensity: Float = Float.NaN
  public var specularIntensity: Float
    get() = _specularIntensity
      .takeOrElse { styleLighting.specularIntensity }
      .takeOrElse { localLighting.specularIntensity }
      .takeOrElse { GlassDefaults.specularIntensity }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_specularIntensity.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "specularIntensity changed. Current: $_specularIntensity. New: $value" }
        _specularIntensity = normalized
        markDirty(GlassDirtyFields.SpecularIntensity)
      }
    }

  /**
   * Strength of ambient lighting response and Fresnel accent.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.ambientResponse] value set in [style], if specified.
   *  - [GlassStyle.ambientResponse] value set in the [LocalGlassStyle] composition local.
   */
  private var _ambientResponse: Float = Float.NaN
  public var ambientResponse: Float
    get() = _ambientResponse
      .takeOrElse { styleLighting.ambientResponse }
      .takeOrElse { localLighting.ambientResponse }
      .takeOrElse { GlassDefaults.ambientResponse }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_ambientResponse.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "ambientResponse changed. Current: $_ambientResponse. New: $value" }
        _ambientResponse = normalized
        markDirty(GlassDirtyFields.AmbientResponse)
      }
    }

  /**
   * Glass tint applied to the refracted content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.tint] value set in [style], if specified.
   *  - [GlassStyle.tint] value set in the [LocalGlassStyle] composition local.
   */
  private var _tint: Color = Color.Unspecified
  public var tint: Color
    get() = _tint
      .takeOrElse { style.tint }
      .takeOrElse { compositionLocalStyle.tint }
      .takeOrElse { GlassDefaults.tint }
    set(value) {
      if (_tint != value) {
        HazeLogger.d(TAG) { "tint changed. Current: $_tint. New: $value" }
        _tint = value
        markDirty(GlassDirtyFields.Tint)
      }
    }

  /**
   * Softening distance for glass edges.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.edgeSoftness] value set in [style], if specified.
   *  - [GlassStyle.edgeSoftness] value set in the [LocalGlassStyle] composition local.
   */
  private var _edgeSoftness: Dp = Dp.Unspecified
  public var edgeSoftness: Dp
    get() = _edgeSoftness
      .takeOrElse { styleRendering.edgeSoftness }
      .takeOrElse { localRendering.edgeSoftness }
      .takeOrElse { GlassDefaults.edgeSoftness }
    set(value) {
      if (_edgeSoftness != value) {
        HazeLogger.d(TAG) { "edgeSoftness changed. Current: $_edgeSoftness. New: $value" }
        _edgeSoftness = value
        markDirty(GlassDirtyFields.EdgeSoftness)
      }
    }

  /**
   * Position of the virtual light source. When unspecified, the center of the layer is used.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.lightPosition] value set in [style], if specified.
   *  - [GlassStyle.lightPosition] value set in the [LocalGlassStyle] composition local.
   *
   * If no value is specified through any of the above, the delegate falls back to the
   * center of the layer at draw time.
   */
  private var _lightPosition: Offset = Offset.Unspecified
  public var lightPosition: Offset
    get() = _lightPosition
      .takeOrElse { styleLighting.lightPosition }
      .takeOrElse { localLighting.lightPosition }
    set(value) {
      if (_lightPosition != value) {
        HazeLogger.d(TAG) { "lightPosition changed. Current: $_lightPosition. New: $value" }
        _lightPosition = value
        markDirty(GlassDirtyFields.LightPosition)
      }
    }

  /**
   * Strength of chromatic aberration, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.chromaticAberrationStrength] value set in [style], if specified.
   *  - [GlassStyle.chromaticAberrationStrength] value set in the [LocalGlassStyle] composition local.
   */
  private var _chromaticAberrationStrength: Float = Float.NaN
  public var chromaticAberrationStrength: Float
    get() = _chromaticAberrationStrength
      .takeOrElse { styleRendering.chromaticAberrationStrength }
      .takeOrElse { localRendering.chromaticAberrationStrength }
      .takeOrElse { GlassDefaults.chromaticAberrationStrength }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_chromaticAberrationStrength.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) {
          "chromaticAberrationStrength changed. Current: $_chromaticAberrationStrength. New: $value"
        }
        _chromaticAberrationStrength = normalized
        markDirty(GlassDirtyFields.ChromaticAberration)
      }
    }

  /**
   * Surface cross-section profile used for the refraction bezel.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.surfaceProfile] value set in [style], if specified.
   *  - [GlassStyle.surfaceProfile] value set in the [LocalGlassStyle] composition local.
   */
  private var _surfaceProfile: SurfaceProfile? = null

  public var surfaceProfile: SurfaceProfile
    get() = _surfaceProfile ?: styleRendering.surfaceProfile ?: localRendering.surfaceProfile ?: GlassDefaults.surfaceProfile
    set(value) {
      if (value != _surfaceProfile) {
        HazeLogger.d(TAG) { "surfaceProfile changed. Current: $_surfaceProfile. New: $value" }
        _surfaceProfile = value
        markDirty(GlassDirtyFields.SurfaceProfile)
      }
    }

  /**
   * Clears the direct [surfaceProfile] override and restores inherited values from [style] and
   * [LocalGlassStyle].
   */
  public fun clearSurfaceProfileOverride() {
    if (_surfaceProfile != null) {
      HazeLogger.d(TAG) { "surfaceProfile override cleared. Current: $_surfaceProfile" }
      _surfaceProfile = null
      markDirty(GlassDirtyFields.SurfaceProfile)
    }
  }

  /**
   * Quality mode for chromatic aberration (color dispersion).
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.chromaticAberrationMode] value set in [style], if specified.
   *  - [GlassStyle.chromaticAberrationMode] value set in the [LocalGlassStyle] composition local.
   */
  private var _chromaticAberrationMode: ChromaticAberrationMode? = null

  public var chromaticAberrationMode: ChromaticAberrationMode
    get() = _chromaticAberrationMode ?: styleRendering.chromaticAberrationMode ?: localRendering.chromaticAberrationMode ?: GlassDefaults.chromaticAberrationMode
    set(value) {
      if (value != _chromaticAberrationMode) {
        HazeLogger.d(TAG) { "chromaticAberrationMode changed. Current: $_chromaticAberrationMode. New: $value" }
        _chromaticAberrationMode = value
        markDirty(GlassDirtyFields.ChromaticAberrationMode)
      }
    }

  /**
   * Clears the direct [chromaticAberrationMode] override and restores inherited values from [style]
   * and [LocalGlassStyle].
   */
  public fun clearChromaticAberrationModeOverride() {
    if (_chromaticAberrationMode != null) {
      HazeLogger.d(TAG) { "chromaticAberrationMode override cleared. Current: $_chromaticAberrationMode" }
      _chromaticAberrationMode = null
      markDirty(GlassDirtyFields.ChromaticAberrationMode)
    }
  }

  /**
   * Shape applied to the glass. Defaults to [RoundedCornerShape] with 16.dp corners.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.shape] value set in [style], if specified.
   *  - [GlassStyle.shape] value set in the [LocalGlassStyle] composition local.
   */
  private var _shape: RoundedCornerShape? = null

  public var shape: RoundedCornerShape
    get() = _shape ?: style.shape ?: compositionLocalStyle.shape ?: GlassDefaults.shape
    set(value) {
      if (value != _shape) {
        HazeLogger.d(TAG) { "shape changed. Current: $_shape. New: $value" }
        _shape = value
        markDirty(GlassDirtyFields.Shape)
      }
    }

  /**
   * Clears the direct [shape] override and restores inherited values from [style] and
   * [LocalGlassStyle].
   */
  public fun clearShapeOverride() {
    if (_shape != null) {
      HazeLogger.d(TAG) { "shape override cleared. Current: $_shape" }
      _shape = null
      markDirty(GlassDirtyFields.Shape)
    }
  }

  /**
   * Opacity for the effect, in the range `0f..1f`.
   *
   * The base material is composited as one group. Rim and interaction lighting use the same
   * opacity in a separate foreground pass so that they remain above this node's content.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.alpha] value set in [style], if specified.
   *  - [GlassStyle.alpha] value set in the [LocalGlassStyle] composition local.
   */
  private var _alpha: Float = Float.NaN
  public var alpha: Float
    get() = _alpha
      .takeOrElse { styleColor.alpha }
      .takeOrElse { localColor.alpha }
      .takeOrElse { GlassDefaults.alpha }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_alpha.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "alpha changed. Current: $_alpha. New: $value" }
        _alpha = normalized
        markDirty(GlassDirtyFields.Alpha)
      }
    }

  /**
   * Overall contrast adjustment, in the range `-1f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.contrast] value set in [style], if specified.
   *  - [GlassStyle.contrast] value set in the [LocalGlassStyle] composition local.
   */
  private var _contrast: Float = Float.NaN
  public var contrast: Float
    get() = _contrast
      .takeOrElse { styleColor.contrast }
      .takeOrElse { localColor.contrast }
      .takeOrElse { GlassDefaults.contrast }
    set(value) {
      val normalized = value.coerceIn(-1f, 1f)
      if (!_contrast.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "contrast changed. Current: $_contrast. New: $value" }
        _contrast = normalized
        markDirty(GlassDirtyFields.Contrast)
      }
    }

  /**
   * White point adjustment, in the range `-1f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.whitePoint] value set in [style], if specified.
   *  - [GlassStyle.whitePoint] value set in the [LocalGlassStyle] composition local.
   */
  private var _whitePoint: Float = Float.NaN
  public var whitePoint: Float
    get() = _whitePoint
      .takeOrElse { styleColor.whitePoint }
      .takeOrElse { localColor.whitePoint }
      .takeOrElse { GlassDefaults.whitePoint }
    set(value) {
      val normalized = value.coerceIn(-1f, 1f)
      if (!_whitePoint.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "whitePoint changed. Current: $_whitePoint. New: $value" }
        _whitePoint = normalized
        markDirty(GlassDirtyFields.WhitePoint)
      }
    }

  /**
   * Chroma multiplier for saturation control, in the range `0f..2f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.chromaMultiplier] value set in [style], if specified.
   *  - [GlassStyle.chromaMultiplier] value set in the [LocalGlassStyle] composition local.
   */
  private var _chromaMultiplier: Float = Float.NaN
  public var chromaMultiplier: Float
    get() = _chromaMultiplier
      .takeOrElse { styleColor.chromaMultiplier }
      .takeOrElse { localColor.chromaMultiplier }
      .takeOrElse { GlassDefaults.chromaMultiplier }
    set(value) {
      val normalized = value.coerceIn(0f, 2f)
      if (!_chromaMultiplier.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "chromaMultiplier changed. Current: $_chromaMultiplier. New: $value" }
        _chromaMultiplier = normalized
        markDirty(GlassDirtyFields.ChromaMultiplier)
      }
    }

  /**
   * Blend factor for content normals, in the range `0f..1f`.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.contentNormalBlend] value set in [style], if specified.
   *  - [GlassStyle.contentNormalBlend] value set in the [LocalGlassStyle] composition local.
   */
  private var _contentNormalBlend: Float = Float.NaN
  public var contentNormalBlend: Float
    get() = _contentNormalBlend
      .takeOrElse { styleRendering.contentNormalBlend }
      .takeOrElse { localRendering.contentNormalBlend }
      .takeOrElse { GlassDefaults.contentNormalBlend }
    set(value) {
      val normalized = value.coerceIn(0f, 1f)
      if (!_contentNormalBlend.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "contentNormalBlend changed. Current: $_contentNormalBlend. New: $value" }
        _contentNormalBlend = normalized
        markDirty(GlassDirtyFields.ContentNormalBlend)
      }
    }

  /**
   * Exponent controlling specular highlight shape. A value of `0f` produces a full response.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.specularExponent] value set in [style], if specified.
   *  - [GlassStyle.specularExponent] value set in the [LocalGlassStyle] composition local.
   */
  private var _specularExponent: Float = Float.NaN
  public var specularExponent: Float
    get() = _specularExponent
      .takeOrElse { styleLighting.specularExponent }
      .takeOrElse { localLighting.specularExponent }
      .takeOrElse { GlassDefaults.specularExponent }
    set(value) {
      val normalized = value.coerceAtLeast(0f)
      if (!_specularExponent.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "specularExponent changed. Current: $_specularExponent. New: $value" }
        _specularExponent = normalized
        markDirty(GlassDirtyFields.SpecularExponent)
      }
    }

  /**
   * Exponent controlling Fresnel edge effect intensity. A value of `0f` produces a full response.
   *
   * There are precedence rules to how this styling property is applied:
   *
   *  - This property value, if specified.
   *  - [GlassStyle.fresnelExponent] value set in [style], if specified.
   *  - [GlassStyle.fresnelExponent] value set in the [LocalGlassStyle] composition local.
   */
  private var _fresnelExponent: Float = Float.NaN
  public var fresnelExponent: Float
    get() = _fresnelExponent
      .takeOrElse { styleLighting.fresnelExponent }
      .takeOrElse { localLighting.fresnelExponent }
      .takeOrElse { GlassDefaults.fresnelExponent }
    set(value) {
      val normalized = value.coerceAtLeast(0f)
      if (!_fresnelExponent.hasSameOverrideValueAs(normalized)) {
        HazeLogger.d(TAG) { "fresnelExponent changed. Current: $_fresnelExponent. New: $value" }
        _fresnelExponent = normalized
        markDirty(GlassDirtyFields.FresnelExponent)
      }
    }

  /**
   * Optional style container that can set multiple parameters at once.
   *
   * There are precedence rules to how each styling property is applied. The order of precedence
   * for each property are as follows:
   *
   *  - Property value set directly on this [GlassVisualEffect], if specified.
   *  - Value set here in [style], if specified.
   *  - Value set in the [LocalGlassStyle] composition local.
   */
  public var style: GlassStyle = GlassStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        onStyleChanged(old = field, new = value)
        field = value
        markDirty(GlassDirtyFields.Style)
      }
    }

  private val styleLighting: GlassLighting get() = style.lighting
  private val localLighting: GlassLighting get() = compositionLocalStyle.lighting
  private val styleColor: GlassColor get() = style.color
  private val localColor: GlassColor get() = compositionLocalStyle.color
  private val styleRendering: GlassRendering get() = style.rendering
  private val localRendering: GlassRendering get() = compositionLocalStyle.rendering

  internal var compositionLocalStyle: GlassStyle = GlassDefaults.style
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "LocalGlassStyle changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  internal interface Delegate {
    fun attach() = Unit
    fun DrawScope.prepareDraw(context: VisualEffectContext) = Unit
    fun DrawScope.draw(context: VisualEffectContext)
    fun DrawScope.drawForeground(context: VisualEffectContext) = Unit
    fun detach() = Unit
    fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) = Unit
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

  private fun DrawScope.selectDelegateForDraw(context: VisualEffectContext) {
    if (needsDelegateSelection) {
      delegate = updateDelegate()
      needsDelegateSelection = false
    }
  }

  private fun onStyleChanged(old: GlassStyle, new: GlassStyle) {
    if (old.optics != new.optics) {
      markDirty(GlassDirtyFields.Optics)
    }
    if (
      !old.lighting.specularIntensity.hasSameNormalizedOverrideValueAs(
        new.lighting.specularIntensity,
      ) { it.coerceIn(0f, 1f) }
    ) {
      markDirty(GlassDirtyFields.SpecularIntensity)
    }
    if (
      !old.lighting.ambientResponse.hasSameNormalizedOverrideValueAs(
        new.lighting.ambientResponse,
      ) { it.coerceIn(0f, 1f) }
    ) {
      markDirty(GlassDirtyFields.AmbientResponse)
    }
    if (old.lighting.lightPosition != new.lighting.lightPosition) {
      markDirty(GlassDirtyFields.LightPosition)
    }
    if (
      !old.lighting.specularExponent.hasSameNormalizedOverrideValueAs(
        new.lighting.specularExponent,
      ) { it.coerceAtLeast(0f) }
    ) {
      markDirty(GlassDirtyFields.SpecularExponent)
    }
    if (
      !old.lighting.fresnelExponent.hasSameNormalizedOverrideValueAs(
        new.lighting.fresnelExponent,
      ) { it.coerceAtLeast(0f) }
    ) {
      markDirty(GlassDirtyFields.FresnelExponent)
    }
    if (old.tint != new.tint) {
      markDirty(GlassDirtyFields.Tint)
    }
    if (old.shape != new.shape) {
      markDirty(GlassDirtyFields.Shape)
    }
    if (
      !old.color.alpha.hasSameNormalizedOverrideValueAs(new.color.alpha) { it.coerceIn(0f, 1f) }
    ) {
      markDirty(GlassDirtyFields.Alpha)
    }
    if (
      !old.color.contrast.hasSameNormalizedOverrideValueAs(new.color.contrast) {
        it.coerceIn(-1f, 1f)
      }
    ) {
      markDirty(GlassDirtyFields.Contrast)
    }
    if (
      !old.color.whitePoint.hasSameNormalizedOverrideValueAs(new.color.whitePoint) {
        it.coerceIn(-1f, 1f)
      }
    ) {
      markDirty(GlassDirtyFields.WhitePoint)
    }
    if (
      !old.color.chromaMultiplier.hasSameNormalizedOverrideValueAs(new.color.chromaMultiplier) {
        it.coerceIn(0f, 2f)
      }
    ) {
      markDirty(GlassDirtyFields.ChromaMultiplier)
    }
    if (old.rendering.edgeSoftness != new.rendering.edgeSoftness) {
      markDirty(GlassDirtyFields.EdgeSoftness)
    }
    if (
      !old.rendering.contentNormalBlend.hasSameNormalizedOverrideValueAs(
        new.rendering.contentNormalBlend,
      ) { it.coerceIn(0f, 1f) }
    ) {
      markDirty(GlassDirtyFields.ContentNormalBlend)
    }
    if (old.rendering.surfaceProfile != new.rendering.surfaceProfile) {
      markDirty(GlassDirtyFields.SurfaceProfile)
    }
    if (
      !old.rendering.chromaticAberrationStrength.hasSameNormalizedOverrideValueAs(
        new.rendering.chromaticAberrationStrength,
      ) { it.coerceIn(0f, 1f) }
    ) {
      markDirty(GlassDirtyFields.ChromaticAberration)
    }
    if (old.rendering.chromaticAberrationMode != new.rendering.chromaticAberrationMode) {
      markDirty(GlassDirtyFields.ChromaticAberrationMode)
    }
  }

  internal companion object {
    const val TAG = "GlassVisualEffect"
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

internal fun GlassVisualEffect.updateDelegate(): GlassVisualEffect.Delegate {
  val wantsRuntime =
    preparedRenderBudget is GlassRenderBudgetDecision.Runtime &&
      preparedRender != null
  return when {
    wantsRuntime && delegate !is RuntimeShaderGlassDelegate -> RuntimeShaderGlassDelegate(this)
    !wantsRuntime && delegate !is FallbackGlassDelegate -> FallbackGlassDelegate(this)
    else -> delegate
  }
}

internal expect fun isRuntimeShaderGlassSupported(): Boolean

private fun RoundedCornerShape.hasZeroCornerRadii(): Boolean {
  // Use unit values to check if all corner sizes resolve to zero.
  val unitSize = androidx.compose.ui.geometry.Size(1f, 1f)
  val unitDensity = androidx.compose.ui.unit.Density(1f)
  return topStart.toPx(unitSize, unitDensity) == 0f &&
    topEnd.toPx(unitSize, unitDensity) == 0f &&
    bottomEnd.toPx(unitSize, unitDensity) == 0f &&
    bottomStart.toPx(unitSize, unitDensity) == 0f
}

private inline fun Float.takeOrElse(default: () -> Float): Float {
  return if (this.isNaN()) default() else this
}

private fun Float.hasSameOverrideValueAs(other: Float): Boolean =
  this == other || isNaN() && other.isNaN()

private inline fun Float.hasSameNormalizedOverrideValueAs(
  other: Float,
  normalize: (Float) -> Float,
): Boolean = hasSameOverrideValueAs(other) ||
  isFinite() && other.isFinite() && normalize(this) == normalize(other)

private fun reducedMotion(
  policy: GlassReducedMotionPolicy,
  systemScale: Float,
): Pair<Boolean, Boolean> = when (policy) {
  GlassReducedMotionPolicy.System -> (systemScale == 0f) to false
  GlassReducedMotionPolicy.Reduced -> true to false
  GlassReducedMotionPolicy.Full -> false to true
}
