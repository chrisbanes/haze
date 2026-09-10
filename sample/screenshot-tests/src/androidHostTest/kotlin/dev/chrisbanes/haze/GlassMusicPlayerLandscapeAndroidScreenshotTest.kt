// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "+land")
class GlassMusicPlayerLandscapeAndroidScreenshotTest : ScreenshotTest() {
  @Test
  fun nowPlaying() = runScreenshotTest {
    captureGlassMusicPlayer(isDark = false, contentWrapper = ::previewContent)
  }
}

@androidx.compose.runtime.Composable
private fun previewContent(content: @androidx.compose.runtime.Composable () -> Unit) {
  CompositionLocalProvider(LocalInspectionMode provides true, content = content)
}
