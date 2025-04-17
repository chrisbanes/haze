// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import kotlin.jvm.JvmInline

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeEffect].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
class HazeEffectNode(
  var state: HazeState,
  style: HazeStyle = HazeStyle.Unspecified,
  var block: (HazeEffectScope.() -> Unit)? = null,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode,
  TraversableNode,
  HazeEffectScope {

  override val traverseKey: Any
    get() = HazeTraversableNodeKeys.Effect

  override val shouldAutoInvalidate: Boolean = false

  internal var dirtyTracker = Bitmask()

  override var blurEnabled: Boolean = HazeDefaults.blurEnabled()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurEnabled changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.BlurEnabled
      }
    }

  override var inputScale: HazeInputScale = HazeInputScale.Default
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "inputScale changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.InputScale
      }
    }

  internal var compositionLocalStyle: HazeStyle = HazeStyle.Unspecified
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "LocalHazeStyle changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  override var style: HazeStyle = style
    set(value) {
      if (field != value) {
        HazeLogger.d(TAG) { "style changed. Current: $field. New: $value" }
        onStyleChanged(field, value)
        field = value
      }
    }

  internal var positionOnScreen: Offset = Offset.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "positionOnScreen changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.ScreenPosition
        field = value
      }
    }

  private var areaOffsets: Map<HazeArea, Offset> = emptyMap()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "areaOffsets changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.AreaOffsets
        field = value
      }
    }

  private var forcedInvalidationTick: Long = 0
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "forcedInvalidationTick changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.ForcedInvalidation
        field = value
      }
    }

  private val isValid: Boolean
    get() = size.isSpecified && layerSize.isSpecified && areas.isNotEmpty()

  internal var size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "size changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Size
        field = value
      }
    }

  internal var layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerSize changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.LayerSize
        field = value
      }
    }

  internal var layerOffset: Offset = Offset.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "layerOffset changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.LayerOffset
        field = value
      }
    }

  override var blurRadius: Dp = Dp.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurRadius changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.BlurRadius
        field = value
      }
    }

  override var noiseFactor: Float = -1f
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "noiseFactor changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.NoiseFactor
        field = value
      }
    }

  override var mask: Brush? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "mask changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Mask
        field = value
      }
    }

  override var backgroundColor: Color = Color.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "backgroundColor changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.BackgroundColor
        field = value
      }
    }

  override var tints: List<HazeTint> = emptyList()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "tints changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.Tints
        field = value
      }
    }

  override var fallbackTint: HazeTint = HazeTint.Unspecified
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "fallbackTint changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.FallbackTint
        field = value
      }
    }

  override var alpha: Float = 1f
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "alpha changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Alpha
        field = value
      }
    }

  override var progressive: HazeProgressive? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "progressive changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Progressive
        field = value
      }
    }

  internal var areas: List<HazeArea> = emptyList()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "backgroundAreas changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Areas

        // Remove the layout listener from the current areas
        for (area in field) {
          area.layoutListeners.remove(layoutListener)
        }
        // Re-add the layout listener to all of the areas
        for (area in value) {
          area.layoutListeners.add(layoutListener)
        }

        field = value
      }
    }

  override var canDrawArea: ((HazeArea) -> Boolean)? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "canDrawArea changed. Current $field. New: $value" }
        field = value
      }
    }

  internal var blurEffect: BlurEffect = ScrimBlurEffect(this)
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurEffect changed. Current $field. New: $value" }
        // Cleanup the old value
        field.cleanup()
        field = value
      }
    }

  private val layoutListener = OnLayoutListener {
    if (invalidateOnHazeAreaLayout()) {
      invalidateDraw()
    }
  }

  private fun onStyleChanged(old: HazeStyle?, new: HazeStyle?) {
    if (old?.tints != new?.tints) dirtyTracker += DirtyFields.Tints
    if (old?.fallbackTint != new?.fallbackTint) dirtyTracker += DirtyFields.Tints
    if (old?.backgroundColor != new?.backgroundColor) dirtyTracker += DirtyFields.BackgroundColor
    if (old?.noiseFactor != new?.noiseFactor) dirtyTracker += DirtyFields.NoiseFactor
    if (old?.blurRadius != new?.blurRadius) dirtyTracker += DirtyFields.BlurRadius
  }

  internal fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    update()
  }

  override fun onObservedReadsChanged() = observeReads(::updateEffect)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    // If the positionOnScreen has not been placed yet, we use the value from onPlaced,
    // otherwise we ignore it. This primarily fixes screenshot tests which only run tests
    // up to the first draw. We usually need onGloballyPositioned which tends to happen after
    // the first pass
    if (positionOnScreen.isUnspecified) {
      onPositioned(coordinates, "onPlaced")
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPositioned(coordinates, "onGloballyPositioned")
  }

  private fun onPositioned(coordinates: LayoutCoordinates, source: String) {
    positionOnScreen = coordinates.positionForHaze()
    size = coordinates.size.toSize()

    HazeLogger.d(TAG) {
      "$source. Coordinates=$coordinates, positionOnScreen=$positionOnScreen, size=$size"
    }

    updateEffect()
  }

  override fun ContentDrawScope.draw() {
    HazeLogger.d(TAG) { "-> HazeChild. start draw()" }

    if (isValid) {
      updateBlurEffectIfNeeded(this)
      with(blurEffect) { drawEffect() }
    } else {
      HazeLogger.d(TAG) { "-> HazeChild. Draw. State not valid, so no need to draw effect." }
    }

    // Finally we draw the content
    drawContent()

    onPostDraw()

    HazeLogger.d(TAG) { "-> HazeChild. end draw()" }
  }

  private fun updateEffect() {
    compositionLocalStyle = currentValueOf(LocalHazeStyle)

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block?.invoke(this)

    val ancestorSourceNode = (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)
      ?.takeIf { it.state == this.state }

    areas = state.areas
      .also {
        HazeLogger.d(TAG) { "Background Areas observing: $it" }
      }
      .asSequence()
      .filter { area ->
        val filter = canDrawArea
        when {
          filter != null -> filter(area)
          ancestorSourceNode != null -> area.zIndex < ancestorSourceNode.zIndex
          else -> true
        }.also { included ->
          HazeLogger.d(TAG) { "Background Area: $area. Included=$included" }
        }
      }
      .toMutableList()
      .apply { sortBy(HazeArea::zIndex) }

    areaOffsets = areas.associateWith { area -> positionOnScreen - area.positionOnScreen }
    forcedInvalidationTick = areas.sumOf { it.forcedInvalidationTick.toLong() }

    if (size.isSpecified && positionOnScreen.isSpecified) {
      // The rect which covers all areas
      val areasRect = areas.fold(Rect.Zero) { acc, area ->
        acc.expandToInclude(area.bounds ?: Rect.Zero)
      }

      val blurRadiusPx = with(currentValueOf(LocalDensity)) {
        resolveBlurRadius().takeOrElse { 0.dp }.toPx()
      }

      // Now we clip the expanded layer bounds, to remove anything areas which
      // don't overlap any areas
      val clippedLayerBounds = Rect(positionOnScreen, size)
        .inflate(blurRadiusPx)
        .intersect(areasRect)

      layerSize = Size(
        width = clippedLayerBounds.width.coerceAtLeast(0f),
        height = clippedLayerBounds.height.coerceAtLeast(0f),
      )
      layerOffset = positionOnScreen - clippedLayerBounds.topLeft
    } else {
      layerSize = size
      layerOffset = Offset.Unspecified
    }

    invalidateIfNeeded()
  }

  private fun onPostDraw() {
    dirtyTracker = Bitmask()
  }

  private fun invalidateIfNeeded() {
    val invalidateRequired = dirtyTracker.any(DirtyFields.InvalidateFlags)
    HazeLogger.d(TAG) {
      "invalidateRequired=$invalidateRequired. " +
        "Dirty params=${DirtyFields.stringify(dirtyTracker)}"
    }
    if (invalidateRequired) {
      invalidateDraw()
    }
  }

  internal companion object {
    const val TAG = "HazeEffect"
  }
}

/**
 * Parameters for applying a progressive blur effect.
 */
@Immutable
sealed interface HazeProgressive {

  /**
   * A linear gradient effect.
   *
   * You may wish to use the convenience builder functions provided in [horizontalGradient] and
   * [verticalGradient] for more common use cases.
   *
   * The [preferPerformance] flag below can be set to tell Haze how to handle the progressive effect
   * in certain situations:
   *
   * * On certain platforms (Android SDK 32), drawing the progressive effect is inefficient.
   *   When [preferPerformance] is set to true, Haze will use a mask when running on those
   *   platforms, which is far more performant.
   *
   * @param easing - The easing function to use when applying the effect. Defaults to a
   * linear easing effect.
   * @param start - Starting position of the gradient. Defaults to [Offset.Zero] which
   * represents the top-left of the drawing area.
   * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`.
   * @param end - Ending position of the gradient. Defaults to
   * [Offset.Infinite] which represents the bottom-right of the drawing area.
   * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`
   * @param preferPerformance - Whether Haze should prefer performance (when true), or
   * quality (when false). See above for more information.
   */
  data class LinearGradient(
    val easing: Easing = EaseIn,
    val start: Offset = Offset.Zero,
    val startIntensity: Float = 0f,
    val end: Offset = Offset.Infinite,
    val endIntensity: Float = 1f,
    val preferPerformance: Boolean = false,
  ) : HazeProgressive

  /**
   * A radial gradient effect.
   *
   * Platform support:
   * - Skia backed platforms (iOS, Desktop, etc): ✅
   * - Android SDK Level 33+: ✅
   * - Android SDK Level 31-32: Falls back to a mask
   * - Android SDK Level < 31: Falls back to a scrim
   *
   * @param easing - The easing function to use when applying the effect. Defaults to a
   * linear easing effect.
   * @param center Center position of the radial gradient circle. If this is set to
   * [Offset.Unspecified] then the center of the drawing area is used as the center for
   * the radial gradient. [Float.POSITIVE_INFINITY] can be used for either [Offset.x] or
   * [Offset.y] to indicate the far right or far bottom of the drawing area respectively.
   * @param centerIntensity - The intensity of the haze effect at the [center], in the range `0f`..`1f`.
   * @param radius Radius for the radial gradient. Defaults to positive infinity to indicate
   * the largest radius that can fit within the bounds of the drawing area.
   * @param radiusIntensity - The intensity of the haze effect at the [radius], in the range `0f`..`1f`
   */
  @Poko
  class RadialGradient(
    val easing: Easing = EaseIn,
    val center: Offset = Offset.Unspecified,
    val centerIntensity: Float = 1f,
    val radius: Float = Float.POSITIVE_INFINITY,
    val radiusIntensity: Float = 0f,
  ) : HazeProgressive

  /**
   * A progressive effect which is derived by using the provided [Brush] as an alpha mask.
   *
   * This allows custom effects driven from a brush. It could be using a bitmap shader, via
   * a [ShaderBrush] or something more complex. The RGB values from the brush's pixels will
   * be ignored, only the alpha values are used.
   */
  @JvmInline
  value class Brush(val brush: androidx.compose.ui.graphics.Brush) : HazeProgressive

  companion object {
    /**
     * A vertical gradient effect.
     *
     * @param easing - The easing function to use when applying the effect. Defaults to a
     * linear easing effect.
     * @param startY - Starting x position of the horizontal gradient. Defaults to 0 which
     * represents the top of the drawing area.
     * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`.
     * @param endY - Ending x position of the horizontal gradient. Defaults to
     * [Float.POSITIVE_INFINITY] which represents the bottom of the drawing area.
     * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`.
     * @param preferPerformance - Whether Haze should prefer performance (when true), or
     * quality (when false). See [HazeProgressive.LinearGradient]'s documentation for more
     * information.
     */
    fun verticalGradient(
      easing: Easing = EaseIn,
      startY: Float = 0f,
      startIntensity: Float = 0f,
      endY: Float = Float.POSITIVE_INFINITY,
      endIntensity: Float = 1f,
      preferPerformance: Boolean = false,
    ): LinearGradient = LinearGradient(
      easing = easing,
      start = Offset(0f, startY),
      startIntensity = startIntensity,
      end = Offset(0f, endY),
      endIntensity = endIntensity,
      preferPerformance = preferPerformance,
    )

    /**
     * A horizontal gradient effect.
     *
     * @param easing - The easing function to use when applying the effect. Defaults to a
     * linear easing effect.
     * @param startX - Starting x position of the horizontal gradient. Defaults to 0 which
     * represents the left of the drawing area
     * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`
     * @param endX - Ending x position of the horizontal gradient. Defaults to
     * [Float.POSITIVE_INFINITY] which represents the right of the drawing area.
     * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`.
     * @param preferPerformance - Whether Haze should prefer performance (when true), or
     * quality (when false). See [HazeProgressive.LinearGradient]'s documentation for more
     * information.
     */
    fun horizontalGradient(
      easing: Easing = EaseIn,
      startX: Float = 0f,
      startIntensity: Float = 0f,
      endX: Float = Float.POSITIVE_INFINITY,
      endIntensity: Float = 1f,
      preferPerformance: Boolean = false,
    ): LinearGradient = LinearGradient(
      easing = easing,
      start = Offset(startX, 0f),
      startIntensity = startIntensity,
      end = Offset(endX, 0f),
      endIntensity = endIntensity,
      preferPerformance = preferPerformance,
    )

    /**
     * Helper function for building a [HazeProgressive.Brush] with a [Shader]. The block is
     * provided with the size of the content, allowing you to setup the shader as required.
     */
    inline fun forShader(
      crossinline block: (Size) -> Shader,
    ): Brush = Brush(
      object : ShaderBrush() {
        override fun createShader(size: Size): Shader = block(size)
      },
    )
  }
}

private val renderEffectCache by unsynchronizedLazy { SimpleLruCache<RenderEffectParams, RenderEffect>(10) }

@Poko
internal class RenderEffectParams(
  val blurRadius: Dp,
  val noiseFactor: Float,
  val tints: List<HazeTint> = emptyList(),
  val tintAlphaModulate: Float = 1f,
  val contentBounds: Rect,
  val mask: Brush? = null,
  val progressive: HazeProgressive? = null,
)

@ExperimentalHazeApi
internal fun HazeEffectNode.calculateInputScaleFactor(
  blurRadius: Dp = resolveBlurRadius(),
): Float = when (val s = inputScale) {
  HazeInputScale.None -> 1f
  is HazeInputScale.Fixed -> s.scale
  HazeInputScale.Auto -> {
    when {
      // For small blurRadius values, input scaling is very noticeable therefore we turn it off
      blurRadius < 7.dp -> 1f
      // For progressive and masks, we need to keep enough resolution for the lowest intensity.
      // 0.5f is about right.
      progressive != null -> 0.5f
      mask != null -> 0.5f
      // Otherwise we use 1/3
      else -> 0.3334f
    }
  }
}

@OptIn(ExperimentalHazeApi::class)
internal fun HazeEffectNode.getOrCreateRenderEffect(
  inputScale: Float = calculateInputScaleFactor(),
  blurRadius: Dp = resolveBlurRadius().takeOrElse { 0.dp } * inputScale,
  noiseFactor: Float = resolveNoiseFactor(),
  tints: List<HazeTint> = resolveTints(),
  tintAlphaModulate: Float = 1f,
  contentSize: Size = this.size * inputScale,
  contentOffset: Offset = this.layerOffset * inputScale,
  mask: Brush? = this.mask,
  progressive: HazeProgressive? = null,
): RenderEffect? = getOrCreateRenderEffect(
  RenderEffectParams(
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    tints = tints,
    tintAlphaModulate = tintAlphaModulate,
    contentBounds = Rect(contentOffset, contentSize),
    mask = mask,
    progressive = progressive,
  ),
)

internal fun CompositionLocalConsumerModifierNode.getOrCreateRenderEffect(params: RenderEffectParams): RenderEffect? {
  HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect: $params" }
  val cached = renderEffectCache[params]
  if (cached != null) {
    HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect. Returning cached: $params" }
    return cached
  }

  HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect. Creating: $params" }
  return createRenderEffect(params)
    ?.also { renderEffectCache[params] = it }
}

internal expect fun CompositionLocalConsumerModifierNode.createRenderEffect(params: RenderEffectParams): RenderEffect?

internal expect fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope)

internal expect fun HazeEffectNode.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive,
  contentLayer: GraphicsLayer,
)

internal expect fun HazeEffectNode.invalidateOnHazeAreaLayout(): Boolean

internal fun HazeEffectNode.resolveBackgroundColor(): Color {
  return backgroundColor
    .takeOrElse { style.backgroundColor }
    .takeOrElse { compositionLocalStyle.backgroundColor }
}

internal fun HazeEffectNode.resolveBlurRadius(): Dp {
  return blurRadius
    .takeOrElse { style.blurRadius }
    .takeOrElse { compositionLocalStyle.blurRadius }
}

internal fun HazeEffectNode.resolveTints(): List<HazeTint> {
  return tints.takeIf { it.isNotEmpty() }
    ?: style.tints.takeIf { it.isNotEmpty() }
    ?: compositionLocalStyle.tints.takeIf { it.isNotEmpty() }
    ?: emptyList()
}

internal fun HazeEffectNode.resolveFallbackTint(): HazeTint {
  return fallbackTint.takeIf { it.isSpecified }
    ?: style.fallbackTint.takeIf { it.isSpecified }
    ?: compositionLocalStyle.fallbackTint
}

internal fun HazeEffectNode.resolveNoiseFactor(): Float {
  return noiseFactor
    .takeOrElse { style.noiseFactor }
    .takeOrElse { compositionLocalStyle.noiseFactor }
}

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
internal object DirtyFields {
  const val BlurEnabled: Int = 0b1
  const val InputScale = BlurEnabled shl 1
  const val ScreenPosition = InputScale shl 1
  const val AreaOffsets = ScreenPosition shl 1
  const val Size = AreaOffsets shl 1
  const val BlurRadius = Size shl 1
  const val NoiseFactor = BlurRadius shl 1
  const val Mask = NoiseFactor shl 1
  const val BackgroundColor = Mask shl 1
  const val Tints = BackgroundColor shl 1
  const val FallbackTint = Tints shl 1
  const val Alpha = FallbackTint shl 1
  const val Progressive = Alpha shl 1
  const val Areas = Progressive shl 1
  const val ForcedInvalidation = Areas shl 1
  const val LayerSize = ForcedInvalidation shl 1
  const val LayerOffset = LayerSize shl 1

  const val RenderEffectAffectingFlags =
    BlurEnabled or
      InputScale or
      Size or
      LayerSize or
      LayerOffset or
      BlurRadius or
      NoiseFactor or
      Mask or
      Tints or
      FallbackTint or
      Progressive

  const val InvalidateFlags =
    RenderEffectAffectingFlags or // Eventually we'll move this out of invalidation
      BlurEnabled or
      InputScale or
      AreaOffsets or
      Size or
      LayerSize or
      LayerOffset or
      BackgroundColor or
      Progressive or // TODO: only on Android SDK 32-33
      Areas or
      Alpha or
      ForcedInvalidation

  fun stringify(dirtyTracker: Bitmask): String {
    val params = buildList {
      if (BlurEnabled in dirtyTracker) add("BlurEnabled")
      if (InputScale in dirtyTracker) add("InputScale")
      if (ScreenPosition in dirtyTracker) add("ScreenPosition")
      if (AreaOffsets in dirtyTracker) add("RelativePosition")
      if (Size in dirtyTracker) add("Size")
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
      if (BlurRadius in dirtyTracker) add("BlurRadius")
      if (NoiseFactor in dirtyTracker) add("NoiseFactor")
      if (Mask in dirtyTracker) add("Mask")
      if (BackgroundColor in dirtyTracker) add("BackgroundColor")
      if (Tints in dirtyTracker) add("Tints")
      if (FallbackTint in dirtyTracker) add("FallbackTint")
      if (Alpha in dirtyTracker) add("Alpha")
      if (Progressive in dirtyTracker) add("Progressive")
      if (Areas in dirtyTracker) add("Areas")
      if (ForcedInvalidation in dirtyTracker) add("ForcedInvalidation")
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
