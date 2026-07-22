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

private data class GlassPreparedRenderCacheKey(
  val style: ResolvedGlassStyle,
  val coordinates: GlassCoordinates,
  val interaction: ResolvedGlassInteraction,
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

/**
 * A [VisualEffect] implementation that renders a translucent refractive glass material.
 * refraction, depth layering, specular highlights, and soft tinted glass.
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
  }

  private var isAttached: Boolean = false

  private var attachedContext: VisualEffectContext? = null

  private var interactionController: GlassInteractionController? = null

  internal val interactionControllerForTest: GlassInteractionController?
    get() = interactionController

  internal val attachedContextForTest: VisualEffectContext?
    get() = attachedContext

  internal val currentInteractionState: GlassInteractionRenderState
    get() = interactionController?.renderState ?: GlassInteractionRenderState(Offset.Zero)

  internal val currentInteractionSignals: GlassInteractionSignals
    get() = interactionController?.currentSignals ?: GlassInteractionSignals()

  private var needsDelegateSelection: Boolean = true

  internal var preparedRenderBudget: GlassRenderBudgetDecision =
    GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry)
    private set

  internal var preparedRender: GlassPreparedRender? = null
    private set

  private var preparedRenderCacheKey: GlassPreparedRenderCacheKey? = null
  private var preparedRenderCache: GlassPreparedRender? = null

  private var budgetCacheStamp: GlassRenderBudgetStamp? = null
  private var budgetCacheDecision: GlassRenderBudgetDecision? = null

  internal var dirtyTracker: Bitmask by mutableStateOf(Bitmask())
    private set

  private var nextInteractionRevision: Long = 0L

  internal var hoveredSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var focusedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal var pressedSlot: GlassInteractionSlot? by mutableStateOf(null)
    private set

  internal val interactionSlots: GlassInteractionSlots
    get() = GlassInteractionSlots(
      focused = focusedSlot,
      hovered = hoveredSlot,
      pressed = pressedSlot,
    )

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

  private var interactionConfigurationVersion: Int by mutableIntStateOf(0)

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
    dirtyTracker += GlassDirtyFields.Interaction
    if (
      previousRefractionMultiplier != null &&
      previousRefractionMultiplier != maximumInteractionRefractionMultiplier()
    ) {
      dirtyTracker += GlassDirtyFields.InteractionLayerBounds
    }
    interactionConfigurationVersion++
    if (hoveredSlot == null && focusedSlot == null && pressedSlot == null) {
      attachedContext?.let(::syncInteractionController)
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
    preparedRender = null
    clearPreparedRenderCache()
  }

  override fun update(context: VisualEffectContext) {
    interactionConfigurationVersion
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
    val previousBudget = preparedRenderBudget
    prepareRenderBudget(context, runtimeShaderSupported = isRuntimeShaderGlassSupported())
    if (previousBudget::class != preparedRenderBudget::class) {
      needsDelegateSelection = true
    }
    selectDelegateForDraw(context)
    with(delegate) { prepareDraw(context) }
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
    return interactionController?.renderState ?: GlassInteractionRenderState(
      position = context.size.center,
    )
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

  override fun shouldClipToNodeBounds(): Boolean = edgeSoftness > 0.dp || !shape.hasZeroCornerRadii()

  internal fun resolveInputScaleFactor(scale: HazeInputScale): Float = when (scale) {
    is HazeInputScale.None -> 1f
    is HazeInputScale.Fixed -> scale.scale
    HazeInputScale.Auto -> 0.75f
  }

  internal fun resolveGlassRenderBudget(context: VisualEffectContext): GlassRenderBudgetDecision {
    return resolveGlassRenderPreparation(context, runtimeShaderSupported = true).decision
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
      return GlassRenderPreparation(
        GlassRenderBudgetDecision.Fallback(GlassRenderBudgetFallbackReason.InvalidGeometry),
        null,
      )
    }
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val style = resolveGlassStyle(this, context.size, density, layoutDirection)
    val interaction = resolveGlassInteraction(
      state = interactionRenderState(context),
      radiusFraction = interactionLightRadiusFraction,
    )
    val optics = style.resolvedOptics
    val stamp = GlassRenderBudgetStamp(
      requestedScale = requestedScale,
      layerWidth = context.layerSize.width,
      layerHeight = context.layerSize.height,
      materialWidth = context.size.width,
      materialHeight = context.size.height,
      requiresGroupAlpha = requiresGlassGroupAlpha(style.alpha),
      blurRadiusPx = optics.blurRadiusPx,
      depth = optics.depth,
      allowMultiscaleBlur = optics.progressive == null,
      refractionStrength = optics.refractionStrength,
      refractionScalePx = optics.refractionScalePx,
      refractionHeightPx = optics.refractionHeightPx,
      edgeSoftnessPx = style.edgeSoftnessPx,
      rimActive = style.specularIntensity > 0f,
      interactionOpticsActive = interaction.hasOptics,
      interactionLightingActive = interaction.hasLighting,
    )
    val decision = if (stamp == budgetCacheStamp) {
      checkNotNull(budgetCacheDecision)
    } else {
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
        buildGlassBudgetLayerPlan(
          sampleSize = coordinates.sampleSize.roundToIntSize(),
          groupCompositeSize = context.size.roundToIntSize()
            .takeIf { requiresGlassGroupAlpha(style.alpha) },
          blurRadiusPx = optics.blurRadiusPx * scaleFactor,
          depth = optics.depth,
          allowMultiscaleBlur = optics.progressive == null,
          refractionDetailActive = isGlassRefractionDetailActive(
            refractionStrength = optics.refractionStrength,
            refractionScalePx = optics.refractionScalePx * scaleFactor,
            refractionHeightPx = optics.refractionHeightPx * scaleFactor,
            edgeSoftnessPx = style.edgeSoftnessPx * scaleFactor,
            sampleStepPx = 2f * scaleFactor,
          ),
          rimActive = style.specularIntensity > 0f,
          interactionOpticsActive = interaction.hasOptics,
          interactionLightingActive = interaction.hasLighting,
        )
      }.also {
        budgetCacheStamp = stamp
        budgetCacheDecision = it
      }
    }
    if (decision !is GlassRenderBudgetDecision.Runtime) {
      clearPreparedRenderCache()
      return GlassRenderPreparation(decision, null)
    }
    if (!runtimeShaderSupported) {
      return GlassRenderPreparation(decision, null)
    }
    val coordinates = resolveGlassCoordinates(
      layerSize = context.layerSize,
      layerOffset = context.layerOffset,
      materialSize = context.size,
      scaleFactor = decision.scaleFactor,
    ).withRoundedSampleSize()
    val preparedRenderCacheKey = GlassPreparedRenderCacheKey(
      style = style,
      coordinates = coordinates,
      interaction = interaction,
    )
    if (preparedRenderCacheKey == this.preparedRenderCacheKey) {
      return GlassRenderPreparation(decision, checkNotNull(preparedRenderCache))
    }
    val previousCacheKey = this.preparedRenderCacheKey
    val previousPrepared = preparedRenderCache
    val params = if (
      previousCacheKey != null && previousPrepared != null &&
      coordinates == previousCacheKey.coordinates &&
      style.hasSameRenderParams(previousCacheKey.style)
    ) {
      previousPrepared.params
    } else {
      buildGlassRenderParams(style, coordinates)
    }
    val interactionUniforms = if (
      previousCacheKey != null && previousPrepared != null &&
      coordinates == previousCacheKey.coordinates &&
      interaction == previousCacheKey.interaction
    ) {
      previousPrepared.interactionUniforms
    } else {
      interaction.uniforms(coordinates)
    }
    val exactPrepared = buildGlassPreparedRender(
      params = params,
      interactionUniforms = interactionUniforms,
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
      return GlassRenderPreparation(fallback, null)
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
    this.preparedRenderCacheKey = preparedRenderCacheKey
    preparedRenderCache = prepared
    return GlassRenderPreparation(validatedDecision, prepared)
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

  override fun shouldPreferClipToAreaBounds(): Boolean = edgeSoftness <= 0.dp && shape.hasZeroCornerRadii()

  private fun maximumInteractionRefractionMultiplier(): Float = maxOf(
    1f,
    hoveredSlot?.response?.refractionMultiplier?.value ?: 1f,
    focusedSlot?.response?.refractionMultiplier?.value ?: 1f,
    pressedSlot?.response?.refractionMultiplier?.value ?: 1f,
  )

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
        dirtyTracker += GlassDirtyFields.Optics
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
      dirtyTracker += GlassDirtyFields.Optics
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
        dirtyTracker += GlassDirtyFields.SpecularIntensity
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
        dirtyTracker += GlassDirtyFields.AmbientResponse
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
        dirtyTracker += GlassDirtyFields.Tint
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
        dirtyTracker += GlassDirtyFields.EdgeSoftness
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
        dirtyTracker += GlassDirtyFields.LightPosition
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
        dirtyTracker += GlassDirtyFields.ChromaticAberration
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
        dirtyTracker += GlassDirtyFields.SurfaceProfile
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
      dirtyTracker += GlassDirtyFields.SurfaceProfile
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
        dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
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
      dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
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
        dirtyTracker += GlassDirtyFields.Shape
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
      dirtyTracker += GlassDirtyFields.Shape
    }
  }

  /**
   * Overall opacity for the effect, in the range `0f..1f`.
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
        dirtyTracker += GlassDirtyFields.Alpha
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
        dirtyTracker += GlassDirtyFields.Contrast
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
        dirtyTracker += GlassDirtyFields.WhitePoint
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
        dirtyTracker += GlassDirtyFields.ChromaMultiplier
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
        dirtyTracker += GlassDirtyFields.ContentNormalBlend
      }
    }

  /**
   * Exponent controlling specular highlight shape.
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
        dirtyTracker += GlassDirtyFields.SpecularExponent
      }
    }

  /**
   * Exponent controlling Fresnel edge effect intensity.
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
        dirtyTracker += GlassDirtyFields.FresnelExponent
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
        dirtyTracker += GlassDirtyFields.Style
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

  private fun DrawScope.selectDelegateForDraw(context: VisualEffectContext) {
    if (needsDelegateSelection) {
      delegate = updateDelegate(context, this)
      needsDelegateSelection = false
    }
  }

  private fun onStyleChanged(old: GlassStyle, new: GlassStyle) {
    if (old.optics != new.optics) {
      dirtyTracker += GlassDirtyFields.Optics
    }
    if (
      !old.lighting.specularIntensity.hasSameNormalizedOverrideValueAs(
        new.lighting.specularIntensity,
      ) { it.coerceIn(0f, 1f) }
    ) {
      dirtyTracker += GlassDirtyFields.SpecularIntensity
    }
    if (
      !old.lighting.ambientResponse.hasSameNormalizedOverrideValueAs(
        new.lighting.ambientResponse,
      ) { it.coerceIn(0f, 1f) }
    ) {
      dirtyTracker += GlassDirtyFields.AmbientResponse
    }
    if (old.lighting.lightPosition != new.lighting.lightPosition) {
      dirtyTracker += GlassDirtyFields.LightPosition
    }
    if (
      !old.lighting.specularExponent.hasSameNormalizedOverrideValueAs(
        new.lighting.specularExponent,
      ) { it.coerceAtLeast(0f) }
    ) {
      dirtyTracker += GlassDirtyFields.SpecularExponent
    }
    if (
      !old.lighting.fresnelExponent.hasSameNormalizedOverrideValueAs(
        new.lighting.fresnelExponent,
      ) { it.coerceAtLeast(0f) }
    ) {
      dirtyTracker += GlassDirtyFields.FresnelExponent
    }
    if (old.tint != new.tint) {
      dirtyTracker += GlassDirtyFields.Tint
    }
    if (old.shape != new.shape) {
      dirtyTracker += GlassDirtyFields.Shape
    }
    if (
      !old.color.alpha.hasSameNormalizedOverrideValueAs(new.color.alpha) { it.coerceIn(0f, 1f) }
    ) {
      dirtyTracker += GlassDirtyFields.Alpha
    }
    if (
      !old.color.contrast.hasSameNormalizedOverrideValueAs(new.color.contrast) {
        it.coerceIn(-1f, 1f)
      }
    ) {
      dirtyTracker += GlassDirtyFields.Contrast
    }
    if (
      !old.color.whitePoint.hasSameNormalizedOverrideValueAs(new.color.whitePoint) {
        it.coerceIn(-1f, 1f)
      }
    ) {
      dirtyTracker += GlassDirtyFields.WhitePoint
    }
    if (
      !old.color.chromaMultiplier.hasSameNormalizedOverrideValueAs(new.color.chromaMultiplier) {
        it.coerceIn(0f, 2f)
      }
    ) {
      dirtyTracker += GlassDirtyFields.ChromaMultiplier
    }
    if (old.rendering.edgeSoftness != new.rendering.edgeSoftness) {
      dirtyTracker += GlassDirtyFields.EdgeSoftness
    }
    if (
      !old.rendering.contentNormalBlend.hasSameNormalizedOverrideValueAs(
        new.rendering.contentNormalBlend,
      ) { it.coerceIn(0f, 1f) }
    ) {
      dirtyTracker += GlassDirtyFields.ContentNormalBlend
    }
    if (old.rendering.surfaceProfile != new.rendering.surfaceProfile) {
      dirtyTracker += GlassDirtyFields.SurfaceProfile
    }
    if (
      !old.rendering.chromaticAberrationStrength.hasSameNormalizedOverrideValueAs(
        new.rendering.chromaticAberrationStrength,
      ) { it.coerceIn(0f, 1f) }
    ) {
      dirtyTracker += GlassDirtyFields.ChromaticAberration
    }
    if (old.rendering.chromaticAberrationMode != new.rendering.chromaticAberrationMode) {
      dirtyTracker += GlassDirtyFields.ChromaticAberrationMode
    }
  }

  internal companion object {
    const val TAG = "GlassVisualEffect"
  }
}

private data class GlassRenderPreparation(
  val decision: GlassRenderBudgetDecision,
  val prepared: GlassPreparedRender?,
)

private data class GlassRenderBudgetStamp(
  val requestedScale: Float,
  val layerWidth: Float,
  val layerHeight: Float,
  val materialWidth: Float,
  val materialHeight: Float,
  val requiresGroupAlpha: Boolean,
  val blurRadiusPx: Float,
  val depth: Float,
  val allowMultiscaleBlur: Boolean,
  val refractionStrength: Float,
  val refractionScalePx: Float,
  val refractionHeightPx: Float,
  val edgeSoftnessPx: Float,
  val rimActive: Boolean,
  val interactionOpticsActive: Boolean,
  val interactionLightingActive: Boolean,
)

internal interface RetainedOutputDelegate {
  fun canDrawRetainedOutput(): Boolean

  fun shouldDrawRetainedOutput(): Boolean = canDrawRetainedOutput()

  fun clearRetainedOutput()
}

internal expect fun GlassVisualEffect.updateDelegate(
  context: VisualEffectContext,
  drawScope: DrawScope,
): GlassVisualEffect.Delegate

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
