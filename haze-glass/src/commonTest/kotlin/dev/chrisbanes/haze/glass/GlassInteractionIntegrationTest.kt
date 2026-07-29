// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectNode
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalHazeApi::class,
  InternalHazeApi::class,
)
class GlassInteractionIntegrationTest : ContextTest() {
  private val attachedRuntimes = mutableMapOf<GlassVisualEffect, GlassRuntimeEffect>()

  @Test
  fun interactiveGlass_doesNotBlockClick() = runComposeUiTest {
    val source = MutableInteractionSource()
    val effect = GlassVisualEffect().apply {
      pressed()
      interactionSource = source
    }
    var clicks = 0
    setContent {
      GlassTestFixture { hazeState ->
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect(hazeState) { trackRenderer(effect) }
            .clickable(interactionSource = source, indication = null, onClick = { clicks++ }),
        )
      }
    }

    onNodeWithTag("glass").performClick()

    assertThat(clicks).isEqualTo(1)
  }

  @Test
  fun scrollConsumesMovementAndCancelsOnlyRawPress() = runComposeUiTest {
    val scroll = ScrollState(0)
    val effect = GlassVisualEffect().apply {
      pressed()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      GlassTestFixture { hazeState ->
        Column(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect(hazeState) { trackRenderer(effect) }
            .verticalScroll(scroll),
        ) { Spacer(Modifier.height(400.dp)) }
      }
    }

    onNodeWithTag("glass").performTouchInput {
      down(center)
      moveBy(Offset(0f, -60f))
      up()
    }
    waitForIdle()

    assertThat(scroll.value).isGreaterThan(0)
    assertThat(runtime(effect).currentInteractionSignals.rawPressed).isEqualTo(false)
  }

  @Test
  fun mouseHoverTracksPointerAndEndsOnExit() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      hovered()
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      GlassTestFixture { hazeState ->
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect(hazeState) { trackRenderer(effect) },
        )
      }
    }

    onNodeWithTag("glass").performMouseInput {
      enter(Offset(10f, 20f))
      moveTo(Offset(70f, 60f))
    }
    waitForIdle()
    assertThat(runtime(effect).currentInteractionState.position).isEqualTo(Offset(70f, 60f))
    assertThat(runtime(effect).currentInteractionSignals.rawHovered).isEqualTo(true)

    onNodeWithTag("glass").performMouseInput { exit() }
    waitForIdle()

    assertThat(runtime(effect).currentInteractionSignals.rawHovered).isEqualTo(false)
  }

  @Test
  fun interactionFrames_doNotRecomposeConfiguration() = runComposeUiTest {
    val effect = GlassVisualEffect().apply { pressed() }
    var compositions = 0
    setContent {
      SideEffect { compositions++ }
      GlassTestFixture { hazeState ->
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect(hazeState) { trackRenderer(effect) },
        )
      }
    }
    waitForIdle()
    val initial = compositions

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
      moveTo(Offset(80f, 80f))
      up()
    }
    mainClock.advanceTimeBy(1_000)

    assertThat(compositions).isEqualTo(initial)
  }

  @Test
  fun sourceFocusAndPress_useCenterThenReleaseToIdentity() = runComposeUiTest {
    val source = MutableInteractionSource()
    val focus = FocusInteraction.Focus()
    val press = PressInteraction.Press(Offset.Unspecified)
    val effect = GlassVisualEffect().apply {
      focused()
      pressed()
      interactionSource = source
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent {
      GlassTestFixture { hazeState ->
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .hazeEffect(hazeState) { trackRenderer(effect) },
        )
      }
    }
    waitForIdle()

    source.tryEmit(focus)
    source.tryEmit(press)
    waitForIdle()

    assertThat(runtime(effect).currentInteractionState.position).isEqualTo(Offset(50f, 50f))
    assertThat(runtime(effect).currentInteractionState.lightingIntensity).isEqualTo(1f)

    source.tryEmit(PressInteraction.Release(press))
    source.tryEmit(FocusInteraction.Unfocus(focus))
    waitForIdle()

    assertThat(runtime(effect).currentInteractionState).isEqualTo(
      GlassInteractionRenderState(position = Offset(50f, 50f)),
    )
  }

  private fun HazeEffectScope.trackRenderer(effect: GlassVisualEffect) {
    visualEffect = effect
    attachedRuntimes[effect] =
      ((this as HazeEffectNode).activeVisualEffect as GlassRenderer).runtimeForTest
  }

  private fun runtime(effect: GlassVisualEffect): GlassRuntimeEffect =
    checkNotNull(attachedRuntimes[effect])
}

@Composable
private fun GlassTestFixture(content: @Composable (HazeState) -> Unit) {
  val hazeState = remember { HazeState() }
  Box(Modifier.size(100.dp)) {
    Box(Modifier.fillMaxSize().background(Color(0xFF2874A6)).hazeSource(hazeState))
    Box(Modifier.fillMaxSize()) { content(hazeState) }
  }
}
