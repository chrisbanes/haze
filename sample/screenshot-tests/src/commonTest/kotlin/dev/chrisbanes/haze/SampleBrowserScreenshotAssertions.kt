// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.sample.Samples
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureSampleBrowser(selectGlass: Boolean = false) {
  setContent {
    CompositionLocalProvider(LocalInspectionMode provides true) {
      ScreenshotTheme {
        Samples(
          appTitle = "Haze Samples",
          useDarkColors = false,
        )
      }
    }
  }
  waitForIdle()
  if (selectGlass) {
    onNodeWithTag("sample_effect_glass").performClick()
    waitForIdle()
  }
  captureRoot()
}
