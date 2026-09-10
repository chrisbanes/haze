// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class GlassMusicPlayerAndroidScreenshotTest : ScreenshotTest() {
  @Test fun nowPlaying() = runScreenshotTest { captureGlassMusicPlayer(isDark = false) }

  @Test fun library() = runScreenshotTest { captureGlassMusicPlayer(isDark = true, library = true) }
}
