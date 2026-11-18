// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTestWithoutMotion
import kotlin.test.Test

class GlassContainerDesktopScreenshotTest : ScreenshotTest() {
  // Captures are pixel-identical without motion; skipping it avoids rendering every animation frame.
  @Test fun container() = runScreenshotTestWithoutMotion { captureGlassContainer() }
}
