// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun glassDepthProgressionVisualEffect(
  depth: Float,
  shape: RoundedCornerShape,
): GlassVisualEffect {
  return GlassVisualEffect().apply {
    tint = Color.White.copy(alpha = 0.12f)
    optics = GlassOptics.Absolute(depth = depth, blurRadius = 32.dp)
    specularIntensity = 0.4f
    ambientResponse = 0.5f
    edgeSoftness = 8.dp
    this.shape = shape
  }
}

internal fun ScreenshotUiTest.assertGlassDepthProgression() {
  val shape = RoundedCornerShape(28.dp)
  val visualEffect = glassDepthProgressionVisualEffect(depth = 0f, shape = shape)

  setContent {
    ScreenshotTheme {
      GlassDepthSingleSample(
        visualEffect = visualEffect,
        shape = shape,
      )
    }
  }

  val depth0 = captureRootPixels().snapshot()

  visualEffect.updateAbsoluteOptics { copy(depth = 0.5f) }
  waitForIdle()
  val depth50 = captureRootPixels().snapshot()

  visualEffect.updateAbsoluteOptics { copy(depth = 1f) }
  waitForIdle()
  val depth100 = captureRootPixels().snapshot()

  assertDepthProgression(depth0, depth50, depth100)

  listOf(0f to "0", 0.5f to "50", 1f to "100").forEach { (depth, snapshotName) ->
    visualEffect.updateAbsoluteOptics { copy(depth = depth) }
    waitForIdle()
    captureRoot(snapshotName)
  }
}

@Composable
internal fun GlassDepthComparisonSample(
  visualEffects: List<GlassVisualEffect>,
  shape: RoundedCornerShape,
  cardWidth: Dp = 94.dp,
  cardHeight: Dp = 440.dp,
  horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
) {
  val hazeState = remember { HazeState() }

  Box(Modifier.fillMaxSize()) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState, zIndex = 0f),
    ) {
      drawRect(Color(0xFF101820))

      val stripeWidth = 16.dp.toPx()
      var x = 0f
      var stripeIndex = 0
      while (x < size.width) {
        drawRect(
          color = DepthStripeColors[stripeIndex % DepthStripeColors.size],
          topLeft = Offset(x, 0f),
          size = Size(stripeWidth, size.height),
        )
        x += stripeWidth
        stripeIndex++
      }

      val lineSpacing = 28.dp.toPx()
      val strokeWidth = 4.dp.toPx()
      var y = 0f
      while (y < size.height) {
        drawLine(
          color = Color.Black.copy(alpha = 0.7f),
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = strokeWidth,
        )
        y += lineSpacing
      }
    }

    Row(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 18.dp, vertical = 84.dp),
      horizontalArrangement = horizontalArrangement,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      visualEffects.forEach { visualEffect ->
        Box(
          modifier = Modifier
            .size(width = cardWidth, height = cardHeight)
            .clip(shape)
            .hazeEffect(state = hazeState) {
              this.visualEffect = visualEffect
            }
            .border(width = 1.dp, color = Color(0x66000000), shape = shape),
        )
      }
    }
  }
}

@Composable
internal fun GlassDepthSingleSample(
  visualEffect: GlassVisualEffect,
  shape: RoundedCornerShape,
  cardWidth: Dp = 280.dp,
  cardHeight: Dp = 440.dp,
) {
  GlassDepthComparisonSample(
    visualEffects = listOf(visualEffect),
    shape = shape,
    cardWidth = cardWidth,
    cardHeight = cardHeight,
    horizontalArrangement = Arrangement.Center,
  )
}

private val DepthStripeColors = listOf(
  Color(0xFF061A40),
  Color(0xFFFFD166),
  Color(0xFF118AB2),
  Color(0xFFEF476F),
  Color(0xFF06D6A0),
  Color(0xFFFFFFFF),
)
