// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.BaseObj
import android.renderscript.Element
import android.renderscript.RSRuntimeException
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.renderscript.Type
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import dev.chrisbanes.haze.HazeLogger
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking

internal class RenderScriptContext(
  val rs: RenderScript,
  val size: IntSize,
  // Test seam: called after each native object is created, so host tests can fail construction partway.
  afterNativeCreate: (BaseObj) -> Unit = {},
) {
  private val lock = ReentrantLock()
  private val type: Type
  private val blurScript: ScriptIntrinsicBlur

  private val inputAlloc: Allocation
  val inputSurface: Surface
    get() = inputAlloc.surface

  private val outputAlloc: Allocation

  /**
   * The published blur output. Only [publishOutput], on the main thread, writes its pixels.
   *
   * HWUI keeps a live reference to a recorded mutable bitmap's pixels and reads them while the UI
   * thread is blocked on the RenderThread: at each frame sync, or for the whole draw when pinning
   * fails ([SkiaDisplayList::prepareListAndChildren](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-11.0.0_r1/libs/hwui/pipeline/skia/SkiaDisplayList.cpp#L88-L98)).
   * A write from any other thread can therefore race a read of a previously recorded frame.
   */
  val outputBitmap: Bitmap

  // Written by applyBlur on a background thread, read by publishOutput on the main thread.
  private val staging: ByteArray

  private val channel = Channel<Unit>(Channel.CONFLATED)

  @Volatile
  private var isDestroyed = false

  init {
    val width = size.width
    val height = size.height
    val created = mutableListOf<BaseObj>()
    // Set once the input allocation has a buffer listener, which pins it in a static framework map.
    var listening: Allocation? = null
    fun <T : BaseObj> T.track(): T = also {
      created += it
      afterNativeCreate(it)
    }

    try {
      // Allocation.createTyped does not take ownership of the type, so it is destroyed here.
      type = Type.Builder(rs, Element.U8_4(rs)).setX(width).setY(height).create().track()

      val flags = Allocation.USAGE_SCRIPT or Allocation.USAGE_IO_INPUT

      inputAlloc = Allocation.createTyped(rs, type, flags).track()
      inputAlloc.setOnBufferAvailableListener { receiveInputBuffer() }
      listening = inputAlloc

      outputBitmap = createBitmap(width, height)
      // Not backed by outputBitmap, so the blur kernel never writes published pixels.
      outputAlloc = Allocation.createTyped(rs, type, Allocation.USAGE_SCRIPT).track()
      staging = ByteArray(width * height * 4)

      blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs)).track()
      blurScript.setInput(inputAlloc)
    } catch (e: Throwable) {
      // A partial construction is never released by its owner, so destroy what it created here.
      // Clearing a listener also adds the allocation to the static map, so clear only one that was set.
      runCatching { listening?.setOnBufferAvailableListener(null) }
        .onFailure { HazeLogger.d(TAG) { "Failed to clear the input buffer listener: $it" } }
      for (obj in created) {
        runCatching { obj.destroy() }
          .onFailure { HazeLogger.d(TAG) { "Failed to destroy $obj: $it" } }
      }
      throw e
    }
  }

  /**
   * Receives the input buffer the Surface write produced and signals [awaitSurfaceWritten]. A
   * native failure closes the channel with it, so the waiting update fails instead of hanging.
   */
  internal fun receiveInputBuffer(receive: () -> Unit = inputAlloc::ioReceive) {
    // Keep the liveness check and native receive atomic with allocation teardown.
    lock.withLock {
      if (isDestroyed) return
      try {
        receive()
      } catch (e: RSRuntimeException) {
        channel.close(e)
        return
      }
      channel.trySendBlocking(Unit)
    }
  }

  fun applyBlur(blurRadius: Float) {
    lock.withLock {
      require(blurRadius.isFinite() && blurRadius > 0f && blurRadius <= 25f) {
        "blurRadius needs to be finite, > 0, and <= 25"
      }
      if (isDestroyed) return@withLock

      blurScript.setRadius(blurRadius.coerceAtMost(25f))
      blurScript.forEach(outputAlloc)

      if (!isDestroyed) {
        outputAlloc.copyTo(staging)
      }
    }
  }

  /**
   * Copies the latest [applyBlur] result into [outputBitmap]. Call on the main thread, outside a
   * draw, so the copy cannot overlap a RenderThread read of the bitmap's pixels.
   */
  fun publishOutput() {
    lock.withLock {
      if (isDestroyed) return@withLock
      // A fresh wrap each call: copyPixelsFromBuffer advances the buffer's position.
      outputBitmap.copyPixelsFromBuffer(ByteBuffer.wrap(staging))
    }
  }

  suspend fun awaitSurfaceWritten() = channel.receive()

  fun release() {
    // The static allocation map pins the input allocation, so drop its listener's reference to this
    // context. Outside lock: the framework calls the listener while holding the map's monitor.
    runCatching { inputAlloc.setOnBufferAvailableListener(null) }
      .onFailure { HazeLogger.d(TAG) { "Failed to clear the input buffer listener: $it" } }

    lock.withLock {
      HazeLogger.d(TAG) { "Release resources" }
      isDestroyed = true

      // Before destroy(): getSurface() is cached, but its first call needs a live allocation.
      runCatching { inputAlloc.surface.release() }
        .onFailure { HazeLogger.d(TAG) { "Failed to release the input surface: $it" } }

      // Destroy each independently, so one failure does not leak the others.
      for (obj in listOf(blurScript, inputAlloc, outputAlloc, type)) {
        runCatching { obj.destroy() }
          .onFailure { HazeLogger.d(TAG) { "Failed to destroy $obj: $it" } }
      }
      // Note: rs (RenderScript) is NOT destroyed here — SharedRenderScript owns its lifecycle.
    }
  }

  private companion object {
    const val TAG = "RenderScriptContext"
  }
}
