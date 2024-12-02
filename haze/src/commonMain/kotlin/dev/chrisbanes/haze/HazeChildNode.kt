// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import dev.drewhamilton.poko.Poko
import io.github.reactivecircus.cache4k.Cache

/**
 * The [Modifier.Node] implementation used by [Modifier.hazeChild].
 *
 * This is public API in order to aid custom extensible modifiers, _but_ we reserve the right
 * to be able to change the API in the future, hence why it is marked as experimental forever.
 */
@ExperimentalHazeApi
class HazeChildNode(
  var state: HazeState,
  style: HazeStyle = HazeStyle.Unspecified,
  var block: (HazeChildScope.() -> Unit)? = null,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  GlobalPositionAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode,
  HazeChildScope {

  override val shouldAutoInvalidate: Boolean = false

  private val paint by lazy { Paint() }

  private var renderEffect: RenderEffect? = null
  private var renderEffectDirty: Boolean = true
  private var positionChanged: Boolean = true
  private var drawParametersDirty: Boolean = true
  private var progressiveDirty: Boolean = true

  override var blurEnabled: Boolean = HazeDefaults.blurEnabled()
    set(value) {
      if (value != field) {
        log(TAG) { "blurEnabled changed. Current: $field. New: $value" }
        field = value
        drawParametersDirty = true
      }
    }

  override var inputScale: HazeInputScale = HazeInputScale.Default
    set(value) {
      if (value != field) {
        log(TAG) { "inputScale changed. Current: $field. New: $value" }
        field = value
        drawParametersDirty = true
        // Need to scale down the blurRadius
        renderEffectDirty = true
      }
    }

  internal var compositionLocalStyle: HazeStyle = HazeStyle.Unspecified
    set(value) {
      if (field != value) {
        log(TAG) { "LocalHazeStyle changed. Current: $field. New: $value" }
        field = value
        renderEffectDirty = true
      }
    }

  override var style: HazeStyle = style
    set(value) {
      if (field != value) {
        log(TAG) { "style changed. Current: $field. New: $value" }
        field = value
        renderEffectDirty = true
      }
    }

  private var positionInContent: Offset = Offset.Unspecified
    set(value) {
      if (value != field) {
        log(TAG) { "positionInContent changed. Current: $field. New: $value" }
        positionChanged = true
        field = value
      }
    }

  private val isValid: Boolean
    get() = size.isSpecified && layerSize.isSpecified

  internal var size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        log(TAG) { "size changed. Current: $field. New: $value" }
        // We use the size for crop rects/brush sizing
        renderEffectDirty = true
        field = value
      }
    }

  private var layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        log(TAG) { "layerSize changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  internal val contentOffset: Offset
    get() = when {
      isValid -> {
        Offset(
          x = (layerSize.width - size.width) / 2f,
          y = (layerSize.height - size.height) / 2f,
        )
      }

      else -> Offset.Zero
    }

  override var blurRadius: Dp = Dp.Unspecified
    set(value) {
      if (value != field) {
        log(TAG) { "blurRadius changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var noiseFactor: Float = -1f
    set(value) {
      if (value != field) {
        log(TAG) { "noiseFactor changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var mask: Brush? = null
    set(value) {
      if (value != field) {
        log(TAG) { "mask changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var backgroundColor: Color = Color.Unspecified
    set(value) {
      if (value != field) {
        log(TAG) { "backgroundColor changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var tints: List<HazeTint> = emptyList()
    set(value) {
      if (value != field) {
        log(TAG) { "tints changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var fallbackTint: HazeTint = HazeTint.Unspecified
    set(value) {
      if (value != field) {
        log(TAG) { "fallbackTint changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var alpha: Float = 1f
    set(value) {
      if (value != field) {
        log(TAG) { "alpha changed. Current $field. New: $value" }
        drawParametersDirty = true
        field = value
      }
    }

  override var progressive: HazeProgressive? = null
    set(value) {
      if (value != field) {
        log(TAG) { "progressive changed. Current $field. New: $value" }
        progressiveDirty = true
        field = value
      }
    }

  internal fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    update()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      updateEffect()
      compositionLocalStyle = currentValueOf(LocalHazeStyle)
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    log(TAG) {
      "onGloballyPositioned: " +
        "positionInWindow=${coordinates.positionInWindow()}"
    }

    positionInContent = coordinates.positionInWindow() +
      calculateWindowOffset() - state.positionOnScreen.takeOrElse { Offset.Zero }

    size = coordinates.size.toSize()

    val blurRadiusPx = with(currentValueOf(LocalDensity)) {
      resolveBlurRadius().takeOrElse { 0.dp }.toPx()
    }
    layerSize = size.expand(blurRadiusPx * 2)

    updateEffect()
  }

  override fun ContentDrawScope.draw() {
    require(!state.contentDrawing) {
      "Layout nodes using Modifier.haze and Modifier.hazeChild can not be descendants of each other"
    }

    log(TAG) { "-> HazeChild. start draw()" }

    if (isValid) {
      val contentLayer = state.contentLayer
      if (contentLayer != null && blurEnabled && canUseGraphicLayers()) {
        drawEffectWithGraphicsLayer(contentLayer)
      } else {
        drawEffectWithScrim()
      }
    }

    // Finally we draw the content
    drawContent()

    onPostDraw()

    log(TAG) { "-> HazeChild. end draw()" }
  }

  private fun updateEffect() {
    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block?.invoke(this)

    if (needInvalidation()) {
      log(TAG) { "invalidateDraw called, due to effect needing invalidation" }
      invalidateDraw()
    }
  }

  @OptIn(ExperimentalHazeApi::class)
  private fun DrawScope.drawEffectWithGraphicsLayer(contentLayer: GraphicsLayer) {
    // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
    // The RenderEffect applied will provide the blurring effect.
    val graphicsContext = currentValueOf(LocalGraphicsContext)
    val clippedContentLayer = graphicsContext.createGraphicsLayer()

    // The layer size is usually than the bounds. This is so that we include enough
    // content around the edges to keep the blurring uniform. Without the extra border,
    // the blur will naturally fade out at the edges.
    val scaleFactor = calculateInputScaleFactor()
    val inflatedSize = layerSize * scaleFactor
    // This is the topLeft in the inflated bounds where the real are should be at [0,0]
    val inflatedOffset = contentOffset

    val bg = resolveBackgroundColor()
    require(bg.isSpecified) { "backgroundColor not specified. Please provide a color." }

    clippedContentLayer.record(inflatedSize.roundToIntSize()) {
      drawRect(bg)

      clipRect {
        scale(scaleFactor, Offset.Zero) {
          translate(inflatedOffset - positionInContent) {
            // Draw the content into our effect layer
            drawLayer(contentLayer)
          }
        }
      }
    }

    clipRect {
      translate(-inflatedOffset) {
        scale(1f / scaleFactor, Offset.Zero) {
          val p = progressive
          if (p is HazeProgressive.LinearGradient) {
            drawLinearGradientProgressiveEffect(
              drawScope = this,
              progressive = p,
              contentLayer = clippedContentLayer,
            )
          } else {
            // First make sure that the RenderEffect is updated (if necessary)
            updateRenderEffectIfDirty()

            clippedContentLayer.renderEffect = renderEffect
            clippedContentLayer.alpha = alpha

            // Since we included a border around the content, we need to translate so that
            // we don't see it (but it still affects the RenderEffect)
            drawLayer(clippedContentLayer)
          }
        }
      }
    }

    graphicsContext.releaseGraphicsLayer(clippedContentLayer)
  }

  private fun DrawScope.drawEffectWithScrim() {
    val scrimTint = resolveFallbackTint().takeIf { it.isSpecified }
      ?: resolveTints().firstOrNull()?.boostForFallback(resolveBlurRadius().takeOrElse { 0.dp })
      ?: return

    fun scrim(tint: HazeTint) {
      val m = mask
      val p = progressive

      if (m != null) {
        drawRect(brush = m, colorFilter = ColorFilter.tint(tint.color))
      } else if (p is HazeProgressive.LinearGradient) {
        drawRect(brush = p.asBrush(), colorFilter = ColorFilter.tint(tint.color))
      } else {
        drawRect(color = tint.color, blendMode = tint.blendMode)
      }
    }

    if (alpha != 1f) {
      paint.alpha = alpha
      drawContext.canvas.withSaveLayer(size.toRect(), paint) {
        scrim(scrimTint)
      }
    } else {
      scrim(scrimTint)
    }
  }

  private fun updateRenderEffectIfDirty() {
    if (renderEffectDirty) {
      renderEffect = getOrCreateRenderEffect()
      renderEffectDirty = false
    }
  }

  private fun onPostDraw() {
    drawParametersDirty = false
    progressiveDirty = false
    positionChanged = false
  }

  private fun needInvalidation(): Boolean {
    log(TAG) {
      "needInvalidation. renderEffectDirty=$renderEffectDirty, " +
        "drawParametersDirty=$drawParametersDirty, " +
        "progressiveDirty=$progressiveDirty, " +
        "positionChanged=$positionChanged"
    }
    return renderEffectDirty || drawParametersDirty || progressiveDirty || positionChanged
  }

  internal companion object {
    const val TAG = "HazeChild"
  }
}

/**
 * Parameters for applying a progressive blur effect.
 */
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
  }
}

private val renderEffectCache by lazy {
  Cache.Builder<RenderEffectParams, RenderEffect>()
    .maximumCacheSize(10)
    .build()
}

@Poko
internal class RenderEffectParams(
  val blurRadius: Dp,
  val noiseFactor: Float,
  val tints: List<HazeTint> = emptyList(),
  val tintAlphaModulate: Float = 1f,
  val contentSize: Size,
  val contentOffset: Offset,
  val mask: Brush? = null,
  val progressive: Brush? = null,
)

@ExperimentalHazeApi
internal fun HazeChildNode.calculateInputScaleFactor(
  blurRadius: Dp = resolveBlurRadius(),
): Float = when (val s = inputScale) {
  HazeInputScale.None -> 1f
  is HazeInputScale.Fixed -> s.scale
  HazeInputScale.Auto -> {
    when {
      // For small blurRadius values, input scaling is very noticeable therefore we turn it off
      blurRadius < 7.dp -> 1f
      progressive != null -> 0.5f
      mask != null -> 0.5f
      else -> 0.3334f
    }
  }
}

@OptIn(ExperimentalHazeApi::class)
internal fun HazeChildNode.getOrCreateRenderEffect(
  inputScale: Float = calculateInputScaleFactor(),
  blurRadius: Dp = resolveBlurRadius().takeOrElse { 0.dp } * inputScale,
  noiseFactor: Float = resolveNoiseFactor(),
  tints: List<HazeTint> = resolveTints(),
  tintAlphaModulate: Float = 1f,
  contentSize: Size = this.size * inputScale,
  contentOffset: Offset = this.contentOffset * inputScale,
  mask: Brush? = this.mask,
  progressive: Brush? = null,
): RenderEffect? = getOrCreateRenderEffect(
  RenderEffectParams(
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    tints = tints,
    tintAlphaModulate = tintAlphaModulate,
    contentSize = contentSize,
    contentOffset = contentOffset,
    mask = mask,
    progressive = progressive,
  ),
)

internal fun CompositionLocalConsumerModifierNode.getOrCreateRenderEffect(params: RenderEffectParams): RenderEffect? {
  log(HazeChildNode.TAG) { "getOrCreateRenderEffect: $params" }
  val cached = renderEffectCache.get(params)
  if (cached != null) {
    log(HazeChildNode.TAG) { "getOrCreateRenderEffect. Returning cached: $params" }
    return cached
  }

  log(HazeChildNode.TAG) { "getOrCreateRenderEffect. Creating: $params" }
  return createRenderEffect(params)
    ?.also { renderEffectCache.put(params, it) }
}

internal expect fun CompositionLocalConsumerModifierNode.createRenderEffect(params: RenderEffectParams): RenderEffect?

internal expect fun HazeChildNode.drawLinearGradientProgressiveEffect(
  drawScope: DrawScope,
  progressive: HazeProgressive.LinearGradient,
  contentLayer: GraphicsLayer,
)

internal fun HazeChildNode.resolveBackgroundColor(): Color {
  return backgroundColor
    .takeOrElse { style.backgroundColor }
    .takeOrElse { compositionLocalStyle.backgroundColor }
}

internal fun HazeChildNode.resolveBlurRadius(): Dp {
  return blurRadius
    .takeOrElse { style.blurRadius }
    .takeOrElse { compositionLocalStyle.blurRadius }
}

internal fun HazeChildNode.resolveTints(): List<HazeTint> {
  return tints.takeIf { it.isNotEmpty() }
    ?: style.tints.takeIf { it.isNotEmpty() }
    ?: compositionLocalStyle.tints.takeIf { it.isNotEmpty() }
    ?: emptyList()
}

internal fun HazeChildNode.resolveFallbackTint(): HazeTint {
  return fallbackTint.takeIf { it.isSpecified }
    ?: style.fallbackTint.takeIf { it.isSpecified }
    ?: compositionLocalStyle.fallbackTint
}

internal fun HazeChildNode.resolveNoiseFactor(): Float {
  return noiseFactor
    .takeOrElse { style.noiseFactor }
    .takeOrElse { compositionLocalStyle.noiseFactor }
}
