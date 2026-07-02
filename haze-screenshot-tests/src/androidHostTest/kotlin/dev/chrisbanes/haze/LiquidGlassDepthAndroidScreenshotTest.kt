// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

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
import dev.chrisbanes.haze.liquidglass.LiquidGlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35])
class LiquidGlassDepthAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun liquidGlass_depthProgression() = runScreenshotTest(relaxedTolerance = true) {
    val shape = RoundedCornerShape(28.dp)
    val visualEffects = listOf(0f, 0.5f, 1f).map { depth ->
      LiquidGlassVisualEffect().apply {
        tint = Color.Transparent
        refractionStrength = 0f
        this.depth = depth
        blurRadius = 32.dp
        specularIntensity = 0f
        ambientResponse = 0f
        edgeSoftness = 0.dp
        this.shape = shape
      }
    }

    setContent {
      ScreenshotTheme {
        LiquidGlassDepthComparisonSample(
          visualEffects = visualEffects,
          shape = shape,
        )
      }
    }

    captureRoot("comparison")
  }
}

@Composable
private fun LiquidGlassDepthComparisonSample(
  visualEffects: List<LiquidGlassVisualEffect>,
  shape: RoundedCornerShape,
  cardWidth: Dp = 94.dp,
  cardHeight: Dp = 440.dp,
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
      horizontalArrangement = Arrangement.SpaceBetween,
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

private val DepthStripeColors = listOf(
  Color(0xFF061A40),
  Color(0xFFFFD166),
  Color(0xFF118AB2),
  Color(0xFFEF476F),
  Color(0xFF06D6A0),
  Color(0xFFFFFFFF),
)
