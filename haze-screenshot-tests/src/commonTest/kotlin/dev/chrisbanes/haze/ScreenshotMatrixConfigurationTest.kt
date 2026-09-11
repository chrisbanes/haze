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
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
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
  fun nativeHostCases_applyOnlyToSdk37AndUseNativeInput() {
    val cases = ScreenshotMatrix.nativeHostCases(
      ScreenshotMatrixProfile.AndroidHost(SCREENSHOT_MATRIX_ANDROID_SDK_37),
    )

    assertThat(cases.size).isEqualTo(8)
    assertThat(cases.map { it.input.id }.distinct()).containsExactly("backdrop-native")
    assertThat(cases.map { it.artifactPath }.toSet().size).isEqualTo(cases.size)
    assertThat(cases.map { it.artifactPath }).each {
      it.startsWith("screenshots/matrix/android-sdk-37/")
    }
    assertFailure {
      ScreenshotMatrix.nativeHostCases(ScreenshotMatrixProfile.AndroidHost(35))
    }
  }

  @Test
  fun nativeBackdropInput_usesSeparateEmptyFallbackState() {
    val sourceState = HazeState()
    val nativeFallbackState = HazeState()

    val input = ScreenshotMatrixInput.BackdropNative.createInput(sourceState, nativeFallbackState)
      as HazeInput.Backdrop

    assertThat(input.fallback.state).isSameInstanceAs(nativeFallbackState)
    assertThat(input.fallback.state).isNotSameInstanceAs(sourceState)
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
  fun backdropFlag_tracksInputAndIsRestoredAfterSuccessAndFailure() {
    val original = HazeFeatureFlags.isPlatformBackdropEnabled
    val fallbackCase = ScreenshotMatrix.hostCases(ScreenshotMatrixProfile.Desktop).first()
    val nativeCase = ScreenshotMatrix.nativeHostCases(
      ScreenshotMatrixProfile.AndroidHost(SCREENSHOT_MATRIX_ANDROID_SDK_37),
    ).first()
    try {
      HazeFeatureFlags.isPlatformBackdropEnabled = false
      nativeCase.withPlatformBackdropFlag { assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue() }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isFalse()
      assertFailure { nativeCase.withPlatformBackdropFlag { error("capture failed") } }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isFalse()

      HazeFeatureFlags.isPlatformBackdropEnabled = true
      fallbackCase.withPlatformBackdropFlag { assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isFalse() }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue()
      assertFailure { fallbackCase.withPlatformBackdropFlag { error("capture failed") } }
      assertThat(HazeFeatureFlags.isPlatformBackdropEnabled).isTrue()
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = original
    }
  }
}
