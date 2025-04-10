package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Looper
import android.view.Surface
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.unit.LayoutDirection

// Consider using a regular class instead of an object,
// as the RenderScript output has one instance per effect,
// and the output bitmap is overwritten with each effect node using this RenderScriptBlurEffect.
object RenderScriptBlurEffect : BlurEffect {
  private var bitmap: ImageBitmap? = null
  private var renderScriptContext: RenderScriptContext? = null
  private val drawingScope: CanvasDrawScope = CanvasDrawScope()
  private val mainHandler = android.os.Handler(Looper.getMainLooper())

  override fun DrawScope.drawEffect(node: HazeEffectNode) {
    val blurRadiusPx = with(node.currentValueOf(LocalDensity)) { node.resolveBlurRadius().toPx() }
    val scaleFactor = node.calculateInputScaleFactor()

    drawScaledContentLayer(node = node, scaleFactor = scaleFactor, releaseLayerOnExit = false) { layer ->
      updateBitmap(
        node = node,
        blurRadius = blurRadiusPx * scaleFactor,
        layer = layer,
      )

      bitmap?.let { b ->
        drawImage(b)
      }
    }
  }

  private fun updateBitmap(
    node: HazeEffectNode,
    blurRadius: Float,
    layer: GraphicsLayer,
  ) {
    if (renderScriptContext == null) {
      renderScriptContext = RenderScriptContext(
        context = node.currentValueOf(LocalContext),
        size = layer.size,
        onDataReceived = {
          applyBlur(blurRadius)
          notifyBitmapUpdated(outputBitmap, node)
        }
      )
    }
    renderScriptContext?.let { rsc ->
      // This starts recording data asynchronously into the input Allocation.
      // Once the drawing process is complete and the surface canvas is unlocked,
      // the `setOnBufferAvailableListener` is triggered,
      // and we need to complete the data transfer by calling `ioReceive()` on the Allocation object.
      layer.renderToSurface(
        surface = rsc.inputSurface,
        density = node.currentValueOf(LocalDensity),
        drawingScope = drawingScope
      )
    }
  }

  private fun notifyBitmapUpdated(outputBitmap: Bitmap, node: HazeEffectNode) {
    mainHandler.post {
      // info: We’re asynchronously processing a bitmap and writing the result into the same instance.
      //       Meanwhile, the UI reads from it and may trigger another processing pass, causing visible tearing.
      // todo: implement simple double buffering or bitmap pooling
      bitmap = outputBitmap.asImageBitmap()
      node.invalidateDraw()
    }
  }

//  override fun onDetachEffect() {
//    // todo: need some sort of detach mechanism to clean up resources
//    renderScriptContext?.release()
//  }
}

private fun GraphicsLayer.renderToSurface(
  surface: Surface,
  density: Density,
  drawingScope: CanvasDrawScope,
  layoutDirection: LayoutDirection = LayoutDirection.Ltr
) {
  surface.withSurfaceCanvas { canvas ->
    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    drawingScope.draw(
      density = density,
      layoutDirection = layoutDirection,
      canvas = Canvas(canvas),
      size = Size(canvas.width.toFloat(), canvas.height.toFloat())
    ) {
      drawLayer(this@renderToSurface)
    }
  }
}

private inline fun Surface.withSurfaceCanvas(block: (android.graphics.Canvas) -> Unit) {
  val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    lockHardwareCanvas()
  } else {
    lockCanvas(null)
  }
  block(canvas)
  unlockCanvasAndPost(canvas)
}
