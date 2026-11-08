// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [30, 32])
class GlassFallbackScreenshotTest : ScreenshotTest() {
  @Test fun singleLight() = scene(dark = false, kind = 0)

  @Test fun singleDark() = scene(dark = true, kind = 0)

  @Test fun progressiveLight() = scene(dark = false, kind = 1)

  @Test fun progressiveDark() = scene(dark = true, kind = 1)

  @Test fun mergedLight() = scene(dark = false, kind = 2)

  @Test fun mergedDark() = scene(dark = true, kind = 2)

  private fun scene(dark: Boolean, kind: Int) = runScreenshotTest(size = Size(420f, 280f)) {
    val state = HazeState()
    val effects = List(2) { GlassRuntimeEffect() }
    setContent {
      val density = LocalDensity.current
      fun Modifier.pxSize(w: Int, h: Int) = with(density) { size(w.toDp(), h.toDp()) }
      val config = Configuration(LocalConfiguration.current).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
          if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      }
      CompositionLocalProvider(LocalConfiguration provides config) {
        Box(Modifier.pxSize(420, 280).testTag("root").background(Color(0xff172b4d))) {
          Box(Modifier.pxSize(420, 280).hazeSource(state).background(Color(0xff172b4d))) {
            for (x in 0 until 420 step 40) {
              Box(Modifier.offset { IntOffset(x, 0) }.pxSize(20, 280).background(Color(0xfff08a5d)))
            }
          }
          val progressive = if (kind == 1) HazeProgressive.verticalGradient() else null
          val regular = GlassStyle.regular.then {
            shape(RoundedCornerShape(20.dp))
            if (progressive != null) optics { copy(progressive = progressive) }
          }
          val clear = GlassStyle.clear.then {
            shape(RoundedCornerShape(20.dp))
            fallbackColor(Color(0x6620c080))
            if (progressive != null) optics { copy(progressive = progressive) }
          }
          if (kind == 2) {
            Box(Modifier.pxSize(420, 280).hazeGlassContainer(20.dp)) {
              Box(Modifier.offset { IntOffset(55, 70) }.pxSize(170, 140).hazeGlass(factory = HazeEffectFactory { effects[0] }, input = HazeInput.Sources(state), style = regular, performanceMode = null, expandLayerBounds = true, interactionSource = null))
              Box(Modifier.offset { IntOffset(190, 70) }.pxSize(170, 140).hazeGlass(factory = HazeEffectFactory { effects[1] }, input = HazeInput.Sources(state), style = regular.then { fallbackColor(Color(0x6620c080)) }, performanceMode = null, expandLayerBounds = true, interactionSource = null))
            }
          } else {
            Box(Modifier.offset { IntOffset(35, 70) }.pxSize(160, 140).hazeGlass(factory = HazeEffectFactory { effects[0] }, input = HazeInput.Sources(state), style = regular, performanceMode = null, expandLayerBounds = true, interactionSource = null))
            Box(Modifier.offset { IntOffset(225, 70) }.pxSize(160, 140).hazeGlass(factory = HazeEffectFactory { effects[1] }, input = HazeInput.Sources(state), style = clear, performanceMode = null, expandLayerBounds = true, interactionSource = null))
          }
        }
      }
    }
    captureRoot()
    if (Build.VERSION.SDK_INT == 32) {
      val delegate = if (kind == 2) {
        val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
        owner.renderer.runtimeSurfaces.single().delegate
      } else {
        effects.first().delegate
      }
      assertThat((delegate as FallbackGlassDelegate).filteredInputDrawnForTest).isTrue()
    }
  }
}
