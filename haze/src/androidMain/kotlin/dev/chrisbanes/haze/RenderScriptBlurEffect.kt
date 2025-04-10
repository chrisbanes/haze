// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

internal class RenderScriptBlurEffect(
  private val node: HazeEffectNode,
) : BlurEffect {
  private var bitmap: ImageBitmap? = null
  private var renderScriptContext: RenderScriptContext? = null
  private val drawingScope = CanvasDrawScope()
  private val mainHandler = Handler(Looper.getMainLooper())

  override fun DrawScope.drawEffect() {
    val blurRadiusPx = with(node.currentValueOf(LocalDensity)) { node.resolveBlurRadius().toPx() }
    val scaleFactor = node.calculateInputScaleFactor()

    drawScaledContentLayer(node = node, scaleFactor = scaleFactor) { layer ->
      updateBitmap(layer = layer, blurRadius = blurRadiusPx * scaleFactor)

      bitmap?.let { b ->
        drawImage(b)
      }
    }
  }

  private fun updateBitmap(layer: GraphicsLayer, blurRadius: Float) {
    getRenderScriptContext(node.currentValueOf(LocalContext), layer.size, blurRadius)
      .inputSurface
      .drawGraphicsLayer(
        layer = layer,
        density = node.currentValueOf(LocalDensity),
        drawingScope = drawingScope,
      )
  }

  private fun onBitmapUpdated(output: Bitmap) {
    mainHandler.post {
      // info: We’re asynchronously processing a bitmap and writing the result into the same instance.
      //       Meanwhile, the UI reads from it and may trigger another processing pass, causing visible tearing.
      // todo: implement simple double buffering or bitmap pooling
      bitmap = output.asImageBitmap()
      node.invalidateDraw()
    }
  }

  private fun getRenderScriptContext(
    context: Context,
    size: IntSize,
    blurRadius: Float,
  ): RenderScriptContext {
    val rs = renderScriptContext
    if (rs != null && rs.size == size && rs.context == context) return rs

    // Release any existing context
    rs?.release()
    // Return a new context and store it
    return RenderScriptContext(
      context = context,
      size = size,
      onDataReceived = {
        applyBlur(blurRadius)
        onBitmapUpdated(outputBitmap)
      },
    ).also { renderScriptContext = it }
  }

  override fun cleanup() {
    renderScriptContext?.release()
    bitmap?.asAndroidBitmap()?.recycle()
  }
}

private fun Surface.drawGraphicsLayer(
  layer: GraphicsLayer,
  density: Density,
  drawingScope: CanvasDrawScope,
  layoutDirection: LayoutDirection = LayoutDirection.Ltr,
) {
  withSurfaceCanvas { surfaceCanvas ->
    surfaceCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    with(drawingScope) {
      draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = Canvas(surfaceCanvas),
        size = Size(surfaceCanvas.width.toFloat(), surfaceCanvas.height.toFloat()),
      ) {
        drawLayer(layer)
      }
    }
  }
}

private inline fun Surface.withSurfaceCanvas(block: (android.graphics.Canvas) -> Unit) {
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
