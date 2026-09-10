// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassMusicPlayerDesktopScreenshotTest : ScreenshotTest() {
  @Test fun nowPlaying() = runScreenshotTest { captureGlassMusicPlayer(isDark = true) }
  @Test fun library() = runScreenshotTest { captureGlassMusicPlayer(isDark = false, library = true) }
  @Test fun landscape() = runScreenshotTest(size = Size(1920f, 1080f)) { captureGlassMusicPlayer(isDark = true) }
}
