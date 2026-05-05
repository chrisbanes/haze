// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal const val IDLE_TIMEOUT_MS = 3000L

internal fun ComposeContentTestRule.awaitIdleWithTimeout(description: String) {
  val executor = Executors.newSingleThreadExecutor()
  val finished = CountDownLatch(1)
  var failure: Throwable? = null

  try {
    executor.submit {
      try {
        waitForIdle()
      } catch (t: Throwable) {
        failure = t
      } finally {
        finished.countDown()
      }
    }

    if (!finished.await(IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
      throw AssertionError(
        "Infinite recomposition loop detected $description. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        TimeoutException("Timed out waiting for waitForIdle()"),
      )
    }

    failure?.let { throw it }
  } finally {
    executor.shutdownNow()
  }
}

@Composable
internal fun BlurTestGradientBox(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(
      Brush.linearGradient(
        colors = listOf(
          Color(0xFF6B73FF),
          Color(0xFF9B59B6),
          Color(0xFFE74C3C),
          Color(0xFFF39C12),
        ),
      ),
    ),
  )
}
