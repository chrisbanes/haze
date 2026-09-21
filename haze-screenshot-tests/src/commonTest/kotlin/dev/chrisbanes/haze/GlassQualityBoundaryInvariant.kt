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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val SOURCE_ALPHA = 0.8f
private const val GROUP_ALPHA = 0.5f
private const val EXPECTED_INTERIOR_ALPHA = 102
private const val CROP_PADDING_PX = 4

internal data class AlphaSnapshot(
  val width: Int,
  val height: Int,
  val alpha: ByteArray,
) {
  init {
    require(width >= 0 && height >= 0)
    require(alpha.size == width * height)
  }

  operator fun get(index: Int): Int = alpha[index].toInt() and 0xff
}

internal class FrozenQualityBoundary(
  val revision: String,
  val platform: String,
  val density: Float,
  val rootWidth: Int,
  val rootHeight: Int,
  val crop: IntRect,
  val materialBounds: String,
  val transform: String,
  val geometry: String,
  encodedAlpha: String,
) {
  val alpha: AlphaSnapshot = decodeAlphaRle(crop.width, crop.height, encodedAlpha)
}

private class GlassBoundaryCapture(
  val candidate: AlphaSnapshot,
  val composeDiagnostic: AlphaSnapshot,
  val density: Float,
  val rootWidth: Int,
  val rootHeight: Int,
  val crop: IntRect,
  val materialBounds: Rect,
  val interiorIndex: Int,
)

internal fun ScreenshotUiTest.assertGlassOutputCoverageMatchesQualityBoundary(
  performanceMode: HazePerformanceMode,
  reference: FrozenQualityBoundary,
) {
  val capture = captureGlassQualityBoundary(performanceMode)
  require(reference.revision == "a9bc783540bd9dec472bab66057b4601586f09a3")
  require(reference.rootWidth == capture.rootWidth && reference.rootHeight == capture.rootHeight) {
    "${reference.platform} root changed from ${reference.rootWidth}x${reference.rootHeight} " +
      "to ${capture.rootWidth}x${capture.rootHeight}"
  }
  require(abs(reference.density - capture.density) < 0.0001f) {
    "${reference.platform} density changed from ${reference.density} to ${capture.density}"
  }
  require(reference.crop == capture.crop) {
    "${reference.platform} crop changed from ${reference.crop} to ${capture.crop}; " +
      "material=${capture.materialBounds}"
  }

  assertGlassOutputCoverageMatchesQualityBoundary(
    candidate = capture.candidate,
    reference = reference.alpha,
    expectedInteriorAlpha = EXPECTED_INTERIOR_ALPHA,
    interiorIndex = capture.interiorIndex,
  )
  reportComposeCompatibility(
    performanceMode = performanceMode,
    glass = capture.candidate,
    compose = capture.composeDiagnostic,
    crop = capture.crop,
  )
}

private fun ScreenshotUiTest.captureGlassQualityBoundary(
  performanceMode: HazePerformanceMode,
): GlassBoundaryCapture {
  val shape = RoundedCornerShape(
    topStart = 37.dp,
    topEnd = 13.dp,
    bottomEnd = 29.dp,
    bottomStart = 7.dp,
  )
  val effect = neutralBoundaryConfiguration(shape)
  var showComposeDiagnostic by mutableStateOf(false)
  var matte by mutableStateOf(Color.Black)
  var materialBounds: Rect? = null
  var density = 0f

  setContent {
    density = LocalDensity.current.density
    Box(
      Modifier
        .fillMaxSize()
        .background(matte),
      contentAlignment = Alignment.Center,
    ) {
      val material = Modifier
        .size(width = 217.5.dp, height = 103.5.dp)
        .graphicsLayer {
          translationX = 0.5f
          translationY = 0.5f
        }
        .onGloballyPositioned { materialBounds = it.boundsInRoot() }
      val boundary = if (showComposeDiagnostic) {
        material
          .clip(shape)
          .graphicsLayer { alpha = GROUP_ALPHA }
      } else {
        material.hazeGlass(
          input = HazeInput.Content,
          configuration = effect,
          performanceMode = performanceMode,
        )
      }
      Box(boundary) {
        Canvas(Modifier.fillMaxSize()) {
          drawRect(Color.White.copy(alpha = SOURCE_ALPHA))
        }
      }
    }
  }
  waitForIdle()

  matte = Color.Black
  waitForIdle()
  val candidateOverBlack = captureRootPixels().snapshot()
  matte = Color.White
  waitForIdle()
  val candidateOverWhite = captureRootPixels().snapshot()
  val candidateRoot = recoverPremultipliedSnapshot(candidateOverBlack, candidateOverWhite)
  val bounds = checkNotNull(materialBounds)
  val crop = bounds.toPaddedIntRect(candidateRoot.width, candidateRoot.height)
  val candidate = candidateRoot.alphaSnapshot(crop)
  val interiorX = floor(bounds.center.x).toInt() - crop.left
  val interiorY = floor(bounds.center.y).toInt() - crop.top
  val interiorIndex = interiorY * crop.width + interiorX

  showComposeDiagnostic = true
  matte = Color.Black
  waitForIdle()
  val composeOverBlack = captureRootPixels().snapshot()
  matte = Color.White
  waitForIdle()
  val composeOverWhite = captureRootPixels().snapshot()
  val composeRoot = recoverPremultipliedSnapshot(composeOverBlack, composeOverWhite)

  return GlassBoundaryCapture(
    candidate = candidate,
    composeDiagnostic = composeRoot.alphaSnapshot(crop),
    density = density,
    rootWidth = candidateRoot.width,
    rootHeight = candidateRoot.height,
    crop = crop,
    materialBounds = bounds,
    interiorIndex = interiorIndex,
  )
}

private fun neutralBoundaryConfiguration(shape: RoundedCornerShape) =
  GlassTestConfiguration().apply {
    style = GlassStyle { edgeShadow(Color.Transparent) }
    optics = GlassOptics(
      refractionStrength = 0f,
      refractionDisplacement = 0.dp,
      depth = OpticalSizeValue.Fixed(0f),
      blurRadius = OpticalSizeValue.Fixed(0.dp),
      refractionProfile = RefractionProfile.Edge(0.dp),
    )
    tint = Color.Transparent
    specularIntensity = 0f
    ambientResponse = 0f
    contrast = 0f
    whitePoint = 0f
    chromaMultiplier = 1f
    contentNormalBlend = 0f
    edgeSoftness = 3.dp
    alpha = GROUP_ALPHA
    this.shape = shape
  }

internal fun assertGlassOutputCoverageMatchesQualityBoundary(
  candidate: AlphaSnapshot,
  reference: AlphaSnapshot,
  expectedInteriorAlpha: Int,
  interiorIndex: Int,
) {
  require(candidate.width == reference.width && candidate.height == reference.height)
  require(interiorIndex in candidate.alpha.indices)

  val candidateMaximum = candidate.alpha.maxOf { it.toInt() and 0xff }
  assertThat(candidateMaximum, "candidate has nonempty support").isGreaterThan(0)
  assertThat(
    abs(candidate[interiorIndex] - expectedInteriorAlpha),
    "candidate interior alpha",
  ).isLessThanOrEqualTo(1)

  val probe = candidate.maximumDifferenceFrom(reference)
  val candidateOutsideReference = candidate.maximumAlphaOutsideSupportOf(reference)
  val referenceOutsideCandidate = reference.maximumAlphaOutsideSupportOf(candidate)
  println(
    "Glass Quality-boundary probe: maxDifference=${probe.difference} at " +
      "(${probe.x},${probe.y}), candidate=${probe.candidate}, reference=${probe.reference}, " +
      "candidateOutsideReference=$candidateOutsideReference, " +
      "referenceOutsideCandidate=$referenceOutsideCandidate, " +
      "candidateNeighborhood=${candidate.alphaNeighborhood(probe.x, probe.y)}, " +
      "referenceNeighborhood=${reference.alphaNeighborhood(probe.x, probe.y)}",
  )

  assertThat(probe.difference, "maximum alpha difference").isLessThanOrEqualTo(2)
  assertThat(candidateOutsideReference, "candidate alpha outside reference support")
    .isLessThanOrEqualTo(1)
  assertThat(referenceOutsideCandidate, "reference alpha outside candidate support")
    .isLessThanOrEqualTo(1)
}

private class AlphaDifferenceProbe(
  val difference: Int,
  val x: Int,
  val y: Int,
  val candidate: Int,
  val reference: Int,
)

private fun AlphaSnapshot.maximumDifferenceFrom(reference: AlphaSnapshot): AlphaDifferenceProbe {
  require(width == reference.width && height == reference.height)
  var result = AlphaDifferenceProbe(0, 0, 0, this[0], reference[0])
  for (index in alpha.indices) {
    val difference = abs(this[index] - reference[index])
    if (difference > result.difference) {
      result = AlphaDifferenceProbe(
        difference = difference,
        x = index % width,
        y = index / width,
        candidate = this[index],
        reference = reference[index],
      )
    }
  }
  return result
}

private fun AlphaSnapshot.maximumAlphaOutsideSupportOf(other: AlphaSnapshot): Int {
  require(width == other.width && height == other.height)
  var maximum = 0
  for (index in alpha.indices) {
    if (other[index] == 0) maximum = maxOf(maximum, this[index])
  }
  return maximum
}

private fun AlphaSnapshot.alphaNeighborhood(x: Int, y: Int): String = buildString {
  for (candidateY in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
    if (isNotEmpty()) append('/')
    for (candidateX in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
      if (candidateX > maxOf(0, x - 1)) append(',')
      append(this@alphaNeighborhood[candidateY * width + candidateX])
    }
  }
}

private fun reportComposeCompatibility(
  performanceMode: HazePerformanceMode,
  glass: AlphaSnapshot,
  compose: AlphaSnapshot,
  crop: IntRect,
) {
  val probe = glass.maximumDifferenceFrom(compose)
  println(
    "Glass Compose compatibility diagnostic for $performanceMode: " +
      "maxDifference=${probe.difference} at (${crop.left + probe.x},${crop.top + probe.y}), " +
      "glass=${probe.candidate}, compose=${probe.reference}, " +
      "glassOutsideCompose=${glass.maximumAlphaOutsideSupportOf(compose)}, " +
      "composeOutsideGlass=${compose.maximumAlphaOutsideSupportOf(glass)}",
  )
}

private fun Rect.toPaddedIntRect(rootWidth: Int, rootHeight: Int): IntRect = IntRect(
  left = (floor(left).toInt() - CROP_PADDING_PX).coerceAtLeast(0),
  top = (floor(top).toInt() - CROP_PADDING_PX).coerceAtLeast(0),
  right = (ceil(right).toInt() + CROP_PADDING_PX).coerceAtMost(rootWidth),
  bottom = (ceil(bottom).toInt() + CROP_PADDING_PX).coerceAtMost(rootHeight),
)

private fun PixelSnapshot.alphaSnapshot(bounds: IntRect): AlphaSnapshot =
  AlphaSnapshot(
    width = bounds.width,
    height = bounds.height,
    alpha = ByteArray(bounds.width * bounds.height) { index ->
      val x = bounds.left + index % bounds.width
      val y = bounds.top + index / bounds.width
      (this[x, y].alpha * 255f).roundToInt().coerceIn(0, 255).toByte()
    },
  )

private fun decodeAlphaRle(width: Int, height: Int, encoded: String): AlphaSnapshot {
  val values = ByteArray(width * height)
  var index = 0
  if (encoded.isNotEmpty()) {
    encoded.split(',').forEach { run ->
      val separator = run.indexOf(':')
      require(separator > 0)
      val count = run.substring(0, separator).toInt()
      val value = run.substring(separator + 1).toInt().coerceIn(0, 255).toByte()
      repeat(count) {
        require(index < values.size)
        values[index++] = value
      }
    }
  }
  require(index == values.size) { "Frozen alpha run length decoded $index of ${values.size} pixels" }
  return AlphaSnapshot(width, height, values)
}
