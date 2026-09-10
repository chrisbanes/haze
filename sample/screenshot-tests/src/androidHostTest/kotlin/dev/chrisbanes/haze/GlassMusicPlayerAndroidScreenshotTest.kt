// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class GlassMusicPlayerAndroidScreenshotTest : ScreenshotTest() {
  @Test fun nowPlaying() = runScreenshotTest { captureGlassMusicPlayer(isDark = false, contentWrapper = ::previewContent) }

  @Test fun library() = runScreenshotTest { captureGlassMusicPlayer(isDark = true, library = true, contentWrapper = ::previewContent) }

  @Test
  fun wideNowPlaying() = runScreenshotTest(size = Size(1920f, 1080f)) {
    captureGlassMusicPlayer(isDark = false, contentWrapper = ::previewContent)
  }
}

@androidx.compose.runtime.Composable
private fun previewContent(content: @androidx.compose.runtime.Composable () -> Unit) {
  CompositionLocalProvider(LocalInspectionMode provides true, content = content)
}
