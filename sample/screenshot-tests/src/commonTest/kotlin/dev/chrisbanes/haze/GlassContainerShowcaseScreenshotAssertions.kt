// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import dev.chrisbanes.haze.sample.GlassContainerShowcaseSampleContent
import dev.chrisbanes.haze.sample.GlassContainerShowcaseState
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureGlassContainerShowcase(
  isDark: Boolean,
  contentWrapper: @Composable (@Composable () -> Unit) -> Unit = { content -> content() },
) {
  // Drive the scripted loop by hand, without its autoplay, as it looks in recording mode.
  val state = GlassContainerShowcaseState().apply {
    pause()
    updateRecordingMode(true)
  }
  setContent {
    SamplesTheme(useDarkColors = isDark) {
      ScreenshotTheme {
        contentWrapper {
          GlassContainerShowcaseSampleContent(state = state, onBack = {})
        }
      }
    }
  }

  // Layered runtime shaders amplify Skia's platform-specific pixel variance.
  val unmatchedPixelThreshold = 0.013f
  waitForIdle()
  captureRoot("rest", unmatchedPixelThreshold)

  state.seek(4f / 14)
  waitForIdle()
  captureRoot("expanded", unmatchedPixelThreshold)

  state.seek(0.5f)
  waitForIdle()
  captureRoot("split", unmatchedPixelThreshold)

  // The demo controls must stay clear of the palette.
  state.updateRecordingMode(false)
  waitForIdle()
  captureRoot("controls", unmatchedPixelThreshold)
}
