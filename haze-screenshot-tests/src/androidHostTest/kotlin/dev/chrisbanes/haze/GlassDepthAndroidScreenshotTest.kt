// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassDepthAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun glass_depthProgression() = runScreenshotTest {
    val shape = RoundedCornerShape(28.dp)
    val visualEffect = glassDepthProgressionVisualEffect(
      depth = 0f,
      shape = shape,
    )

    setContent {
      ScreenshotTheme {
        GlassDepthSingleSample(
          visualEffect = visualEffect,
          shape = shape,
        )
      }
    }

    val depth0 = captureRootPixels().snapshot()

    visualEffect.depth = 0.5f
    waitForIdle()
    val depth50 = captureRootPixels().snapshot()

    visualEffect.depth = 1f
    waitForIdle()
    val depth100 = captureRootPixels().snapshot()

    assertDepthProgression(depth0, depth50, depth100)

    visualEffect.depth = 0f
    waitForIdle()
    captureRoot("0")
    visualEffect.depth = 0.5f
    waitForIdle()
    captureRoot("50")
    visualEffect.depth = 1f
    waitForIdle()
    captureRoot("100")
  }

  @Test
  fun glass_blurReducesHighFrequencyEnergy() = runScreenshotTest {
    assertGlassBlurInvariant()
  }

  @Test
  fun glass_semanticBlurHasCommonHighFrequencyResponse() = runScreenshotTest {
    assertGlassSemanticBlurHfInvariant()
  }

  @Test
  fun glass_adversarialDownsampleRejectsAliasing() = runScreenshotTest {
    assertGlassAdversarialDownsampleInvariant()
  }

  @Test
  fun glass_progressiveBlurPreservesZeroAndFullRegions() = runScreenshotTest {
    assertGlassProgressiveBlurInvariant()
  }

  @Test
  fun glass_paddingPreservesSourceAppearance() = runScreenshotTest {
    assertGlassPaddingPreservesSourceInvariant()
  }

  @Test
  fun glass_hardClipMatchesBackground() = runScreenshotTest {
    assertGlassHardClipInvariant()
  }

  @Test
  fun glass_transparentOutputIsPremultiplied() = runScreenshotTest {
    assertGlassTransparentOutputInvariant()
  }

  @Test
  fun glass_translucentSourceUsesPremultipliedSourceOver() = runScreenshotTest {
    assertGlassTranslucentSourceInvariant()
  }

  @Test
  fun glass_paddingAndScalePreserveGeometry() = runScreenshotTest {
    assertGlassPaddingAndScaleInvariants()
  }

  @Test
  fun glass_firstEnabledFrameIsStable() = runScreenshotTest {
    assertGlassFirstEnabledFrameInvariant()
  }

  @Test
  fun glass_profileBranchIsContinuous() = runScreenshotTest {
    assertGlassProfileBranchContinuous()
  }

  @Test
  fun glass_defaultRefractionIsVisiblyDisplaced() = runScreenshotTest {
    assertGlassDefaultRefractionVisibleInvariant()
  }

  @Test fun glass_regularDefaultsMatchIos26LightCapsule() = assertRegularReference(GlassAppearance.Light, GlassSurface.Capsule)

  @Test fun glass_regularDefaultsMatchIos26LightCard() = assertRegularReference(GlassAppearance.Light, GlassSurface.Card)

  @Test fun glass_regularDefaultsMatchIos26LightPanel() = assertRegularReference(GlassAppearance.Light, GlassSurface.Panel)

  @Test fun glass_regularDefaultsMatchIos26DarkCapsule() = assertRegularReference(GlassAppearance.Dark, GlassSurface.Capsule)

  @Test fun glass_regularDefaultsMatchIos26DarkCard() = assertRegularReference(GlassAppearance.Dark, GlassSurface.Card)

  @Test fun glass_regularDefaultsMatchIos26DarkPanel() = assertRegularReference(GlassAppearance.Dark, GlassSurface.Panel)

  private fun assertRegularReference(appearance: GlassAppearance, surface: GlassSurface) = runScreenshotTest {
    assertGlassMatchesIos26RegularReferenceBands(appearance, surface)
  }

  @Test
  fun glass_depthZeroMasksShape() = runScreenshotTest {
    val shape = RoundedCornerShape(48.dp)
    val visualEffect = GlassVisualEffect().apply {
      tint = Color.White.copy(alpha = 0.28f)
      refractionStrength = 0f
      depth = 0f
      blurRadius = 32.dp
      specularIntensity = 0f
      ambientResponse = 0f
      edgeSoftness = 16.dp
      this.shape = shape
    }

    setContent {
      ScreenshotTheme {
        GlassBlurRadiusSample(
          visualEffect = visualEffect,
          shape = shape,
          clipShape = false,
        )
      }
    }

    captureRoot()
  }
}
