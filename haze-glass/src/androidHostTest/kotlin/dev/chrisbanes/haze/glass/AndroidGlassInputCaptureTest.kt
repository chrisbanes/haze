// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.graphics.ImageBitmap
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Routing/failure ownership only; native capture pixels are tested on device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidGlassInputCaptureTest {
  @Test fun api33_usesExistingPlatformCapture() = runBlocking {
    val capture = AndroidGlassInputCapture(supportsHardwareBuffer = false)
    val image = ImageBitmap(1, 1)
    val result = capture.capture(hardware = { error("Unsupported API must not load the hardware backend") }, platform = { image })
    assertThat(result).isSameInstanceAs(image)
  }

  @Test fun hardwareSuccess_keepsBackendEnabled() = runBlocking {
    val capture = AndroidGlassInputCapture(supportsHardwareBuffer = true)
    val image = ImageBitmap(1, 1)
    var attempts = 0
    repeat(2) {
      val result = capture.capture(hardware = {
        attempts++
        image
      }, platform = { error("Successful capture must not fall back") })
      assertThat(result).isSameInstanceAs(image)
    }
    assertThat(attempts).isEqualTo(2)
  }

  @Test fun backendFailure_fallsBackAndAvoidsRepeatedSetup() = runBlocking {
    val capture = AndroidGlassInputCapture(supportsHardwareBuffer = true)
    val image = ImageBitmap(1, 1)
    var attempts = 0
    var fallback = 0
    repeat(2) {
      val result = capture.capture(hardware = {
        attempts++
        error("Rejected native completion")
      }, platform = {
        fallback++
        image
      })
      assertThat(result).isSameInstanceAs(image)
    }
    assertThat(attempts).isEqualTo(1)
    assertThat(fallback).isEqualTo(2)
  }

  @Test fun cancellation_doesNotFallBackOrDisableBackend() = runBlocking {
    val capture = AndroidGlassInputCapture(supportsHardwareBuffer = true)
    val image = ImageBitmap(1, 1)
    var attempts = 0
    var fallback = 0
    try {
      capture.capture(hardware = {
        attempts++
        throw CancellationException("Cancelled caller")
      }, platform = {
        fallback++
        image
      })
      error("Cancellation must propagate")
    } catch (_: CancellationException) {
      // Expected: the next independent caller may still use this supported backend.
    }
    val result = capture.capture(hardware = {
      attempts++
      image
    }, platform = {
      fallback++
      image
    })
    assertThat(result).isSameInstanceAs(image)
    assertThat(attempts).isEqualTo(2)
    assertThat(fallback).isEqualTo(0)
  }

  @Test fun failureAfterCallerCancellation_doesNotStartFallback() = runBlocking {
    val capture = AndroidGlassInputCapture(supportsHardwareBuffer = true)
    val image = ImageBitmap(1, 1)
    var fallback = 0
    val job = launch {
      capture.capture(
        hardware = {
          currentCoroutineContext().cancel()
          error("Failure while cancelling")
        },
        platform = {
          fallback++
          image
        },
      )
    }
    job.join()
    assertThat(fallback).isEqualTo(0)
  }
}
