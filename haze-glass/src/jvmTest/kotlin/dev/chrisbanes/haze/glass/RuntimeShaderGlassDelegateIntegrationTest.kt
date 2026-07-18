// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
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
  fun idleInteractiveEffect_doesNotAllocateInteractionStages() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()

    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.interactionFrameCount).isEqualTo(0)
    assertThat(delegate.layers.hasInteractionOptical).isFalse()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isFalse()
    assertThat(delegate.layers.hasInteractionLighting).isFalse()
  }

  @Test
  fun interactionFrames_doNotRebuildBaseEffectsOrRecordSource() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val opticalBuilds = delegate.baseOpticalEffectCreationCount
    val sourceRecords = delegate.sourceRecordCount

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
      moveTo(Offset(80f, 60f))
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    assertThat(delegate.baseOpticalEffectCreationCount).isEqualTo(opticalBuilds)
    assertThat(delegate.sourceRecordCount).isEqualTo(sourceRecords)
    assertThat(delegate.interactionFrameCount).isGreaterThan(0)
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
  }

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
    val effect = activeDetailEffect().apply {
      optics = (optics as GlassOptics.Absolute).copy(refractionScale = 0f)
    }

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
    val effect = activeDetailEffect().apply {
      optics = (optics as GlassOptics.Absolute).copy(refractionStrength = 1e-6f)
    }

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
    val effect = activeDetailEffect().apply {
      optics = (optics as GlassOptics.Absolute).copy(refractionStrength = .1f)
    }

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
    optics = GlassOptics.Absolute(
      refractionStrength = 0.5f,
      refractionScale = 20f,
      blurRadius = 0.dp,
    )
    specularIntensity = 0f
  }

  private fun runtimeInteractiveEffect() = activeDetailEffect().apply {
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
    }
    interactionLightRadiusFraction = 0.7f
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  }

  @Composable
  private fun RuntimeGlassTestContent(effect: GlassVisualEffect, tag: String) {
    val hazeState = remember { HazeState() }
    Box(Modifier.size(120.dp)) {
      Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
      Box(
        Modifier
          .fillMaxSize()
          .testTag(tag)
          .hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      )
    }
  }
}
