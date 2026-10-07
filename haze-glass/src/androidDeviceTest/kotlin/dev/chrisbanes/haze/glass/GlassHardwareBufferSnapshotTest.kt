// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.HardwareBufferRenderer
import android.hardware.HardwareBuffer
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Hardware pixel proof and fault injection at the native completion boundary, not benchmarks. */
@SdkSuppress(minSdkVersion = 34)
class GlassHardwareBufferSnapshotTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var graphics: GraphicsContext
  private val layers = mutableListOf<GraphicsLayer>()
  private val direct = Executor { it.run() }

  @Before fun createGraphicsOwner() {
    compose.setContent {
      graphics = LocalGraphicsContext.current
      Box(Modifier.size(48.dp))
    }
    compose.waitForIdle()
  }

  @After fun releaseInputs() {
    compose.runOnIdle {
      layers.filterNot { it.isReleased }.forEach(graphics::releaseGraphicsLayer)
    }
  }

  @Test fun capture_patternAndTransparencyMatchPlatformImage() = runBlocking {
    val layer = createLayer()
    val (expected, actual) = withContext(Dispatchers.Main.immediate) {
      layer.toImageBitmap() to layer.captureHardwareBufferSnapshot()
    }
    assertPixelsMatch(expected, actual)
    val readable = readable(actual)
    assertThat(android.graphics.Color.alpha(readable.getPixel(0, 0))).isEqualTo(0)
    assertThat(readable.getPixel(6, 6)).isEqualTo(android.graphics.Color.RED)
  }

  @Test fun capture_firstImageStaysImmutableAfterRecapture() = runBlocking {
    val layer = createLayer()
    val first = withContext(Dispatchers.Main.immediate) { layer.captureHardwareBufferSnapshot() }
    withContext(Dispatchers.Main.immediate) { recordPattern(layer, Color.Green) }
    val second = withContext(Dispatchers.Main.immediate) { layer.captureHardwareBufferSnapshot() }
    assertThat(readable(first).getPixel(6, 6)).isEqualTo(android.graphics.Color.RED)
    assertThat(readable(second).getPixel(6, 6)).isEqualTo(android.graphics.Color.GREEN)
  }

  @Test fun capture_cancelBeforeSubmissionDoesNotSubmit() = runBlocking {
    val layer = createLayer()
    var submitted = 0
    val job = launch(Dispatchers.Main.immediate) {
      currentCoroutineContext().cancel()
      layer.captureHardwareBufferSnapshot(submit = { _, _ -> submitted++ })
    }
    job.join()
    assertThat(job.isCancelled).isTrue()
    assertThat(submitted).isEqualTo(0)
  }

  @Test fun capture_cancelDuringAllocationClosesBufferWithoutSubmission() = runBlocking {
    val layer = createLayer()
    val allocated = CompletableDeferred<HardwareBuffer>()
    val drainGate = CompletableDeferred<Unit>()
    var submitted = 0
    var wrapped = 0
    var finished = 0
    val job = launch(Dispatchers.Main.immediate) {
      try {
        layer.captureHardwareBufferSnapshot(
          allocateBuffer = { width, height ->
            withContext(Dispatchers.IO) {
              val buffer = allocateHardwareBufferCapture(width, height)
              allocated.complete(buffer)
              drainGate.await()
              buffer
            }
          },
          submit = { _, _ -> submitted++ },
          wrapBuffer = {
            wrapped++
            wrap(it)
          },
        )
      } finally {
        graphics.releaseGraphicsLayer(layer)
        finished++
      }
    }
    val buffer = allocated.await()
    try {
      job.cancel()
      withContext(Dispatchers.Main.immediate) {
        assertThat(buffer.isClosed).isFalse()
        assertThat(layer.isReleased).isFalse()
        assertThat(job.isCompleted).isFalse()
        assertThat(finished).isEqualTo(0)
      }
    } finally {
      drainGate.complete(Unit)
      job.join()
    }
    assertThat(buffer.isClosed).isTrue()
    assertThat(submitted).isEqualTo(0)
    assertThat(wrapped).isEqualTo(0)
    assertThat(finished).isEqualTo(1)
    assertThat(layer.isReleased).isTrue()
    assertThat(job.isCancelled).isTrue()
  }

  @Test fun capture_cancelAfterSubmissionRetainsInputUntilDrain() = runBlocking {
    val layer = createLayer()
    val reachedFence = CompletableDeferred<Unit>()
    val drainGate = CompletableDeferred<Unit>()
    var wrapped = 0
    var finished = 0
    var waited = 0
    val job = launch(Dispatchers.Main.immediate) {
      try {
        layer.captureHardwareBufferSnapshot(
          awaitFence = { fence ->
            reachedFence.complete(Unit)
            drainGate.await()
            waited++
            awaitHardwareBufferCaptureFence(fence)
          },
          wrapBuffer = { buffer ->
            wrapped++
            wrap(buffer)
          },
        )
      } finally {
        graphics.releaseGraphicsLayer(layer)
        finished++
      }
    }
    try {
      reachedFence.await()
      job.cancel()
      withContext(Dispatchers.Main.immediate) {
        assertThat(layer.isReleased).isFalse()
        assertThat(finished).isEqualTo(0)
        assertThat(wrapped).isEqualTo(0)
        assertThat(job.isCompleted).isFalse()
      }
    } finally {
      drainGate.complete(Unit)
      job.join()
    }
    assertThat(waited).isEqualTo(1)
    assertThat(wrapped).isEqualTo(0)
    assertThat(finished).isEqualTo(1)
    assertThat(layer.isReleased).isTrue()
    assertThat(job.isCancelled).isTrue()
  }

  @Test fun capture_invalidFenceIsRejectedAndClosed() = runBlocking {
    val layer = createLayer()
    var wrapped = 0
    val error = failure {
      withContext(Dispatchers.Main.immediate) {
        layer.captureHardwareBufferSnapshot(
          submit = { request, ready ->
            request.draw(direct) { result ->
              // Deliberately remove completion evidence; these pixels cannot qualify for publication.
              result.fence.close()
              ready(GlassHardwareBufferRenderResult(result.status, result.fence))
            }
          },
          wrapBuffer = {
            wrapped++
            wrap(it)
          },
        )
      }
    }
    assertThat(error).isInstanceOf<IllegalStateException>()
    assertThat(wrapped).isEqualTo(0)
  }

  @Test fun capture_failedFenceWaitDoesNotPublish() = runBlocking {
    val layer = createLayer()
    var wrapped = 0
    val error = failure {
      withContext(Dispatchers.Main.immediate) {
        layer.captureHardwareBufferSnapshot(
          awaitFence = { false },
          wrapBuffer = {
            wrapped++
            wrap(it)
          },
        )
      }
    }
    assertThat(error).isInstanceOf<IllegalStateException>()
    assertThat(wrapped).isEqualTo(0)
  }

  @Test fun capture_unsuccessfulResultDoesNotPublish() = runBlocking {
    val layer = createLayer()
    var wrapped = 0
    val error = failure {
      withContext(Dispatchers.Main.immediate) {
        layer.captureHardwareBufferSnapshot(
          submit = { request, ready ->
            request.draw(direct) { result ->
              ready(GlassHardwareBufferRenderResult(HardwareBufferRenderer.RenderResult.ERROR_UNKNOWN, result.fence))
            }
          },
          wrapBuffer = {
            wrapped++
            wrap(it)
          },
        )
      }
    }
    assertThat(error).isInstanceOf<IllegalStateException>()
    assertThat(wrapped).isEqualTo(0)
  }

  @Test fun capture_wrapFailureClosesFence() = runBlocking {
    val layer = createLayer()
    val received = CompletableDeferred<GlassHardwareBufferRenderResult>()
    val error = failure {
      withContext(Dispatchers.Main.immediate) {
        layer.captureHardwareBufferSnapshot(
          submit = { request, ready ->
            request.draw(direct) { result ->
              val owned = GlassHardwareBufferRenderResult(result.status, result.fence)
              received.complete(owned)
              ready(owned)
            }
          },
          wrapBuffer = { null },
        )
      }
    }
    assertThat(error).isInstanceOf<IllegalStateException>()
    assertThat(received.await().fence.isValid).isFalse()
  }

  @Test fun capture_submissionThrowClosesDeliveredOrLateFence() = runBlocking {
    val layer = createLayer()
    val received = CompletableDeferred<GlassHardwareBufferRenderResult>()
    val error = failure {
      withContext(Dispatchers.Main.immediate) {
        layer.captureHardwareBufferSnapshot(submit = { request, ready ->
          request.draw(direct) { result ->
            val owned = GlassHardwareBufferRenderResult(result.status, result.fence)
            received.complete(owned)
            ready(owned)
          }
          throw IllegalStateException("Injected exception after native submission")
        })
      }
    }
    assertThat(error).isInstanceOf<IllegalStateException>()
    assertThat(received.await().fence.isValid).isFalse()
  }

  @Test fun capture_callbackTimeoutDisposesLateResultWithoutPublication() = runBlocking {
    val layer = createLayer()
    val received = CompletableDeferred<GlassHardwareBufferRenderResult>()
    var late: ((GlassHardwareBufferRenderResult) -> Unit)? = null
    var wrapped = 0
    val error = failure {
      withContext(Dispatchers.Main.immediate) {
        layer.captureHardwareBufferSnapshot(
          submit = { request, ready ->
            late = ready
            request.draw(direct) { result ->
              received.complete(GlassHardwareBufferRenderResult(result.status, result.fence))
            }
          },
          wrapBuffer = {
            wrapped++
            wrap(it)
          },
          callbackTimeoutMillis = 20,
        )
      }
    }
    val result = received.await()
    assertThat(error).isInstanceOf<IllegalStateException>()
    assertThat(result.fence.isValid).isTrue()
    checkNotNull(late)(result)
    assertThat(result.fence.isValid).isFalse()
    assertThat(wrapped).isEqualTo(0)
  }

  private suspend fun createLayer(): GraphicsLayer = withContext(Dispatchers.Main.immediate) {
    graphics.createGraphicsLayer().also {
      layers += it
      recordPattern(it, Color.Red)
    }
  }

  private fun recordPattern(layer: GraphicsLayer, color: Color) {
    layer.record(Density(1f), LayoutDirection.Ltr, IntSize(24, 24)) {
      drawRect(color, Offset(4f, 4f), Size(8f, 8f))
      drawRect(Color.Blue.copy(alpha = .5f), Offset(12f, 12f), Size(8f, 8f))
    }
  }

  private fun wrap(buffer: HardwareBuffer): ImageBitmap? =
    Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))?.asImageBitmap()

  private fun readable(image: ImageBitmap): Bitmap = checkNotNull(image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false))

  private fun assertPixelsMatch(expected: ImageBitmap, actual: ImageBitmap) {
    assertThat(actual.width).isEqualTo(expected.width)
    assertThat(actual.height).isEqualTo(expected.height)
    val first = readable(expected)
    val second = readable(actual)
    repeat(first.height) { y ->
      repeat(first.width) { x ->
        val a = first.getPixel(x, y)
        val b = second.getPixel(x, y)
        listOf(0, 8, 16, 24).forEach { shift ->
          assertThat(abs(((a ushr shift) and 255) - ((b ushr shift) and 255)), "pixel $x,$y channel $shift").isLessThan(3)
        }
      }
    }
  }

  private suspend fun failure(block: suspend () -> Unit): Throwable {
    val error = try {
      block()
      null
    } catch (error: Throwable) {
      error
    }
    assertThat(error).isNotNull()
    return checkNotNull(error)
  }
}
