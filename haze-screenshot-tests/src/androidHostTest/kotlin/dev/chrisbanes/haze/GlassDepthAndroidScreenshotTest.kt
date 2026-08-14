// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

@Config(sdk = [35], qualifiers = "w393dp-h698dp-440dpi")
class GlassDepthAndroidScreenshotTest : ScreenshotTest() {

  @Test
  fun glass_asymmetricCornerNormalsAreContinuous() = runScreenshotTest {
    assertGlassAsymmetricCornerNormalsContinuous()
  }

  @Test
  fun glass_squircleInteriorIsContinuous() = runScreenshotTest {
    assertGlassSquircleInteriorContinuous()
  }

  @Test
  fun glass_squircleAmbientDoesNotGlowInside() = runScreenshotTest {
    assertGlassSquircleAmbientDoesNotGlowInside()
  }

  @Test
  fun glass_medialAxesAreContinuous() = runScreenshotTest {
    assertGlassMedialAxesContinuous()
  }

  @Test
  fun glass_depthProgression() = runScreenshotTest {
    assertGlassDepthProgression()
  }

  @Test
  fun glass_blurReducesHighFrequencyEnergy() = runScreenshotTest {
    assertGlassBlurInvariant()
  }

  @Test
  fun glass_backgroundColorBlursTransparentSource() = runScreenshotTest {
    assertGlassBackgroundColorBlurInvariant()
  }

  @Test
  fun glass_refractionDetailPreservesSharpSource() = runScreenshotTest {
    assertGlassRefractionDetailPreservesSharpSourceInvariant()
  }

  @Test
  fun glass_interactionRefractionDetailPreservesSharpSource() = runScreenshotTest {
    assertGlassRefractionDetailPreservesSharpSourceInvariant(withInteraction = true)
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
  fun glass_progressiveMaskGeometryIsInputScaleInvariant() = runScreenshotTest {
    assertGlassProgressiveMaskScaleInvariant()
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
  fun glass_roundedEdgePixelsAreContinuous() = runScreenshotTest {
    assertGlassRoundedEdgePixelsAreContinuous()
  }

  @Test
  fun glass_transparentOutputIsPremultiplied() = runScreenshotTest {
    assertGlassTransparentOutputInvariant()
  }

  @Test
  fun glass_neutralOpticsPreserveTranslucentPremultipliedRgba() = runScreenshotTest {
    assertGlassTranslucentSourceInvariant()
  }

  @Test
  fun glass_interactionPreservesTranslucentPremultipliedRgba() = runScreenshotTest {
    assertGlassTranslucentSourceInvariant(withInteraction = true)
  }

  @Test
  fun glass_maximumChromaKeepsSaturatedPrimariesFinite() = runScreenshotTest {
    assertGlassChromaMultiplierFiniteInvariant()
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
  fun glass_monotonicRefractionIsVisiblyDisplaced() = runScreenshotTest {
    assertGlassMonotonicRefractionVisibleInvariant()
  }

  @Test
  fun glass_refractionFoldInvertsIncomingContent() = runScreenshotTest {
    assertGlassRefractionFoldInvertsIncomingContentInvariant()
  }

  @Test
  fun glass_refractionFoldDoesNotFormSeparateEdgeBand() = runScreenshotTest {
    assertGlassRefractionFoldDoesNotFormSeparateEdgeBandInvariant()
  }

  @Test
  fun glass_oversizedAsymmetricCornersMatchComposeClip() = runScreenshotTest {
    assertGlassOversizedAsymmetricCornersInvariant()
  }

  @Test
  fun glass_crossEdgeCornersMatchComposeClip() = runScreenshotTest {
    assertGlassCrossEdgeCornersInvariant()
  }

  @Test
  fun glass_zeroLightingExponentsProduceFullResponse() = runScreenshotTest {
    assertGlassZeroExponentLightingInvariant()
  }

  @Test
  fun glass_depthZeroMasksShape() = runScreenshotTest {
    val shape = RoundedCornerShape(48.dp)
    val style = GlassStyle {
      tint(Color.White.copy(alpha = 0.28f))
      optics(refractionStrength = 0f, depth = 0f, blurRadius = 32.dp)
      specularIntensity(0f)
      ambientResponse(0f)
      edgeSoftness(16.dp)
      this.shape(shape)
    }

    setContent {
      ScreenshotTheme {
        GlassBlurRadiusSample(
          style = style,
          shape = shape,
          clipShape = false,
        )
      }
    }

    captureRoot()
  }
}
