// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test

class GlassDepthDesktopScreenshotTest : ScreenshotTest() {

  @Test
  fun glass_asymmetricCornerNormalsAreContinuous() = runScreenshotTest {
    assertGlassAsymmetricCornerNormalsContinuous()
  }

  @Test
  fun glass_squircleInteriorIsContinuous() = runScreenshotTest {
    assertGlassSquircleInteriorContinuous()
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
  fun glass_transparentOutputIsPremultiplied() = runScreenshotTest {
    assertGlassTransparentOutputInvariant()
  }

  @Test
  fun glass_neutralOpticsPreserveTranslucentPremultipliedRgba() = runScreenshotTest {
    assertGlassTranslucentSourceInvariant()
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
  fun glass_defaultRefractionIsVisiblyDisplaced() = runScreenshotTest {
    assertGlassDefaultRefractionVisibleInvariant()
  }

  @Test
  fun glass_oversizedAsymmetricCornersMatchComposeClip() = runScreenshotTest {
    assertGlassOversizedAsymmetricCornersInvariant()
  }

  @Test
  fun glass_crossEdgeCornersMatchComposeClip() = runScreenshotTest {
    assertGlassCrossEdgeCornersInvariant()
  }
}
