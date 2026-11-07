// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.scaledContentScale

internal actual fun RenderEffectBlurVisualEffectDelegate.drawLogicalCustomStyle(
  drawScope: DrawScope,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
): Boolean {
  if (Build.VERSION.SDK_INT !in 31..32) return false
  val effect = blurVisualEffect
  val progressiveBrush = (effect.progressive as? HazeProgressive.Brush)?.brush
  val custom = effect.mask?.isCustomShaderBrush() == true ||
    progressiveBrush?.isCustomShaderBrush() == true ||
    effect.colorEffects.orEmpty().any { it is TintBrushHazeColorEffect && it.brush.isCustomShaderBrush() }
  if (!custom) return false
  val style = LogicalStyle(
    mask = effect.mask,
    progressive = effect.progressive,
    progressiveBrush = effect.progressiveBrush,
    colorEffects = effect.colorEffects.orEmpty(),
    noiseFactor = effect.noiseFactor,
    alpha = effect.alpha,
  )

  if (!logicalPathActive) {
    effectSlot.apply(contentLayer, null)
    effectSlot.reset()
    logicalPathActive = true
  }
  contentLayer.alpha = 1f
  val captureScale = scaledContentScale(context.layerSize, inputScale)
  val offset = context.layerOffset
  val expandedSize = context.layerSize
  val bounds = Rect(-offset, expandedSize)
  val clip = effect.shouldClipToNodeBounds()

  fun DrawScope.present(layer: GraphicsLayer) {
    drawScaledContent(offset = -offset, captureScale = captureScale, clip = false) { drawLayer(layer) }
  }

  fun DrawScope.prepare(layer: GraphicsLayer, slot: BlurRenderEffectSlot, intensity: Float) {
    layer.record(contentLayer.size) { drawLayer(contentLayer) }
    layer.alpha = 1f
    layer.clip = false
    slot.apply(
      layer,
      effect.getOrCreateRenderEffect(
        context = context,
        inputScale = inputScale,
        captureScale = captureScale,
        captureSize = contentLayer.size.toSize(),
        blurRadius = effect.blurRadius.takeOrElse { 0.dp } * intensity,
        noiseFactor = 0f,
        colorEffects = emptyList(),
        mask = null,
        progressiveMask = null,
        progressive = null,
        slot = slot,
      ),
    )
  }

  fun Brush.logical(): Brush = if (this is ShaderBrush) ShaderBrush(createShader(context.size)) else this

  fun DrawScope.mask(brush: Brush) {
    drawRect(brush = brush.logical(), topLeft = -offset, size = expandedSize, blendMode = BlendMode.DstIn)
  }

  fun DrawScope.style(layer: GraphicsLayer, intensity: Float) {
    val effects = style.colorEffects
    val canvas = drawContext.canvas

    // shortcut: stacked color filters replay prior draws exponentially; retain stages if this becomes common.
    fun DrawScope.drawStack(count: Int) {
      if (count == 0) {
        present(layer)
        if (style.noiseFactor.hasVisibleNoise()) {
          PaintPool.usePaint { paint ->
            paint.blendMode = BlendMode.Softlight
            canvas.saveLayer(bounds, paint)
            val noise = ShaderBrush(context.requirePlatformContext().createNoiseShader(inputScale))
            drawScaledContent(offset = -offset, captureScale = captureScale, clip = false) {
              drawRect(brush = noise, size = contentLayer.size.toSize(), alpha = style.noiseFactor.coerceIn(0f, 1f))
            }
            canvas.restore()
          }
        }
        return
      }
      drawStack(count - 1)
      when (val colorEffect = effects[count - 1]) {
        is TintBrushHazeColorEffect -> drawRect(
          brush = colorEffect.brush.logical(),
          topLeft = -offset,
          size = expandedSize,
          alpha = intensity.coerceIn(0f, 1f),
          blendMode = colorEffect.blendMode,
        )
        is TintColorHazeColorEffect -> {
          val color = colorEffect.color.copy(alpha = colorEffect.color.alpha * intensity)
          if (color.alpha >= 0.005f) drawRect(color, topLeft = -offset, size = expandedSize, blendMode = colorEffect.blendMode)
        }
        is ColorFilterHazeColorEffect -> PaintPool.usePaint { blend ->
          blend.blendMode = colorEffect.blendMode
          canvas.saveLayer(bounds, blend)
          PaintPool.usePaint { filter ->
            filter.colorFilter = colorEffect.colorFilter
            canvas.saveLayer(bounds, filter)
            drawStack(count - 1)
            canvas.restore()
          }
          canvas.restore()
        }
      }
    }
    drawStack(effects.size)
  }

  fun DrawScope.drawOutput() {
    val canvas = drawContext.canvas
    PaintPool.usePaint { alpha ->
      alpha.alpha = style.alpha
      canvas.saveLayer(bounds, alpha)
      val progressive = style.progressive
      if (progressive is HazeProgressive.LinearGradient && shouldDrawProgressiveWithLayers(progressive, inputScale)) {
        logicalSlot.reset()
        logicalLayer.release()
        var band = 0
        drawProgressiveWithMultipleLayers(progressive, masks = bandMasks) { bandMask, intensity ->
          val index = band++
          val layer = bandLayer(index).acquire(context.requireGraphicsContext())
          prepare(layer, bandSlot(index), intensity)
          canvas.saveLayer(bounds, Paint())
          style(layer, intensity)
          mask(bandMask)
          canvas.restore()
        }
        trimBandSlots(band)
      } else {
        trimBandSlots(0)
        val layer = logicalLayer.acquire(context.requireGraphicsContext())
        prepare(layer, logicalSlot, 1f)
        if (progressive is HazeProgressive.LinearGradient) present(contentLayer)
        if (progressive != null) canvas.saveLayer(bounds, Paint())
        style(layer, 1f)
        if (progressive != null) {
          style.progressiveBrush?.let { mask(it) }
          canvas.restore()
        }
      }
      style.mask?.let { mask(it) }
      canvas.restore()
    }
  }

  with(drawScope) {
    if (clip) clipRect { drawOutput() } else drawOutput()
  }
  return true
}

private data class LogicalStyle(
  val mask: Brush?,
  val progressive: HazeProgressive?,
  val progressiveBrush: Brush?,
  val colorEffects: List<HazeColorEffect>,
  val noiseFactor: Float,
  val alpha: Float,
)
