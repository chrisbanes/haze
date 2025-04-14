// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking

internal class RenderScriptContext(
  val context: Context,
  val size: IntSize,
) {
  private val rs = RenderScript.create(context)
  private val inputAlloc: Allocation
  private val outputAlloc: Allocation
  private val blurScript: ScriptIntrinsicBlur

  private val channel = Channel<Unit>(Channel.CONFLATED)

  val outputBitmap: Bitmap

  val inputSurface: Surface
    get() = inputAlloc.surface

  init {
    val type = Type.Builder(rs, Element.U8_4(rs))
      .setX(size.width)
      .setY(size.height)
      .create()

    val flags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_INPUT

    inputAlloc = Allocation.createTyped(rs, type, flags)
    inputAlloc.setOnBufferAvailableListener { allocation ->
      allocation.ioReceive()
      channel.trySendBlocking(Unit)
    }

    outputBitmap = createBitmap(size.width, size.height)
    outputAlloc = Allocation.createFromBitmap(rs, outputBitmap)

    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    blurScript.setInput(inputAlloc)
  }

  fun applyBlur(blurRadius: Float) {
    blurScript.setRadius(blurRadius.coerceAtMost(25f))
    blurScript.forEach(outputAlloc)
    outputAlloc.copyTo(outputBitmap)
  }

  suspend fun awaitSurfaceWritten() = channel.receive()

  fun release() {
    HazeLogger.d(TAG) { "Release resources" }
    inputAlloc.destroy()
    outputAlloc.destroy()
    outputBitmap.recycle()
    blurScript.destroy()
    rs.destroy()
  }

  private companion object {
    const val TAG = "RenderScriptContext"
  }
}
