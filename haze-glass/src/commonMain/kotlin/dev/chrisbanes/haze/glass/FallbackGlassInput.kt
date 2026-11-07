// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.asComposeRenderEffect
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal expect fun DrawScope.supportsFallbackGlassBlur(): Boolean
internal expect fun createFallbackGlassInputEffect(style: ResolvedGlassStyle, blurred: Boolean): PlatformRenderEffect?

private data class FallbackGlassInputKey(
  val input: HazeEffectInputSnapshot?,
  val size: Size,
  val offset: Offset,
  val bounds: Rect,
  val optics: ResolvedGlassOptics,
  val contrast: Float,
  val whitePoint: Float,
  val chromaMultiplier: Float,
)

/** Owns the fallback's capture and filtered outputs. */
internal class FallbackGlassInput {
  private var source: GraphicsLayer? = null
  internal var filtered: GraphicsLayer? = null
    private set
  private var sharp: GraphicsLayer? = null
  private var owner: GraphicsContext? = null
  private var key: FallbackGlassInputKey? = null
  private var filteredEffect: PlatformRenderEffect? = null
  private var sharpEffect: PlatformRenderEffect? = null
  internal var lastEffectForTest: PlatformRenderEffect? = null
    private set
  internal var filteredRecordCount: Int = 0
    private set

  private fun captureBounds(context: HazeEffectRuntimeDrawScope, style: ResolvedGlassStyle, bounds: Rect): Rect {
    val offset = context.layerOffset
    val padding = ceil(style.resolvedOptics.blurRadiusPx)
    return Rect(
      floor((bounds.left + offset.x - padding).coerceAtLeast(0f)),
      floor((bounds.top + offset.y - padding).coerceAtLeast(0f)),
      ceil((bounds.right + offset.x + padding).coerceAtMost(context.layerSize.width)),
      ceil((bounds.bottom + offset.y + padding).coerceAtMost(context.layerSize.height)),
    )
  }

  fun DrawScope.canDraw(context: HazeEffectRuntimeDrawScope, style: ResolvedGlassStyle, bounds: Rect): Boolean =
    supportsFallbackGlassBlur() && context.hasDrawableInput && style.fallbackBaseColor == null &&
      (
        style.resolvedOptics.depth > 0f && style.resolvedOptics.blurRadiusPx > 0f ||
          style.contrast != 0f || style.whitePoint != 0f || style.chromaMultiplier != 1f
        ) && !bounds.isEmpty &&
      captureBounds(context, style, bounds).size.roundToIntSize().fitsGlassLayerBudget()

  fun DrawScope.draw(
    context: HazeEffectRuntimeDrawScope,
    style: ResolvedGlassStyle,
    alpha: Float,
    bounds: Rect,
    drawScrim: (DrawScope.() -> Unit)? = null,
  ): Boolean {
    if (!canDraw(context, style, bounds)) {
      release()
      return false
    }
    val optics = style.resolvedOptics
    val graphicsContext = context.requireGraphicsContext()
    if (owner != null && owner !== graphicsContext) release()
    owner = graphicsContext
    val offset = context.layerOffset
    val captureBounds = captureBounds(context, style, bounds)
    val left = captureBounds.left
    val top = captureBounds.top
    val captureSize = captureBounds.size.roundToIntSize()
    val capture = source?.takeUnless { it.isReleased } ?: graphicsContext.createGraphicsLayer()
    capture.renderEffect = null
    capture.alpha = 1f
    capture.blendMode = BlendMode.SrcOver
    recordGlassLayer(capture, captureSize) {
      translate(-left, -top) { with(context) { this@recordGlassLayer.drawInput() } }
    }
    source = capture

    val nextKey = FallbackGlassInputKey(
      context.inputSnapshot,
      context.layerSize,
      offset,
      bounds,
      optics,
      style.contrast,
      style.whitePoint,
      style.chromaMultiplier,
    )
    if (nextKey != key) {
      filteredEffect = createFallbackGlassInputEffect(style, blurred = true)
      sharpEffect = if (optics.progressive != null) createFallbackGlassInputEffect(style, blurred = false) else null
      key = nextKey
    }
    fun output(previous: GraphicsLayer?, blurred: Boolean): GraphicsLayer {
      val layer = previous?.takeUnless { it.isReleased } ?: graphicsContext.createGraphicsLayer()
      layer.alpha = 1f
      layer.blendMode = BlendMode.SrcOver
      val nativeEffect = if (blurred) filteredEffect else sharpEffect
      if (blurred) lastEffectForTest = nativeEffect
      layer.renderEffect = nativeEffect?.asComposeRenderEffect()
      recordGlassLayer(layer, captureSize) { drawLayer(capture) }
      return layer
    }
    val blurred = output(filtered, blurred = true).also { filtered = it }
    filteredRecordCount++
    val progressive = optics.progressive
    if (progressive == null) {
      releaseLayer(sharp, owner)
      sharp = null
    }
    val unblurred = if (progressive != null) output(sharp, blurred = false).also { sharp = it } else null
    fun DrawScope.drawOutput(layer: GraphicsLayer) {
      translate(left - offset.x, top - offset.y) { drawLayer(layer) }
    }
    withLayerPaint(alpha = alpha, blendMode = BlendMode.SrcOver, bounds = bounds) {
      val canvas = drawContext.canvas
      val paint = Paint().apply { colorFilter = fallbackToneFilter(style) }
      canvas.saveLayer(bounds, paint)
      try {
        if (progressive == null) {
          drawOutput(blurred)
        } else {
          val mask = progressive.asBrush()
          withLayerPaint(alpha = 1f, blendMode = BlendMode.SrcOver, bounds = bounds) {
            drawOutput(checkNotNull(unblurred))
            drawRect(mask, blendMode = BlendMode.DstOut)
          }
          withLayerPaint(alpha = 1f, blendMode = BlendMode.Plus, bounds = bounds) {
            drawOutput(blurred)
            drawRect(mask, blendMode = BlendMode.DstIn)
          }
        }
      } finally {
        canvas.restore()
      }
      drawScrim?.invoke(this)
    }
    return true
  }

  fun release() {
    releaseLayer(source, owner)
    releaseLayer(filtered, owner)
    releaseLayer(sharp, owner)
    source = null
    filtered = null
    sharp = null
    key = null
    filteredEffect = null
    sharpEffect = null
    lastEffectForTest = null
    owner = null
  }
}

private fun fallbackToneFilter(style: ResolvedGlassStyle): ColorFilter? {
  if (style.whitePoint == 0f && style.contrast == 0f && style.chromaMultiplier == 1f) return null
  val white = style.whitePoint.coerceIn(-1f, 1f)
  val contrast = 1f + style.contrast
  val scale = (1f - abs(white)) * contrast
  val bias = ((if (white > 0f) white else 0f) * contrast + 0.5f * (1f - contrast)) * 255f
  val saturation = style.chromaMultiplier
  val red = (1f - saturation) * 0.213f
  val green = (1f - saturation) * 0.715f
  val blue = (1f - saturation) * 0.072f
  return ColorFilter.colorMatrix(
    ColorMatrix(
      floatArrayOf(
        (red + saturation) * scale, green * scale, blue * scale, 0f, bias,
        red * scale, (green + saturation) * scale, blue * scale, 0f, bias,
        red * scale, green * scale, (blue + saturation) * scale, 0f, bias,
        0f, 0f, 0f, 1f, 0f,
      ),
    ),
  )
}
