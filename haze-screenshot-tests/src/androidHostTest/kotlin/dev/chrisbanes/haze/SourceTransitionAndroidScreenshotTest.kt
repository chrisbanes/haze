// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [32, 35])
class SourceTransitionAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_sourceRemoved_retainsLastOutput() = runScreenshotTest(relaxedTolerance = true) {
    val visualEffect = BlurVisualEffect().apply {
      blurRadius = 16.dp
      colorEffects = listOf(
        HazeColorEffect.tint(
          Color.White.copy(alpha = 0.12f),
          HazeColorEffect.DefaultBlendMode,
        ),
      )
    }
    var showSource by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        SourceTransitionSample(
          visualEffect = visualEffect,
          showSource = showSource,
        )
      }
    }

    waitForIdle()
    captureRoot("source")

    showSource = false
    waitForIdle()
    captureRoot("source_removed")
  }
}
