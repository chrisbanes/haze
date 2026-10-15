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

  // Search is held away from the tabs, beyond the spacing, so it draws separately.
  state.seek(0.27f)
  waitForIdle()
  captureRoot("split", unmatchedPixelThreshold)

  // Selection mode, after Search retracts and the actions grow out of the pill.
  state.seek(0.6f)
  waitForIdle()
  captureRoot("selecting", unmatchedPixelThreshold)

  // The demo controls, which must not cover the bar.
  state.updateRecordingMode(false)
  waitForIdle()
  captureRoot("controls", unmatchedPixelThreshold)
}
