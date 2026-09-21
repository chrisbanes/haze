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
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass as typedHazeGlass
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs
import kotlin.math.floor

/** Protects valid edge RGB/alpha while exercising expanded and root-clipped layer geometry. */
internal fun ScreenshotUiTest.assertGlassNonuniformEdgeContentIsPreserved() {
  val hazeState = HazeState()
  var performanceMode by mutableStateOf(HazePerformanceMode.Quality)
  var matte by mutableStateOf(Color.Black)
  var contentBounds: Rect? = null
  var sourcesBounds: Rect? = null
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
    edgeSoftness(8.dp)
    shape(RoundedCornerShape(0.dp))
  }

  setContent {
    Box(Modifier.fillMaxSize().background(matte)) {
      Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
        drawRect(EDGE_BASE)
        drawRect(
          color = EDGE_STRIPE,
          topLeft = Offset(90.dp.toPx() - EDGE_STRIPE_WIDTH_PX, 0f),
          size = androidx.compose.ui.geometry.Size(EDGE_STRIPE_WIDTH_PX, size.height),
        )
      }
      val contentGlass = Modifier
        .offset(x = (-6).dp, y = 37.dp)
        .size(width = 96.dp, height = 64.dp)
        .onGloballyPositioned { coordinates ->
          contentBounds = runCatching { coordinates.boundsInRoot() }.getOrNull() ?: contentBounds
        }
        .typedHazeGlass(
          input = HazeInput.Content,
          style = style,
          performanceMode = performanceMode,
        )
      Box(contentGlass) {
        Canvas(Modifier.fillMaxSize()) {
          drawRect(EDGE_BASE)
          drawRect(
            color = EDGE_STRIPE,
            topLeft = Offset(size.width - EDGE_STRIPE_WIDTH_PX, 0f),
            size = androidx.compose.ui.geometry.Size(EDGE_STRIPE_WIDTH_PX, size.height),
          )
        }
      }
      Box(
        Modifier
          .offset(x = (-6).dp, y = 120.dp)
          .size(width = 96.dp, height = 64.dp)
          .onGloballyPositioned { coordinates ->
            sourcesBounds = runCatching { coordinates.boundsInRoot() }.getOrNull() ?: sourcesBounds
          }
          .typedHazeGlass(
            input = HazeInput.Sources(hazeState),
            style = style,
            performanceMode = performanceMode,
          ),
      )
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
  listOf("Content" to checkNotNull(contentBounds), "Sources" to checkNotNull(sourcesBounds))
    .forEach { (edgeInput, bounds) ->
      val x = floor(bounds.right).toInt() - 2
      val y = floor(bounds.center.y).toInt()
      val difference = quality[x, y].maximumChannelDifference(reduced[x, y])
      println(
        "Glass $edgeInput edge probe at ($x,$y): quality=${quality[x, y]}, " +
          "reduced=${reduced[x, y]}, difference=$difference, bounds=$bounds",
      )
      assertThat(difference, "$edgeInput valid edge RGB/alpha difference")
        // The three-pixel stripe is narrower than two reduced samples; allow their fixed
        // interpolation budget while still rejecting replacement by the inset base colour.
        .isLessThanOrEqualTo(24f / 255f)
    }
}

private fun Color.maximumChannelDifference(other: Color): Float = maxOf(
  abs(red - other.red),
  abs(green - other.green),
  abs(blue - other.blue),
  abs(alpha - other.alpha),
)

private val EDGE_BASE = Color(0xE6F07824)
private val EDGE_STRIPE = Color(0x5918D9A4)
private const val EDGE_STRIPE_WIDTH_PX = 3f
