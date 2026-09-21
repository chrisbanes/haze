// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass as typedHazeGlass
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs
import kotlin.math.floor

internal fun ScreenshotUiTest.assertGlassIsolatedTranslucentEdgeAlphaPreserved() {
  var performanceMode by mutableStateOf(HazePerformanceMode.Quality)
  var matte by mutableStateOf(Color.Black)
  var glassBounds: Rect? = null
  val style = GlassStyle {
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
    specularIntensity(0f)
    edgeShadow(Color.Transparent)
    ambientResponse(0f)
    contrast(0f)
    whitePoint(0f)
    chromaMultiplier(1f)
    contentNormalBlend(0f)
    edgeSoftness(0.dp)
    shape(RoundedCornerShape(0.dp))
  }

  setContent {
    Box(Modifier.fillMaxSize().background(matte)) {
      Box(
        Modifier
          .offset(x = 40.dp, y = 40.dp)
          .size(width = 96.dp, height = 64.dp)
          .onGloballyPositioned { coordinates ->
            glassBounds = runCatching { coordinates.boundsInRoot() }.getOrNull() ?: glassBounds
          }
          .typedHazeGlass(
            input = HazeInput.Content,
            style = style,
            performanceMode = performanceMode,
          ),
      ) {
        Canvas(Modifier.fillMaxSize()) {
          drawRect(
            color = ISOLATED_EDGE_STRIPE,
            topLeft = Offset(size.width - ISOLATED_EDGE_STRIPE_WIDTH_PX, 0f),
            size = androidx.compose.ui.geometry.Size(
              ISOLATED_EDGE_STRIPE_WIDTH_PX,
              size.height,
            ),
          )
        }
      }
    }
  }
  waitForIdle()

  fun capture(mode: HazePerformanceMode): PixelSnapshot {
    performanceMode = mode
    matte = Color.Black
    waitForIdle()
    val overBlack = captureRootPixels().snapshot()
    matte = Color.White
    waitForIdle()
    val overWhite = captureRootPixels().snapshot()
    return recoverPremultipliedSnapshot(overBlack, overWhite)
  }

  val quality = capture(HazePerformanceMode.Quality)
  val reduced = capture(HazePerformanceMode.Performance)
  val bounds = checkNotNull(glassBounds)
  val edgeX = floor(bounds.right).toInt() - 2
  val transparentX = floor(bounds.left).toInt() + 16
  val y = floor(bounds.center.y).toInt()
  println(
    "Glass isolated edge alpha probes: quality=${quality[edgeX, y].alpha}, " +
      "reduced=${reduced[edgeX, y].alpha}, " +
      "qualityTransparent=${quality[transparentX, y].alpha}, " +
      "reducedTransparent=${reduced[transparentX, y].alpha}, bounds=$bounds",
  )
  assertIsolatedEdgeAlphaPreserved(
    reference = quality[edgeX, y],
    candidate = reduced[edgeX, y],
    transparentReference = quality[transparentX, y],
    transparentCandidate = reduced[transparentX, y],
  )
}

internal fun assertIsolatedEdgeAlphaPreserved(
  reference: Color,
  candidate: Color,
  transparentReference: Color,
  transparentCandidate: Color,
) {
  assertThat(reference.alpha, "reference edge alpha")
    .isGreaterThanOrEqualTo(64f / 255f)
  assertThat(reference.alpha, "reference edge alpha")
    .isLessThanOrEqualTo(128f / 255f)
  assertThat(transparentReference.alpha, "reference transparent-region alpha")
    .isLessThanOrEqualTo(2f / 255f)
  assertThat(transparentCandidate.alpha, "candidate transparent-region alpha")
    .isLessThanOrEqualTo(2f / 255f)
  assertThat(abs(reference.alpha - candidate.alpha), "edge alpha difference")
    // Keep this independent from the wider RGB interpolation budget. It covers one reduced-edge
    // filter step plus dual-matte quantization while rejecting a collapsed transparent sample.
    .isLessThanOrEqualTo(16f / 255f)
}

private val ISOLATED_EDGE_STRIPE = Color(0x5918D9A4)
private const val ISOLATED_EDGE_STRIPE_WIDTH_PX = 3f
