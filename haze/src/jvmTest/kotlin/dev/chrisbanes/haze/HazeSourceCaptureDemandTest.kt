// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeSourceCaptureDemandTest {

  @Test
  fun sourceWithoutAttachedEffect_drawsDirectlyWithoutCapture() = runComposeUiTest {
    val state = HazeState()

    setContent {
      Source(state)
    }
    waitForIdle()

    val area = state.areas.single()
    assertThat(area.contentLayer).isNull()
    assertThat(area.contentVersion).isEqualTo(0L)
    assertThat(onNodeWithTag(SOURCE_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun firstAttachedEffect_hasDrawableInputOnFirstDraw() = runComposeUiTest {
    val state = HazeState()
    val factory = DemandRecordingFactory()

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(state)
        Effect(state, factory)
      }
    }
    waitForIdle()

    val renderer = factory.renderer
    assertThat(renderer.snapshots.first()).isNotNull()
    assertThat(onNodeWithTag(EFFECT_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun lastEffectDetaches_releasesCapture() = runComposeUiTest {
    val state = HazeState()
    val showFirst = mutableStateOf(true)
    val showSecond = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(state)
        if (showFirst.value) Effect(state, DemandRecordingFactory())
        if (showSecond.value) Effect(state, DemandRecordingFactory())
      }
    }
    waitForIdle()
    val area = state.areas.single()
    assertThat(area.contentLayer).isNotNull()

    showFirst.value = false
    waitForIdle()
    assertThat(area.contentLayer).isNotNull()

    showSecond.value = false
    waitForIdle()
    assertThat(area.contentLayer).isNull()
    assertThat(onNodeWithTag(SOURCE_TAG).captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun effectStateChange_transfersCaptureDemand() = runComposeUiTest {
    val firstState = HazeState()
    val secondState = HazeState()
    val effectState = mutableStateOf(firstState)

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(firstState)
        Source(secondState)
        Effect(effectState.value, DemandRecordingFactory())
      }
    }
    waitForIdle()
    val firstArea = firstState.areas.first()
    val secondArea = secondState.areas.first()
    assertThat(firstArea.contentLayer).isNotNull()
    assertThat(secondArea.contentLayer).isNull()

    effectState.value = secondState
    waitForIdle()

    assertThat(firstArea.contentLayer).isNull()
    assertThat(secondArea.contentLayer).isNotNull()
  }

  @Test
  fun sourceStateChange_rebindsCaptureDemand() = runComposeUiTest {
    val activeState = HazeState()
    val dormantState = HazeState()
    val sourceState = mutableStateOf(activeState)

    setContent {
      Box(Modifier.size(100.dp)) {
        Source(sourceState.value)
        Effect(activeState, DemandRecordingFactory())
      }
    }
    waitForIdle()
    val activeArea = activeState.areas.single()
    assertThat(activeArea.contentLayer).isNotNull()

    sourceState.value = dormantState
    waitForIdle()
    val dormantArea = dormantState.areas.single()
    assertThat(activeArea.contentLayer).isNull()
    assertThat(dormantArea.contentLayer).isNull()

    sourceState.value = activeState
    waitForIdle()
    assertThat(activeArea.contentLayer).isNotNull()
  }

  @Composable
  private fun Source(state: HazeState) {
    Box(
      Modifier
        .fillMaxSize()
        .testTag(SOURCE_TAG)
        .hazeSource(state)
        .background(Color.Red),
    )
  }

  @Composable
  private fun Effect(state: HazeState, factory: DemandRecordingFactory) {
    Box(
      Modifier
        .fillMaxSize()
        .testTag(EFFECT_TAG)
        .hazeEffect(
          factory = factory,
          input = HazeInput.Sources(state),
          style = Unit,
        ),
    )
  }

  private companion object {
    const val SOURCE_TAG = "demand-source"
    const val EFFECT_TAG = "demand-effect"
  }
}

private class DemandRecordingFactory : HazeEffectFactory<Unit> {
  lateinit var renderer: DemandRecordingRenderer
    private set

  override fun createRenderer(): HazeEffectRenderer<Unit> = DemandRecordingRenderer().also {
    renderer = it
  }
}

@OptIn(InternalHazeApi::class)
private class DemandRecordingRenderer : HazeEffectRenderer<Unit> {
  val snapshots = mutableListOf<HazeEffectInputSnapshot?>()

  override fun HazeEffectDrawScope.draw(style: Unit) {
    snapshots += (this as HazeEffectRuntimeDrawScope).inputSnapshot
    drawInput()
  }
}
