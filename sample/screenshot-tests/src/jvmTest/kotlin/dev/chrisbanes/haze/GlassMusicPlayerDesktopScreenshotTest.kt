// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassMusicPlayerDesktopScreenshotTest : ScreenshotTest() {
  @Test fun compactDark() = runScreenshotTest { captureGlassMusicPlayer(isDark = true) }
  @Test fun compactLightLibrary() = runScreenshotTest { captureGlassMusicPlayer(isDark = false, library = true) }
  @Test fun wideDark() = runScreenshotTest(size = Size(1920f, 1080f)) { captureGlassMusicPlayer(isDark = true) }
  @Test fun wideLight() = runScreenshotTest(size = Size(1920f, 1080f)) { captureGlassMusicPlayer(isDark = false) }
}
