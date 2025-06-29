// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.blur

import android.graphics.BitmapShader
import android.graphics.Color
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
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.PaintPool
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.createScaledContentLayer
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.drawScrim
import dev.chrisbanes.haze.expand
import dev.chrisbanes.haze.trace
import dev.chrisbanes.haze.traceAsync
import dev.chrisbanes.haze.translate
import dev.chrisbanes.haze.usePaint
import dev.chrisbanes.haze.withGraphicsLayer
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

internal class RenderScriptBlurVisualEffectDelegate(
  private val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {

  private val renderScript = RenderScript.create(
    blurVisualEffect.requireNode().currentValueOf(LocalContext),
  )

  private var renderScriptContext: RenderScriptContext? = null
  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null
  private var drawSkipped: Boolean = false

  private val graphicsContext: GraphicsContext
    get() = blurVisualEffect.requireNode().currentValueOf(LocalGraphicsContext)

  private val contentLayer: GraphicsLayer = graphicsContext.createGraphicsLayer()

  private val density: Density
    get() = blurVisualEffect.requireNode().requireDensity()

  override fun DrawScope.draw() {
    val node = blurVisualEffect.requireNode()
    val context = node.currentValueOf(LocalContext)
    val offset = node.layerOffset
    var scaleFactor = blurVisualEffect.calculateInputScaleFactor(node.inputScale)

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
        node = node,
        scale = scaleFactor,
        layerSize = node.layerSize,
        layerOffset = offset,
        backgroundColor = blurVisualEffect.backgroundColor,
        areas = node.areas,
        positionOnScreen = node.positionOnScreen,
      )?.let { layer ->
        layer.clip = node.visualEffect.shouldClip()

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

    node.withGraphicsLayer { layer ->
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
          clip = node.visualEffect.shouldClip(),
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
              val texture = context.getNoiseTexture(noiseFactor)
              paint.shader = BitmapShader(texture, REPEAT, REPEAT)
              paint.blendMode = BlendMode.SrcAtop
              drawContext.canvas.drawRect(expandedSize.toRect(), paint)
            }
          }
        }

        // Then the tints...
        translate(offset = -offset) {
          for (tint in blurVisualEffect.tints) {
            drawScrim(tint = tint, node = node, offset = offset, expandedSize = expandedSize, mask = mask)
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

  private suspend fun updateSurface(content: GraphicsLayer, blurRadius: Float) {
    traceAsync("Haze-RenderScriptBlurEffect-updateSurface", 0) {
      val rs = getRenderScriptContext(content.size)
      traceAsync("Haze-RenderScriptBlurEffect-updateSurface-drawLayerToSurface", 0) {
        // Draw the layer (this is async)
        rs.inputSurface.drawGraphicsLayer(layer = content, density = density, drawScope = drawScope)
        // Wait for the layer to be written to the Surface
        rs.awaitSurfaceWritten()
      }

      val node = blurVisualEffect.attachedNode
      if (node?.isAttached != true) {
        return@traceAsync
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
            layoutDirection = node.currentValueOf(LocalLayoutDirection),
            size = IntSize(output.width, output.height),
          ) {
            drawImage(output.asImageBitmap())
          }
        }
      } else {
        // If the blur radius is 0, we just copy the input content into our contentLayer
        contentLayer.record(
          density = density,
          layoutDirection = node.currentValueOf(LocalLayoutDirection),
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

    fun createOrNull(effect: BlurVisualEffect): RenderScriptBlurVisualEffectDelegate? {
      if (isEnabled) {
        return runCatching { RenderScriptBlurVisualEffectDelegate(effect) }
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
