// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test

class GlassBuiltInStyleScreenshotTest : ScreenshotTest() {

  @Test
  fun builtInStyles_blurRespondsToMaterialSize() = runScreenshotTest(
    size = Size(1080f, 1600f),
  ) {
    var style by mutableStateOf<GlassStyle?>(null)
    setContent { GlassBuiltInStyleGeometrySample(style) }
    waitForIdle()

    val regions = geometryRegions()
    val identity = captureRootPixels().snapshot()

    style = GlassStyle.regular
    waitForIdle()
    val regular = captureRootPixels().snapshot()
    captureRoot("regular")

    style = GlassStyle.clear
    waitForIdle()
    val clear = captureRootPixels().snapshot()
    captureRoot("clear", unmatchedPixelThreshold = 0.01f)

    if (isRuntimeShaderRenderEffectSupported()) {
      val regularRetention = regions.mapValues { (_, bounds) ->
        regular.highFrequencyEnergy(bounds) / identity.highFrequencyEnergy(bounds)
      }
      val clearRetention = regions.mapValues { (_, bounds) ->
        clear.highFrequencyEnergy(bounds) / identity.highFrequencyEnergy(bounds)
      }
      println("Regular high-frequency retention: $regularRetention")
      println("Clear high-frequency retention: $clearRetention")

      assertSizeProgression(regularRetention, "Regular")
      assertSizeProgression(clearRetention, "Clear")
      regions.keys.forEach { region ->
        assertThat(regularRetention.getValue(region), "$region Regular retention")
          .isLessThan(clearRetention.getValue(region))
      }
      assertThat(clearRetention.getValue("capsule"), "Clear capsule retention")
        .isGreaterThan(0.8f)
    }
  }
}

private fun ScreenshotUiTest.geometryRegions(): Map<String, IntRect> = mapOf(
  "capsule" to insetBounds("capsule", inset = 16.dp, logicalHeight = 64.dp),
  "card" to insetBounds("card", inset = 24.dp, logicalHeight = 176.dp),
  "panel" to insetBounds("panel", inset = 24.dp, logicalHeight = 220.dp),
)

private fun ScreenshotUiTest.insetBounds(
  tag: String,
  inset: Dp,
  logicalHeight: Dp,
): IntRect {
  val bounds = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
  val density = bounds.height / logicalHeight.value
  return bounds.inflate(-inset.value * density).toIntRect()
}

private fun Rect.toIntRect(): IntRect = IntRect(
  left = floor(left).toInt(),
  top = floor(top).toInt(),
  right = ceil(right).toInt(),
  bottom = ceil(bottom).toInt(),
)

private fun assertSizeProgression(retention: Map<String, Float>, label: String) {
  assertThat(retention.getValue("capsule"), "$label capsule retention")
    .isGreaterThan(retention.getValue("card"))
  assertThat(retention.getValue("card"), "$label card retention")
    .isGreaterThan(retention.getValue("panel"))
}

@Composable
private fun GlassBuiltInStyleGeometrySample(style: GlassStyle?) {
  val hazeState = remember { HazeState() }

  Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      drawRect(Color(0xFF07141A))
      val spacing = 8.dp.toPx()
      val strokeWidth = 1.dp.toPx()
      var x = 0f
      while (x < size.width) {
        drawLine(
          color = Color.White.copy(alpha = 0.8f),
          start = Offset(x, 0f),
          end = Offset(x, size.height),
          strokeWidth = strokeWidth,
        )
        x += spacing
      }
      var y = 0f
      while (y < size.height) {
        drawLine(
          color = Color.Cyan.copy(alpha = 0.65f),
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = strokeWidth,
        )
        y += spacing
      }
    }

    Column(
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      GlassBuiltInStyleSurface(
        tag = "capsule",
        width = 240.dp,
        height = 64.dp,
        shape = RoundedCornerShape(32.dp),
        style = style,
        hazeState = hazeState,
      )
      GlassBuiltInStyleSurface(
        tag = "card",
        width = 280.dp,
        height = 176.dp,
        shape = RoundedCornerShape(28.dp),
        style = style,
        hazeState = hazeState,
      )
      GlassBuiltInStyleSurface(
        tag = "panel",
        width = 320.dp,
        height = 220.dp,
        shape = RoundedCornerShape(32.dp),
        style = style,
        hazeState = hazeState,
      )
    }
  }
}

@Composable
private fun GlassBuiltInStyleSurface(
  tag: String,
  width: Dp,
  height: Dp,
  shape: RoundedCornerShape,
  style: GlassStyle?,
  hazeState: HazeState,
) {
  val modifier = Modifier
    .size(width, height)
    .testTag(tag)
  Box(
    modifier = if (style != null) {
      modifier.hazeGlass(
        input = HazeInput.Sources(hazeState),
        style = style.then { shape(shape) },
        performanceMode = HazePerformanceMode.Quality,
      )
    } else {
      modifier
    },
  )
}
