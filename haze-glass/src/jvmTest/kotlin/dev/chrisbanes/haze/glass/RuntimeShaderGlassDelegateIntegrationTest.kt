// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class RuntimeShaderGlassDelegateIntegrationTest : ContextTest() {

  @Test
  fun activeDetail_recordsAndSurvivesRetainedSourceGap() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val effect = activeDetailEffect()

    setContent {
      Box(Modifier.size(120.dp)) {
        if (showSource.value) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Red)
              .hazeSource(hazeState),
          )
        }
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val detailLayer = checkNotNull(delegate.layers.refractionDetail)
    val detailKey = checkNotNull(delegate.lastSuccessfulStageInputs?.detail)
    val sourceSnapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    showSource.value = false
    waitForIdle()

    assertSame(detailLayer, delegate.layers.refractionDetail)
    assertSame(delegate, effect.delegate)
    assertSame(sourceSnapshot, delegate.lastSuccessfulSourceSnapshot)
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNotNull().isEqualTo(detailKey)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun zeroRefractionScale_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply { refractionScale = 0f }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Red)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun epsilonRefractionStrength_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply { refractionStrength = 1e-6f }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun lowVisibleRefractionStrength_allocatesAndRecordsDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply { refractionStrength = .1f }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNotNull()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun effectAlpha_isAppliedToOneGroupedOpticalAndDetailOutput() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply { alpha = 0.5f }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Red)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(checkNotNull(delegate.layers.optical).alpha).isEqualTo(1f)
    assertThat(checkNotNull(delegate.layers.refractionDetail).alpha).isEqualTo(1f)
  }

  private fun activeDetailEffect() = GlassVisualEffect().apply {
    refractionStrength = 0.5f
    refractionScale = 20f
    blurRadius = 0.dp
    specularIntensity = 0f
  }
}
