// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class, com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.glass.ContinuousRoundedCornerShape
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.LocalGlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.test.HazeRoborazziDefaults
import dev.chrisbanes.haze.test.ScreenshotUiTest
import haze_root.haze_screenshot_tests.generated.resources.Res
import haze_root.haze_screenshot_tests.generated.resources.photo
import org.jetbrains.compose.resources.painterResource

internal fun candidateStyles(
  direction: Int,
  clear: Boolean,
  dark: Boolean,
  before: GlassStyle = GlassStyle,
  after: GlassStyle = GlassStyle,
): Pair<GlassStyle, GlassStyle> {
  val seed = if (direction <= 0) GlassStyle else GlassStyle { shape(ContinuousRoundedCornerShape(16.dp, 1f)) }
  val builtIn = if (clear) GlassStyle.clear else GlassStyle.regular
  val material = if (direction <= 0) {
    builtIn
  } else {
    builtIn.then {
      val c1 = direction == 1 || direction == 3
      if (direction < 3) {
        edgeOutline(Color.Black.copy(alpha = if (c1) 0.4f else 0.25f), 0.7.dp)
        rimHighlight(
          if (clear) {
            if (c1) 1f else 0.8f
          } else {
            if (c1) 0.6f else 0.45f
          },
          1.dp,
          0f,
        )
      }
      if (clear) {
        specularIntensity(0.55f)
        edgeShadow(Color.Black.copy(alpha = 0.1f))
        ambientResponse(0.42f)
        contrast(0.08f)
        whitePoint(0.17f)
        chromaMultiplier(1.05f)
      } else if (dark) {
        whitePoint(if (c1) -0.10f else 0f)
        contrast(if (c1) 0.02f else -0.04f)
      }
    }
  }
  return seed to before.then(material).then(after)
}

internal fun candidateAppearance(dark: Boolean): GlassTestSystemAppearance =
  if (dark) GlassTestSystemAppearance.Dark else GlassTestSystemAppearance.Light

internal fun PixelSnapshot.surfaceDifference(other: PixelSnapshot, bounds: IntRect): Float {
  var sum = 0f
  for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
    val a = this[x, y]
    val b = other[x, y]
    sum += (kotlin.math.abs(a.red - b.red) + kotlin.math.abs(a.green - b.green) + kotlin.math.abs(a.blue - b.blue)) / 3f
  }
  return sum / (bounds.width * bounds.height)
}

internal fun ScreenshotUiTest.captureCandidateCase(
  profile: String,
  clear: Boolean,
  merged: Boolean,
  mode: HazePerformanceMode = HazePerformanceMode.Quality,
  photograph: Boolean = false,
  expectBridge: Boolean = true,
  observeCanvas: DrawScope.() -> Unit = {},
) {
  val direction = mutableStateOf(-1)
  val dark = mutableStateOf(false)
  setContent {
    WithGlassTestSystemAppearance(candidateAppearance(dark.value)) {
      CompositionLocalProvider(LocalDensity provides Density(1f)) {
        val state = remember { HazeState() }
        val selected = direction.value
        val (seed, style) = candidateStyles(selected, clear, dark.value)
        CompositionLocalProvider(LocalGlassStyle provides seed) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (photograph) {
              Image(painterResource(Res.drawable.photo), null, Modifier.fillMaxSize().hazeSource(state), contentScale = ContentScale.Crop)
            } else {
              Canvas(Modifier.fillMaxSize().hazeSource(state)) {
                observeCanvas()
                drawRect(Color(0xFF07141A))
                drawRect(Color(0xFFB8C5C8), Offset(size.width / 2f, 0f), Size(size.width / 2f, size.height))
                var x = 0f
                while (x < size.width) {
                  drawLine(Color.White.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                  x += 8.dp.toPx()
                }
                var y = 0f
                while (y < size.height) {
                  drawLine(Color.Cyan.copy(alpha = 0.65f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                  y += 8.dp.toPx()
                }
              }
            }
            if (selected < 0) {
              Box(Modifier.size(280.dp, 176.dp))
            } else if (merged) {
              Box(Modifier.size(280.dp, 176.dp).testTag("candidate-surface").hazeGlassContainer(40.dp)) {
                Box(Modifier.offset(x = 0.dp).size(130.dp, 176.dp).hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = mode))
                Box(Modifier.offset(x = 149.dp).size(130.dp, 176.dp).hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = mode))
              }
            } else {
              Box(Modifier.size(280.dp, 176.dp).testTag("candidate-surface").hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = mode))
            }
          }
        }
      }
    }
  }
  waitForIdle()
  val backdrop = captureRootPixels().snapshot()
  for (candidate in 0..2) {
    direction.value = candidate
    dark.value = false
    waitForIdle()
    val rect = onNodeWithTag("candidate-surface").fetchSemanticsNode().boundsInRoot
    val bounds = IntRect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
    val light = captureRootPixels().snapshot()
    assertThat(light.changedPixelRatio(backdrop)).isGreaterThan(0.0001f)
    if (merged && expectBridge) {
      assertThat(light[light.width / 2, light.height / 2]).isNotEqualTo(backdrop[backdrop.width / 2, backdrop.height / 2])
    }
    fun capture(appearance: String) {
      val kind = if (clear) "clear" else "regular"
      val geometry = if (merged) "merged" else "single"
      val background = if (photograph) "photo" else "grid"
      if (HazeRoborazziDefaults.roborazziOptions.taskType.isRecording()) captureRoot(artifactPath = "build/outputs/candidates/1743/$profile-$kind-$geometry-$mode-$background-$appearance-b$candidate.png")
    }
    capture("light")
    dark.value = true
    waitForIdle()
    val darkPixels = captureRootPixels().snapshot()
    if (merged && expectBridge) {
      assertThat(darkPixels[darkPixels.width / 2, darkPixels.height / 2]).isNotEqualTo(backdrop[backdrop.width / 2, backdrop.height / 2])
    }
    val difference = darkPixels.surfaceDifference(light, bounds)
    if (clear) assertThat(difference).isLessThan(1f / 255f) else assertThat(difference).isGreaterThan(0.001f)
    capture("dark")
    dark.value = false
    waitForIdle()
    assertThat(captureRootPixels().snapshot().surfaceDifference(light, bounds)).isLessThan(1f / 255f)
  }
}
internal fun ScreenshotUiTest.assertCandidateAuthoredShape() {
  val order = mutableStateOf(0)
  setContent {
    CompositionLocalProvider(LocalDensity provides Density(1f)) {
      val state = remember { HazeState() }
      val authored = GlassStyle { shape(androidx.compose.foundation.shape.RoundedCornerShape(topStart = 4.dp, topEnd = 32.dp, bottomEnd = 8.dp, bottomStart = 40.dp)) }
      val (seed, candidate) = when (order.value) {
        0 -> candidateStyles(1, clear = false, dark = false)
        1 -> candidateStyles(1, clear = false, dark = false, before = authored)
        2 -> candidateStyles(1, clear = false, dark = false, after = authored)
        3 -> candidateStyles(1, clear = false, dark = false).let { it.first to it.second.then(authored) }
        else -> candidateStyles(1, clear = false, dark = false).let { it.first to it.second.then { shape(ContinuousRoundedCornerShape(16.dp, 1f)) } }
      }
      val style = candidate.then { tint(Color.Red) }
      CompositionLocalProvider(LocalGlassStyle provides seed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize().hazeSource(state)) { drawRect(Color.Cyan) }
          Box(Modifier.size(280.dp, 176.dp).hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Quality))
        }
      }
    }
  }
  waitForIdle()
  val default = captureRootPixels().snapshot()
  order.value = 1
  waitForIdle()
  val before = captureRootPixels().snapshot()
  order.value = 2
  waitForIdle()
  val after = captureRootPixels().snapshot()
  order.value = 3
  waitForIdle()
  val reference = captureRootPixels().snapshot()
  order.value = 4
  waitForIdle()
  val defaultReference = captureRootPixels().snapshot()
  val corner = IntRect(default.width / 2 - 140, default.height / 2 - 88, default.width / 2 - 108, default.height / 2 - 56)
  assertThat(default.surfaceDifference(defaultReference, corner)).isLessThan(1f / 255f)
  assertThat(before.meanAbsoluteDifference(reference)).isLessThan(1f / 255f)
  assertThat(before.changedPixelRatio(default)).isGreaterThan(0.0001f)
  assertThat(after.meanAbsoluteDifference(before)).isLessThan(1f / 255f)
}
