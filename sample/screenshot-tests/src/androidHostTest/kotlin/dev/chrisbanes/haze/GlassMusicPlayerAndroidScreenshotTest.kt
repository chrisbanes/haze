// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.sample.MusicPlayerTab
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class GlassMusicPlayerAndroidScreenshotTest : ScreenshotTest() {
  @Test fun nowPlaying() = runScreenshotTest { captureGlassMusicPlayer(isDark = false, contentWrapper = ::previewContent) }

  @Test fun library() = runScreenshotTest { captureGlassMusicPlayer(isDark = true, tab = MusicPlayerTab.Library, contentWrapper = ::previewContent) }

  // Keep the artwork shelf behind the mini-player so these captures exercise backdrop blur.
  @Config(qualifiers = "+h698dp")
  @Test
  fun homeLight() = runScreenshotTest { captureGlassMusicPlayer(isDark = false, tab = MusicPlayerTab.Home, contentWrapper = ::previewContent) }

  @Config(qualifiers = "+h698dp")
  @Test
  fun homeDark() = runScreenshotTest { captureGlassMusicPlayer(isDark = true, tab = MusicPlayerTab.Home, contentWrapper = ::previewContent) }
}

@OptIn(ExperimentalCoilApi::class)
@androidx.compose.runtime.Composable
private fun previewContent(content: @androidx.compose.runtime.Composable () -> Unit) {
  val handler = AsyncImagePreviewHandler {
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    for (y in 0 until 16) {
      for (x in 0 until 16) {
        paint.color = if ((x + y) % 2 == 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        canvas.drawRect(x * 16f, y * 16f, (x + 1) * 16f, (y + 1) * 16f, paint)
      }
    }
    bitmap.asImage()
  }
  CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides handler, content = content)
}
