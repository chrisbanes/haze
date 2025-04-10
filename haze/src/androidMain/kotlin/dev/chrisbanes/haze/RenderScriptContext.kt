package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
import android.util.Log
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap

@Suppress("DEPRECATION")
class RenderScriptContext(
    context: Context,
    val size: IntSize,
    private val onDataReceived: RenderScriptContext.() -> Unit,
) {

  val inputSurface: Surface
    get() = inputAlloc.surface

  private val rs: RenderScript = RenderScript.create(context)
  private var inputAlloc: Allocation
  private var outputAlloc: Allocation
  var outputBitmap: Bitmap
    private set
  private var blurScript: ScriptIntrinsicBlur

  init {
    val type = Type.Builder(rs, Element.U8_4(rs))
      .setX(size.width)
      .setY(size.height)
      .create()
    val flags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_INPUT

    inputAlloc = Allocation.createTyped(rs, type, flags).apply {
      setOnBufferAvailableListener { allocation ->
        allocation.ioReceive()
        onDataReceived()
      }
    }

    outputBitmap = createBitmap(size.width, size.height)
    outputAlloc = Allocation.createFromBitmap(rs, outputBitmap)

    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs)).apply {
      setInput(inputAlloc)
    }
  }

  fun applyBlur(blurRadius: Float) {
    blurScript.let { bs ->
      bs.setRadius(blurRadius.coerceAtMost(25f))
      bs.forEach(outputAlloc)
    }
    outputAlloc.copyTo(outputBitmap)
  }

  fun release() {
    Log.d("RenderScriptContext", "Releasing resources")
    inputAlloc.destroy()
    outputAlloc.destroy()
    outputBitmap.recycle()
    blurScript.destroy()
    rs.destroy()
  }
}
