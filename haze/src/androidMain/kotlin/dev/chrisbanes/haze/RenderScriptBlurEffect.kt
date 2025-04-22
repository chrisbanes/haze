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
import android.view.View
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
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RenderScriptBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private var renderScriptContext: RenderScriptContext? = null
  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null
  private var drawSkipped: Boolean = false

  private val graphicsContext: GraphicsContext
    get() = node.currentValueOf(LocalGraphicsContext)

  private val contentLayer: GraphicsLayer = graphicsContext.createGraphicsLayer()

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
      )?.let { layer ->
        currentJob =
          node.coroutineScope.launch(Dispatchers.Main.immediate, CoroutineStart.UNDISPATCHED) {
            // Wait for the real content to drawn on screen
            if (node.isAttached) {
              node.currentValueOf(LocalView).awaitFrameCommit()
            }
            // Now draw the content into the RS Surface
            if (node.isAttached) {
              updateSurface(content = layer, blurRadius = blurRadiusPx)
            }
            // Release the graphics layer
            if (node.isAttached) {
              graphicsContext.releaseGraphicsLayer(layer)
            }
            // If any draws were skipped, let's trigger a draw invalidation
            if (node.isAttached && drawSkipped) {
              node.invalidateDraw()
            }
          }
      }
    } else {
      // Mark this draw as skipped
      drawSkipped = true
    }

    node.withGraphicsLayer { layer ->
      layer.alpha = node.alpha

      val mask = node.mask ?: node.progressive?.asBrush()
      if (mask != null) {
        // If we have a mask, this needs to be drawn offscreen
        layer.compositingStrategy = CompositingStrategy.Offscreen
      }

      layer.record(size = contentLayer.size) {
        drawLayer(contentLayer)

        val contentSize = ceil(node.size * scaleFactor)
        val contentOffset = (offset * scaleFactor).round()

        translate(contentOffset) {
          // Draw the noise on top...
          val noiseFactor = node.resolveNoiseFactor()
          if (noiseFactor > 0f) {
            PaintPool.usePaint { paint ->
              val texture = context.getNoiseTexture(noiseFactor, scaleFactor)
              paint.shader = BitmapShader(texture, REPEAT, REPEAT)
              drawContext.canvas.drawRect(contentSize.toRect(), paint)
            }
          }

          // Then the tints...
          for (tint in node.resolveTints()) {
            drawScrim(tint = tint, node = node, size = contentSize)
          }

          if (mask != null) {
            HazeLogger.d(TAG) {
              "Drawing mask. contentSize=$contentSize, offset=$contentOffset, canvas size=$size"
            }
            drawRect(brush = mask, size = contentSize, blendMode = BlendMode.DstIn)
          }
        }
      }

      drawScaledContent(offset = -offset, scaleFactor = scaleFactor) {
        drawLayer(layer)
      }
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

  private suspend fun updateSurface(content: GraphicsLayer, blurRadius: Float) {
    traceAsync("Haze-RenderScriptBlurEffect-updateSurface", 0) {
      val rs = getRenderScriptContext(
        context = node.currentValueOf(LocalContext),
        size = content.size,
      )
      traceAsync("Haze-RenderScriptBlurEffect-updateSurface-drawLayerToSurface", 0) {
        // Draw the layer (this is async)
        rs.inputSurface.drawGraphicsLayer(layer = content, density = density, drawScope = drawScope)
      }

      // Now apply the blur on a background thread
      val output = traceAsync("Haze-RenderScriptBlurEffect-updateSurface-applyBlur", 0) {
        withContext(Dispatchers.Default) {
          rs.process(blurRadius)
        }
      }

      if (output != null) {
        trace("Haze-RenderScriptBlurEffect-updateSurface-drawToContentLayer") {
          // Finally draw the updated bitmap to our drawing graphics layer
          contentLayer.record(
            density = density,
            layoutDirection = node.currentValueOf(LocalLayoutDirection),
            size = IntSize(output.width, output.height),
          ) {
            drawImage(output.asImageBitmap())
          }
        }
      }

      HazeLogger.d(TAG) { "Output updated in layer" }
    }
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
    graphicsContext.releaseGraphicsLayer(contentLayer)
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

private suspend fun View.awaitFrameCommit() {
  val vto = viewTreeObserver

  if (Build.VERSION.SDK_INT >= 29) {
    suspendCoroutine { cont ->
      vto.registerFrameCommitCallback { cont.resume(Unit) }
    }
  } else {
    suspendCoroutine { cont ->
      post { cont.resume(Unit) }
    }
  }
}
