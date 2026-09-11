// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.glass.GlassStyle

/** Test-only catalog for the screenshot configurations enrolled in the matrix. */
internal object ScreenshotMatrix {
  val scenes = listOf(
    ScreenshotMatrixScene.BlurCreditCard,
    ScreenshotMatrixScene.GlassCreditCard,
  )

  val hostInputs = listOf(
    ScreenshotMatrixInput.Sources,
    ScreenshotMatrixInput.BackdropFallback,
  )

  private val nativeHostInputs = listOf(ScreenshotMatrixInput.BackdropNative)

  val modes = listOf(
    ScreenshotMatrixMode.Quality,
    ScreenshotMatrixMode.Balanced,
    ScreenshotMatrixMode.Performance,
    ScreenshotMatrixMode.Adaptive,
  )

  val androidSdkProfiles = listOf(
    SCREENSHOT_MATRIX_ANDROID_SDK_28,
    SCREENSHOT_MATRIX_ANDROID_SDK_32,
    SCREENSHOT_MATRIX_ANDROID_SDK_35,
  )

  fun hostCases(profile: ScreenshotMatrixProfile): List<ScreenshotMatrixCase> {
    return matrixCases(profile, hostInputs)
  }

  fun nativeHostCases(profile: ScreenshotMatrixProfile.AndroidHost): List<ScreenshotMatrixCase> {
    require(profile.sdk == SCREENSHOT_MATRIX_ANDROID_SDK_37) {
      "Native host cases require SDK $SCREENSHOT_MATRIX_ANDROID_SDK_37, was ${profile.sdk}"
    }
    return matrixCases(profile, nativeHostInputs)
  }

  fun selectHostCases(
    profile: ScreenshotMatrixProfile,
    selectedCaseId: String?,
  ): List<ScreenshotMatrixCase> {
    return selectCases(hostCases(profile), selectedCaseId, profile)
  }

  fun selectNativeHostCases(
    profile: ScreenshotMatrixProfile.AndroidHost,
    selectedCaseId: String?,
  ): List<ScreenshotMatrixCase> {
    return selectCases(nativeHostCases(profile), selectedCaseId, profile)
  }

  private fun selectCases(
    cases: List<ScreenshotMatrixCase>,
    selectedCaseId: String?,
    profile: ScreenshotMatrixProfile,
  ): List<ScreenshotMatrixCase> {
    if (selectedCaseId == null) return cases
    return listOf(
      requireNotNull(cases.singleOrNull { it.selectorId == selectedCaseId }) {
        "Unknown screenshot matrix case '$selectedCaseId' for ${profile.id}"
      },
    )
  }

  private fun matrixCases(
    profile: ScreenshotMatrixProfile,
    inputs: Iterable<ScreenshotMatrixInput>,
  ): List<ScreenshotMatrixCase> = buildList {
    scenes.forEach { scene ->
      inputs.forEach { input ->
        modes.forEach { mode ->
          add(ScreenshotMatrixCase(scene, input, mode, profile))
        }
      }
    }
  }
}

internal const val SCREENSHOT_MATRIX_CASE_PROPERTY = "haze.screenshot.matrix.case"
internal const val SCREENSHOT_MATRIX_ANDROID_SDK_28 = 28
internal const val SCREENSHOT_MATRIX_ANDROID_SDK_32 = 32
internal const val SCREENSHOT_MATRIX_ANDROID_SDK_35 = 35
internal const val SCREENSHOT_MATRIX_ANDROID_SDK_37 = 37

internal enum class ScreenshotMatrixScene(val id: String) {
  BlurCreditCard("blur-credit-card"),
  GlassCreditCard("glass-credit-card"),
}

internal enum class ScreenshotMatrixInput(val id: String) {
  Sources("sources"),
  BackdropFallback("backdrop-fallback"),
  BackdropNative("backdrop-native"),
}

internal enum class ScreenshotMatrixMode(
  val id: String,
  val performanceMode: HazePerformanceMode,
) {
  Quality("quality", HazePerformanceMode.Quality),
  Balanced("balanced", HazePerformanceMode.Balanced),
  Performance("performance", HazePerformanceMode.Performance),
  Adaptive("adaptive", HazePerformanceMode.Adaptive),
}

internal sealed class ScreenshotMatrixProfile(val id: String) {
  data object Desktop : ScreenshotMatrixProfile("desktop")

  @Poko
  class AndroidHost(val sdk: Int) : ScreenshotMatrixProfile("android-sdk-$sdk")
}

@Poko
internal class ScreenshotMatrixCase(
  val scene: ScreenshotMatrixScene,
  val input: ScreenshotMatrixInput,
  val mode: ScreenshotMatrixMode,
  val profile: ScreenshotMatrixProfile,
) {
  val selectorId: String = "${scene.id}-${input.id}-${mode.id}"
  val id: String = "${profile.id}/$selectorId"
  val artifactPath: String = "screenshots/matrix/${profile.id}/${scene.id}/${input.id}/${mode.id}.webp"

  fun withProfile(profile: ScreenshotMatrixProfile): ScreenshotMatrixCase = ScreenshotMatrixCase(
    scene = scene,
    input = input,
    mode = mode,
    profile = profile,
  )

  override fun toString(): String = selectorId
}

@Composable
internal fun ScreenshotMatrixCase.Render() {
  val nativeFallbackState = remember { HazeState() }
  when (scene) {
    ScreenshotMatrixScene.BlurCreditCard -> CreditCardSample(
      visualEffect = HazeBlurStyle {
        colorEffects(listOf(MatrixTint))
        blurRadius(8.dp)
      },
      performanceMode = mode.performanceMode,
      input = { sourceState -> input.createInput(sourceState, nativeFallbackState) },
    )
    ScreenshotMatrixScene.GlassCreditCard -> CreditCardGlassSample(
      style = GlassStyle { tint(MatrixTintColor) },
      performanceMode = mode.performanceMode,
      input = { sourceState -> input.createInput(sourceState, nativeFallbackState) },
    )
  }
}

private val MatrixTintColor = Color.White.copy(alpha = 0.1f)
private val MatrixTint = HazeColorEffect.tint(MatrixTintColor)

internal fun ScreenshotMatrixInput.createInput(
  sourceState: HazeState,
  nativeFallbackState: HazeState,
): HazeInput = when (this) {
  ScreenshotMatrixInput.Sources -> HazeInput.Sources(sourceState)
  ScreenshotMatrixInput.BackdropFallback -> HazeInput.Backdrop(sourceState)
  ScreenshotMatrixInput.BackdropNative -> HazeInput.Backdrop(nativeFallbackState)
}

@OptIn(ExperimentalHazeApi::class)
internal inline fun <T> ScreenshotMatrixCase.withPlatformBackdropFlag(block: () -> T): T {
  val previous = HazeFeatureFlags.isPlatformBackdropEnabled
  HazeFeatureFlags.isPlatformBackdropEnabled = input == ScreenshotMatrixInput.BackdropNative
  return try {
    block()
  } finally {
    HazeFeatureFlags.isPlatformBackdropEnabled = previous
  }
}
