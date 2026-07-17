// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassGalleryPortraitAndroidScreenshotTest : ScreenshotTest() {
  @Test fun productHero() = runScreenshotTest { captureGlassProductHero() }

  @Test fun playgroundBeats() = runScreenshotTest { captureGlassPlaygroundBeats() }

  @Test fun labPresets() = runScreenshotTest { captureGlassLabPresets() }
}
