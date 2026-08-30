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
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotUiTest
import kotlin.math.abs

// Android and Skiko both differ by 102/255 at the independently-rasterized effect clip.
// Keep a two-code-value margin so the clip may antialias differently without allowing a
// visible rounded-boundary discontinuity.
private const val EFFECT_CLIP_ANTIALIASING_TOLERANCE = 104f / 255f
private const val CONTENT_CLIP_ANTIALIASING_TOLERANCE = 64f / 255f

private enum class RoundedEdgeClipPlacement {
  InternalMaskOnly,
  AroundEffect,
  AroundContent,
}

internal fun ScreenshotUiTest.assertGlassRoundedEdgePixelsAreContinuous() {
  val shape = RoundedCornerShape(31.dp)
  val style = GlassStyle {
    tint(Color.White.copy(alpha = 0.8f))
    optics(
      GlassOptics(
        refractionStrength = 0f,
        depth = OpticalSizeValue.Fixed(0f),
        blurRadius = OpticalSizeValue.Fixed(0.dp),
      ),
    )
    specularIntensity(0f)
    ambientResponse(0f)
    edgeSoftness(0.dp)
    this.shape(shape)
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
          RoundedEdgeClipPlacement.InternalMaskOnly -> effectModifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = style,
            performanceMode = HazePerformanceMode.Quality,
          )
          RoundedEdgeClipPlacement.AroundEffect -> effectModifier.clip(shape).hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = style,
            performanceMode = HazePerformanceMode.Quality,
          )
          RoundedEdgeClipPlacement.AroundContent -> effectModifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = style,
            performanceMode = HazePerformanceMode.Quality,
          ).clip(shape)
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

  assertRoundedEdgeContinuity(
    internalMaskOnly = internalMaskOnly,
    effectClip = effectClip,
    contentClip = contentClip,
  )
}

internal fun assertRoundedEdgeContinuity(
  internalMaskOnly: PixelSnapshot,
  effectClip: PixelSnapshot,
  contentClip: PixelSnapshot,
) {
  val effectClipEdgeDifference = internalMaskOnly.maximumChannelDifference(effectClip)
  val contentClipEdgeDifference = internalMaskOnly.maximumChannelDifference(contentClip)
  println(
    "Glass rounded-edge maximum channel differences: " +
      "effectClip=$effectClipEdgeDifference, " +
      "contentClip=$contentClipEdgeDifference",
  )

  // The effect's SDF owns material coverage, so an independently-rasterized clip around the
  // effect may only contribute its calibrated antialiasing difference. A later content clip is
  // measured and asserted independently because it constrains foreground content instead.
  assertThat(effectClipEdgeDifference, name = "effect clip edge difference")
    .isLessThanOrEqualTo(EFFECT_CLIP_ANTIALIASING_TOLERANCE)
  assertThat(contentClipEdgeDifference, name = "content clip edge difference")
    .isLessThanOrEqualTo(CONTENT_CLIP_ANTIALIASING_TOLERANCE)
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
