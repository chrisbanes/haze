// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job

@OptIn(ExperimentalCoroutinesApi::class)
@RequiresApi(29)
internal class OpenGlBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private var currentJob: Job? = null

  private val graphicsContext: GraphicsContext
    get() = node.currentValueOf(LocalGraphicsContext)

  private val contentLayer: GraphicsLayer = graphicsContext.createGraphicsLayer()

  private val renderer = OpenGlRenderNodeBlurrer()

  private val mainHandler = Handler(Looper.getMainLooper())
  private val glThread = HandlerThread("OpenGlBlurEffect").apply { start() }
  private val glHandler = Handler(glThread.looper)

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
      updateSurface(
        content = layer,
        density = drawContext.density,
        layoutDirection = layoutDirection,
        blurRadius = blurRadiusPx,
      )
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

        val contentSize = ceil(node.size * scaleFactor)
        val contentOffset = offset * scaleFactor

        translate(contentOffset) {
          // Draw the noise on top...
          val noiseFactor = node.resolveNoiseFactor()
          if (noiseFactor > 0f) {
            PaintPool.usePaint { paint ->
              val texture = context.getNoiseTexture(noiseFactor, scaleFactor)
              paint.shader = BitmapShader(
                texture,
                Shader.TileMode.REPEAT,
                Shader.TileMode.REPEAT,
              )
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
            drawRect(
              brush = mask,
              size = contentSize,
              blendMode = BlendMode.Companion.DstIn,
            )
          }
        }
      }

      drawScaledContent(offset = -offset, scaleFactor = scaleFactor) {
        drawLayer(layer)
      }
    }
  }

  private fun updateSurface(
    content: GraphicsLayer,
    density: Density,
    layoutDirection: LayoutDirection,
    blurRadius: Float,
    onFinished: (() -> Unit)? = null,
  ) {
    val renderNode = content.asRenderNode(density, layoutDirection)

    glHandler.post {
      val start = System.currentTimeMillis()

      val output = renderer.blurRenderNode(
        renderNode = renderNode,
        radius = blurRadius.roundToInt(),
      )

      HazeLogger.d(TAG) { "Got bitmap from Renderer: $output" }

      if (output != null && node.isAttached) {
        updateContentLayer(output)
      }

      HazeLogger.d(TAG) { "blurRenderNode. Took ${System.currentTimeMillis() - start}ms" }

      mainHandler.post {
        onFinished?.invoke()
      }
    }
  }

  private fun updateContentLayer(bitmap: Bitmap) {
    contentLayer.record(
      density = node.requireDensity(),
      layoutDirection = node.requireLayoutDirection(),
      size = IntSize(bitmap.width, bitmap.height),
    ) {
      drawImage(bitmap.asImageBitmap())
    }
  }

  override fun cleanup() {
    glThread.quit()
    renderer.close()
    currentJob?.cancel()
    graphicsContext.releaseGraphicsLayer(contentLayer)
  }

  companion object {
    const val TAG = "OpenGlBlurEffect"
  }
}

@RequiresApi(29)
private fun GraphicsLayer.asRenderNode(
  density: Density,
  layoutDirection: LayoutDirection,
): RenderNode {
  val layerSize = size

  val renderNode = RenderNode("GraphicsLayer-RenderNode").apply {
    setPosition(0, 0, size.width, size.height)
  }

  val canvas = renderNode.beginRecording()
  val drawScope = CanvasDrawScope()
  try {
    with(drawScope) {
      draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = Canvas(canvas),
        size = layerSize.toSize(),
      ) {
        drawLayer(this@asRenderNode)
      }
    }
  } finally {
    renderNode.endRecording()
  }
  return renderNode
}
