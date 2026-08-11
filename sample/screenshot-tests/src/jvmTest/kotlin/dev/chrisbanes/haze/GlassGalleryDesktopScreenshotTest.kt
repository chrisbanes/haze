// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation.compose.rememberNavController
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.sample.SampleEffect
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.sample.ScaffoldSample
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

  @Test
  @OptIn(ExperimentalCoilApi::class)
  fun scaffoldChrome() = runScreenshotTest {
    val previewHandler = AsyncImagePreviewHandler {
      ColorImage(color = 0xff8b7de8.toInt())
    }
    setContent {
      CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides previewHandler,
      ) {
        SamplesTheme(useDarkColors = false) {
          ScaffoldSample(
            navController = rememberNavController(),
            effect = SampleEffect.Glass,
          )
        }
      }
    }
    waitForIdle()
    captureRoot()
  }
}
