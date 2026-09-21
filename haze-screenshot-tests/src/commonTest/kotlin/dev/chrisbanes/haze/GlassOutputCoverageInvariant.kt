// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs
import kotlinx.coroutines.runBlocking

private const val ALPHA_CODE_VALUE = 1f / 255f

// Calibrated from the unchanged Quality path against the same-backend Compose clip with translucent
// source pixels present throughout the boundary. Skiko's two independent SDF rasterizers differ by
// 26 alpha code values; Android agrees exactly. Reduced modes may differ by one additional value.
internal const val DESKTOP_QUALITY_COMPOSE_ALPHA_TOLERANCE = 26f / 255f + 1e-6f
internal const val ANDROID_QUALITY_COMPOSE_ALPHA_TOLERANCE = 0f

/**
 * Compares one performance mode with the same-backend Quality and Compose-clip controls.
 *
 * The scene deliberately combines fractional geometry, asymmetric radii, a transparent root,
 * non-zero softness, group alpha, sharp detail, an active interaction-optics/lighting patch, and
 * non-zero source alpha throughout the material boundary. Assertions use independent Quality and
 * Compose-clip controls plus individual alpha/support pixels; whole-frame averages would hide the
 * one-pixel corner regression this protects.
 */
internal fun ScreenshotUiTest.assertGlassOutputCoverageMatchesComposeClip(
  performanceMode: HazePerformanceMode,
  qualityControlTolerance: Float,
) {
  val shape = RoundedCornerShape(
    topStart = 37.dp,
    topEnd = 13.dp,
    bottomEnd = 29.dp,
    bottomStart = 7.dp,
  )
  val interactionSource = MutableInteractionSource()
  val effect = GlassTestConfiguration().apply {
    optics = GlassOptics(
      refractionStrength = 0.8f,
      refractionDisplacement = 18.dp,
      depth = OpticalSizeValue.Fixed(0.65f),
      blurRadius = OpticalSizeValue.Fixed(8.dp),
      refractionProfile = RefractionProfile.Edge(7.dp),
    )
    tint = Color.White.copy(alpha = 0.22f)
    specularIntensity = 0.8f
    ambientResponse = 0.12f
    edgeSoftness = 3.dp
    alpha = 0.5f
    this.shape = shape
    this.interactionSource = interactionSource
    interactionLightRadiusFraction = 0.34f
    interactionPositionAnimationSpec = snap()
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    pressed {
      refractionMultiplier(1.12f)
      whitePointDelta(0.04f)
      lightingIntensity(0.9f)
    }
  }
  var mode by mutableStateOf<HazePerformanceMode>(HazePerformanceMode.Quality)
  var composeClip by mutableStateOf(false)

  setContent {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      val material = Modifier
        .size(width = 217.5.dp, height = 103.5.dp)
        .graphicsLayer {
          translationX = 0.5f
          translationY = 0.5f
        }
        .testTag("glass-output-coverage")
      val coverageControl = if (composeClip) material.clip(shape) else material
      val glass = coverageControl.hazeGlass(
        input = HazeInput.Content,
        configuration = effect,
        performanceMode = mode,
      )
      Box(glass) {
        Canvas(Modifier.fillMaxSize()) {
          drawRect(color = Color(0xCC20B8D8))
          drawRect(
            color = Color(0xFFF7C843),
            topLeft = Offset(size.width * 0.32f, size.height * 0.38f),
            size = Size(size.width * 0.36f, size.height * 0.24f),
          )
          drawCircle(
            color = Color(0xFFF06090),
            radius = size.minDimension * 0.09f,
            center = Offset(size.width * 0.58f, size.height * 0.5f),
          )
        }
      }
    }
  }
  runBlocking {
    interactionSource.emit(PressInteraction.Press(Offset(174f, 64f)))
  }
  waitForIdle()

  val quality = captureRootPixels().snapshot()
  composeClip = true
  waitForIdle()
  val qualityComposeClip = captureRootPixels().snapshot()

  mode = performanceMode
  composeClip = false
  waitForIdle()
  val candidate = captureRootPixels().snapshot()

  assertGlassOutputCoverageMatchesControls(
    performanceMode = performanceMode,
    candidate = candidate,
    quality = quality,
    composeClip = qualityComposeClip,
    qualityControlTolerance = qualityControlTolerance,
  )
}

internal fun assertGlassOutputCoverageMatchesControls(
  performanceMode: HazePerformanceMode,
  candidate: PixelSnapshot,
  quality: PixelSnapshot,
  composeClip: PixelSnapshot,
  qualityControlTolerance: Float,
) {
  val acceptedDifference = qualityControlTolerance + ALPHA_CODE_VALUE
  val qualityComposeDifference = quality.maximumAlphaDifference(composeClip)
  val candidateQualityDifference = candidate.maximumAlphaDifference(quality)
  val candidateComposeDifference = candidate.maximumAlphaDifference(composeClip)
  val candidateOutsideQuality = candidate.maximumAlphaOutsideSupportOf(quality)
  val qualityOutsideCandidate = quality.maximumAlphaOutsideSupportOf(candidate)
  val candidateOutsideCompose = candidate.maximumAlphaOutsideSupportOf(composeClip)
  val composeOutsideCandidate = composeClip.maximumAlphaOutsideSupportOf(candidate)
  val candidateMatchesQualitySupport = candidate.hasSupportOnlyWithinOnePixelOf(quality) &&
    quality.hasSupportOnlyWithinOnePixelOf(candidate)
  val candidateMatchesComposeSupport = candidate.hasSupportOnlyWithinOnePixelOf(composeClip) &&
    composeClip.hasSupportOnlyWithinOnePixelOf(candidate)
  println(
    "Glass output-coverage probes for $performanceMode: " +
      "qualityCompose=$qualityComposeDifference, candidateQuality=$candidateQualityDifference, " +
      "candidateCompose=$candidateComposeDifference, " +
      "outsideQuality=($candidateOutsideQuality,$qualityOutsideCandidate), " +
      "outsideCompose=($candidateOutsideCompose,$composeOutsideCandidate), " +
      "support=(quality=$candidateMatchesQualitySupport,compose=$candidateMatchesComposeSupport)",
  )

  assertThat(qualityComposeDifference, "Quality-to-Compose alpha control")
    .isLessThanOrEqualTo(qualityControlTolerance)
  assertThat(candidateQualityDifference, "$performanceMode-to-Quality alpha difference")
    .isLessThanOrEqualTo(acceptedDifference)
  assertThat(candidateComposeDifference, "$performanceMode-to-Compose alpha difference")
    .isLessThanOrEqualTo(acceptedDifference)
  assertThat(candidateOutsideQuality, "$performanceMode alpha outside Quality support")
    .isLessThanOrEqualTo(acceptedDifference)
  assertThat(qualityOutsideCandidate, "Quality alpha outside $performanceMode support")
    .isLessThanOrEqualTo(acceptedDifference)
  assertThat(candidateOutsideCompose, "$performanceMode alpha outside Compose support")
    .isLessThanOrEqualTo(acceptedDifference)
  assertThat(composeOutsideCandidate, "Compose alpha outside $performanceMode support")
    .isLessThanOrEqualTo(acceptedDifference)
  assertThat(candidateMatchesQualitySupport, "$performanceMode-to-Quality support displacement")
    .isTrue()
  assertThat(candidateMatchesComposeSupport, "$performanceMode-to-Compose support displacement")
    .isTrue()
}

private fun PixelSnapshot.maximumAlphaDifference(other: PixelSnapshot): Float {
  require(width == other.width && height == other.height)
  return colors.indices.maxOf { index -> abs(colors[index].alpha - other.colors[index].alpha) }
}

private fun PixelSnapshot.maximumAlphaOutsideSupportOf(other: PixelSnapshot): Float {
  require(width == other.width && height == other.height)
  var maximum = 0f
  for (index in colors.indices) {
    if (other.colors[index].alpha <= 0f) {
      maximum = maxOf(maximum, colors[index].alpha)
    }
  }
  return maximum
}

private fun PixelSnapshot.hasSupportOnlyWithinOnePixelOf(other: PixelSnapshot): Boolean {
  require(width == other.width && height == other.height)
  for (index in colors.indices) {
    if (colors[index].alpha <= ALPHA_CODE_VALUE) continue
    val x = index % width
    val y = index / width
    var supported = false
    for (candidateY in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
      for (candidateX in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
        if (other.colors[candidateY * width + candidateX].alpha > 0f) {
          supported = true
        }
      }
    }
    if (!supported) return false
  }
  return true
}
