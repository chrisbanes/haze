// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import dev.chrisbanes.haze.HazeLogger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking

internal class RenderScriptContext(
  val rs: RenderScript,
  val size: IntSize,
) {
  private val blurScript: ScriptIntrinsicBlur

  private val inputAlloc: Allocation
  val inputSurface: Surface
    get() = inputAlloc.surface

  private var outputAlloc: Allocation
  var outputBitmap: Bitmap
    private set

  private val channel = Channel<Unit>(Channel.CONFLATED)

  private var isDestroyed = false

  init {
    val width = size.width.increaseToDivisor(4)
    val height = size.height.increaseToDivisor(4)

    val type = Type.Builder(rs, Element.U8_4(rs)).setX(width).setY(height).create()

    val flags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_INPUT

    inputAlloc = Allocation.createTyped(rs, type, flags)
    inputAlloc.setOnBufferAvailableListener { allocation ->
      if (!isDestroyed) {
        allocation.ioReceive()
        channel.trySendBlocking(Unit)
      }
    }

    outputBitmap = createBitmap(width, height)
    outputAlloc = Allocation.createFromBitmap(rs, outputBitmap)

    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
    blurScript.setInput(inputAlloc)
  }

  fun applyBlur(blurRadius: Float) {
    require(blurRadius in 1f..25f) {
      "blurRadius needs to be >= 1 and <= 25"
    }
    if (isDestroyed) return

    blurScript.setRadius(blurRadius.coerceAtMost(25f))
    blurScript.forEach(outputAlloc)

    if (!isDestroyed) {
      outputAlloc.copyTo(outputBitmap)
    }
  }

  suspend fun awaitSurfaceWritten() = channel.receive()

  fun release() {
    HazeLogger.d(TAG) { "Release resources" }
    isDestroyed = true

    blurScript.destroy()
    inputAlloc.destroy()
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
