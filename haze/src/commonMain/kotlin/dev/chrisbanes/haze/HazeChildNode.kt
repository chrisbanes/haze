// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

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

  private var size: Size = Size.Unspecified
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

  private val layerOffset: Offset
    get() = when {
      isValid -> {
        Offset(
          x = (layerSize.width - size.width) / 2f,
          y = (layerSize.height - size.height) / 2f,
        )
      }

      else -> Offset.Zero
    }

  override var blurRadius: Dp = HazeDefaults.blurRadius
    set(value) {
      if (value != field) {
        log(TAG) { "blurRadius changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var noiseFactor: Float = HazeDefaults.noiseFactor
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
    log(TAG) { "onGloballyPositioned: positionInWindow=${coordinates.positionInWindow()}" }
    positionInContent =
      coordinates.positionInWindow() + calculateWindowOffset() - state.positionOnScreen
    size = coordinates.size.toSize()

    val blurRadiusPx = with(currentValueOf(LocalDensity)) {
      resolvedBlurRadius.takeOrElse { 0.dp }.toPx()
    }
    layerSize = size.expand(blurRadiusPx * 2)

    updateEffect()
  }

  override fun ContentDrawScope.draw() {
    log(TAG) { "-> HazeChild. start draw()" }

    require(!state.contentDrawing) {
      "Layout nodes using Modifier.haze and Modifier.hazeChild can not be descendants of each other"
    }

    if (!isValid) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      log(TAG) { "-> HazeChild. end draw()" }
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    onPreDraw(drawContext.density)

    if (useGraphicLayers()) {
      val contentLayer = state.contentLayer
      if (contentLayer != null) {
        drawEffectWithGraphicsLayer(contentLayer)
      }
    } else {
      drawEffectWithScrim()
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

  private fun DrawScope.drawEffectWithGraphicsLayer(contentLayer: GraphicsLayer) {
    // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
    // The RenderEffect applied will provide the blurring effect.
    val graphicsContext = currentValueOf(LocalGraphicsContext)
    val clippedContentLayer = graphicsContext.createGraphicsLayer()

    // The layer size is usually than the bounds. This is so that we include enough
    // content around the edges to keep the blurring uniform. Without the extra border,
    // the blur will naturally fade out at the edges.
    val inflatedSize = layerSize
    // This is the topLeft in the inflated bounds where the real are should be at [0,0]
    val inflatedOffset = layerOffset

    clippedContentLayer.record(inflatedSize.roundToIntSize()) {
      val bg = resolvedBackgroundColor
      require(bg.isSpecified) {
        "backgroundColor not specified. Please provide a color."
      }
      drawRect(bg)

      translate(inflatedOffset - positionInContent) {
        // Draw the content into our effect layer
        drawLayer(contentLayer)
      }
    }

    val p = progressive
    if (p is HazeProgressive.LinearGradient && useGraphicLayers()) {
      drawLinearGradientProgressiveEffect(
        progressive = p,
        innerDrawOffset = -inflatedOffset,
        contentLayer = clippedContentLayer,
      )
    } else {
      clippedContentLayer.renderEffect = renderEffect
      clippedContentLayer.alpha = alpha

      clipRect(right = size.width, bottom = size.height) {
        translate(-inflatedOffset) {
          // Since we included a border around the content, we need to translate so that
          // we don't see it (but it still affects the RenderEffect)
          drawLayer(clippedContentLayer)
        }
      }
    }

    graphicsContext.releaseGraphicsLayer(clippedContentLayer)
  }

  private fun DrawScope.drawEffectWithScrim() {
    val scrimTint = resolvedFallbackTint.takeIf { it.isSpecified }
      ?: resolvedTints.firstOrNull()?.boostForFallback(resolvedBlurRadius.takeOrElse { 0.dp })

    fun scrim() {
      if (scrimTint != null) {
        val m = mask
        if (m != null) {
          drawRect(brush = m, colorFilter = ColorFilter.tint(scrimTint.color))
        } else {
          drawRect(color = scrimTint.color, blendMode = scrimTint.blendMode)
        }
      }
    }

    if (alpha != 1f) {
      paint.alpha = alpha
      drawContext.canvas.withSaveLayer(size.toRect(), paint) {
        scrim()
      }
    } else {
      scrim()
    }
  }

  private fun DrawScope.drawLinearGradientProgressiveEffect(
    progressive: HazeProgressive.LinearGradient,
    innerDrawOffset: Offset,
    contentLayer: GraphicsLayer,
  ) {
    require(progressive.steps == HazeProgressive.STEPS_AUTO_BALANCED || progressive.steps > 1) {
      "steps needs to be STEPS_AUTO_BALANCED, or a value greater than 1"
    }
    require(progressive.startIntensity in 0f..1f)
    require(progressive.endIntensity in 0f..1f)

    var steps = progressive.steps
    if (steps == HazeProgressive.STEPS_AUTO_BALANCED) {
      // Here we're going to calculate an appropriate amount of steps for the length.
      // We use a calculation of 48dp per step, which is a good balance between
      // quality vs performance
      val stepHeightPx = with(drawContext.density) { 48.dp.toPx() }
      val length = calculateLength(progressive.start, progressive.end, size)
      steps = ceil(length / stepHeightPx).toInt().coerceAtLeast(2)
    }

    val graphicsContext = currentValueOf(LocalGraphicsContext)

    val seq = when {
      progressive.endIntensity >= progressive.startIntensity -> 0..steps
      else -> steps downTo 0
    }

    for (i in seq) {
      val fraction = i / steps.toFloat()
      val intensity = lerp(
        progressive.startIntensity,
        progressive.endIntensity,
        progressive.easing.transform(fraction),
      )

      val layer = graphicsContext.createGraphicsLayer()
      layer.record(contentLayer.size) {
        drawLayer(contentLayer)
      }

      val maskStops = buildList {
        val min = min(progressive.startIntensity, progressive.endIntensity)
        val max = max(progressive.startIntensity, progressive.endIntensity)
        add(lerp(min, max, (i - 2f) / steps) to Color.Transparent)
        add(lerp(min, max, (i - 1f) / steps) to Color.Black)
        add(lerp(min, max, (i + 0f) / steps) to Color.Black)
        add(lerp(min, max, (i + 1f) / steps) to Color.Transparent)
      }

      log(TAG) {
        "drawProgressiveEffect. " +
          "step=$i, " +
          "fraction=$fraction, " +
          "intensity=$intensity, " +
          "maskStops=${maskStops.map { it.first to it.second.alpha }}"
      }

      val boundsInLayer = Rect(layerOffset, size)

      layer.alpha = alpha
      layer.renderEffect = createRenderEffect(
        blurRadiusPx = with(drawContext.density) {
          intensity * resolvedBlurRadius.takeOrElse { 0.dp }.toPx()
        },
        noiseFactor = resolvedNoiseFactor,
        tints = resolvedTints,
        tintAlphaModulate = intensity,
        boundsInLayer = boundsInLayer, // cache this
        layerSize = layerSize,
        mask = Brush.linearGradient(
          *maskStops.toTypedArray(),
          start = progressive.start,
          end = progressive.end,
        ),
      )

      clipRect(right = size.width, bottom = size.height) {
        translate(innerDrawOffset) {
          // Since we included a border around the content, we need to translate so that
          // we don't see it (but it still affects the RenderEffect)
          drawLayer(layer)
        }
      }

      graphicsContext.releaseGraphicsLayer(layer)
    }
  }

  private fun onPreDraw(density: Density) {
    if (renderEffectDirty) {
      renderEffect = createRenderEffect(
        blurRadiusPx = with(density) {
          resolvedBlurRadius.takeOrElse { 0.dp }.toPx()
        },
        noiseFactor = resolvedNoiseFactor,
        tints = resolvedTints,
        boundsInLayer = Rect(layerOffset, size), // cache this
        layerSize = layerSize,
        mask = mask,
      )
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
        "progressiveDirty=$progressiveDirty" +
        "positionChanged=$positionChanged"
    }
    return renderEffectDirty || drawParametersDirty || progressiveDirty || positionChanged
  }

  private companion object {
    const val TAG = "HazeChild"
  }
}

/**
 * Parameters for applying a progressive blur effect.
 */
sealed interface HazeProgressive {
  /**
   * The number of discrete steps which should be used to make up the progressive / gradient
   * effect. More steps results in a higher quality effect, but at the cost of performance.
   *
   * Set to [STEPS_AUTO_BALANCED] so that the value is automatically computed, balancing quality
   * and performance.
   */
  val steps: Int

  /**
   * A linear gradient effect.
   *
   * You may wish to use the convenience builder functions provided in [horizontalGradient] and
   * [verticalGradient] for more common use cases.
   *
   * @param steps - The number of steps in the effect. See [HazeProgressive.steps] for information.
   * @param easing - The easing function to use when applying the effect. Defaults to a
   * linear easing effect.
   * @param start - Starting position of the gradient. Defaults to [Offset.Zero] which
   * represents the top-left of the drawing area.
   * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`.
   * @param end - Ending position of the gradient. Defaults to
   * [Offset.Infinite] which represents the bottom-right of the drawing area.
   * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`
   */
  data class LinearGradient(
    override val steps: Int = STEPS_AUTO_BALANCED,
    val easing: Easing = EaseIn,
    val start: Offset = Offset.Zero,
    val startIntensity: Float = 0f,
    val end: Offset = Offset.Infinite,
    val endIntensity: Float = 1f,
  ) : HazeProgressive

  companion object {
    /**
     * A vertical gradient effect.
     *
     * @param steps - The number of steps in the effect. See [HazeProgressive.steps] for information.
     * @param easing - The easing function to use when applying the effect. Defaults to a
     * linear easing effect.
     * @param startY - Starting x position of the horizontal gradient. Defaults to 0 which
     * represents the top of the drawing area.
     * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`.
     * @param endY - Ending x position of the horizontal gradient. Defaults to
     * [Float.POSITIVE_INFINITY] which represents the bottom of the drawing area.
     * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`
     */
    fun verticalGradient(
      steps: Int = STEPS_AUTO_BALANCED,
      easing: Easing = EaseIn,
      startY: Float = 0f,
      startIntensity: Float = 0f,
      endY: Float = Float.POSITIVE_INFINITY,
      endIntensity: Float = 1f,
    ): LinearGradient = LinearGradient(
      steps = steps,
      easing = easing,
      start = Offset(0f, startY),
      startIntensity = startIntensity,
      end = Offset(0f, endY),
      endIntensity = endIntensity,
    )

    /**
     * A horizontal gradient effect.
     *
     * @param steps - The number of steps in the effect. See [HazeProgressive.steps] for information.
     * @param easing - The easing function to use when applying the effect. Defaults to a
     * linear easing effect.
     * @param startX - Starting x position of the horizontal gradient. Defaults to 0 which
     * represents the left of the drawing area
     * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`
     * @param endX - Ending x position of the horizontal gradient. Defaults to
     * [Float.POSITIVE_INFINITY] which represents the right of the drawing area.
     * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`
     */
    fun horizontalGradient(
      steps: Int = STEPS_AUTO_BALANCED,
      easing: Easing = EaseIn,
      startX: Float = 0f,
      startIntensity: Float = 0f,
      endX: Float = Float.POSITIVE_INFINITY,
      endIntensity: Float = 1f,
    ): LinearGradient = LinearGradient(
      steps = steps,
      easing = easing,
      start = Offset(startX, 0f),
      startIntensity = startIntensity,
      end = Offset(endX, 0f),
      endIntensity = endIntensity,
    )

    /**
     * Value which indicates the [steps] value should be automatically computed,
     * balancing quality and performance.
     */
    const val STEPS_AUTO_BALANCED = -1
  }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
  return start + fraction * (stop - start)
}

internal expect fun HazeChildNode.createRenderEffect(
  blurRadiusPx: Float,
  noiseFactor: Float,
  tints: List<HazeTint> = emptyList(),
  tintAlphaModulate: Float = 1f,
  boundsInLayer: Rect,
  layerSize: Size,
  mask: Brush? = null,
): RenderEffect?

internal val HazeChildNode.resolvedBackgroundColor: Color
  get() = backgroundColor
    .takeOrElse { style.backgroundColor }
    .takeOrElse { compositionLocalStyle.backgroundColor }

internal val HazeChildNode.resolvedBlurRadius: Dp
  get() = blurRadius
    .takeOrElse { style.blurRadius }
    .takeOrElse { compositionLocalStyle.blurRadius }

internal val HazeChildNode.resolvedTints: List<HazeTint>
  get() = tints.takeIf { it.isNotEmpty() }
    ?: style.tints.takeIf { it.isNotEmpty() }
    ?: compositionLocalStyle.tints.takeIf { it.isNotEmpty() }
    ?: emptyList()

internal val HazeChildNode.resolvedFallbackTint: HazeTint
  get() = fallbackTint.takeIf { it.isSpecified }
    ?: style.fallbackTint.takeIf { it.isSpecified }
    ?: compositionLocalStyle.fallbackTint

internal val HazeChildNode.resolvedNoiseFactor: Float
  get() = noiseFactor
    .takeOrElse { style.noiseFactor }
    .takeOrElse { compositionLocalStyle.noiseFactor }

private fun Size.expand(expansion: Float): Size {
  return Size(width = width + expansion, height = height + expansion)
}

private fun calculateLength(
  start: Offset,
  end: Offset,
  size: Size,
): Float {
  val (startX, startY) = start
  val endX = end.x.coerceAtMost(size.width)
  val endY = end.y.coerceAtMost(size.height)
  return hypot(endX - startX, endY - startY)
}
