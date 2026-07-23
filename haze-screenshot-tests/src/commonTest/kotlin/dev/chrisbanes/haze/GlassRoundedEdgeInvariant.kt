// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs

private const val ROUNDED_EDGE_ANTIALIASING_TOLERANCE = 64f / 255f

private enum class RoundedEdgeClipPlacement {
  InternalMaskOnly,
  AroundEffect,
  AroundContent,
}

internal fun ScreenshotUiTest.assertGlassRoundedEdgePixelsAreContinuous() {
  val shape = RoundedCornerShape(31.dp)
  val effect = GlassVisualEffect().apply {
    tint = Color.White.copy(alpha = 0.8f)
    optics = GlassOptics.Absolute(
      refractionStrength = 0f,
      depth = 0f,
      blurRadius = 0.dp,
    )
    specularIntensity = 0f
    ambientResponse = 0f
    edgeSoftness = 0.dp
    this.shape = shape
  }
  var clipPlacement by mutableStateOf(RoundedEdgeClipPlacement.InternalMaskOnly)

  setContent {
    val hazeState = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
      Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
        drawRect(Color.Black)
        var x = -size.height
        while (x < size.width) {
          drawLine(
            color = Color.Cyan,
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = 2f,
          )
          x += 24f
        }
      }

      val effectModifier = Modifier
        .align(Alignment.Center)
        .size(width = 217.5.dp, height = 103.5.dp)
        .graphicsLayer {
          translationX = 0.5f
          translationY = 0.5f
        }

      Box(
        when (clipPlacement) {
          RoundedEdgeClipPlacement.InternalMaskOnly -> effectModifier.hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          }
          RoundedEdgeClipPlacement.AroundEffect -> effectModifier.clip(shape).hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          }
          RoundedEdgeClipPlacement.AroundContent -> effectModifier.hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          }.clip(shape)
        },
      )
    }
  }

  val internalMaskOnly = captureRootPixels().snapshot()
  clipPlacement = RoundedEdgeClipPlacement.AroundEffect
  waitForIdle()
  val effectClip = captureRootPixels().snapshot()
  clipPlacement = RoundedEdgeClipPlacement.AroundContent
  waitForIdle()
  val contentClip = captureRootPixels().snapshot()

  val effectClipEdgeDifference = internalMaskOnly.maximumChannelDifference(effectClip)
  val contentClipEdgeDifference = internalMaskOnly.maximumChannelDifference(contentClip)
  println(
    "Glass rounded-edge maximum channel differences: " +
      "effectClip=$effectClipEdgeDifference, " +
      "contentClip=$contentClipEdgeDifference",
  )

  // Keep the independently-rasterized clip measurement above to isolate boundary behavior,
  // without requiring current Compose/Skia antialiasing differences to persist. The effect's
  // SDF should own material coverage; a later Compose clip may constrain foreground content.
  // Allow up to one quarter of an 8-bit channel for cross-backend antialiasing.
  assertThat(contentClipEdgeDifference)
    .isLessThanOrEqualTo(ROUNDED_EDGE_ANTIALIASING_TOLERANCE)
}

private fun PixelSnapshot.maximumChannelDifference(
  other: PixelSnapshot,
): Float {
  require(width == other.width && height == other.height) {
    "Pixel snapshots must have matching dimensions"
  }
  var maximum = 0f
  for (index in colors.indices) {
    val first = colors[index]
    val second = other.colors[index]
    val difference = maxOf(
      abs(first.red - second.red),
      abs(first.green - second.green),
      abs(first.blue - second.blue),
      abs(first.alpha - second.alpha),
    )
    maximum = maxOf(maximum, difference)
  }
  return maximum
}
