// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class GlassTiltAndroidScreenshotTest : ScreenshotTest() {
  @Test fun fixedAndInjectedTilt() = runScreenshotTest { captureGlassTiltLight() }
}
