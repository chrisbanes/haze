// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeComposeUnitTests : ContextTest() {
  @Test
  fun testChangingHazeState() = runComposeUiTest {
    val hazeState1 = HazeState()
    val hazeState2 = HazeState()

    val selectedHazeState = mutableStateOf(hazeState1)

    setContent {
      Box(Modifier.hazeSource(selectedHazeState.value)) {
        Spacer(
          Modifier.hazeEffect(selectedHazeState.value),
        )
      }
    }

    // Assert that the HazeArea is in hazeState1
    assertThat(hazeState1.areas).hasSize(1)
    assertThat(hazeState2.areas).isEmpty()

    // Update the selected HazeState and wait for idle
    selectedHazeState.value = hazeState2
    waitForIdle()

    // Assert that the HazeArea moved to hazeState2
    assertThat(hazeState1.areas).isEmpty()
    assertThat(hazeState2.areas).hasSize(1)
  }

  @Test
  fun test_zeroSize() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box {
        Spacer(
          Modifier
            .hazeSource(hazeState)
            .size(width = 0.dp, height = 30.dp),
        )

        Spacer(
          Modifier
            .hazeEffect(hazeState)
            .size(width = 30.dp, height = 0.dp),
        )
      }
    }

    waitForIdle()
  }

  @Test
  fun testDefaultPositionStrategyIsAuto() {
    val state = HazeState()
    assertThat(state.positionStrategy).isEqualTo(HazePositionStrategy.Auto)
    // resolvedStrategy is no longer updated by effects; it's always Local by default
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testExplicitLocalStrategy() = runComposeUiTest {
    val state = HazeState().apply {
      positionStrategy = HazePositionStrategy.Local
    }
    setContent {
      Box(Modifier.hazeSource(state)) {
        Spacer(Modifier.hazeEffect(state).size(30.dp))
      }
    }
    waitForIdle()
    // resolvedStrategy is no longer updated by effects; it's always Local by default
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testExplicitScreenStrategy() = runComposeUiTest {
    val state = HazeState().apply {
      positionStrategy = HazePositionStrategy.Screen
    }
    setContent {
      Box(Modifier.hazeSource(state)) {
        Spacer(Modifier.hazeEffect(state).size(30.dp))
      }
    }
    waitForIdle()
    // resolvedStrategy is no longer updated by effects; it's always Local by default
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testAutoStrategyResolvesToLocalInSameWindow() = runComposeUiTest {
    val state = HazeState()
    setContent {
      Box(Modifier.hazeSource(state)) {
        Spacer(Modifier.hazeEffect(state).size(30.dp))
      }
    }
    waitForIdle()
    // resolvedStrategy is no longer updated by effects; it's always Local by default
    assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testResolvePositionStrategy_autoPromotesToScreenForCrossWindow() {
    val area = HazeAreaTestFactory.create(windowId = "dialog-window")

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Auto,
      areas = listOf(area),
      windowId = "host-window",
    )

    assertThat(resolved).isEqualTo(HazePositionStrategy.Screen)
  }

  @Test
  fun testResolvePositionStrategy_autoResolvesToLocalForNullWindowIdAreas() {
    val area1 = HazeAreaTestFactory.create(windowId = null)
    val area2 = HazeAreaTestFactory.create(windowId = null)

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Auto,
      areas = listOf(area1, area2),
      windowId = "host-window",
    )

    assertThat(resolved).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testResolvePositionStrategy_autoResolvesToLocalForMatchingWindowId() {
    val area = HazeAreaTestFactory.create(windowId = "host-window")

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Auto,
      areas = listOf(area),
      windowId = "host-window",
    )

    // Same window is treated as local
    assertThat(resolved).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testResolvePositionStrategy_passesThroughExplicitLocal() {
    val area = HazeAreaTestFactory.create(windowId = "dialog-window")

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Local,
      areas = listOf(area),
      windowId = "host-window",
    )

    assertThat(resolved).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun testResolvePositionStrategy_passesThroughExplicitScreen() {
    val area = HazeAreaTestFactory.create(windowId = null)

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Screen,
      areas = listOf(area),
      windowId = "host-window",
    )

    assertThat(resolved).isEqualTo(HazePositionStrategy.Screen)
  }

  @Test
  fun testResolvePositionStrategy_autoPromotesToScreenForMixedWindowIds() {
    val area1 = HazeAreaTestFactory.create(windowId = "dialog-window")
    val area2 = HazeAreaTestFactory.create(windowId = "host-window")

    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Auto,
      areas = listOf(area1, area2),
      windowId = "host-window",
    )

    // One area is in a different window — promote to Screen
    assertThat(resolved).isEqualTo(HazePositionStrategy.Screen)
  }

  @Test
  fun testResolvePositionStrategy_emptyAreasResolvesToLocal() {
    val resolved = resolvePositionStrategy(
      configured = HazePositionStrategy.Auto,
      areas = emptyList(),
      windowId = "host-window",
    )

    assertThat(resolved).isEqualTo(HazePositionStrategy.Local)
  }

  @Test
  fun test_effect_areas_updated_when_source_added_after_first_draw() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = AreaCapturingVisualEffect()
    val showSecondSource = mutableStateOf(false)

    setContent {
      Box {
        Box(Modifier.hazeSource(hazeState)) { }
        if (showSecondSource.value) {
          Spacer(Modifier.hazeSource(hazeState).size(10.dp))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.lastAreas).hasSize(1)

    showSecondSource.value = true
    waitForIdle()
    assertThat(effect.lastAreas).hasSize(2)
  }

  @Test
  fun test_effect_areas_updated_when_source_removed_after_first_draw() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = AreaCapturingVisualEffect()
    val showSecondSource = mutableStateOf(true)

    setContent {
      Box {
        Box(Modifier.hazeSource(hazeState)) { }
        if (showSecondSource.value) {
          Spacer(Modifier.hazeSource(hazeState).size(10.dp))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.lastAreas).hasSize(2)

    showSecondSource.value = false
    waitForIdle()
    assertThat(effect.lastAreas).hasSize(1)
  }

  @Test
  fun test_effect_areas_updated_when_state_swapped_after_first_draw() = runComposeUiTest {
    val state1 = HazeState()
    val state2 = HazeState()
    val effect = AreaCapturingVisualEffect()
    val selectedState = mutableStateOf(state1)

    setContent {
      Box {
        Spacer(Modifier.hazeSource(state1).size(10.dp))
        Spacer(Modifier.hazeSource(state2).size(10.dp))
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(selectedState.value) {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.lastAreas).hasSize(1)
    val initialArea = effect.lastAreas.single()

    selectedState.value = state2
    waitForIdle()
    assertThat(effect.lastAreas).hasSize(1)
    val swappedArea = effect.lastAreas.single()
    assertThat(swappedArea !== initialArea).isTrue()
  }
}

internal class AreaCapturingVisualEffect : VisualEffect {
  var lastAreas: List<HazeArea> = emptyList()

  override fun update(context: VisualEffectContext) {
    lastAreas = context.areas
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}

internal object HazeAreaTestFactory {
  fun create(windowId: Any?): HazeArea = HazeArea().also { it.windowId = windowId }
}
