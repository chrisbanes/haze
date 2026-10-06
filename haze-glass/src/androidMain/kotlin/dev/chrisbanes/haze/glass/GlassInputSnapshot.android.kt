// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import kotlin.coroutines.resume
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

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
  return toImageBitmap()
}
