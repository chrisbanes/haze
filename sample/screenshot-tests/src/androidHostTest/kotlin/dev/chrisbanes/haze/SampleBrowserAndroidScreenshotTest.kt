// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
class SampleBrowserAndroidScreenshotTest : ScreenshotTest() {
  @Test fun phonePortrait() = runScreenshotTest { captureSampleBrowser() }
}

@Config(sdk = [35], qualifiers = PIXEL_TABLET_PORTRAIT_QUALIFIERS)
class SampleBrowserTabletAndroidScreenshotTest : ScreenshotTest() {
  @Test fun tabletPortrait_glassSelected() = runScreenshotTest {
    captureSampleBrowser(selectGlass = true)
  }
}

private const val PIXEL_TABLET_PORTRAIT_QUALIFIERS =
  "w800dp-h1280dp-large-notlong-notround-port-any-xhdpi-keyshidden-nonav"
