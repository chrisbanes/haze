// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
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
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
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
        if (contentLayer.size == IntSize.Zero) {
          // If the layer is released, or doesn't have a size yet, we'll generate
          // this blocking, so that the user doesn't see an un-blurred first frame
          runBlocking {
            updateSurface(content = layer, blurRadius = blurRadiusPx)
            // Release the graphics layer
            graphicsContext.releaseGraphicsLayer(layer)
          }
        } else {
          currentJob = node.coroutineScope.launch(Dispatchers.Main.immediate) {
            updateSurface(content = layer, blurRadius = blurRadiusPx)
            // Release the graphics layer
            graphicsContext.releaseGraphicsLayer(layer)

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

    graphicsContext.withGraphicsLayer { layer ->
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
    val output = rs.outputBitmap

    contentLayer.record(
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
    graphicsContext.releaseGraphicsLayer(contentLayer)
    renderScriptContext?.release()
  }

  companion object {
    const val TAG = "RenderScriptBlurEffect"
    private const val MAX_BLUR_RADIUS = 25f
  }
}

private class RenderScriptContext(val context: Context, val size: IntSize) {
  private val rs = RenderScript.create(context)
  private val blurScript: ScriptIntrinsicBlur

  private val inputAlloc: Allocation
  val inputSurface: Surface
    get() = inputAlloc.surface

  private var outputAlloc: Allocation
  var outputBitmap: Bitmap
    private set

  private val channel = Channel<Unit>(Channel.CONFLATED)

  private var isDestroyed = false

  init {
    val width = size.width.increaseToDivisor(4)
    val height = size.height.increaseToDivisor(4)

    val type = Type.Builder(rs, Element.U8_4(rs)).setX(width).setY(height).create()

    val flags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_INPUT

    inputAlloc = Allocation.createTyped(rs, type, flags)
    inputAlloc.setOnBufferAvailableListener { allocation ->
      if (!isDestroyed) {
        allocation.ioReceive()
        channel.trySendBlocking(Unit)
      }
    }

    outputBitmap = createBitmap(width, height)
    outputAlloc = Allocation.createFromBitmap(rs, outputBitmap)

    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    blurScript.setInput(inputAlloc)
  }

  fun applyBlur(blurRadius: Float) {
    if (isDestroyed) return

    blurScript.setRadius(blurRadius.coerceAtMost(25f))
    blurScript.forEach(outputAlloc)

    if (!isDestroyed) {
      outputAlloc.copyTo(outputBitmap)
    }
  }

  suspend fun awaitSurfaceWritten() = channel.receive()

  fun release() {
    HazeLogger.d(TAG) { "Release resources" }
    isDestroyed = true

    blurScript.destroy()
    inputAlloc.destroy()
    outputAlloc.destroy()
    rs.destroy()
  }

  private companion object {
    const val TAG = "RenderScriptContext"
  }
}

private fun Int.increaseToDivisor(divisor: Int): Int {
  return this + (this % divisor)
}

internal fun Surface.drawGraphicsLayer(
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

internal inline fun Surface.withSurfaceCanvas(block: android.graphics.Canvas.() -> Unit) {
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
