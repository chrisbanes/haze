// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

private val androidGlassInputCapture = AndroidGlassInputCapture()

internal actual suspend fun GraphicsLayer.captureGlassInputSnapshot(): ImageBitmap {
  // Posting guarantees the active window draw has returned. Resuming the continuation
  // preserves its owner dispatcher; cancellation prevents a queued capture from starting.
  suspendCancellableCoroutine<Unit> { continuation ->
    val handler = Handler(Looper.getMainLooper())
    val resumeOwner = Runnable {
      if (continuation.isActive) continuation.resume(Unit)
    }
    continuation.invokeOnCancellation { handler.removeCallbacks(resumeOwner) }
    check(handler.post(resumeOwner)) { "Unable to enqueue Glass input capture" }
  }
  currentCoroutineContext().ensureActive()
  return androidGlassInputCapture.capture(
    hardware = { captureHardwareBufferSnapshot() },
    platform = { toImageBitmap() },
  )
}

/** Process-local capability: a rejected backend falls back without retrying failed setup. */
internal class AndroidGlassInputCapture(
  supportsHardwareBuffer: Boolean = android.os.Build.VERSION.SDK_INT >= 34,
) {
  private var hardwareBufferAvailable = supportsHardwareBuffer

  suspend fun capture(
    hardware: suspend () -> ImageBitmap,
    platform: suspend () -> ImageBitmap,
  ): ImageBitmap {
    if (hardwareBufferAvailable) {
      try {
        return hardware()
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        // The hardware operation has retired its renderer before returning a failure.
        hardwareBufferAvailable = false
        currentCoroutineContext().ensureActive()
      }
    }
    android.os.Trace.beginSection("HazeGlass.inputCapture.platformFallback")
    try {
      return platform()
    } finally {
      android.os.Trace.endSection()
    }
  }
}
