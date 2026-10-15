// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassContainerShowcaseDesktopScreenshotTest : ScreenshotTest() {
  @Test fun showcaseDark() = runScreenshotTest(size = Size(1280f, 720f)) {
    captureGlassContainerShowcase(isDark = true) { content -> CheckerboardPhotos(isDark = true, content = content) }
  }

  @Test fun showcaseLight() = runScreenshotTest(size = Size(1280f, 720f)) {
    captureGlassContainerShowcase(isDark = false) { content -> CheckerboardPhotos(isDark = false, content = content) }
  }

  // The sample is also reachable on phones, where the bar shrinks to fit.
  @Test fun showcasePhoneDark() = runScreenshotTest(size = Size(360f, 760f)) {
    captureGlassContainerShowcase(isDark = true) { content -> CheckerboardPhotos(isDark = true, content = content) }
  }

  // With large text the title grows, and the demo controls must stay below it.
  @Test fun showcasePhoneLargeTextDark() = runScreenshotTest(size = Size(360f, 760f)) {
    captureGlassContainerShowcase(isDark = true) { content ->
      CheckerboardPhotos(isDark = true, fontScale = 2f, content = content)
    }
  }
}

// Tests run offline, so stand in for the sample's photos with a high-contrast pattern. A density of 1
// makes the capture size the layout size in dp, such as the 1280x720 dp recording window. Glass
// follows the system appearance, so match it to the sample's theme.
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun CheckerboardPhotos(
  isDark: Boolean,
  fontScale: Float = 1f,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalSystemTheme provides if (isDark) SystemTheme.Dark else SystemTheme.Light,
    LocalDensity provides Density(1f, fontScale),
    LocalInspectionMode provides true,
    LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { checkerboardImage() },
    content = content,
  )
}
