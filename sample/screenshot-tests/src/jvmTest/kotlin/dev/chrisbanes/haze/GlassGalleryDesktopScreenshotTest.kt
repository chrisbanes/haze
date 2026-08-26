// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.navigation.compose.rememberNavController
import coil3.BitmapImage
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.sample.SampleEffect
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.sample.ScaffoldSample
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint

class GlassGalleryDesktopScreenshotTest : ScreenshotTest() {
  @Test fun productPortrait() = runScreenshotTest { captureGlassProductHero() }

  @Test
  fun productLandscape() = runScreenshotTest(size = Size(1920f, 1080f)) {
    captureGlassProductHero()
  }

  @Test fun playgroundBeats() = runScreenshotTest { captureGlassPlaygroundBeats() }

  @Test fun labStyles() = runScreenshotTest { captureGlassLabStyles() }

  @Test
  @OptIn(ExperimentalCoilApi::class)
  fun scaffoldChrome() = runScreenshotTest {
    val previewHandler = AsyncImagePreviewHandler {
      checkerboardImage()
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

private fun checkerboardImage(): BitmapImage {
  val size = 256
  val squareSize = 16
  val bitmap = Bitmap().apply {
    check(allocN32Pixels(size, size, true))
  }
  Canvas(bitmap).use { canvas ->
    Paint().use { paint ->
      for (y in 0 until size step squareSize) {
        for (x in 0 until size step squareSize) {
          paint.color = if ((x / squareSize + y / squareSize) % 2 == 0) {
            0xff2f255b.toInt()
          } else {
            0xffd9d3ff.toInt()
          }
          canvas.drawRect(
            x.toFloat(),
            y.toFloat(),
            (x + squareSize).toFloat(),
            (y + squareSize).toFloat(),
            paint,
          )
        }
      }
    }
  }

  return bitmap.asImage()
}
