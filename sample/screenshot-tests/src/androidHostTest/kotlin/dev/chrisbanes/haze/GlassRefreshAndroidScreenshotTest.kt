// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.sample.GlassRefreshSampleContent
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [31])
class GlassRefreshAndroidScreenshotTest : ScreenshotTest() {
  @Test
  fun changeBackground_updatesTheFixedGeometryScene() = runScreenshotTest {
    setContent {
      var alternate by remember { mutableStateOf(false) }
      SamplesTheme {
        GlassRefreshSampleContent(alternate, onChangeBackground = { alternate = !alternate }, onBack = {})
      }
    }
    val before = captureRootPixels()
    captureRoot("before")
    onNodeWithTag("glass_refresh_change").performClick()
    val after = captureRootPixels()
    for (y in listOf(250, 500, 750)) {
      assertThat(after[150, y]).isNotEqualTo(before[150, y])
    }
    captureRoot("after")
  }
}
