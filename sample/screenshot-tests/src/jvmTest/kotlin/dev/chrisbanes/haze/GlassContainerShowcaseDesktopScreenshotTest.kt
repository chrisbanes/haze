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
import androidx.compose.ui.unit.Density
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTestWithoutMotion
import kotlin.test.Test

// Captures are pixel-identical without motion; skipping it avoids rendering every animation frame.
class GlassContainerShowcaseDesktopScreenshotTest : ScreenshotTest() {
  @Test fun showcaseDark() = runScreenshotTestWithoutMotion(size = Size(1280f, 720f)) {
    captureGlassContainerShowcase(isDark = true) { content -> PaletteEnvironment(true, content = content) }
  }

  @Test fun showcaseLight() = runScreenshotTestWithoutMotion(size = Size(1280f, 720f)) {
    captureGlassContainerShowcase(isDark = false) { content -> PaletteEnvironment(false, content = content) }
  }

  @Test fun showcasePhoneDark() = runScreenshotTestWithoutMotion(size = Size(360f, 760f)) {
    captureGlassContainerShowcase(isDark = true) { content -> PaletteEnvironment(true, content = content) }
  }

  @Test fun showcasePhoneLargeTextDark() = runScreenshotTestWithoutMotion(size = Size(360f, 760f)) {
    captureGlassContainerShowcase(isDark = true) { content -> PaletteEnvironment(true, fontScale = 2f, content = content) }
  }
}

@Composable
private fun PaletteEnvironment(isDark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
  CompositionLocalProvider(
    LocalSystemTheme provides if (isDark) SystemTheme.Dark else SystemTheme.Light,
    LocalDensity provides Density(1f, fontScale),
    content = content,
  )
}
