// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

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
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
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
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeProgressive.Companion.horizontalGradient
import dev.chrisbanes.haze.HazeProgressive.Companion.verticalGradient
import dev.chrisbanes.haze.blur.BlurVisualEffect
import kotlin.jvm.JvmInline

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeEffect].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
class HazeEffectNode(
  var state: HazeState? = null,
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

  override var inputScale: HazeInputScale = HazeInputScale.Default
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "inputScale changed. Current: $field. New: $value" }
        field = value
        dirtyTracker += DirtyFields.InputScale
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

  override var blurRadius: Dp
    get() = requireBlurVisualEffect().blurRadius
    set(value) {
      requireBlurVisualEffect().blurRadius = value
    }

  override var noiseFactor: Float
    get() = requireBlurVisualEffect().noiseFactor
    set(value) {
      requireBlurVisualEffect().noiseFactor = value
    }

  override var mask: Brush?
    get() = requireBlurVisualEffect().mask
    set(value) {
      requireBlurVisualEffect().mask = value
    }

  override var backgroundColor: Color
    get() = requireBlurVisualEffect().backgroundColor
    set(value) {
      requireBlurVisualEffect().backgroundColor = value
    }

  override var tints: List<HazeTint>
    get() = requireBlurVisualEffect().tints
    set(value) {
      requireBlurVisualEffect().tints = value
    }

  override var fallbackTint: HazeTint
    get() = requireBlurVisualEffect().fallbackTint
    set(value) {
      requireBlurVisualEffect().fallbackTint = value
    }

  override var alpha: Float
    get() = requireBlurVisualEffect().alpha
    set(value) {
      requireBlurVisualEffect().alpha = value
    }

  override var progressive: HazeProgressive?
    get() = requireBlurVisualEffect().progressive
    set(value) {
      requireBlurVisualEffect().progressive = value
    }

  private var windowId: Any? = null

  internal var areas: List<HazeArea> = emptyList()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "backgroundAreas changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.Areas

        // Remove the layout listener from the current areas
        for (area in field) {
          area.preDrawListeners.remove(areaPreDrawListener)
        }
        // Re-add the layout listener to all of the new areas
        for (area in value) {
          attachPreDrawListenerIfNecessary(area)
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

  internal var visualEffect: VisualEffect = BlurVisualEffect()
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "visualEffect changed. Current $field. New: $value" }
        // attach new VisualEffect
        value.attach(this)
        // detach old VisualEffect
        field.detach()
        field = value
      }
    }

  private fun requireBlurVisualEffect(): BlurVisualEffect = visualEffect as BlurVisualEffect

  override var blurEnabled: Boolean
    get() = requireBlurVisualEffect().blurEnabled
    set(value) {
      requireBlurVisualEffect().blurEnabled = value
    }

  override var style: HazeStyle
    get() = requireBlurVisualEffect().style
    set(value) {
      requireBlurVisualEffect().style = value
    }

  override var blurredEdgeTreatment: BlurredEdgeTreatment
    get() = requireBlurVisualEffect().blurredEdgeTreatment
    set(value) {
      requireBlurVisualEffect().blurredEdgeTreatment = value
    }

  override var drawContentBehind: Boolean = HazeDefaults.drawContentBehind
    set(value) {
      if (value != field) {
        HazeLogger.d(TAG) { "drawContentBehind changed. Current $field. New: $value" }
        dirtyTracker += DirtyFields.DrawContentBehind
        field = value
      }
    }

  private val areaPreDrawListener = OnPreDrawListener { invalidateDraw() }

  /**
   * We need to use the area pre draw listener in a few situations when blurring is enabled:
   *
   * - Globally, if [dev.chrisbanes.haze.blur.invalidateOnHazeAreaPreDraw] is set to true. This is mostly for older
   *   Android versions.
   * - The source haze node is drawn in a different window to us. In this instance, we won't be
   *   in the same invalidation scope, so need to force invalidation. This handles cases
   *   like Dialogs.
   */
  private fun attachPreDrawListenerIfNecessary(area: HazeArea) {
    if (invalidateOnHazeAreaPreDraw() || area.windowId != windowId) {
      area.preDrawListeners.add(areaPreDrawListener)
    }
  }

  init {
    requireBlurVisualEffect().style = style
  }

  internal fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    visualEffect.attach(this)
    update()
  }

  override fun onObservedReadsChanged() = observeReads(::updateEffect)

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

      with(visualEffect) {
        onPreDrawEffect(this@HazeEffectNode)
      }

      if (size.isSpecified && layerSize.isSpecified) {
        if (state != null) {
          if (areas.isNotEmpty()) {
            // If the state is not null and we have some areas, let's perform background blurring
            with(visualEffect) {
              drawEffect(this@HazeEffectNode)
            }
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
          if (drawContentBehind || visualEffect.needContentDrawBehind()) {
            // We need to draw the content for scrims
            drawLayer(contentLayer)
          }
          with(visualEffect) {
            drawEffect(this@HazeEffectNode)
          }
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
    visualEffect.update()
    windowId = getWindowId()

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block?.invoke(this)

    val backgroundBlurring = state != null

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

    areaOffsets = if (areas.isNotEmpty()) {
      areas.associateWith { area -> positionOnScreen - area.positionOnScreen }
    } else {
      emptyMap()
    }

    val blurRadiusPx = with(currentValueOf(LocalDensity)) {
      ((visualEffect as? BlurVisualEffect)?.blurRadius ?: 0.dp).takeOrElse { 0.dp }.toPx()
    }

    if (backgroundBlurring && areas.isNotEmpty() && size.isSpecified && positionOnScreen.isSpecified) {
      val inflatedLayerBounds = Rect(positionOnScreen, size).inflate(blurRadiusPx)

      layerSize = inflatedLayerBounds.size
      layerOffset = positionOnScreen - inflatedLayerBounds.topLeft
    } else if (!backgroundBlurring && size.isSpecified && !visualEffect.shouldClip()) {
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
    val invalidateRequired =
      dirtyTracker.any(DirtyFields.InvalidateFlags) ||
        visualEffect.needInvalidation()

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

internal expect fun invalidateOnHazeAreaPreDraw(): Boolean

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
      Progressive or
      BlurredEdgeTreatment

  const val InvalidateFlags =
    RenderEffectAffectingFlags or // Eventually we'll move this out of invalidation
      BlurEnabled or
      InputScale or
      AreaOffsets or
      Size or
      LayerSize or
      LayerOffset or
      BackgroundColor or
      Progressive or
      Areas or
      Alpha or
      BlurredEdgeTreatment or
      DrawContentBehind

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
      if (LayerSize in dirtyTracker) add("LayerSize")
      if (LayerOffset in dirtyTracker) add("LayerOffset")
    }
    return params.joinToString(separator = ", ", prefix = "[", postfix = "]")
  }
}
