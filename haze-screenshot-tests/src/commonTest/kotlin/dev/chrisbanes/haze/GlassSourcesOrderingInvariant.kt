// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.test.ScreenshotUiTest

/** Verifies the Sources draw order through observable RGB output, including foreground overlap. */
internal fun ScreenshotUiTest.assertGlassSourcesCallerContentOrdering() {
  val hazeState = HazeState()
  val effect = GlassTestConfiguration().apply {
    style = sourcesOrderingStyle(edgeShadow = Color.Transparent)
  }
  var showCallerContent by mutableStateOf(false)

  setContent {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Box(Modifier.size(width = 220.dp, height = 120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(SOURCE_BLUE)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeGlass(
              input = HazeInput.Sources(hazeState),
              configuration = effect,
              performanceMode = HazePerformanceMode.Balanced,
            ),
        ) {
          if (showCallerContent) {
            Canvas(Modifier.fillMaxSize()) { drawRect(CALLER_RED) }
          }
        }
      }
    }
  }
  waitForIdle()
  val sourceOnly = captureRootPixels().snapshot()

  showCallerContent = true
  waitForIdle()
  val callerOnly = captureRootPixels().snapshot()

  effect.style = sourcesOrderingStyle(edgeShadow = FOREGROUND_GREEN)
  waitForIdle()
  val ordered = captureRootPixels().snapshot()

  val sourceCenter = sourceOnly.centerColor()
  val callerCenter = callerOnly.centerColor()
  assertThat(sourceCenter.blue, "Sources optical base blue at center")
    .isGreaterThan(sourceCenter.red + 0.25f)
  assertThat(callerCenter.red, "caller content red above Sources optical base")
    .isGreaterThan(callerCenter.blue + 0.5f)

  val overlap = ordered.strongestGreenForegroundOverlap(callerOnly)
  println(
    "Glass Sources ordering probes: sourceCenter=$sourceCenter, callerCenter=$callerCenter, " +
      "foregroundOverlap=$overlap",
  )
  assertThat(overlap.greenIncrease, "green foreground above red caller content")
    .isGreaterThan(0.05f)
  assertThat(overlap.color.red, "red caller content retained under foreground overlap")
    .isGreaterThan(0.1f)
}

private fun sourcesOrderingStyle(edgeShadow: Color) = GlassStyle {
  optics(
    GlassOptics(
      refractionStrength = 0f,
      refractionDisplacement = 0.dp,
      depth = OpticalSizeValue.Fixed(0f),
      blurRadius = OpticalSizeValue.Fixed(0.dp),
    ),
  )
  tint(Color.Transparent)
  backgroundColor(Color.Transparent)
  ambientResponse(0f)
  contrast(0f)
  whitePoint(0f)
  specularIntensity(0f)
  edgeShadow(edgeShadow)
  edgeSoftness(6.dp)
  shape(RoundedCornerShape(28.dp))
}

private fun PixelSnapshot.centerColor(): Color = colors[(height / 2) * width + width / 2]

private data class ForegroundOverlap(val greenIncrease: Float, val color: Color)

private fun PixelSnapshot.strongestGreenForegroundOverlap(
  callerOnly: PixelSnapshot,
): ForegroundOverlap {
  require(width == callerOnly.width && height == callerOnly.height)
  var result = ForegroundOverlap(Float.NEGATIVE_INFINITY, Color.Transparent)
  for (index in colors.indices) {
    val caller = callerOnly.colors[index]
    if (caller.alpha < 0.99f || caller.red < 0.8f) continue
    val ordered = colors[index]
    val increase = ordered.green - caller.green
    if (increase > result.greenIncrease) {
      result = ForegroundOverlap(increase, ordered)
    }
  }
  return result
}

private val SOURCE_BLUE = Color(0xFF1769E0)
private val CALLER_RED = Color(0xFFE02036)
private val FOREGROUND_GREEN = Color(0xFF20E070)
