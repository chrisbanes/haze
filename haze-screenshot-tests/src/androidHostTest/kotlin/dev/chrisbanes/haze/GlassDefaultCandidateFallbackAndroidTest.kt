// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.glass.GlassAccessibilitySettings
import dev.chrisbanes.haze.glass.LocalGlassAccessibilitySettings
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@Config(sdk = [28], qualifiers = "w393dp-h698dp-440dpi")
class GlassDefaultCandidateFallbackAndroidTest : ScreenshotTest() {
  @Test fun authoredShapeBeforeAndAfterCandidate() = runScreenshotTest { assertCandidateAuthoredShape() }

  @Test fun reducedTransparencyUsesOpaqueAppearanceOrExplicitBlue() = runScreenshotTest {
    val dark = mutableStateOf(false)
    val blue = mutableStateOf(false)
    val c2 = mutableStateOf(false)
    setContent {
      WithGlassTestSystemAppearance(if (dark.value) GlassTestSystemAppearance.Dark else GlassTestSystemAppearance.Light) {
        val state = remember { HazeState() }
        val settings = GlassAccessibilitySettings(reduceTransparency = true, reducedTransparencyFallbackColor = if (blue.value) Color.Blue else null)
        CompositionLocalProvider(LocalDensity provides Density(1f), LocalGlassAccessibilitySettings provides settings) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().hazeSource(state)) { drawRect(Color.Green) }
            val (seed, style) = candidateStyles(if (c2.value) 2 else 1, clear = true, dark = dark.value)
            CompositionLocalProvider(dev.chrisbanes.haze.glass.LocalGlassStyle provides seed) {
              Box(Modifier.size(280.dp, 176.dp).hazeGlass(input = HazeInput.Sources(state), style = style))
            }
          }
        }
      }
    }
    for (candidate in listOf(false, true)) for (appearance in listOf(false, true)) for (explicit in listOf(false, true)) {
      c2.value = candidate
      dark.value = appearance
      blue.value = explicit
      waitForIdle()
      val pixels = captureRootPixels()
      val center = pixels[pixels.width / 2, pixels.height / 2]
      println("accessibility c2=$candidate dark=$appearance blue=$explicit center=$center")
      if (explicit) {
        assertThat(center.blue - center.red).isGreaterThan(0.5f)
        assertThat(center.blue - center.green).isGreaterThan(0.5f)
      } else if (appearance) {
        assertThat(center.red).isLessThan(0.3f)
        assertThat(center.green).isLessThan(0.3f)
        assertThat(center.blue).isLessThan(0.3f)
      } else {
        assertThat(center.red).isGreaterThan(0.8f)
        assertThat(center.green).isGreaterThan(0.8f)
        assertThat(center.blue).isGreaterThan(0.8f)
      }
    }
  }

  @Test fun renderer() = runScreenshotTest { captureRenderer("android28") }

  @Test fun regular_single() = runScreenshotTest { captureCandidateCase("android28", clear = false, merged = false) }

  @Test fun regular_merged() = runScreenshotTest { captureCandidateCase("android28", clear = false, merged = true, expectBridge = false) }

  @Test fun clear_single() = runScreenshotTest { captureCandidateCase("android28", clear = true, merged = false) }

  @Test fun clear_merged() = runScreenshotTest { captureCandidateCase("android28", clear = true, merged = true, expectBridge = false) }
}

@Config(sdk = [32], qualifiers = "w393dp-h698dp-440dpi")
class GlassDefaultCandidateSdk32AndroidTest : ScreenshotTest() {
  @Test fun renderer() = runScreenshotTest { captureRenderer("android32") }

  @Test fun regular_photo() = runScreenshotTest { captureCandidateCase("android32", clear = false, merged = false, photograph = true) }

  @Test fun regular_single() = runScreenshotTest { captureCandidateCase("android32", clear = false, merged = false) }

  @Test fun clear_single() = runScreenshotTest { captureCandidateCase("android32", clear = true, merged = false) }
}

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassDefaultCandidateSdk35AndroidTest : ScreenshotTest() {
  @Test fun authoredShapeBeforeAndAfterCandidate() = runScreenshotTest { assertCandidateAuthoredShape() }

  @Test fun renderer() = runScreenshotTest { captureRenderer("android35") }

  @Test fun clear_balanced() = runScreenshotTest { captureCandidateCase("android35", clear = true, merged = true, mode = HazePerformanceMode.Balanced) }

  @Test fun clear_performance() = runScreenshotTest { captureCandidateCase("android35", clear = true, merged = true, mode = HazePerformanceMode.Performance) }

  @Test fun regular_performance() = runScreenshotTest { captureCandidateCase("android35", clear = false, merged = false, mode = HazePerformanceMode.Performance) }

  @Test fun regular_single() = runScreenshotTest { captureCandidateCase("android35", clear = false, merged = false) }

  @Test fun regular_merged() = runScreenshotTest { captureCandidateCase("android35", clear = false, merged = true) }

  @Test fun clear_single() = runScreenshotTest { captureCandidateCase("android35", clear = true, merged = false) }

  @Test fun clear_merged() = runScreenshotTest { captureCandidateCase("android35", clear = true, merged = true) }
}

private fun ScreenshotUiTest.captureRenderer(profile: String) {
  val previousStream = ShadowLog.stream
  val previousLogging = HazeLogger.enabled
  try {
    ShadowLog.stream = System.out
    HazeLogger.enabled = true
    var observed = false
    captureCandidateCase(profile, clear = false, merged = false, observeCanvas = {
      if (!observed) {
        println("candidate canvas sdk=${Build.VERSION.SDK_INT} hardware=${drawContext.canvas.nativeCanvas.isHardwareAccelerated}")
        observed = true
      }
    })
  } finally {
    HazeLogger.enabled = previousLogging
    ShadowLog.stream = previousStream
  }
}
