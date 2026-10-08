// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import android.graphics.Bitmap
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
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
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Robolectric's Picture snapshot loses nested Glass material. Render the same recorded input
 * directly for host assertions; native tests separately cover the production capture backends.
 */
@RequiresApi(34)
internal suspend fun GraphicsLayer.captureHostGlassInputSnapshot(): ImageBitmap {
  // Match production's post-window-draw boundary and keep continuation on its owner dispatcher.
  suspendCancellableCoroutine<Unit> { continuation ->
    val handler = Handler(Looper.getMainLooper())
    val resumeOwner = Runnable { if (continuation.isActive) continuation.resume(Unit) }
    continuation.invokeOnCancellation { handler.removeCallbacks(resumeOwner) }
    check(handler.post(resumeOwner)) { "Unable to enqueue host Glass input capture" }
  }
  currentCoroutineContext().ensureActive()
  val image = captureHostGlassInputBitmap()
  try {
    currentCoroutineContext().ensureActive()
    return image.asImageBitmap()
  } catch (failure: Throwable) {
    // Ownership was never transferred to the capture state.
    image.recycle()
    throw failure
  }
}

@RequiresApi(34)
private fun GraphicsLayer.captureHostGlassInputBitmap(): Bitmap {
  val width = size.width
  val height = size.height
  require(width > 0 && height > 0)
  val root = RenderNode("HazeGlass.hostInput")
  var reader: ImageReader? = null
  var renderer: HardwareRenderer? = null
  try {
    root.setPosition(0, 0, width, height)
    val recording = root.beginRecording(width, height)
    try {
      recording.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(recording),
        size = Size(width.toFloat(), height.toFloat()),
      ) { drawLayer(this@captureHostGlassInputBitmap) }
    } finally {
      root.endRecording()
    }
    val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { reader = it }
    val captureRenderer = HardwareRenderer().also { renderer = it }
    captureRenderer.setOpaque(false)
    captureRenderer.setSurface(imageReader.surface)
    captureRenderer.setContentRoot(root)
    val status = captureRenderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
    check(status == HardwareRenderer.SYNC_OK) { "Host Glass input render failed: $status" }
    val image = checkNotNull(imageReader.acquireNextImage()) { "Host Glass input render supplied no image" }
    try {
      val plane = image.planes.single()
      check(plane.pixelStride == 4) { "Unexpected host Glass input pixel stride" }
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      try {
        val packed = ByteBuffer.allocate(bitmap.rowBytes * height)
        repeat(height) { y ->
          val row = plane.buffer.duplicate()
          row.position(y * plane.rowStride)
          row.limit(y * plane.rowStride + width * 4)
          packed.position(y * bitmap.rowBytes)
          packed.put(row)
        }
        packed.rewind()
        bitmap.copyPixelsFromBuffer(packed)
        return bitmap
      } catch (failure: Throwable) {
        bitmap.recycle()
        throw failure
      }
    } finally {
      image.close()
    }
  } finally {
    try {
      renderer?.destroy()
    } finally {
      try {
        root.discardDisplayList()
      } finally {
        reader?.close()
      }
    }
  }
}
