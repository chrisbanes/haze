// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [33])
class FallbackGlassScrimAndroidHostTest : ScreenshotTest() {
  @Test
  fun overBudgetFallback_usesOverrideAndDoesNotBlur() = runScreenshotTest {
    val state = HazeState()
    val scrim = mutableStateOf(Color.Transparent)
    val effect = GlassRuntimeEffect()
    setContent {
      Box(Modifier.size(300.dp, 180.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(300.dp, 180.dp).hazeSource(state).background(Color.Red))
        Box(
          Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(5000.dp, 120.dp)
            .hazeGlass(
              factory = HazeEffectFactory { effect },
              input = HazeInput.Sources(state),
              style = GlassStyle {
                fallbackColor(scrim.value)
                optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(25.dp), depth = OpticalSizeValue.Fixed(1f)))
                shape(RoundedCornerShape(0.dp))
                specularIntensity(0f)
                edgeSoftness(0.dp)
                edgeShadow(Color.Transparent)
                ambientResponse(0f)
              },
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
        )
      }
    }
    val transparent = captureRootPixels()[100, 100]
    assertThat(effect.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat((effect.delegate as FallbackGlassDelegate).filteredInputDrawnForTest).isFalse()
    scrim.value = Color.Blue.copy(alpha = 0.5f)
    val coloured = captureRootPixels()[100, 100]
    assertThat(coloured.blue).isGreaterThan(transparent.blue)
    assertThat(transparent.red).isGreaterThan(coloured.red)
  }
}
