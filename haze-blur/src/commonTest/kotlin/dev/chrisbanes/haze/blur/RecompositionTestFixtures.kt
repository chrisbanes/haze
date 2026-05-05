// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ComposeUiTest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

internal const val IDLE_TIMEOUT_MS = 3000L

internal suspend fun ComposeUiTest.awaitIdleWithTimeout(description: String) {
  try {
    withTimeout(IDLE_TIMEOUT_MS) {
      waitForIdle()
    }
  } catch (e: TimeoutCancellationException) {
    throw AssertionError(
      "Infinite recomposition loop detected $description. " +
        "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
      e,
    )
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
