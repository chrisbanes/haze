// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class FallbackGlassInteractionTest : ContextTest() {

  @Test
  fun automaticFallback_preservesAppearanceAndLightingWhileOmittingOptics() = runComposeUiTest {
    val firstOptics = GlassOptics.Fixed(
      refractionStrength = 0.8f,
      refractionHeightFraction = 0.4f,
      refractionDisplacement = 18.dp,
      depth = 0.7f,
      blurRadius = 20.dp,
    )
    val secondOptics = GlassOptics.Fixed(
      refractionStrength = 0.2f,
      refractionHeightFraction = 0.8f,
      refractionDisplacement = 4.dp,
      depth = 0.1f,
      blurRadius = 2.dp,
    )
    val style = mutableStateOf(fallbackPortableStyle(firstOptics, includeInteractionOptics = true))
    val interactionSource = MutableInteractionSource()
    var failedRuntimeCreationAttempts = 0
    lateinit var runtime: GlassRuntimeEffect
    val factory = HazeEffectFactory<GlassNodeConfiguration> {
      GlassRuntimeEffect().apply {
        runtimeEffectFactory = GlassRuntimeEffectFactory {
          failedRuntimeCreationAttempts++
          throw RuntimeShaderRenderEffectException(
            IllegalArgumentException("broken runtime effect"),
          )
        }
      }.also { runtime = it }
    }

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("glass")
          .hazeGlass(
            factory = factory,
            input = HazeInput.Content,
            style = style.value,
            sampling = HazeSampling.FullResolution,
            expandLayerBounds = true,
            interactionSource = interactionSource,
            interactionTransformTarget = GlassTransformTarget.MaterialOnly,
            interactionTransformPivot = GlassTransformPivot.Pointer,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced,
          )
          .background(Color.Black),
      )
    }
    waitForIdle()
    val idleWithFirstOptics = onNodeWithTag("glass").captureToImage().toPixelMap()

    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failedRuntimeCreationAttempts).isEqualTo(1)
    assertThat(idleWithFirstOptics[80, 80].red)
      .isGreaterThan(idleWithFirstOptics[80, 80].green)
    assertThat(idleWithFirstOptics[15, 15].luminance())
      .isGreaterThan(idleWithFirstOptics[80, 80].luminance())

    style.value = fallbackPortableStyle(secondOptics, includeInteractionOptics = true)
    waitForIdle()
    val idleWithSecondOptics = onNodeWithTag("glass").captureToImage().toPixelMap()

    assertThat(idleWithSecondOptics.sampledGrid()).isEqualTo(idleWithFirstOptics.sampledGrid())
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failedRuntimeCreationAttempts).isEqualTo(1)

    runTest {
      interactionSource.emit(PressInteraction.Press(Offset(80f, 80f)))
    }
    waitForIdle()
    val pressedWithInteractionOptics = onNodeWithTag("glass").captureToImage().toPixelMap()

    assertThat(pressedWithInteractionOptics[80, 80].luminance())
      .isGreaterThan(idleWithSecondOptics[80, 80].luminance())

    style.value = fallbackPortableStyle(secondOptics, includeInteractionOptics = false)
    waitForIdle()
    val pressedWithoutInteractionOptics = onNodeWithTag("glass").captureToImage().toPixelMap()

    assertThat(pressedWithoutInteractionOptics.sampledGrid())
      .isEqualTo(pressedWithInteractionOptics.sampledGrid())
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failedRuntimeCreationAttempts).isEqualTo(1)
  }
}

private fun fallbackPortableStyle(
  optics: GlassOptics.Fixed,
  includeInteractionOptics: Boolean,
): GlassStyle = GlassStyle {
  optics(optics)
  tint(Color.Red.copy(alpha = 0.6f))
  ambientResponse(0f)
  specularIntensity(1f)
  edgeSoftness(0.dp)
  lightPosition(Alignment.TopStart)
  pressed {
    lightingIntensity(1f)
    if (includeInteractionOptics) {
      refractionMultiplier(1.8f)
      whitePointDelta(0.5f)
    }
    scale(0.96f)
  }
  interactionLightRadiusFraction(0.3f)
}

private fun PixelMap.sampledGrid(): List<Color> = buildList {
  for (y in 5 until height step 10) {
    for (x in 5 until width step 10) {
      add(this@sampledGrid[x, y])
    }
  }
}
