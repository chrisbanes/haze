// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FallbackGlassInteractionTest : ContextTest() {

  @Test
  fun fallback_pressedLighting_isLocalizedAtPointer() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      pressed { lightingIntensity(1f) }
      interactionLightRadiusFraction = 0.4f
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
      tint = Color.Transparent
      specularIntensity = 0f
      ambientResponse = 0f
      lightPosition = Offset(80f, 80f)
    }
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .hazeEffect { visualEffect = effect }
          .background(Color.Black),
      )
    }
    waitForIdle()
    effect.delegate = FallbackGlassDelegate(effect)

    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
    waitForIdle()
    val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()

    assertThat(pixels[20, 20].luminance()).isGreaterThan(pixels[80, 80].luminance())
  }
}
