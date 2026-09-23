// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassSourceRecordInstrumentationTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun highContrastSourceOnlyUpdateRenewsAdaptiveDemandAndChangesGlassPixels() {
    val hazeState = HazeState()
    val color = mutableStateOf(Color(0xFFFF6D00))
    val effect = GlassRuntimeEffect()
    val factory = HazeEffectFactory<GlassNodeConfiguration> { effect }

    composeTestRule.setContent {
      GlassAdaptiveHost {
        Box(Modifier.size(160.dp)) {
          Box(
            Modifier.size(160.dp).hazeSource(hazeState).drawBehind {
              drawRect(color.value)
            },
          )
          Box(
            Modifier.size(160.dp).testTag("glass").hazeGlass(
              factory = factory,
              input = HazeInput.Sources(hazeState),
              style = GlassStyle,
              performanceMode = HazePerformanceMode.Adaptive,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
    composeTestRule.waitForIdle()
    val host = checkNotNull(effect.adaptiveHostForTest)
    assertThat(effect.preparedRender).isNotNull()
    fun center(): Color = composeTestRule.onNodeWithTag("glass").captureToImage().toPixelMap().let {
      it[it.width / 2, it.height / 2]
    }
    val orange = center()
    composeTestRule.waitUntil(timeoutMillis = 5_000) { !host.hasActiveDemand }

    composeTestRule.runOnIdle { color.value = Color(0xFF08274F) }
    composeTestRule.waitUntil(timeoutMillis = 5_000) { host.hasActiveDemand }
    composeTestRule.waitForIdle()
    val navy = center()

    assertThat(orange.red).isGreaterThan(orange.blue)
    assertThat(navy.blue).isGreaterThan(navy.red)
  }
}
