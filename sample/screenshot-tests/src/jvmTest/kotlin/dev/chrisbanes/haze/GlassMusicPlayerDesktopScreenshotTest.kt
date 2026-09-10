// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassMusicPlayerDesktopScreenshotTest : ScreenshotTest() {
  @Test fun compactDark() = runScreenshotTest { captureGlassMusicPlayer(isDark = true, contentWrapper = ::previewContent) }

  @Test fun compactLightLibrary() = runScreenshotTest { captureGlassMusicPlayer(isDark = false, library = true, contentWrapper = ::previewContent) }

  @Test fun wideDark() = runScreenshotTest(size = Size(1920f, 1080f)) { captureGlassMusicPlayer(isDark = true, contentWrapper = ::previewContent) }

  @Test fun wideLight() = runScreenshotTest(size = Size(1920f, 1080f)) { captureGlassMusicPlayer(isDark = false, contentWrapper = ::previewContent) }
}

@OptIn(ExperimentalCoilApi::class)
@androidx.compose.runtime.Composable
private fun previewContent(content: @androidx.compose.runtime.Composable () -> Unit) {
  val handler = AsyncImagePreviewHandler { checkerboardImage() }
  CompositionLocalProvider(
    LocalInspectionMode provides true,
    LocalAsyncImagePreviewHandler provides handler,
    content = content,
  )
}
