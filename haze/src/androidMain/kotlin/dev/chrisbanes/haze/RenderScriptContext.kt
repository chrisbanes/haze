// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

@RequiresApi(Build.VERSION_CODES.Q)
internal class RenderScriptContext(
  val context: Context,
  val size: IntSize,
) {
  private val rs = RenderScript.create(context)
  private val blurScript: ScriptIntrinsicBlur

  private val inputAlloc: Allocation
  val inputSurface: Surface
    get() = inputAlloc.surface

  private var outputAlloc: Allocation
  private var outputImage: Image? = null

  private val imageReader: ImageReader

  private val inputContentDrawnChannel = Channel<Unit>(Channel.CONFLATED)

  private var isDestroyed = false

  init {
    val width = size.width.increaseToDivisor(4)
    val height = size.height.increaseToDivisor(4)

    val type = Type.Builder(rs, Element.U8_4(rs))
      .setX(width)
      .setY(height)
      .create()

    val flags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_INPUT
    inputAlloc = Allocation.createTyped(rs, type, flags)
    inputAlloc.setOnBufferAvailableListener { inputContentDrawnChannel.trySend(Unit) }

    @SuppressLint("WrongConstant")
    imageReader = ImageReader.newInstance(
      width,
      height,
      PixelFormat.RGBA_8888,
      1,
      HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
        HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
        HardwareBuffer.USAGE_CPU_READ_OFTEN or
        USAGE_RENDERSCRIPT,
    )
    imageReader.setOnImageAvailableListener(
      { imageReader ->
        outputImage?.close()
        outputImage = imageReader.acquireNextImage()
      },
      null
    )

    val outputFlags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_OUTPUT
    outputAlloc = Allocation.createTyped(rs, type, outputFlags)
    outputAlloc.surface = imageReader.surface

    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    blurScript.setInput(inputAlloc)
  }

  suspend fun process(blurRadius: Float): Bitmap? {
    if (isDestroyed) return null

    blurScript.setRadius(blurRadius.coerceAtMost(25f))

    // Wait for the content to be written to the Surface
    inputContentDrawnChannel.receive()

    withContext(Dispatchers.Default) {
      inputAlloc.ioReceive()
      blurScript.forEach(outputAlloc)
      outputAlloc.ioSend()
    }

    if (isDestroyed) return null

    return outputImage?.hardwareBuffer?.let { buffer ->
      HazeLogger.d(TAG) { "Hardware bitmap created" }
      Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
    }
  }

  fun release() {
    HazeLogger.d(TAG) { "Release resources" }
    isDestroyed = true

    blurScript.destroy()
    inputAlloc.destroy()

    imageReader.close()
    outputImage?.close()
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

private const val USAGE_RENDERSCRIPT: Long = 0x00100000
