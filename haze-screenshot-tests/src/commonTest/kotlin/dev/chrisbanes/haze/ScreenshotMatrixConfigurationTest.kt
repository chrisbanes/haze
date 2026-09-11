// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertFailure
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
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
    assertThat(cases.map { it.artifactPath }.all { it.startsWith("screenshots/matrix/desktop/") })
      .isTrue()
  }

  @Test
  fun selectedCases_matchExactIdsAndRejectUnknownIds() {
    val selected = ScreenshotMatrix.selectHostCases(
      profile = ScreenshotMatrixProfile.Desktop,
      selectedCaseId = "blur-credit-card-sources-quality",
      fullRun = false,
    )

    assertThat(selected.map { it.selectorId }).containsExactly("blur-credit-card-sources-quality")
    assertFailure {
      ScreenshotMatrix.selectHostCases(
        profile = ScreenshotMatrixProfile.Desktop,
        selectedCaseId = "missing",
        fullRun = false,
      )
    }
  }

  @Test
  fun fullRun_rejectsNarrowing() {
    assertFailure {
      ScreenshotMatrix.selectHostCases(
        profile = ScreenshotMatrixProfile.Desktop,
        selectedCaseId = "blur-credit-card-sources-quality",
        fullRun = true,
      )
    }
  }

  @Test
  fun fullCoverage_requiresEveryApplicableNativeCase() {
    val expected = ScreenshotMatrix.fullCaseIds
    val missingNative = "pixel-6-android-37-2/blur-credit-card-backdrop-native-quality"
    val successful = expected - missingNative

    assertFailure {
      ScreenshotMatrix.requireCompleteFullCoverage(
        passedCaseIds = successful,
        unsupportedCaseIds = emptySet(),
      )
    }

    assertFailure {
      ScreenshotMatrix.requireCompleteFullCoverage(
        passedCaseIds = successful,
        unsupportedCaseIds = setOf(missingNative),
      )
    }
  }
}
