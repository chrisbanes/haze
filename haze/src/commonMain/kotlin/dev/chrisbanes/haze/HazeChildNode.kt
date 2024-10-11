// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
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

internal class HazeChildNode(
  var state: HazeState,
  var block: HazeChildScope.() -> Unit,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode {

  private var positionOnScreen by mutableStateOf(Offset.Unspecified)

  private val effect by lazy(::ReusableHazeEffect)

  override val shouldAutoInvalidate: Boolean = false

  fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    update()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      updateEffect()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    effect.positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
    effect.size = coordinates.size.toSize()

    val blurRadiusPx = with(currentValueOf(LocalDensity)) { effect.blurRadius.toPx() }
    effect.layerSize = effect.size.expand(blurRadiusPx * 2)

    updateEffect()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  override fun ContentDrawScope.draw() {
    log(TAG) { "-> HazeChild. start draw()" }

    if (!effect.isValid) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      log(TAG) { "-> HazeChild. end draw()" }
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    effect.onPreDraw(drawContext.density)

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

    effect.onPostDraw()

    log(TAG) { "-> HazeChild. end draw()" }
  }

  private fun updateEffect() {
    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block(effect)

    if (effect.needInvalidation) {
      log("HazeChildNode") { "invalidateDraw called, due to effect needing invalidation" }
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
    val inflatedSize = effect.layerSize
    // This is the topLeft in the inflated bounds where the real are should be at [0,0]
    val inflatedOffset = effect.layerOffset

    clippedContentLayer.record(inflatedSize.roundToIntSize()) {
      require(effect.backgroundColor.isSpecified) {
        "backgroundColor not specified. Please provide a color."
      }
      drawRect(effect.backgroundColor)

      translate(inflatedOffset + state.positionOnScreen - effect.positionOnScreen) {
        // Draw the content into our effect layer
        drawLayer(contentLayer)
      }
    }

    val progressive = effect.progressive
    if (progressive is HazeProgressive.LinearGradient && useGraphicLayers()) {
      drawLinearGradientProgressiveEffect(
        effect = effect,
        progressive = progressive,
        innerDrawOffset = -inflatedOffset,
        contentLayer = clippedContentLayer,
      )
    } else {
      clippedContentLayer.renderEffect = effect.renderEffect
      clippedContentLayer.alpha = effect.alpha

      withPositionAndClip(
        effectPositionOnScreen = effect.positionOnScreen,
        size = effect.size,
        innerDrawOffset = -inflatedOffset,
      ) {
        drawLayer(clippedContentLayer)
      }
    }

    graphicsContext.releaseGraphicsLayer(clippedContentLayer)
  }

  private fun DrawScope.drawEffectWithScrim() {
    // Maybe we can do this progressive too?
    drawFallbackEffect(
      alpha = effect.alpha,
      blurRadius = effect.blurRadius,
      tints = effect.tints,
      fallbackTint = effect.fallbackTint,
      mask = effect.mask,
    )
  }

  private fun DrawScope.drawLinearGradientProgressiveEffect(
    effect: ReusableHazeEffect,
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
      val length = calculateLength(progressive.start, progressive.end, effect.size)
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

      log("HazeChildNode") {
        "drawProgressiveEffect. " +
          "step=$i, " +
          "fraction=$fraction, " +
          "intensity=$intensity, " +
          "maskStops=${maskStops.map { it.first to it.second.alpha }}"
      }

      val blurRadiusPx = with(drawContext.density) { effect.blurRadiusOrZero.toPx() }
      val boundsInLayer = Rect(effect.layerOffset, effect.size)

      layer.alpha = effect.alpha
      layer.renderEffect = createRenderEffect(
        blurRadiusPx = intensity * blurRadiusPx,
        noiseFactor = effect.noiseFactor,
        tints = effect.tints,
        tintAlphaModulate = intensity,
        boundsInLayer = boundsInLayer, // cache this
        layerSize = effect.layerSize,
        mask = Brush.linearGradient(
          *maskStops.toTypedArray(),
          start = progressive.start,
          end = progressive.end,
        ),
      )

      withPositionAndClip(
        effectPositionOnScreen = effect.positionOnScreen,
        size = effect.size,
        innerDrawOffset = innerDrawOffset,
        block = { drawLayer(layer) },
      )

      graphicsContext.releaseGraphicsLayer(layer)
    }
  }

  private inline fun DrawScope.withPositionAndClip(
    effectPositionOnScreen: Offset,
    size: Size,
    innerDrawOffset: Offset = Offset.Zero,
    block: DrawScope.() -> Unit,
  ) {
    val drawOffset = (effectPositionOnScreen - positionOnScreen).takeOrElse { Offset.Zero }
    translate(drawOffset) {
      clipRect(right = size.width, bottom = size.height) {
        // Since we included a border around the content, we need to translate so that
        // we don't see it (but it still affects the RenderEffect)
        translate(innerDrawOffset, block)
      }
    }
  }

  private fun ReusableHazeEffect.onPreDraw(density: Density) {
    if (renderEffectDirty) {
      renderEffect = createRenderEffect(
        blurRadiusPx = with(density) { blurRadiusOrZero.toPx() }, // cache this
        noiseFactor = noiseFactor,
        tints = tints,
        boundsInLayer = Rect(layerOffset, size), // cache this
        layerSize = layerSize,
        mask = mask,
      )
      renderEffectDirty = false
    }
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }

  private fun ReusableHazeEffect.onPostDraw() {
    drawParametersDirty = false
    progressiveDirty = false
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

internal class ReusableHazeEffect : HazeChildScope {
  var renderEffect: RenderEffect? = null
  var renderEffectDirty: Boolean = true
  var drawParametersDirty: Boolean = true
  var progressiveDirty: Boolean = true

  var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)

  val isValid: Boolean
    get() = size.isSpecified && layerSize.isSpecified

  var size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "size changed. Current: $field. New: $value" }
        // We use the size for crop rects/brush sizing
        renderEffectDirty = true
        field = value
      }
    }

  var layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "layerSize changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  val layerOffset: Offset
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
        log("ReusableHazeEffect") { "blurRadius changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var noiseFactor: Float = HazeDefaults.noiseFactor
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "noiseFactor changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var mask: Brush? = null
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "mask changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var backgroundColor: Color = Color.Unspecified

  override var tints: List<HazeTint> = emptyList()
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "tints changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var fallbackTint: HazeTint? = null
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "fallbackTint changed. Current: $field. New: $value" }
        renderEffectDirty = true
        field = value
      }
    }

  override var alpha: Float = 1f
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "alpha changed. Current $field. New: $value" }
        drawParametersDirty = true
        field = value
      }
    }

  override var progressive: HazeProgressive? = null
    set(value) {
      if (value != field) {
        log("ReusableHazeEffect") { "progressive changed. Current $field. New: $value" }
        progressiveDirty = true
        field = value
      }
    }

  override fun applyStyle(style: HazeStyle) {
    noiseFactor = style.noiseFactor
    blurRadius = style.blurRadius
    tints = style.tints
    fallbackTint = style.fallbackTint
    backgroundColor = style.backgroundColor
  }
}

internal val ReusableHazeEffect.blurRadiusOrZero: Dp
  get() = blurRadius.takeOrElse { 0.dp }

internal val ReusableHazeEffect.needInvalidation: Boolean
  get() {
    log("ReusableHazeEffect") {
      "needInvalidation. renderEffectDirty=$renderEffectDirty, " +
        "drawParametersDirty=$drawParametersDirty, " +
        "progressiveDirty=$progressiveDirty"
    }
    return renderEffectDirty || drawParametersDirty || progressiveDirty
  }

private fun Size.expand(expansion: Float): Size {
  return Size(width = width + expansion, height = height + expansion)
}

private fun DrawScope.drawFallbackEffect(
  alpha: Float,
  blurRadius: Dp,
  tints: List<HazeTint>,
  fallbackTint: HazeTint?,
  mask: Brush?,
) {
  val tint = fallbackTint
    ?.takeIf { it.color.isSpecified }
    ?: tints.firstOrNull()?.boostForFallback(blurRadius.takeOrElse { 0.dp })

  log("HazeChildNode") { "drawEffect. Drawing effect with scrim: tint=$tint, mask=$mask, alpha=$alpha" }

  fun scrim() {
    if (tint != null) {
      if (mask != null) {
        drawRect(brush = mask, colorFilter = ColorFilter.tint(tint.color))
      } else {
        drawRect(color = tint.color, blendMode = tint.blendMode)
      }
    }
  }

  if (alpha != 1f) {
    val paint = Paint().apply {
      this.alpha = alpha
    }
    drawContext.canvas.withSaveLayer(size.toRect(), paint) {
      scrim()
    }
  } else {
    scrim()
  }
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
