// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class, com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.glass.LocalGlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.HazeRoborazziDefaults
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test

class GlassDefaultCandidateScreenshotTest : ScreenshotTest() {
  @Test fun authoredShapeBeforeAndAfterCandidate() = runScreenshotTest(size = Size(420f, 300f)) { assertCandidateAuthoredShape() }

  @Test fun regular_single() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = false, merged = false) }

  @Test fun regular_merged() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = false, merged = true) }

  @Test fun clear_single() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = true, merged = false) }

  @Test fun clear_merged() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = true, merged = true) }

  @Test fun regular_photo() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = false, merged = false, photograph = true) }

  @Test fun regular_performance() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = false, merged = false, mode = HazePerformanceMode.Performance) }

  @Test fun clear_balanced() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = true, merged = true, mode = HazePerformanceMode.Balanced) }

  @Test fun clear_performance() = runScreenshotTest(size = Size(420f, 300f)) { captureCandidateCase("desktop", clear = true, merged = true, mode = HazePerformanceMode.Performance) }

  @Test
  fun calibration_darkRegular() = runScreenshotTest(size = Size(420f, 300f)) {
    val previousLogging = HazeLogger.enabled
    HazeLogger.enabled = true
    try {
      val direction = mutableStateOf(-1)
      setContent {
        WithGlassTestSystemAppearance(GlassTestSystemAppearance.Dark) {
          CompositionLocalProvider(LocalDensity provides Density(1f)) {
            val hazeState = remember { HazeState() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
                drawRect(Color(0xFF07141A))
                drawRect(Color(0xFFB8C5C8), topLeft = Offset(size.width / 2f, 0f), size = Size(size.width / 2f, size.height))
                val spacing = 8.dp.toPx()
                var x = 0f
                while (x < size.width) {
                  drawLine(Color.White.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                  x += spacing
                }
                var y = 0f
                while (y < size.height) {
                  drawLine(Color.Cyan.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                  y += spacing
                }
              }
              val selected = direction.value
              val (seed, style) = candidateStyles(selected, clear = false, dark = true)
              CompositionLocalProvider(LocalGlassStyle provides seed) {
                if (selected >= 0) {
                  Box(Modifier.size(280.dp, 176.dp).hazeGlass(input = HazeInput.Sources(hazeState), style = style, performanceMode = HazePerformanceMode.Quality))
                }
              }
            }
          }
        }
      }
      val captures = mutableListOf<PixelSnapshot>()
      for (index in -1..4) {
        direction.value = index
        waitForIdle()
        captures += captureRootPixels().snapshot()
        if (HazeRoborazziDefaults.roborazziOptions.taskType.isRecording()) captureRoot(artifactPath = "build/outputs/candidates/1743/calibration-${if (index == -1) "raw" else "b$index"}.png")
      }
      val baseline = captures[1]
      val c1 = captures[2]
      val noEdges = captures[4]
      val left = (c1.width * 102f / 420f).toInt()
      val right = (c1.width * 318f / 420f).toInt()
      val top = (c1.height * 94f / 300f).toInt()
      val bottom = (c1.height * 206f / 300f).toInt()
      fun statistics(image: PixelSnapshot): Pair<Float, Float> {
        val samples = (top until bottom).flatMap { y -> (left until right).map { x -> image[x, y].luminance() } }
        val mean = samples.average().toFloat()
        return mean to sqrt(samples.map { (it - mean) * (it - mean) }.average().toFloat())
      }
      fun difference(a: PixelSnapshot, b: PixelSnapshot, x0: Int, x1: Int, y0: Int, y1: Int): Float {
        var sum = 0f
        for (y in y0 until y1) for (x in x0 until x1) sum += abs(a[x, y].luminance() - b[x, y].luminance())
        return sum / ((x1 - x0) * (y1 - y0))
      }
      val toneDifference = difference(c1, baseline, left, right, top, bottom)
      val edgeDifference = difference(c1, noEdges, left, right, (c1.height * 61f / 300f).toInt(), (c1.height * 70f / 300f).toInt())
      println("calibration pixels=${c1.width}x${c1.height} interior mean/stddev B0=${statistics(baseline)} C1=${statistics(c1)} tone=$toneDifference edge=$edgeDifference")
      assertThat(toneDifference).isGreaterThan(0.001f)
      assertThat(edgeDifference).isGreaterThan(0.001f)
    } finally {
      HazeLogger.enabled = previousLogging
    }
  }
}
