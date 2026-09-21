// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.animation.core.snap
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.test.ScreenshotUiTest

/** Verifies the Sources draw order through independently controlled observable RGB output. */
internal fun ScreenshotUiTest.assertGlassSourcesCallerContentOrdering() {
  val hazeState = HazeState()
  val effect = GlassTestConfiguration().apply {
    style = sourcesOrderingStyle(edgeShadow = Color.Transparent)
    pressed { lightingIntensity(1f) }
    interactionLightRadiusFraction = 0.7f
    interactionPositionAnimationSpec = snap()
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
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
            .testTag(GLASS_TAG)
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

  showCallerContent = false
  effect.style = sourcesOrderingStyle(edgeShadow = FOREGROUND_GREEN)
  effect.pressed { lightingIntensity(1f) }
  waitForIdle()
  val foregroundIdle = captureRootPixels().snapshot()

  onNodeWithTag(GLASS_TAG).performTouchInput { down(Offset(center.x * 0.35f, center.y * 0.5f)) }
  waitForIdle()
  val foregroundPressed = captureRootPixels().snapshot()

  showCallerContent = true
  waitForIdle()
  val orderedPressed = captureRootPixels().snapshot()

  val sourceCenter = sourceOnly.centerColor()
  val callerCenter = callerOnly.centerColor()
  assertThat(sourceCenter.blue, "Sources optical base blue at center")
    .isGreaterThan(sourceCenter.red + 0.25f)
  assertThat(callerCenter.red, "caller content red above Sources optical base")
    .isGreaterThan(sourceCenter.red + 0.5f)

  val lightingDelta = foregroundIdle.maxRgbDifference(foregroundPressed)
  assertThat(lightingDelta, "pressed interaction lighting changes observable RGB")
    .isGreaterThan(0.03f)

  val overlap = measureSourcesOverlap(
    ordered = orderedPressed,
    callerOnly = callerOnly,
    foregroundOnly = foregroundPressed,
  )
  println(
    "Glass Sources ordering probes: sourceCenter=$sourceCenter, callerCenter=$callerCenter, " +
      "lightingDelta=$lightingDelta, foregroundOverlap=$overlap",
  )
  assertSourcesOverlap(overlap)
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

private fun PixelSnapshot.maxRgbDifference(other: PixelSnapshot): Float {
  require(width == other.width && height == other.height)
  return colors.indices.maxOf { index ->
    val first = colors[index]
    val second = other.colors[index]
    maxOf(
      kotlin.math.abs(first.red - second.red),
      kotlin.math.abs(first.green - second.green),
      kotlin.math.abs(first.blue - second.blue),
    )
  }
}

internal data class SourcesOverlap(
  val callerRedContribution: Float,
  val foregroundGreenContribution: Float,
  val color: Color,
  val index: Int,
)

internal fun measureSourcesOverlap(
  ordered: PixelSnapshot,
  callerOnly: PixelSnapshot,
  foregroundOnly: PixelSnapshot,
): SourcesOverlap {
  require(ordered.width == callerOnly.width && ordered.height == callerOnly.height)
  require(ordered.width == foregroundOnly.width && ordered.height == foregroundOnly.height)
  var result = SourcesOverlap(
    callerRedContribution = Float.NEGATIVE_INFINITY,
    foregroundGreenContribution = Float.NEGATIVE_INFINITY,
    color = Color.Transparent,
    index = -1,
  )
  var bestJointContribution = Float.NEGATIVE_INFINITY
  for (index in ordered.colors.indices) {
    val color = ordered.colors[index]
    val callerContribution = color.red - foregroundOnly.colors[index].red
    val foregroundContribution = color.green - callerOnly.colors[index].green
    val jointContribution = minOf(callerContribution, foregroundContribution)
    if (jointContribution > bestJointContribution) {
      bestJointContribution = jointContribution
      result = SourcesOverlap(callerContribution, foregroundContribution, color, index)
    }
  }
  return result
}

internal fun assertSourcesOverlap(overlap: SourcesOverlap) {
  assertThat(overlap.foregroundGreenContribution, "foreground contributes green above caller")
    .isGreaterThan(0.03f)
  assertThat(overlap.callerRedContribution, "caller contributes red below foreground")
    .isGreaterThan(0.03f)
}

private const val GLASS_TAG = "sources-ordering-glass"
private val SOURCE_BLUE = Color(0xFF1769E0)
private val CALLER_RED = Color(0xFFE02036)
private val FOREGROUND_GREEN = Color(0x9900E070)
