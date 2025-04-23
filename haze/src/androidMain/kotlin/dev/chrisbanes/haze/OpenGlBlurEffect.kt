package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.currentValueOf
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

internal class OpenGlBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private val drawScope = CanvasDrawScope()

  private var currentJob: Job? = null

  private val graphicsContext: GraphicsContext
    get() = node.currentValueOf(LocalGraphicsContext)

  private val contentLayer: GraphicsLayer = graphicsContext.createGraphicsLayer()

  private val density: Density
    get() = node.requireDensity()

  override fun DrawScope.drawEffect() {
    val context = node.currentValueOf(LocalContext)
    val offset = node.layerOffset
    val scaleFactor = node.calculateInputScaleFactor()
    val blurRadiusPx = scaleFactor * with(density) { node.resolveBlurRadius().toPx() }

    HazeLogger.d(TAG) { "drawEffect. blurRadius=${blurRadiusPx}px. scaleFactor=$scaleFactor" }

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
        }
      }
    }

    graphicsContext.withGraphicsLayer { layer ->
      layer.alpha = node.alpha

      val mask = node.mask ?: node.progressive?.asBrush()
      if (mask != null) {
        // If we have a mask, this needs to be drawn offscreen
        layer.compositingStrategy = CompositingStrategy.Companion.Offscreen
      }

      layer.record(size = contentLayer.size) {
        drawLayer(contentLayer)

        val contentSize = floor(node.size * scaleFactor)
        val contentOffset = offset * scaleFactor

        translate(contentOffset) {
          // Draw the noise on top...
          val noiseFactor = node.resolveNoiseFactor()
          if (noiseFactor > 0f) {
            PaintPool.usePaint { paint ->
              val texture = context.getNoiseTexture(noiseFactor)
              paint.shader = BitmapShader(texture, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
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
            drawRect(brush = mask, size = contentSize, blendMode = BlendMode.Companion.DstIn)
          }
        }
      }

      drawScaledContent(offset = -offset, scaleFactor = scaleFactor) {
        drawLayer(layer)
      }
    }
  }

  private suspend fun updateSurface(content: GraphicsLayer, blurRadius: Float) {
    val output: Bitmap? = null // TODO

    if (output != null) {
      contentLayer.record(
        density = density,
        layoutDirection = node.currentValueOf(LocalLayoutDirection),
        size = IntSize(output.width, output.height),
      ) {
        drawImage(output.asImageBitmap())
      }
    }

    HazeLogger.d(TAG) { "Output updated in layer" }
  }

  override fun cleanup() {
    currentJob?.cancel()
    graphicsContext.releaseGraphicsLayer(contentLayer)
  }

  companion object {
    const val TAG = "OpenGlBlurEffect"
  }
}
