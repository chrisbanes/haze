// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassTiltSampleTest : ContextTest() {
  @Test
  fun fixedMode_usesTheCenteredLightPosition() {
    val state = AndroidGlassTiltIntegration()

    assertThat(state.lightPosition).isEqualTo(GLASS_TILT_FIXED_LIGHT_POSITION)
    state.updateTiltEnabled(false)
    assertThat(state.lightPosition).isEqualTo(GLASS_TILT_FIXED_LIGHT_POSITION)
  }

  @Test
  fun gravity_updatesReachBothSidesOfCenterAndStayFiniteAndBounded() {
    val state = AndroidGlassTiltIntegration()
    state.updateTiltEnabled(true)

    state.onGravity(Offset(-100f, -100f), GlassTiltDisplayRotation.Rotation0)
    val leftPosition = state.lightPosition
    state.restartFromFixedPosition()
    state.onGravity(Offset(100f, 100f), GlassTiltDisplayRotation.Rotation0)
    val rightPosition = state.lightPosition

    assertThat(leftPosition.x < 0.5f).isTrue()
    assertThat(rightPosition.x > 0.5f).isTrue()
    assertThat(leftPosition.x.isFinite() && leftPosition.y.isFinite()).isTrue()
    assertThat(rightPosition.x.isFinite() && rightPosition.y.isFinite()).isTrue()
    assertThat(leftPosition.x >= GLASS_TILT_FIXED_LIGHT_POSITION.x - GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(leftPosition.x <= GLASS_TILT_FIXED_LIGHT_POSITION.x + GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(leftPosition.y >= GLASS_TILT_FIXED_LIGHT_POSITION.y - GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(leftPosition.y <= GLASS_TILT_FIXED_LIGHT_POSITION.y + GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(rightPosition.x >= GLASS_TILT_FIXED_LIGHT_POSITION.x - GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(rightPosition.x <= GLASS_TILT_FIXED_LIGHT_POSITION.x + GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(rightPosition.y >= GLASS_TILT_FIXED_LIGHT_POSITION.y - GLASS_TILT_MAX_DISPLACEMENT).isTrue()
    assertThat(rightPosition.y <= GLASS_TILT_FIXED_LIGHT_POSITION.y + GLASS_TILT_MAX_DISPLACEMENT).isTrue()
  }

  @Test
  fun displayRotation_mapsGravityIntoCurrentDisplayCoordinates() {
    val gravity = Offset(2f, 4f)

    assertThat(mapGlassTiltGravity(gravity, GlassTiltDisplayRotation.Rotation0)).isEqualTo(Offset(2f, 4f))
    assertThat(mapGlassTiltGravity(gravity, GlassTiltDisplayRotation.Rotation90)).isEqualTo(Offset(-4f, 2f))
    assertThat(mapGlassTiltGravity(gravity, GlassTiltDisplayRotation.Rotation180)).isEqualTo(Offset(-2f, -4f))
    assertThat(mapGlassTiltGravity(gravity, GlassTiltDisplayRotation.Rotation270)).isEqualTo(Offset(4f, -2f))
  }

  @Test
  fun invalidGravityAndUnavailableTilt_keepTheFixedPosition() {
    val state = AndroidGlassTiltIntegration()
    state.updateTiltEnabled(true)

    state.onGravity(Offset(Float.NaN, 1f), GlassTiltDisplayRotation.Rotation0)
    state.setTiltUnavailable()

    assertThat(state.lightPosition).isEqualTo(GLASS_TILT_FIXED_LIGHT_POSITION)
    assertThat(state.isTiltEnabled).isFalse()
    assertThat(state.isTiltUnavailable).isTrue()
  }

  @Test
  fun invalidGravity_restoresTheFixedPositionAfterTilt() {
    val state = AndroidGlassTiltIntegration()
    state.updateTiltEnabled(true)

    state.onGravity(Offset(6f, -6f), GlassTiltDisplayRotation.Rotation0)
    assertThat(state.lightPosition != GLASS_TILT_FIXED_LIGHT_POSITION).isTrue()
    state.onGravity(Offset(Float.NaN, 1f), GlassTiltDisplayRotation.Rotation0)
    assertThat(state.lightPosition).isEqualTo(GLASS_TILT_FIXED_LIGHT_POSITION)

    state.onGravity(Offset(-6f, 6f), GlassTiltDisplayRotation.Rotation0)
    assertThat(state.lightPosition != GLASS_TILT_FIXED_LIGHT_POSITION).isTrue()
    state.onGravity(Offset(1f, Float.NEGATIVE_INFINITY), GlassTiltDisplayRotation.Rotation0)
    assertThat(state.lightPosition).isEqualTo(GLASS_TILT_FIXED_LIGHT_POSITION)
  }

  @Test
  fun smoothing_convergesAndRestartsFromTheFixedPosition() {
    val state = AndroidGlassTiltIntegration()
    state.updateTiltEnabled(true)
    repeat(80) {
      state.onGravity(Offset(6f, 0f), GlassTiltDisplayRotation.Rotation0)
    }
    val settled = state.lightPosition

    state.restartFromFixedPosition()

    assertThat(settled.x > GLASS_TILT_FIXED_LIGHT_POSITION.x).isTrue()
    assertThat(state.lightPosition).isEqualTo(GLASS_TILT_FIXED_LIGHT_POSITION)
  }

  @Test
  fun tiltMode_registersOnlyWhileEnabledAndStopsOnDisposal() = runComposeUiTest {
    val sensor = FakeGravitySensor()
    val showSample = mutableStateOf(true)
    setContent {
      if (showSample.value) GlassTiltSample(onBack = {}, gravitySensor = sensor)
    }

    assertThat(sensor.startCount).isEqualTo(0)
    onNodeWithTag("glass_tilt_enable").performClick()
    waitForIdle()
    assertThat(sensor.startCount).isEqualTo(1)

    onNodeWithTag("glass_tilt_fixed").performClick()
    waitForIdle()
    assertThat(sensor.stopCount).isEqualTo(1)

    onNodeWithTag("glass_tilt_enable").performClick()
    waitForIdle()
    assertThat(sensor.startCount).isEqualTo(2)

    runOnIdle { showSample.value = false }
    waitForIdle()
    assertThat(sensor.stopCount).isEqualTo(2)
  }

  @Test
  fun unavailableGravitySensor_keepsFixedModeAndExplainsTheFallback() = runComposeUiTest {
    setContent {
      GlassTiltSample(onBack = {}, gravitySensor = FakeGravitySensor(available = false))
    }

    onNodeWithTag("glass_tilt_enable").performClick()

    onNodeWithTag("glass_tilt_unavailable").assertIsDisplayed()
  }

  @Test
  fun registrationFailure_retriesWhenTheLifecycleResumes() = runComposeUiTest {
    val lifecycleOwner = TestLifecycleOwner().apply { lifecycleRegistry.currentState = Lifecycle.State.RESUMED }
    val sensor = FakeGravitySensor(failuresBeforeSuccess = 1)
    setContent {
      CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
        GlassTiltSample(onBack = {}, gravitySensor = sensor)
      }
    }

    onNodeWithTag("glass_tilt_enable").performClick()
    waitForIdle()
    assertThat(sensor.startCount).isEqualTo(1)
    assertThat(sensor.stopCount).isEqualTo(0)
    onNodeWithText("Light position: 50%, 50%").assertIsDisplayed()
    onNodeWithTag("glass_tilt_unavailable").assertIsDisplayed()

    runOnIdle {
      lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.CREATED
      lifecycleOwner.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
    waitForIdle()
    assertThat(sensor.startCount).isEqualTo(2)
    onAllNodesWithText("Tilt unavailable.").assertCountEquals(0)
  }

  @Test
  fun embeddedSample_hidesBackNavigation() = runComposeUiTest {
    setContent {
      CompositionLocalProvider(LocalSampleNavigationEnabled provides false) {
        GlassTiltSample(onBack = {}, gravitySensor = FakeGravitySensor())
      }
    }

    onAllNodesWithText("Back").assertCountEquals(0)
  }

  private class FakeGravitySensor(
    private val available: Boolean = true,
    private var failuresBeforeSuccess: Int = 0,
  ) : GlassTiltGravitySensor {
    var startCount = 0
    var stopCount = 0

    override val isAvailable: Boolean
      get() = available

    override fun start(onGravity: (Offset) -> Unit): Boolean {
      startCount++
      return if (failuresBeforeSuccess > 0) {
        failuresBeforeSuccess--
        false
      } else {
        available
      }
    }

    override fun stop() {
      stopCount++
    }
  }

  private class TestLifecycleOwner : LifecycleOwner {
    val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
      get() = lifecycleRegistry
  }
}
