// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.hardware.SyncFence
import android.os.Trace
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.time.Duration
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Owns the fence delivered by a single native render request. */
@RequiresApi(34)
internal class GlassHardwareBufferRenderResult(val status: Int, val fence: SyncFence) : AutoCloseable {
  override fun close() = fence.close()
}

private val captureTraceIds = AtomicInteger()
private val directCaptureExecutor = Executor { it.run() }

/** The caller keeps the recorded layer and its source leases alive until this operation returns. */
@RequiresApi(34)
@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun GraphicsLayer.captureHardwareBufferSnapshot(
  allocateBuffer: suspend (Int, Int) -> HardwareBuffer = ::allocateHardwareBufferCapture,
  awaitFence: suspend (SyncFence) -> Boolean = ::awaitHardwareBufferCaptureFence,
  wrapBuffer: (HardwareBuffer) -> ImageBitmap? = { buffer ->
    Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))?.asImageBitmap()
  },
  submit: (HardwareBufferRenderer.RenderRequest, (GlassHardwareBufferRenderResult) -> Unit) -> Unit = { request, ready ->
    request.draw(directCaptureExecutor) { result ->
      ready(GlassHardwareBufferRenderResult(result.status, result.fence))
    }
  },
  callbackTimeoutMillis: Long = 1_000,
): ImageBitmap {
  val caller = currentCoroutineContext()
  caller.ensureActive()
  val layer = this
  val width = size.width
  val height = size.height
  require(width > 0 && height > 0)
  val image = withContext(NonCancellable) {
    val completion = CompletableDeferred<GlassHardwareBufferRenderResult>()
    val cookie = captureTraceIds.incrementAndGet()
    val root = RenderNode("HazeGlass.rawSnapshot")
    var buffer: HardwareBuffer? = null
    var renderer: HardwareBufferRenderer? = null
    Trace.beginAsyncSection("HazeGlass.inputCapture.hardware", cookie)
    try {
      root.setPosition(0, 0, width, height)
      val recording = root.beginRecording(width, height)
      try {
        // HardwareBufferRenderer preserves prior contents; define the transparent area explicitly.
        recording.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        CanvasDrawScope().draw(
          density = Density(1f),
          layoutDirection = LayoutDirection.Ltr,
          canvas = Canvas(recording),
          size = Size(width.toFloat(), height.toFloat()),
        ) {
          drawLayer(layer)
        }
      } finally {
        root.endRecording()
      }
      // Independent allocation may suspend; retain input ownership until its result is closed.
      val output = allocateBuffer(width, height).also { buffer = it }
      caller.ensureActive()
      Trace.beginSection("HazeGlass.inputCapture.rendererCreate")
      val captureRenderer = try {
        HardwareBufferRenderer(output).also { renderer = it }
      } finally {
        Trace.endSection()
      }
      captureRenderer.setContentRoot(root)
      Trace.beginSection("HazeGlass.inputCapture.submit")
      try {
        submit(captureRenderer.obtainRenderRequest().setColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))) { result ->
          // A timeout cancels only this promise. A late result still owns a fence that must close.
          if (!completion.complete(result)) result.close()
        }
      } finally {
        Trace.endSection()
      }
      val result = try {
        withTimeout(callbackTimeoutMillis) { completion.await() }
      } catch (timeout: TimeoutCancellationException) {
        // This timeout belongs to the backend, not the original caller's cancellation.
        throw IllegalStateException("Glass input capture callback timed out", timeout)
      }
      val signalled = result.fence.isValid && awaitFence(result.fence)
      check(result.status == HardwareBufferRenderer.RenderResult.SUCCESS && signalled) {
        "Glass input capture did not supply a successful completed fence"
      }
      caller.ensureActive()
      // The bitmap owns a buffer reference. This buffer is never submitted for rendering again.
      checkNotNull(wrapBuffer(output)) { "Unable to wrap Glass input capture" }
    } finally {
      completion.cancel()
      Trace.beginSection("HazeGlass.inputCapture.cleanup")
      try {
        try {
          // Retires RenderThread traversal on failure too; this alone is not GPU completion.
          renderer?.close()
        } finally {
          root.discardDisplayList()
          try {
            // Cancellation cannot change an already-completed promise into an unowned result.
            if (completion.isCompleted && !completion.isCancelled) completion.getCompleted().close()
          } finally {
            buffer?.close()
          }
        }
      } finally {
        Trace.endSection()
        Trace.endAsyncSection("HazeGlass.inputCapture.hardware", cookie)
      }
    }
  }
  caller.ensureActive()
  return image
}

@RequiresApi(34)
internal suspend fun awaitHardwareBufferCaptureFence(fence: SyncFence): Boolean = withContext(Dispatchers.IO) {
  Trace.beginSection("HazeGlass.inputCapture.fenceWait")
  try {
    fence.await(Duration.ofSeconds(1))
  } finally {
    Trace.endSection()
  }
}

@RequiresApi(34)
internal suspend fun allocateHardwareBufferCapture(width: Int, height: Int): HardwareBuffer = withContext(Dispatchers.IO) {
  val usage = HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
  Trace.beginSection("HazeGlass.inputCapture.isSupported")
  try {
    check(HardwareBuffer.isSupported(width, height, HardwareBuffer.RGBA_8888, 1, usage))
  } finally {
    Trace.endSection()
  }
  Trace.beginSection("HazeGlass.inputCapture.allocate")
  try {
    HardwareBuffer.create(width, height, HardwareBuffer.RGBA_8888, 1, usage)
  } finally {
    Trace.endSection()
  }
}
