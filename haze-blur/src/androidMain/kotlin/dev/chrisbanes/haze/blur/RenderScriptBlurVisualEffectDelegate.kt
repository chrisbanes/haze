// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")
@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import android.renderscript.RenderScript
import android.view.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.trace
import dev.chrisbanes.haze.traceAsync
import dev.chrisbanes.haze.withGraphicsLayer
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RenderScriptBlurVisualEffectDelegate(
  private val blurVisualEffect: BlurVisualEffect,
  private val graphicsContext: GraphicsContext,
  androidContext: android.content.Context,
) : BlurVisualEffect.Delegate {

  private val renderScript = RenderScript.create(androidContext)

  private var renderScriptContext: RenderScriptContext? = null
  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null
  private var drawSkipped: Boolean = false

  private val contentLayer: GraphicsLayer = graphicsContext.createGraphicsLayer()

  override fun DrawScope.draw(context: VisualEffectContext) {
    val density = context.requireDensity()
    val androidContext = context.requirePlatformContext()
    val offset = context.layerOffset
    var scaleFactor = blurVisualEffect.calculateInputScaleFactor(context.inputScale)

    var blurRadiusPx = scaleFactor * with(density) {
      blurVisualEffect.blurRadius.takeOrElse { 0.dp }.toPx()
    }
    if (blurRadiusPx > MAX_BLUR_RADIUS) {
      // RenderScript has a max blur radius (25px), so to create an equivalent visual effect
      // we need to increase the scale factor
      scaleFactor *= (MAX_BLUR_RADIUS / blurRadiusPx)
      blurRadiusPx = MAX_BLUR_RADIUS
    }

    HazeLogger.d(TAG) { "drawEffect. blurRadius=${blurRadiusPx}px. scaleFactor=$scaleFactor" }

    if (shouldUpdateLayer()) {
      drawSkipped = false

      createScaledContentLayer(
        context = context,
        scaleFactor = scaleFactor,
        layerSize = context.layerSize,
        layerOffset = offset,
        backgroundColor = blurVisualEffect.backgroundColor,
      )?.let { layer ->
        layer.clip = context.visualEffect.shouldClip()

        currentJob = context.coroutineScope.launch(Dispatchers.Main.immediate) {
          updateSurface(
            content = layer,
            blurRadius = blurRadiusPx,
            context = context,
            density = density,
          )
          // Release the graphics layer
          graphicsContext.releaseGraphicsLayer(layer)

          if (drawSkipped) {
            // If any draws were skipped, let's trigger a draw invalidation
            context.invalidateDraw()
          }
        }
      }
    } else {
      // Mark this draw as skipped
      drawSkipped = true
    }

    context.withGraphicsLayer { layer ->
      layer.alpha = blurVisualEffect.alpha
      layer.clip = blurVisualEffect.shouldClip()

      val mask = blurVisualEffect.progressive?.asBrush() ?: blurVisualEffect.mask
      if (mask != null) {
        // If we have a mask, this needs to be drawn offscreen
        layer.compositingStrategy = CompositingStrategy.Offscreen
      }

      layer.record(size = size.toIntSize()) {
        drawScaledContent(
          offset = -offset,
          scaledSize = size * scaleFactor,
          clip = context.visualEffect.shouldClip(),
        ) {
          drawLayer(contentLayer)
        }

        val expandedSize = size.expand(
          expansionWidth = max(offset.x, 0f) * 2,
          expansionHeight = max(offset.y, 0f) * 2,
        )

        // Draw the noise on top...
        val noiseFactor = blurVisualEffect.noiseFactor
        if (noiseFactor > 0f) {
          translate(offset = -offset) {
            PaintPool.usePaint { paint ->
              paint.isAntiAlias = true
              paint.alpha = noiseFactor.coerceIn(0f, 1f)
              val shader = BitmapShader(androidContext.getNoiseTexture(), REPEAT, REPEAT)
              val normalizedScale = if (scaleFactor > 0f) scaleFactor else 1f
              if (abs(normalizedScale - 1f) >= 0.001f) {
                val matrix = Matrix().apply {
                  val reciprocal = 1f / normalizedScale
                  setScale(reciprocal, reciprocal)
                }
                shader.setLocalMatrix(matrix)
              }
              paint.shader = shader
              paint.blendMode = BlendMode.SrcAtop
              drawContext.canvas.drawRect(expandedSize.toRect(), paint)
            }
          }
        }

        // Then the tints...
        translate(offset = -offset) {
          for (tint in blurVisualEffect.tints) {
            drawScrim(
              tint = tint,
              context = context,
              offset = offset,
              expandedSize = expandedSize,
              mask = mask,
            )
          }
        }

        if (mask != null) {
          HazeLogger.d(TAG) {
            "Drawing mask, canvas size=$size"
          }
          drawRect(brush = mask, size = size, blendMode = BlendMode.DstIn)
        }
      }

      drawLayer(layer)
    }
  }

  private fun shouldUpdateLayer(): Boolean = when {
    // We don't have a layer yet...
    contentLayer.size == IntSize.Zero -> true
    // No ongoing update, so start an update...
    currentJob?.isActive != true -> true
    // Otherwise, there must be a job ongoing, skip this update
    else -> false
  }

  private suspend fun updateSurface(
    content: GraphicsLayer,
    blurRadius: Float,
    context: VisualEffectContext,
    density: Density,
  ) {
    traceAsync("Haze-RenderScriptBlurEffect-updateSurface", 0) {
      val rs = getRenderScriptContext(content.size)
      traceAsync("Haze-RenderScriptBlurEffect-updateSurface-drawLayerToSurface", 0) {
        // Draw the layer (this is async)
        rs.inputSurface.drawGraphicsLayer(layer = content, density = density, drawScope = drawScope)
        // Wait for the layer to be written to the Surface
        rs.awaitSurfaceWritten()
      }

      if (blurRadius > 0f) {
        // Now apply the blur on a background thread
        traceAsync("Haze-RenderScriptBlurEffect-updateSurface-applyBlur", 0) {
          withContext(Dispatchers.Default) {
            rs.applyBlur(blurRadius)
          }
        }

        trace("Haze-RenderScriptBlurEffect-updateSurface-drawToContentLayer") {
          // Finally draw the updated bitmap to our drawing graphics layer
          val output = rs.outputBitmap

          contentLayer.record(
            density = density,
            layoutDirection = context.currentValueOf(LocalLayoutDirection),
            size = IntSize(output.width, output.height),
          ) {
            drawImage(output.asImageBitmap())
          }
        }
      } else {
        // If the blur radius is 0, we just copy the input content into our contentLayer
        contentLayer.record(
          density = density,
          layoutDirection = context.currentValueOf(LocalLayoutDirection),
          size = content.size,
        ) {
          drawLayer(content)
        }
      }

      HazeLogger.d(TAG) { "Output updated in layer" }
    }
  }

  private fun getRenderScriptContext(size: IntSize): RenderScriptContext {
    val rs = renderScriptContext
    if (rs != null && rs.size == size) return rs

    // Release any existing context
    rs?.release()
    // Return a new context and store it
    return RenderScriptContext(rs = renderScript, size = size)
      .also { renderScriptContext = it }
  }

  override fun detach() {
    currentJob?.cancel()
    graphicsContext.releaseGraphicsLayer(contentLayer)
    renderScriptContext?.release()
  }

  internal companion object {
    const val TAG = "RenderScriptBlurVisualEffectDelegate"

    private var isEnabled: Boolean = true

    fun createOrNull(
      effect: BlurVisualEffect,
      context: VisualEffectContext,
    ): RenderScriptBlurVisualEffectDelegate? {
      if (isEnabled) {
        return runCatching {
          RenderScriptBlurVisualEffectDelegate(
            blurVisualEffect = effect,
            graphicsContext = context.requireGraphicsContext(),
            androidContext = context.requirePlatformContext(),
          )
        }
          .onFailure { isEnabled = false }
          .getOrNull()
      }
      return null
    }
  }
}

private const val MAX_BLUR_RADIUS = 25f

private fun Surface.drawGraphicsLayer(
  layer: GraphicsLayer,
  density: Density,
  drawScope: CanvasDrawScope,
) {
  withSurfaceCanvas {
    drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    with(drawScope) {
      draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = Canvas(this@withSurfaceCanvas),
        size = Size(width.toFloat(), height.toFloat()),
      ) {
        drawLayer(layer)
      }
    }
  }
}

private inline fun Surface.withSurfaceCanvas(block: android.graphics.Canvas.() -> Unit) {
  val canvas = if (Build.VERSION.SDK_INT >= 23) {
    lockHardwareCanvas()
  } else {
    lockCanvas(null)
  }
  try {
    block(canvas)
  } finally {
    unlockCanvasAndPost(canvas)
  }
}
