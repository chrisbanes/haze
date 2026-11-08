// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.glass.GlassAccessibilitySettings
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import dev.chrisbanes.haze.glass.LocalGlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassClearAppearanceReplayScreenshotTest : ScreenshotTest() {
  @Test
  fun stableRegular_observesSystemAppearance() = checkAppearanceControl(GlassStyle.regular, false)

  private fun checkAppearanceControl(style: GlassStyle, reduced: Boolean) = runScreenshotTest(size = Size(420f, 800f)) {
    var appearance by mutableStateOf(GlassTestSystemAppearance.Light)
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        CompositionLocalProvider(
          LocalDensity provides Density(1f),
          LocalGlassAccessibilitySettings provides GlassAccessibilitySettings(reduceTransparency = reduced),
        ) {
          val state = remember { HazeState() }
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().hazeSource(state)) { drawRect(Color(0xFF315478)) }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
              repeat(2) { index ->
                Box(Modifier.size(280.dp, 176.dp).testTag("node$index").hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Quality))
              }
            }
          }
        }
      }
    }
    waitForIdle()
    for (index in 0..1) {
      val bounds = onNodeWithTag("node$index").fetchSemanticsNode().boundsInRoot
      println("stable node $index bounds=$bounds")
      assertThat(bounds.left).isEqualTo(70f)
      assertThat(bounds.top).isEqualTo(212f + 200f * index)
      assertThat(bounds.width).isEqualTo(280f)
      assertThat(bounds.height).isEqualTo(176f)
    }
    val light = captureRootPixels().snapshot()
    appearance = GlassTestSystemAppearance.Dark
    waitForIdle()
    val dark = captureRootPixels().snapshot()
    val deltas = listOf(212, 412).map { top ->
      dark.cropNode(top).meanAbsoluteDifference(light.cropNode(top)).also {
        println("stable appearance control reduced=$reduced top=$top meanDelta=$it")
      }
    }
    deltas.forEach { assertThat(it).isGreaterThan(1f / 255f) }
  }

  private fun PixelSnapshot.cropNode(top: Int): PixelSnapshot = PixelSnapshot(
    width = 280,
    height = 176,
    colors = buildList { for (y in top until top + 176) for (x in 70 until 350) add(this@cropNode[x, y]) },
  )

  @Test
  fun sharedClear_independentLocalStylesDoNotContaminateOtherNode() = runScreenshotTest(size = Size(420f, 800f)) {
    var appearance by mutableStateOf(GlassTestSystemAppearance.Light)
    var tintA by mutableStateOf(Color.Transparent)
    var tintB by mutableStateOf(Color.Transparent)
    val localA = GlassStyle { tint(tintA) }
    val localB = GlassStyle { tint(tintB) }
    val shared = GlassStyle.clear
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          val state = remember { HazeState() }
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().hazeSource(state)) {
              drawRect(Color(0xFF07141A))
              repeat(53) { i -> drawLine(Color.White, Offset(i * 8f, 0f), Offset(i * 8f, size.height), 1f) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
              listOf(localA, localB).forEach { local ->
                CompositionLocalProvider(LocalGlassStyle provides local) {
                  Box(Modifier.size(280.dp, 176.dp).hazeGlass(input = HazeInput.Sources(state), style = shared, performanceMode = HazePerformanceMode.Quality))
                }
              }
            }
          }
        }
      }
    }
    waitForIdle()
    val initial = captureRootPixels().snapshot()
    tintA = Color.Red.copy(alpha = 0.5f)
    waitForIdle()
    val afterA = captureRootPixels().snapshot()
    assertThat(afterA.cropNode(212).changedPixelRatio(initial.cropNode(212))).isGreaterThan(0.01f)
    assertThat(afterA.cropNode(412).meanAbsoluteDifference(initial.cropNode(412))).isLessThan(1f / 255f)
    appearance = GlassTestSystemAppearance.Dark
    waitForIdle()
    val darkA = captureRootPixels().snapshot()
    for (top in listOf(212, 412)) assertThat(darkA.cropNode(top).meanAbsoluteDifference(afterA.cropNode(top))).isLessThan(1f / 255f)
    appearance = GlassTestSystemAppearance.Light
    waitForIdle()
    tintB = Color.Blue.copy(alpha = 0.5f)
    waitForIdle()
    val afterB = captureRootPixels().snapshot()
    assertThat(afterB.cropNode(212).meanAbsoluteDifference(afterA.cropNode(212))).isLessThan(1f / 255f)
    assertThat(afterB.cropNode(412).changedPixelRatio(afterA.cropNode(412))).isGreaterThan(0.01f)
    appearance = GlassTestSystemAppearance.Dark
    waitForIdle()
    val darkB = captureRootPixels().snapshot()
    for (top in listOf(212, 412)) assertThat(darkB.cropNode(top).meanAbsoluteDifference(afterB.cropNode(top))).isLessThan(1f / 255f)
    appearance = GlassTestSystemAppearance.Light
    waitForIdle()
    val restored = captureRootPixels().snapshot()
    for (top in listOf(212, 412)) assertThat(restored.cropNode(top).meanAbsoluteDifference(afterB.cropNode(top))).isLessThan(1f / 255f)
  }

  @Test
  fun sharedClear_repeatedAppearanceSwitchAndObservedWrite() = runScreenshotTest(size = Size(420f, 800f)) {
    var appearance by mutableStateOf(GlassTestSystemAppearance.Light)
    var tint by mutableStateOf(Color.Transparent)
    val shared = GlassStyle.clear.then { tint(tint) }
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
          val state = remember { HazeState() }
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().hazeSource(state)) {
              drawRect(Color(0xFF07141A))
              repeat(53) { index ->
                val x = index * 8f
                drawLine(Color.White.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 1f)
              }
              repeat(100) { index ->
                val y = index * 8f
                drawLine(Color.Cyan.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y), 1f)
              }
            }
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
              repeat(2) {
                Box(Modifier.size(280.dp, 176.dp).hazeGlass(input = HazeInput.Sources(state), style = shared, performanceMode = HazePerformanceMode.Quality))
              }
            }
          }
        }
      }
    }
    waitForIdle()
    val light = captureRootPixels().snapshot()
    repeat(2) {
      appearance = GlassTestSystemAppearance.Dark
      waitForIdle()
      val current = captureRootPixels().snapshot()
      for (top in listOf(212, 412)) {
        assertThat(current.cropNode(top).meanAbsoluteDifference(light.cropNode(top))).isLessThan(1f / 255f)
      }
      appearance = GlassTestSystemAppearance.Light
      waitForIdle()
      val restored = captureRootPixels().snapshot()
      for (top in listOf(212, 412)) {
        assertThat(restored.cropNode(top).meanAbsoluteDifference(light.cropNode(top))).isLessThan(1f / 255f)
      }
    }
    tint = Color.Red.copy(alpha = 0.5f)
    waitForIdle()
    val updated = captureRootPixels().snapshot()
    for (top in listOf(212, 412)) {
      assertThat(updated.cropNode(top).changedPixelRatio(light.cropNode(top))).isGreaterThan(0.01f)
    }
  }
}
