package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch


@Suppress("DEPRECATION")
object RenderScriptBlurEffect: BlurEffect {
  private var bitmap: ImageBitmap? = null

  private var currentJob: Job? = null

  override fun DrawScope.drawEffect(node: HazeEffectNode) {
    val blurRadiusPx = with(node.currentValueOf(LocalDensity)) { node.resolveBlurRadius().toPx() }
    val scaleFactor = node.calculateInputScaleFactor()

    drawScaledContentLayer(node = node, scaleFactor = scaleFactor, releaseLayerOnExit = false) { layer ->
      if (currentJob == null || currentJob?.isActive == false) {
        currentJob = node.coroutineScope.launch(Dispatchers.Default) {
          updateBitmap(
            node = node,
            blurRadius = blurRadiusPx * scaleFactor,
            layer = layer,
          )
        }
      }

      bitmap?.let { b ->
        drawImage(b)
      }
    }
  }

  private suspend fun updateBitmap(
    node: HazeEffectNode,
    blurRadius: Float,
    layer: GraphicsLayer,
  ) {
    val rs = RenderScript.create(node.currentValueOf(LocalContext))

    val src = layer
      .toImageBitmap()
      .asAndroidBitmap()
      .copy(Bitmap.Config.ARGB_8888, false)

    val inputAlloc = Allocation.createFromBitmap(rs, src)

    val outputBitmap= createBitmap(src.width, src.height)
    val outputAlloc = Allocation.createFromBitmap(rs, outputBitmap)

    ScriptIntrinsicBlur.create(rs, Element.U8_4(rs)).apply {
      setInput(inputAlloc)
      setRadius(blurRadius.coerceAtMost(25f))
      forEach(outputAlloc)
    }
    outputAlloc.copyTo(outputBitmap)

    bitmap = outputBitmap.asImageBitmap()
    node.invalidateDraw()

    rs.finish()
  }
}
