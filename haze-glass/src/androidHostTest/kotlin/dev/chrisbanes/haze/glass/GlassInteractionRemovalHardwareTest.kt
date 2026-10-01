// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
@Config(sdk = [37])
class GlassInteractionRemovalHardwareTest : ScreenshotTest() {
  @Test
  fun sourceBackedFusedMaterial_hardwareCaptureDistinguishesSource() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val factory = HazeEffectFactory<GlassNodeConfiguration> { effect }
    setContent {
      Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          Modifier.size(120.dp).testTag("material").hazeGlass(
            factory = factory,
            input = HazeInput.Sources(state),
            interactionSource = null,
            style = GlassStyle.regular.then { tint(Color.Blue.copy(alpha = 0.5f)) },
            performanceMode = HazePerformanceMode.Quality,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
            expandLayerBounds = true,
          ),
        )
        Box(Modifier.align(Alignment.TopStart).size(80.dp).testTag("source-control"))
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    assertThat(effect.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.source).isNotNull()
    assertThat(delegate.layers.optical).isNotNull()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNotNull()
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(delegate.layers.interactionRefractionDetail).isNull()
    val pixels = captureRootPixels()
    val controlBounds = onNodeWithTag("source-control").fetchSemanticsNode().boundsInRoot
    val materialBounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
    val control = pixels[controlBounds.center.x.roundToInt(), controlBounds.center.y.roundToInt()]
    val material = pixels[materialBounds.center.x.roundToInt(), materialBounds.center.y.roundToInt()]
    println("P1 hardware source=$control material=$material sourceSnapshot=${delegate.lastSuccessfulSourceSnapshot}")
    assertThat(control.red, "source control red").isGreaterThan(0.9f)
    assertThat(control.blue, "source control blue").isLessThan(0.1f)
    assertThat(material.blue, "material blue distinguishes source").isGreaterThan(control.blue + 0.2f)
  }
}
