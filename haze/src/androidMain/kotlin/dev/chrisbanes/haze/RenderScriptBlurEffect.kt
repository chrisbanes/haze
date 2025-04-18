// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import android.view.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class RenderScriptBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private var renderScriptContext: RenderScriptContext? = null
  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null
  private var drawSkipped: Boolean = false

  private var drawGraphicsLayer: GraphicsLayer? = null
  private val graphicsContext: GraphicsContext
    get() = node.currentValueOf(LocalGraphicsContext)

  private val density: Density
    get() = node.requireDensity()

  override fun DrawScope.drawEffect() {
    val context = node.currentValueOf(LocalContext)
    val offset = node.layerOffset
    var scaleFactor = node.calculateInputScaleFactor()
    var blurRadiusPx = scaleFactor * with(density) { node.resolveBlurRadius().toPx() }

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
        node = node,
        scaleFactor = scaleFactor,
        layerSize = node.layerSize,
        layerOffset = offset,
      )?.let { scaledLayer ->
        if (drawGraphicsLayer == null) {
          // If there is no graphics layer yet, then this is the first draw. We'll generate
          // this blocking, so that the user doesn't see an un-blurred first frame
          runBlocking {
            updateSurface(content = scaledLayer, blurRadius = blurRadiusPx)
            // Release the graphics layer
            graphicsContext.releaseGraphicsLayer(scaledLayer)
          }
        } else {
          currentJob = node.coroutineScope.launch(Dispatchers.Main.immediate) {
            updateSurface(content = scaledLayer, blurRadius = blurRadiusPx)
            // Release the graphics layer
            graphicsContext.releaseGraphicsLayer(scaledLayer)

            if (drawSkipped) {
              // If any draws were skipped, let's trigger a draw invalidation
              node.invalidateDraw()
            }
          }
        }
      }
    } else {
      // Mark this draw as skipped
      drawSkipped = true
    }

    drawGraphicsLayer?.let { layer ->
      drawScaledContentLayer(offset = -offset, scaleFactor = scaleFactor) {
        // TODO: apply mask or progressive to layer?

        layer.alpha = node.alpha
        drawLayer(layer)

        withAlpha(node.alpha) {
          val noiseFactor = node.resolveNoiseFactor()
          if (noiseFactor > 0f) {
            PaintPool.usePaint { paint ->
              val texture = context.getNoiseTexture(noiseFactor)
              paint.shader = BitmapShader(texture, REPEAT, REPEAT)
              drawContext.canvas.drawRect(size.toRect(), paint)
            }
          }

          for (tint in node.resolveTints()) {
            drawScrim(mask = node.mask, progressive = node.progressive, tint = tint)
          }
        }
      }
    }
  }

  private fun shouldUpdateLayer(): Boolean = when {
    // We don't have a layer yet...
    drawGraphicsLayer == null -> true
    // No ongoing update, so start an update...
    currentJob?.isActive != true -> true
    // Otherwise, there must be a job ongoing, skip this update
    else -> false
  }

  private suspend fun updateSurface(content: GraphicsLayer, blurRadius: Float) {
    val rs = getRenderScriptContext(
      context = node.currentValueOf(LocalContext),
      size = content.size,
    )
    // Draw the layer (this is async)
    rs.inputSurface.drawGraphicsLayer(layer = content, density = density, drawScope = drawScope)
    // Wait for the layer to be written to the Surface
    rs.awaitSurfaceWritten()

    if (!node.isAttached) return

    // Now apply the blur on a background thread
    withContext(Dispatchers.Default) {
      rs.applyBlur(blurRadius)
    }

    // Finally draw the updated bitmap to our drawing graphics layer
    val layer = drawGraphicsLayer
      ?: graphicsContext.createGraphicsLayer().also { drawGraphicsLayer = it }

    val output = rs.outputBitmap

    layer.record(
      density = density,
      layoutDirection = node.currentValueOf(LocalLayoutDirection),
      size = IntSize(output.width, output.height),
    ) {
      drawImage(output.asImageBitmap())
    }

    HazeLogger.d(TAG) { "Output updated in layer" }
  }

  private fun getRenderScriptContext(context: Context, size: IntSize): RenderScriptContext {
    val rs = renderScriptContext
    if (rs != null && rs.size == size && rs.context == context) return rs

    // Release any existing context
    rs?.release()
    // Return a new context and store it
    return RenderScriptContext(context = context, size = size)
      .also { renderScriptContext = it }
  }

  override fun cleanup() {
    currentJob?.cancel()
    drawGraphicsLayer?.let { graphicsContext.releaseGraphicsLayer(it) }
    renderScriptContext?.release()
  }

  companion object {
    const val TAG = "RenderScriptBlurEffect"
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
