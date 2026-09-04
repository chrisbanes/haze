// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeEffectInputTest {

  @Test
  fun content_capturesTheModifierOwnContent() = runComposeUiTest {
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag(EFFECT_TAG)
          .hazeEffect(
            factory = PassthroughFactory,
            input = HazeInput.Content,
            style = Unit,
          )
          .background(Color.Red),
      )
    }

    assertThat(effectCenterColor()).isEqualTo(Color.Red)
  }

  @Test
  fun sourcesWhere_filtersStableInfoAndComposesWithAnd() = runComposeUiTest {
    val state = HazeState()
    val selection = HazeSourceSelection.Behind
      .where { info -> info.zIndex > 0f }
      .where { info -> (info.key as? String)?.startsWith("keep") == true }

    setContent {
      Box(Modifier.size(100.dp)) {
        source(state, "keep-low", 0f, Color.Red)
        source(state, "drop-high", 1f, Color.Blue)
        source(state, "keep-high", 2f, Color.Green)
        effect(state, selection)
      }
    }

    assertThat(effectCenterColor()).isEqualTo(Color.Green)
  }

  @Test
  fun sourcesWhere_behindUsesNearestMatchingAncestorAcrossNestedStates() = runComposeUiTest {
    val unrelatedState = HazeState()
    val innerState = HazeState()

    setContent {
      Box(Modifier.size(100.dp)) {
        source(innerState, "inner", 1f, Color.Green) {
          source(innerState, "lower", 0f, Color.Yellow)
          source(unrelatedState, "unrelated", 2f, Color.Blue) {
            effect(innerState, HazeSourceSelection.Behind)
          }
        }
      }
    }

    assertThat(effectCenterColor()).isEqualTo(Color.Yellow)
  }

  @Test
  fun sourcesWhere_behindUsesNearestOfMultipleMatchingAncestors() = runComposeUiTest {
    val state = HazeState()
    val unrelatedState = HazeState()

    setContent {
      Box(Modifier.size(100.dp)) {
        source(state, "outer", 3f, Color.Red) {
          source(state, "inner", 1f, Color.Green) {
            source(state, "lower", 0f, Color.Yellow)
            source(unrelatedState, "unrelated", 2f, Color.Blue) {
              effect(state, HazeSourceSelection.Behind)
            }
          }
        }
      }
    }

    assertThat(effectCenterColor()).isEqualTo(Color.Yellow)
  }

  @Test
  fun sourcesWhere_reactsToPredicateStateChanges() = runComposeUiTest {
    val state = HazeState()
    val selectedKey = mutableStateOf("first")
    val selection = HazeSourceSelection.All.where { info -> info.key == selectedKey.value }

    setContent {
      Box(Modifier.size(100.dp)) {
        source(state, "first", 0f, Color.Red)
        source(state, "second", 1f, Color.Blue)
        effect(state, selection)
      }
    }

    assertThat(effectCenterColor()).isEqualTo(Color.Red)
    selectedKey.value = "second"
    waitForIdle()
    assertThat(effectCenterColor()).isEqualTo(Color.Blue)
  }

  @Test
  fun sourcesWhere_doesNotReevaluateForUnrelatedStyleChange() = runComposeUiTest {
    val state = HazeState()
    val style = mutableStateOf(false)
    var predicateCalls = 0
    val selection = HazeSourceSelection.All.where {
      predicateCalls++
      true
    }

    setContent {
      Box(Modifier.size(100.dp)) {
        source(state, "first", 0f, Color.Red)
        source(state, "second", 1f, Color.Blue)
        Box(
          Modifier
            .fillMaxSize()
            .testTag(EFFECT_TAG)
            .hazeEffect(
              factory = BooleanPassthroughFactory,
              input = HazeInput.Sources(state, selection),
              style = style.value,
            ),
        )
      }
    }
    waitForIdle()

    val callsBeforeStyleChange = predicateCalls
    style.value = true
    waitForIdle()

    assertThat(predicateCalls).isEqualTo(callsBeforeStyleChange)
  }

  @Test
  fun sourceSnapshot_changesWhenEffectMovesRelativeToSource() = runComposeUiTest {
    val state = HazeState()
    val effectOffset = mutableStateOf(IntOffset.Zero)
    val factory = RecordingRendererFactory(::SnapshotRenderer)

    setContent {
      Box(Modifier.size(100.dp)) {
        source(state, "source", 0f, Color.Red)
        Box(
          Modifier
            .fillMaxSize()
            .offset { effectOffset.value }
            .hazeEffect(
              factory = factory,
              input = HazeInput.Sources(state),
              style = Unit,
            ),
        )
      }
    }
    waitForIdle()

    val renderer = factory.renderers.single()
    val initial = renderer.snapshots.last()
    assertThat(renderer.reusesUnchangedSnapshot).isTrue()

    effectOffset.value = IntOffset(10, 12)
    waitForIdle()

    assertThat(renderer.snapshots.last()).isNotEqualTo(initial)
  }

  @Test
  fun sampling_isDeliveredToRenderer() = runComposeUiTest {
    val sampling = mutableStateOf<HazeSampling>(HazeSampling.Default)
    val factory = RecordingRendererFactory(::SamplingRenderer)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = Unit,
            sampling = sampling.value,
          ),
      )
    }
    waitForIdle()

    val renderer = factory.renderers.single()
    assertThat(renderer.sampling).isEqualTo(HazeSampling.Default)
    sampling.value = HazeSampling.FullResolution
    waitForIdle()
    assertThat(renderer.sampling).isEqualTo(HazeSampling.FullResolution)
    sampling.value = HazeSampling.Fixed(0.6f)
    waitForIdle()
    assertThat(renderer.sampling).isEqualTo(HazeSampling.Fixed(0.6f))
  }

  @Test
  fun sourcesClearWhenUnavailable_clearsRetainedOutput() = runComposeUiTest {
    val state = HazeState()
    val factory = RecordingRendererFactory(::RetainedOutputRenderer)
    val showSource = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          source(state, "source", 0f, Color.Red)
        }
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(
              factory = factory,
              input = HazeInput.Sources(
                state = state,
                retention = HazeSourceRetention.ClearWhenUnavailable,
              ),
              style = Unit,
            ),
        )
      }
    }
    waitForIdle()

    val renderer = factory.renderers.single()
    val clearsBeforeRemoval = renderer.clearCalls
    showSource.value = false
    waitForIdle()

    assertThat(renderer.clearCalls).isGreaterThan(clearsBeforeRemoval)
  }

  @Test
  fun sourcesKeepLastFrame_preservesRetainedOutput() = runComposeUiTest {
    val state = HazeState()
    val factory = RecordingRendererFactory(::RetainedOutputRenderer)
    val showSource = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          source(state, "source", 0f, Color.Red)
        }
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(
              factory = factory,
              input = HazeInput.Sources(state),
              style = Unit,
            ),
        )
      }
    }
    waitForIdle()

    val renderer = factory.renderers.single()
    val drawsBeforeRemoval = renderer.drawCalls
    val clearsBeforeRemoval = renderer.clearCalls
    showSource.value = false
    waitForIdle()

    assertThat(renderer.clearCalls).isEqualTo(clearsBeforeRemoval)
    assertThat(renderer.drawCalls).isGreaterThan(drawsBeforeRemoval)
  }

  @Test
  fun sourcesKeepLastFrame_factoryReplacementDoesNotReusePreviousRendererOutput() =
    runComposeUiTest {
      val state = HazeState()
      val firstFactory = RecordingRendererFactory(::RetainedOutputRenderer)
      val secondFactory = RecordingRendererFactory(::RetainedOutputRenderer)
      val factory = mutableStateOf<HazeEffectFactory<Unit>>(firstFactory)
      val showSource = mutableStateOf(true)

      setContent {
        Box(Modifier.size(100.dp)) {
          if (showSource.value) {
            source(state, "source", 0f, Color.Red)
          }
          Box(
            Modifier
              .fillMaxSize()
              .hazeEffect(
                factory = factory.value,
                input = HazeInput.Sources(state),
                style = Unit,
              ),
          )
        }
      }
      waitForIdle()

      assertThat(firstFactory.renderers.single().drawCalls).isGreaterThan(0)
      showSource.value = false
      factory.value = secondFactory
      waitForIdle()

      assertThat(secondFactory.renderers.single().drawCalls).isEqualTo(0)
    }

  @Test
  fun expandLayerBounds_disabledSkipsRendererExpansion() = runComposeUiTest {
    val factory = RecordingRendererFactory(::BoundsRenderer)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = Unit,
            expandLayerBounds = false,
          ),
      )
    }
    waitForIdle()

    val renderer = factory.renderers.single()
    assertThat(renderer.calculateCalls).isEqualTo(0)
  }

  private fun androidx.compose.ui.test.ComposeUiTest.effectCenterColor(): Color =
    onNodeWithTag(EFFECT_TAG).captureToImage().toPixelMap()[50, 50]

  private companion object {
    const val EFFECT_TAG = "effect"
  }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.source(
  state: HazeState,
  key: String,
  zIndex: Float,
  color: Color,
  content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
  Box(
    Modifier
      .fillMaxSize()
      .hazeSource(state, zIndex = zIndex, key = key)
      .background(color),
    content = content,
  )
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.effect(
  state: HazeState,
  selection: HazeSourceSelection,
) {
  Box(
    Modifier
      .fillMaxSize()
      .testTag("effect")
      .hazeEffect(
        factory = PassthroughFactory,
        input = HazeInput.Sources(state, selection),
        style = Unit,
      ),
  )
}

private data object PassthroughFactory : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = PassthroughRenderer()
}

private data object BooleanPassthroughFactory : HazeEffectFactory<Boolean> {
  override fun createRenderer(): HazeEffectRenderer<Boolean> = BooleanPassthroughRenderer()
}

private class PassthroughRenderer : HazeEffectRenderer<Unit> {
  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
}

private class BooleanPassthroughRenderer : HazeEffectRenderer<Boolean> {
  override fun HazeEffectDrawScope.draw(style: Boolean) = drawInput()
}

private class RecordingRendererFactory<Style, Renderer : HazeEffectRenderer<Style>>(
  private val rendererProvider: () -> Renderer,
) : HazeEffectFactory<Style> {
  val renderers = mutableListOf<Renderer>()

  override fun createRenderer(): HazeEffectRenderer<Style> {
    val renderer = rendererProvider()
    renderers.add(renderer)
    return renderer
  }
}

private class SamplingRenderer : HazeEffectRenderer<Unit> {
  var sampling: HazeSampling? = null

  override fun HazeEffectDrawScope.draw(style: Unit) {
    this@SamplingRenderer.sampling = this.sampling
  }
}

@OptIn(InternalHazeApi::class)
private class SnapshotRenderer : HazeEffectRenderer<Unit> {
  val snapshots = mutableListOf<HazeEffectInputSnapshot?>()
  var reusesUnchangedSnapshot = true
    private set

  override fun HazeEffectDrawScope.draw(style: Unit) {
    val runtimeScope = this as HazeEffectRuntimeDrawScope
    val snapshot = runtimeScope.inputSnapshot
    snapshots += snapshot
    reusesUnchangedSnapshot = reusesUnchangedSnapshot && snapshot === runtimeScope.inputSnapshot
    drawInput()
  }
}

@OptIn(InternalHazeApi::class)
private class RetainedOutputRenderer :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererRetainedOutput {
  var drawCalls = 0
  var clearCalls = 0

  override fun HazeEffectDrawScope.draw(style: Unit) {
    drawCalls++
    drawInput()
  }

  override fun canDrawRetainedOutput(): Boolean = true

  override fun clearRetainedOutput() {
    clearCalls++
  }
}

private class BoundsRenderer : HazeEffectRenderer<Unit> {
  var calculateCalls = 0

  override fun HazeEffectDrawScope.draw(style: Unit) = Unit

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: Unit): Rect {
    calculateCalls++
    return modifierBounds.inflate(10f)
  }
}
