// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.then
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [32, 35])
class SourceTransitionAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun blur_sourceRemoved_retainsLastOutput() = runScreenshotTest {
    val visualEffect = HazeBlurStyle {
      blurRadius(16.dp)
      colorEffects(
        listOf(
          HazeColorEffect.tint(
            Color.White.copy(alpha = 0.12f),
            HazeColorEffect.DefaultBlendMode,
          ),
        ),
      )
    }
    var showSource by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        SourceTransitionSample(
          visualEffect = visualEffect,
          showSource = showSource,
        )
      }
    }

    waitForIdle()
    captureRoot("source")

    showSource = false
    waitForIdle()
    captureRoot("source_removed")
  }

  @Test
  @Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
  fun glass_sourceRemoved_reprocessesRetainedCapture() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(Color.White.copy(alpha = 0.12f))
        optics(GlassOptics.Absolute(refractionStrength = 0.45f, depth = 0.35f))
        specularIntensity(0.45f)
      },
    )
    var showSource by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        SourceTransitionGlassSample(
          style = style,
          showSource = showSource,
        )
      }
    }

    val withSource = captureRootPixels().snapshot()
    showSource = false
    waitForIdle()
    val retained = captureRootPixels().snapshot()
    style = style.then { tint(Color.Magenta.copy(alpha = 0.24f)) }
    waitForIdle()
    val reprocessed = captureRootPixels().snapshot()

    val retainedBounds = IntRect(0, 0, withSource.width, withSource.height / 4)
    assertThat(
      withSource.crop(retainedBounds)
        .meanAbsoluteDifference(retained.crop(retainedBounds)),
    ).isLessThan(1f / 255f)
    assertThat(
      retained.crop(retainedBounds)
        .changedPixelRatio(reprocessed.crop(retainedBounds)),
    ).isGreaterThan(0.01f)
  }
}
