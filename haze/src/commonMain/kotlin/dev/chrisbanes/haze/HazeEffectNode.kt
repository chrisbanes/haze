// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.collection.LruCache
import androidx.collection.MutableObjectLongMap
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
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
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
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
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeProgressive.Companion.horizontalGradient
import dev.chrisbanes.haze.HazeProgressive.Companion.verticalGradient
import kotlin.jvm.JvmInline
import kotlin.math.max
import kotlin.math.min

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeEffect].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
public class HazeEffectNode(
  public var state: HazeState? = null,
  style: HazeStyle = HazeStyle.Unspecified,
  public var block: (HazeEffectScope.() -> Unit)? = null,
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

  internal var blurEnabledSet: Boolean = false
  override var blurEnabled: Boolean = resolveBlurEnabled()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurEnabled changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.BlurEnabled
      }
      // Mark the set flag, to indicate that this value should take precedence
      blurEnabledSet = true
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

  internal var rootBoundsOnScreen: Rect = Rect.Zero
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "rootBoundsOnScreen changed. Current: $field. New: $value" }
        dirtyTracker += DirtyFields.ScreenPosition
        field = value
      }
    }

  private val areaOffsets = MutableObjectLongMap<HazeArea>()

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

  internal var windowId: Any? = null

  internal var areas: List<HazeArea> = emptyList()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "backgroundAreas changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Areas

        // Remove the pre-draw listener from the current areas
        for (area in field) {
          area.preDrawListeners -= areaPreDrawListener
        }
        // Add the pre-draw listener to all of the new areas
        for (area in value) {
          area.preDrawListeners += areaPreDrawListener
        }
        field = value
      }
    }

  private val contentDrawArea by lazy { HazeArea() }

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

  override var blurredEdgeTreatment: BlurredEdgeTreatment = HazeDefaults.blurredEdgeTreatment
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "blurredEdgeTreatment. Current $field. New: $value" }
        dirtyTracker += DirtyFields.BlurredEdgeTreatment
        field = value
      }
    }

  override var drawContentBehind: Boolean = HazeDefaults.drawContentBehind
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "drawContentBehind changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.DrawContentBehind
        field = value
      }
    }

  override var clipToAreasBounds: Boolean? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "clipToAreasBounds changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ClipToAreas
        field = value
      }
    }

  override var expandLayerBounds: Boolean? = null
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "expandLayer changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ExpandLayer
        field = value
      }
    }

  override var forceInvalidateOnPreDraw: Boolean = false
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "forceInvalidateOnPreDraw changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.ForcePreDraw
        field = value
      }
    }
  private val areaPreDrawListener by unsynchronizedLazy { OnPreDrawListener(::invalidateDraw) }

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

  override fun onObservedReadsChanged() {
    observeReads(::updateEffect)
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    // If the positionOnScreen has not been placed yet, we use the value on onPlaced,
    // otherwise we ignore it. This primarily fixes screenshot tests which only run tests
    // up to the first draw. We need onGloballyPositioned which tends to happen after
    // the first pass
    Snapshot.withoutReadObservation {
      if (positionOnScreen.isUnspecified) {
        onPositioned(coordinates, "onPlaced")
      }
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPositioned(coordinates, "onGloballyPositioned")
  }

  private fun onPositioned(coordinates: LayoutCoordinates, source: String) {
    if (!isAttached) {
      // This shouldn't happen, but it does...
      // https://github.com/chrisbanes/haze/issues/665
      return
    }

    positionOnScreen = coordinates.positionForHaze()
    size = coordinates.size.toSize()
    windowId = getWindowId()

    val rootLayoutCoords = coordinates.findRootCoordinates()
    rootBoundsOnScreen = Rect(
      offset = rootLayoutCoords.positionForHaze(),
      size = rootLayoutCoords.size.toSize(),
    )

    HazeLogger.d(TAG) {
      "$source: positionOnScreen=$positionOnScreen, size=$size"
    }

    updateEffect()
  }

  override fun ContentDrawScope.draw() {
    try {
      HazeLogger.d(TAG) { "-> start draw()" }

      if (!isAttached) {
        // This shouldn't happen, but it does...
        // https://github.com/chrisbanes/haze/issues/665
        return
      }

      if (size.isSpecified && layerSize.isSpecified) {
        if (state != null) {
          if (areas.isNotEmpty()) {
            // If the state is not null and we have some areas, let's perform background blurring
            updateBlurEffectIfNeeded(this)
            with(blurEffect) { drawEffect() }
          }
          // Finally we draw the content over the background
          drawContentSafely()
        } else {
          // Else we're doing content (foreground) blurring, so we need to use our
          // contentDrawArea
          val contentLayer = contentDrawArea.contentLayer
            ?.takeUnless { it.isReleased }
            ?: requireGraphicsContext().createGraphicsLayer().also {
              contentDrawArea.contentLayer = it
              HazeLogger.d(TAG) { "Updated contentLayer in content HazeArea" }
            }
          // Record the this node's content into the layer
          contentLayer.record(size.toIntSize()) {
            this@draw.drawContentSafely()
          }
          updateBlurEffectIfNeeded(this)
          if (drawContentBehind || blurEffect is ScrimBlurEffect) {
            // We need to draw the content for scrims
            drawLayer(contentLayer)
          }
          with(blurEffect) { drawEffect() }
        }
      } else {
        HazeLogger.d(TAG) { "-> State not valid, so no need to draw effect." }
        drawContentSafely()
      }
    } finally {
      onPostDraw()
      HazeLogger.d(TAG) { "-> end draw()" }
    }
  }

  private fun updateEffect(): Unit = trace("HazeEffectNode-updateEffect") {
    if (!isAttached) return@trace

    compositionLocalStyle = currentValueOf(LocalHazeStyle)
    windowId = getWindowId()

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block?.invoke(this)

    val backgroundBlurring = state != null

    areas.forEach { area ->
      // Remove our pre draw listener from the current areas
      area.preDrawListeners -= areaPreDrawListener
    }

    areas = if (backgroundBlurring) {
      val ancestorSourceNode =
        (findNearestAncestor(HazeTraversableNodeKeys.Source) as? HazeSourceNode)
          ?.takeIf { it.state == this.state }

      state?.areas.orEmpty()
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
    } else {
      contentDrawArea.size = size
      contentDrawArea.positionOnScreen = positionOnScreen
      contentDrawArea.windowId = windowId
      listOf(contentDrawArea)
    }

    if (shouldUsePreDrawListener()) {
      for (area in areas) {
        area.preDrawListeners += areaPreDrawListener
      }
    }

    updateAreaOffsets()

    val blurRadiusPx = with(currentValueOf(LocalDensity)) {
      resolveBlurRadius().takeOrElse { 0.dp }.toPx()
    }

    if (backgroundBlurring && areas.isNotEmpty() && size.isSpecified && positionOnScreen.isSpecified) {
      val blurRadiusPx = with(requireDensity()) {
        resolveBlurRadius().takeOrElse { 0.dp }.toPx()
      }

      // Now we clip the expanded layer bounds, to remove anything areas which
      // don't overlap any areas, and the window bounds
      val clippedLayerBounds = Rect(positionOnScreen, size)
        .letIf(shouldExpandLayer()) { it.inflate(blurRadiusPx) }
        .letIf(shouldClipToAreaBounds()) { rect ->
          // Calculate the dimensions which covers all areas...
          var left = Float.POSITIVE_INFINITY
          var top = Float.POSITIVE_INFINITY
          var right = Float.NEGATIVE_INFINITY
          var bottom = Float.NEGATIVE_INFINITY
          for (area in areas) {
            val bounds = area.bounds ?: continue
            left = min(left, bounds.left)
            top = min(top, bounds.top)
            right = max(right, bounds.right)
            bottom = max(bottom, bounds.bottom)
          }
          rect.intersect(left, top, right, bottom)
        }
        .intersect(rootBoundsOnScreen)

      layerSize = Size(
        width = clippedLayerBounds.width.coerceAtLeast(0f),
        height = clippedLayerBounds.height.coerceAtLeast(0f),
      )
      layerOffset = positionOnScreen - clippedLayerBounds.topLeft
    } else if (!backgroundBlurring && size.isSpecified && !shouldClip()) {
      layerSize = Size(
        width = size.width + (blurRadiusPx * 2),
        height = size.height + (blurRadiusPx * 2),
      )
      layerOffset = Offset(blurRadiusPx, blurRadiusPx)
    } else {
      layerSize = size
      layerOffset = Offset.Zero
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

  private fun updateAreaOffsets() {
    // Calculate new offsets and detect changes for diff tracking
    val hasAreaOffsetsChanged = when {
      areaOffsets.size != areas.size -> true
      else -> {
        areas.any { area ->
          val newOffset = positionOnScreen - area.positionOnScreen
          !areaOffsets.contains(area) || areaOffsets[area] != newOffset.packedValue
        }
      }
    }

    if (hasAreaOffsetsChanged) {
      HazeLogger.d(TAG) { "areaOffsets changed" }
      dirtyTracker += DirtyFields.AreaOffsets

      areaOffsets.clear()
      areas.forEach { area ->
        val offset = positionOnScreen - area.positionOnScreen
        areaOffsets[area] = offset.packedValue
      }
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
public sealed interface HazeProgressive {

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
  public data class LinearGradient(
    public val easing: Easing = EaseIn,
    public val start: Offset = Offset.Zero,
    public val startIntensity: Float = 0f,
    public val end: Offset = Offset.Infinite,
    public val endIntensity: Float = 1f,
    public val preferPerformance: Boolean = false,
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
  public class RadialGradient(
    public val easing: Easing = EaseIn,
    public val center: Offset = Offset.Unspecified,
    public val centerIntensity: Float = 1f,
    public val radius: Float = Float.POSITIVE_INFINITY,
    public val radiusIntensity: Float = 0f,
  ) : HazeProgressive

  /**
   * A progressive effect which is derived by using the provided [Brush] as an alpha mask.
   *
   * This allows custom effects driven from a brush. It could be using a bitmap shader, via
   * a [ShaderBrush] or something more complex. The RGB values from the brush's pixels will
   * be ignored, only the alpha values are used.
   */
  @JvmInline
  public value class Brush(public val brush: androidx.compose.ui.graphics.Brush) : HazeProgressive

  public companion object {
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
    public fun verticalGradient(
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
    public fun horizontalGradient(
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
    public inline fun forShader(
      crossinline block: (Size) -> Shader,
    ): Brush = Brush(
      object : ShaderBrush() {
        override fun createShader(size: Size): Shader = block(size)
      },
    )
  }
}

private val renderEffectCache by unsynchronizedLazy {
  LruCache<RenderEffectParams, RenderEffect>(50)
}

@Poko
internal class RenderEffectParams(
  val blurRadius: Dp,
  val noiseFactor: Float,
  val scale: Float,
  val contentSize: Size,
  val contentOffset: Offset,
  val tints: List<HazeTint> = emptyList(),
  val tintAlphaModulate: Float = 1f,
  val mask: Brush? = null,
  val progressive: HazeProgressive? = null,
  val blurTileMode: TileMode,
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

private fun HazeEffectNode.calculateBlurTileMode(): TileMode = when (blurredEdgeTreatment) {
  BlurredEdgeTreatment.Unbounded -> TileMode.Decal
  else -> TileMode.Clamp
}

@OptIn(ExperimentalHazeApi::class)
internal fun HazeEffectNode.getOrCreateRenderEffect(
  inputScale: Float = calculateInputScaleFactor(),
  blurRadius: Dp = resolveBlurRadius().takeOrElse { 0.dp },
  noiseFactor: Float = resolveNoiseFactor(),
  tints: List<HazeTint> = resolveTints(),
  tintAlphaModulate: Float = 1f,
  contentSize: Size = this.size,
  contentOffset: Offset = this.layerOffset,
  mask: Brush? = this.mask,
  progressive: HazeProgressive? = null,
  blurTileMode: TileMode = calculateBlurTileMode(),
): RenderEffect? = trace("HazeEffectNode-getOrCreateRenderEffect") {
  getOrCreateRenderEffect(
    RenderEffectParams(
      blurRadius = blurRadius,
      noiseFactor = noiseFactor,
      scale = inputScale,
      tints = tints,
      tintAlphaModulate = tintAlphaModulate,
      contentSize = contentSize,
      contentOffset = contentOffset,
      mask = mask,
      progressive = progressive,
      blurTileMode = blurTileMode,
    ),
  )
}

internal fun CompositionLocalConsumerModifierNode.getOrCreateRenderEffect(params: RenderEffectParams): RenderEffect? {
  HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect: $params" }
  val cached = renderEffectCache[params]
  if (cached != null) {
    HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect. Returning cached: $params" }
    return cached
  }

  HazeLogger.d(HazeEffectNode.TAG) { "getOrCreateRenderEffect. Creating: $params" }
  return createRenderEffect(params)
    ?.also { renderEffectCache.put(params, it) }
}

internal expect fun CompositionLocalConsumerModifierNode.createRenderEffect(params: RenderEffectParams): RenderEffect?

internal expect fun HazeEffectNode.updateBlurEffectIfNeeded(drawScope: DrawScope)

internal expect fun invalidateOnHazeAreaPreDraw(): Boolean

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

internal fun HazeEffectNode.resolveBlurEnabled(): Boolean = when {
  blurEnabledSet -> blurEnabled
  state != null -> state?.blurEnabled == true
  else -> HazeDefaults.blurEnabled()
}

internal fun HazeEffectNode.shouldClip(): Boolean = blurredEdgeTreatment.shape != null

internal fun HazeEffectNode.shouldClipToAreaBounds(): Boolean {
  val param = clipToAreasBounds
  if (param != null) {
    return param
  }

  val bgColor = resolveBackgroundColor()
  return bgColor.alpha <= 0.9f
}

internal fun HazeEffectNode.shouldExpandLayer(): Boolean {
  val param = expandLayerBounds
  if (param != null) {
    return param
  }
  return true
}

/**
 * We need to use the area pre draw listener in a few situations when blurring is enabled:
 *
 * - Globally, if [invalidateOnHazeAreaPreDraw] is set to true. This is mostly for older
 *   Android versions.
 * - The source haze node is drawn in a different window to us. In this instance, we won't be
 *   in the same invalidation scope, so need to force invalidation. This handles cases
 *   like Dialogs.
 */
internal fun HazeEffectNode.shouldUsePreDrawListener(): Boolean {
  if (!resolveBlurEnabled()) return false
  if (forceInvalidateOnPreDraw) return true
  if (invalidateOnHazeAreaPreDraw()) return true
  if (areas.any { it.windowId != windowId }) return true
  return false
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
  const val LayerSize = Areas shl 1
  const val LayerOffset = LayerSize shl 1
  const val BlurredEdgeTreatment = LayerOffset shl 1
  const val DrawContentBehind = BlurredEdgeTreatment shl 1
  const val ClipToAreas = DrawContentBehind shl 1
  const val ExpandLayer = ClipToAreas shl 1
  const val ForcePreDraw = ExpandLayer shl 1

  const val RenderEffectAffectingFlags =
    BlurEnabled or
      InputScale or
      AreaOffsets or
      Size or
      LayerSize or
      LayerOffset or
      BlurRadius or
      NoiseFactor or
      Mask or
      Tints or
      FallbackTint or
      Progressive or
      BlurredEdgeTreatment or
      ClipToAreas or
      ExpandLayer

  const val InvalidateFlags =
    RenderEffectAffectingFlags or // Eventually we'll move this out of invalidation
      BlurEnabled or
      InputScale or
      Size or
      ScreenPosition or
      LayerSize or
      LayerOffset or
      BackgroundColor or
      Progressive or
      Areas or
      Alpha or
      BlurredEdgeTreatment or
      DrawContentBehind or
      ClipToAreas or
      ExpandLayer or
      ForcePreDraw

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
      if (ExpandLayer in dirtyTracker) add("ExpandLayer")
      if (ForcePreDraw in dirtyTracker) add("ForcePreDraw")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
