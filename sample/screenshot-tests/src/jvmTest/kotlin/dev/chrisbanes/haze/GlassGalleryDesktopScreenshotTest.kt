// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassGalleryDesktopScreenshotTest : ScreenshotTest() {
  @Test fun productPortrait() = runScreenshotTest { captureGlassProductHero() }

  @Test
  fun productLandscape() = runScreenshotTest(size = Size(1920f, 1080f)) {
    captureGlassProductHero()
  }

  @Test fun playgroundBeats() = runScreenshotTest { captureGlassPlaygroundBeats() }

  @Test fun labPresets() = runScreenshotTest { captureGlassLabPresets() }
}
