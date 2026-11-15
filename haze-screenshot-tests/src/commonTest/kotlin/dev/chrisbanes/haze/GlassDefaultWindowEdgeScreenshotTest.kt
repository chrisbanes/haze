// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

/** Compares an inset default bar with the same style flush against the host window's bottom edge. */
class GlassDefaultWindowEdgeScreenshotTest : ScreenshotTest() {
  @Test
  fun regular_light() = capture(GlassStyle.regular, GlassTestSystemAppearance.Light)

  @Test
  fun regular_dark() = capture(GlassStyle.regular, GlassTestSystemAppearance.Dark)

  @Test
  fun clear_light() = capture(GlassStyle.clear, GlassTestSystemAppearance.Light)

  @Test
  fun clear_dark() = capture(GlassStyle.clear, GlassTestSystemAppearance.Dark)

  private fun capture(style: GlassStyle, appearance: GlassTestSystemAppearance) = runScreenshotTest(size = Size(840f, 640f)) {
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        CompositionLocalProvider(LocalDensity provides Density(2f)) {
          val state = remember { HazeState() }
          Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().hazeSource(state)) {
              val colors = listOf(Color(0xFF245C78), Color(0xFFE6B56A), Color(0xFF6E9980), Color(0xFF934B69))
              colors.forEachIndexed { index, color ->
                drawRect(color, Offset(index * size.width / colors.size, 0f), Size(size.width, size.height))
              }
            }
            Box(
              Modifier.align(Alignment.Center).size(340.dp, 80.dp).hazeGlass(
                input = HazeInput.Sources(state),
                style = style,
                performanceMode = HazePerformanceMode.Quality,
              ),
            )
            Box(
              Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(80.dp).hazeGlass(
                input = HazeInput.Sources(state),
                style = style,
                performanceMode = HazePerformanceMode.Quality,
              ),
            )
          }
        }
      }
    }
    waitForIdle()
    captureRoot()
  }
}
