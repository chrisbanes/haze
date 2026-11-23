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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toSize
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.scaledContentScale

internal actual fun RenderEffectBlurVisualEffectDelegate.drawLogicalCustomStyle(
  drawScope: DrawScope,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
): Boolean {
  if (Build.VERSION.SDK_INT !in 31..32 && !runtimeShaderFallback) return false
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

  // A ShaderBrush keeps the shader it built for the last size, so passing the node size reuses it.
  fun DrawScope.drawLogicalRect(brush: Brush, alpha: Float, blendMode: BlendMode) {
    brush.applyTo(context.size, LogicalRectPaint, alpha)
    LogicalRectPaint.blendMode = blendMode
    drawContext.canvas.drawRect(bounds, LogicalRectPaint)
  }

  fun DrawScope.mask(brush: Brush) = drawLogicalRect(brush, 1f, BlendMode.DstIn)

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
            canvas.withSaveLayer(bounds, paint) {
              // Presentation space, so the grain does not follow the capture scale.
              val noise = ShaderBrush(LogicalNoiseShaders.get(context.requirePlatformContext()))
              translate(-offset.x, -offset.y) {
                drawRect(brush = noise, size = expandedSize, alpha = style.noiseFactor.coerceIn(0f, 1f))
              }
            }
          }
        }
        return
      }
      drawStack(count - 1)
      val effect = effects[count - 1]
      val colorEffect = (effect as? TintBrushHazeColorEffect)?.asColorTintOrNull() ?: effect
      when (colorEffect) {
        is TintBrushHazeColorEffect ->
          drawLogicalRect(colorEffect.brush, intensity.coerceIn(0f, 1f), colorEffect.blendMode)
        is TintColorHazeColorEffect -> {
          colorEffect.resolveColor(intensity)?.let { color ->
            drawRect(color, topLeft = -offset, size = expandedSize, blendMode = colorEffect.blendMode)
          }
        }
        is ColorFilterHazeColorEffect -> PaintPool.usePaint { blend ->
          blend.blendMode = colorEffect.blendMode
          canvas.withSaveLayer(bounds, blend) {
            PaintPool.usePaint { filter ->
              filter.colorFilter = colorEffect.colorFilter
              canvas.withSaveLayer(bounds, filter) { drawStack(count - 1) }
            }
          }
        }
      }
    }
    drawStack(effects.size)
  }

  fun DrawScope.drawOutput() {
    val canvas = drawContext.canvas
    PaintPool.usePaint { alpha ->
      alpha.alpha = style.alpha
      canvas.withSaveLayer(bounds, alpha) {
        val progressive = style.progressive
        if (progressive is HazeProgressive.LinearGradient && shouldDrawProgressiveWithLayers(progressive, inputScale)) {
          logicalSlot.reset()
          logicalLayer.release()
          var band = 0
          drawProgressiveWithMultipleLayers(progressive, masks = bandMasks) { bandMask, intensity ->
            val index = band++
            val layer = bandLayer(index).acquire(context.requireGraphicsContext())
            prepare(layer, bandSlot(index), intensity)
            canvas.withSaveLayer(bounds, LogicalLayerPaint) {
              style(layer, intensity)
              mask(bandMask)
            }
          }
          trimBandSlots(band)
        } else {
          trimBandSlots(0)
          val layer = logicalLayer.acquire(context.requireGraphicsContext())
          prepare(layer, logicalSlot, 1f)
          if (progressive is HazeProgressive.LinearGradient) present(contentLayer)
          if (progressive != null) {
            canvas.withSaveLayer(bounds, LogicalLayerPaint) {
              style(layer, 1f)
              style.progressiveBrush?.let { mask(it) }
            }
          } else {
            style(layer, 1f)
          }
        }
        style.mask?.let { mask(it) }
      }
    }
  }

  with(drawScope) {
    if (clip) clipRect { drawOutput() } else drawOutput()
  }
  return true
}

// Draws run on one thread, as for PaintPool. Canvas reads a paint when called, so reuse is safe.
private val LogicalLayerPaint = Paint()
private val LogicalRectPaint = Paint()
internal val LogicalNoiseShaders = NoiseShaderCache()

@Poko
private class LogicalStyle(
  val mask: Brush?,
  val progressive: HazeProgressive?,
  val progressiveBrush: Brush?,
  val colorEffects: List<HazeColorEffect>,
  val noiseFactor: Float,
  val alpha: Float,
)
