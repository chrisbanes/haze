// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "+land")
class GlassMusicPlayerLandscapeAndroidScreenshotTest : ScreenshotTest() {
  @Test
  fun nowPlaying() = runScreenshotTest {
    captureGlassMusicPlayer(isDark = false, contentWrapper = ::previewContent)
  }
}

@OptIn(ExperimentalCoilApi::class)
@androidx.compose.runtime.Composable
private fun previewContent(content: @androidx.compose.runtime.Composable () -> Unit) {
  val handler = AsyncImagePreviewHandler { ColorImage(color = 0xff34506f.toInt()) }
  CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides handler, content = content)
}
