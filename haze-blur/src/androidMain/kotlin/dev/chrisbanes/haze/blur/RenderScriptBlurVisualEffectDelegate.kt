// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")
@file:OptIn(InternalHazeApi::class)

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
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
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@OptIn(ExperimentalHazeApi::class)
internal class RenderScriptBlurVisualEffectDelegate(
  private val visualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {

  private var renderScript: RenderScript? = null
  private var renderScriptContext: RenderScriptContext? = null
  private var contentLayer: GraphicsLayer? = null

  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null
  private var drawSkipped: Boolean = false

  override fun attach(context: VisualEffectContext) {
    renderScript = RenderScript.create(context.currentValueOf(LocalContext))
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    val androidContext = context.currentValueOf(LocalContext)
    var scaleFactor = visualEffect.calculateInputScaleFactor(context)
    val layerOffset = context.layerOffset

    var blurRadiusPx = scaleFactor * with(context.density) {
      visualEffect.blurRadius.takeOrElse { 0.dp }.toPx()
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

      val output = contentLayer ?: context.currentValueOf(LocalGraphicsContext).createGraphicsLayer()
        .also { contentLayer = it }

      createScaledContentLayer(
        context = context,
        scaleFactor = scaleFactor,
        layerSize = context.layerSize,
        layerOffset = layerOffset,
        backgroundColor = visualEffect.backgroundColor,
      )?.let { scaledLayer ->
        scaledLayer.clip = visualEffect.shouldClip()

        if (output.size == IntSize.Zero) {
          // If the layer is released, or doesn't have a size yet, we'll generate
          // this blocking, so that the user doesn't see an un-blurred first frame
          runBlocking {
            updateSurface(
              context = context,
              content = scaledLayer,
              outputLayer = output,
              blurRadius = blurRadiusPx,
            )
            // Release the graphics layer
            context.currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(scaledLayer)
          }
        } else {
          currentJob = context.node.coroutineScope.launch(Dispatchers.Main.immediate) {
            updateSurface(
              context = context,
              content = scaledLayer,
              outputLayer = output,
              blurRadius = blurRadiusPx,
            )
            // Release the graphics layer
            context.currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(scaledLayer)

            if (drawSkipped) {
              // If any draws were skipped, let's trigger a draw invalidation
              context.node.invalidateDraw()
            }
          }
        }
      }
    } else {
      // Mark this draw as skipped
      drawSkipped = true
    }

    context.withGraphicsLayer { layer ->
      layer.alpha = visualEffect.alpha
      layer.clip = visualEffect.shouldClip()

      val mask = visualEffect.progressive?.asBrush() ?: visualEffect.mask
      if (mask != null) {
        // If we have a mask, this needs to be drawn offscreen
        layer.compositingStrategy = CompositingStrategy.Offscreen
      }

      layer.record(size = size.toIntSize()) {
        drawScaledContent(
          offset = -layerOffset,
          scaledSize = size * scaleFactor,
          clip = visualEffect.shouldClip(),
        ) {
          contentLayer?.let { drawLayer(it) }
        }

        val expandedSize = size.expand(
          expansionWidth = max(layerOffset.x, 0f) * 2,
          expansionHeight = max(layerOffset.y, 0f) * 2,
        )

        // Draw the noise on top...
        val noiseFactor = visualEffect.noiseFactor
        if (noiseFactor > 0f) {
          translate(offset = -layerOffset) {
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
        translate(offset = -layerOffset) {
          for (tint in visualEffect.tints) {
            drawScrim(
              context = context,
              tint = tint,
              offset = layerOffset,
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
    contentLayer == null -> true
    contentLayer?.size == IntSize.Zero -> true
    // No ongoing update, so start an update...
    currentJob?.isActive != true -> true
    // Otherwise, there must be a job ongoing, skip this update
    else -> false
  }

  private suspend fun updateSurface(
    context: VisualEffectContext,
    content: GraphicsLayer,
    outputLayer: GraphicsLayer,
    blurRadius: Float,
  ) {
    traceAsync("Haze-RenderScriptBlurEffect-updateSurface", 0) {
      val rs = getRenderScriptContext(content.size)
      traceAsync("Haze-RenderScriptBlurEffect-updateSurface-drawLayerToSurface", 0) {
        // Draw the layer (this is async)
        rs.inputSurface.drawGraphicsLayer(
          layer = content,
          density = context.density,
          drawScope = drawScope,
        )
        // Wait for the layer to be written to the Surface
        rs.awaitSurfaceWritten()
      }

      if (!context.isAttached) {
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

          outputLayer.record(
            density = context.density,
            layoutDirection = context.currentValueOf(LocalLayoutDirection),
            size = IntSize(output.width, output.height),
          ) {
            drawImage(output.asImageBitmap())
          }
        }
      } else {
        // If the blur radius is 0, we just copy the input content into our contentLayer
        outputLayer.record(
          density = context.density,
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
    val rsc = renderScriptContext
    if (rsc != null && rsc.size == size) return rsc

    // Release any existing context
    rsc?.release()
    // Return a new context and store it
    val rs = requireNotNull(renderScript) { "RenderScript is null. Is this attached?" }
    return RenderScriptContext(rs = rs, size = size).also { renderScriptContext = it }
  }

  override fun detach(context: VisualEffectContext) {
    currentJob?.cancel()
    contentLayer?.let { context.currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(it) }
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
