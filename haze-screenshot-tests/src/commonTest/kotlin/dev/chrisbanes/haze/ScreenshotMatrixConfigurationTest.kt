// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import assertk.assertions.startsWith
import kotlin.test.Test

class ScreenshotMatrixConfigurationTest {

  @Test
  fun enrolledCases_coverTwoScenesTwoHostInputsAndAllNamedModes() {
    assertThat(ScreenshotMatrix.scenes.map { it.id })
      .containsExactly("blur-credit-card", "glass-credit-card")
    assertThat(ScreenshotMatrix.hostInputs.map { it.id })
      .containsExactly("sources", "backdrop-fallback")
    assertThat(ScreenshotMatrix.modes.map { it.id })
      .containsExactly("quality", "balanced", "performance", "adaptive")
  }

  @Test
  fun hostCases_haveStableUniqueIdsAndArtifactPaths() {
    val cases = ScreenshotMatrix.hostCases(ScreenshotMatrixProfile.Desktop)

    assertThat(cases).isNotEmpty()
    assertThat(cases.map { it.id }.toSet().size).isEqualTo(cases.size)
    assertThat(cases.map { it.artifactPath }.toSet().size).isEqualTo(cases.size)
    assertThat(cases.map { it.artifactPath }).each { it.startsWith("screenshots/matrix/desktop/") }
  }

  @Test
  fun selectedCases_matchExactIdsAndRejectUnknownIds() {
    val selected = ScreenshotMatrix.selectHostCases(
      profile = ScreenshotMatrixProfile.Desktop,
      selectedCaseId = "blur-credit-card-sources-quality",
    )

    assertThat(selected.map { it.selectorId }).containsExactly("blur-credit-card-sources-quality")
    assertFailure {
      ScreenshotMatrix.selectHostCases(
        profile = ScreenshotMatrixProfile.Desktop,
        selectedCaseId = "missing",
      )
    }
  }

  @Test
  fun backdropFlag_isRestoredAfterSuccessAndFailure() {
    val original = HazeFeatureFlags.isPlatformBackdropEnabled
    val case = ScreenshotMatrix.hostCases(ScreenshotMatrixProfile.Desktop).first()
    try {
      HazeFeatureFlags.isPlatformBackdropEnabled = true
      case.withPlatformBackdropFlag { assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isFalse() }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue()
      assertFailure { case.withPlatformBackdropFlag { error("capture failed") } }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue()
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = original
    }
  }
}
