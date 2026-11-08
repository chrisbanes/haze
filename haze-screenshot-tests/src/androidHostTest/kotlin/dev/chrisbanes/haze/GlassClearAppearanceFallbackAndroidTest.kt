// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [28], qualifiers = "w393dp-h698dp-440dpi")
class GlassClearAppearanceFallbackAndroidTest : ScreenshotTest() {
  @Test
  fun clear_attachedFallbackRemainsStableAcrossRepeatedSwitches() = checkSwitch(GlassStyle.clear, false)

  @Test
  fun regular_attachedFallbackRespondsToAppearance() = checkSwitch(GlassStyle.regular, true)

  private fun checkSwitch(style: GlassStyle, changes: Boolean) = runScreenshotTest {
    var appearance by mutableStateOf(GlassTestSystemAppearance.Light)
    setContent {
      WithGlassTestSystemAppearance(appearance) {
        val state = rememberHazeState()
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize().hazeSource(state)) {
            drawRect(Color(0xFF102030))
            repeat(14) { index ->
              val x = index * size.width / 14f
              drawLine(Color.Cyan, Offset(x, 0f), Offset(x, size.height), 3f)
            }
          }
          Box(Modifier.size(280.dp, 180.dp).hazeGlass(input = HazeInput.Sources(state), style = style))
        }
      }
    }
    waitForIdle()
    val light = captureRootPixels().snapshot()
    repeat(2) {
      appearance = GlassTestSystemAppearance.Dark
      waitForIdle()
      val dark = captureRootPixels().snapshot()
      if (changes) {
        assertThat(dark.changedPixelRatio(light)).isGreaterThan(0.01f)
      } else {
        assertThat(dark.meanAbsoluteDifference(light)).isLessThan(1f / 255f)
      }
      appearance = GlassTestSystemAppearance.Light
      waitForIdle()
      assertThat(captureRootPixels().snapshot().meanAbsoluteDifference(light)).isLessThan(1f / 255f)
    }
  }
}
