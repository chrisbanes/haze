// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeEffectInputTest {

  @Test
  fun content_capturesTheModifierOwnContent() = runComposeUiTest {
    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("effect")
          .hazeEffect(input = HazeInput.Content) {
            visualEffect = DrawSourceLayersVisualEffect
          }
          .background(Color.Red),
      )
    }

    val pixels = onNodeWithTag("effect").captureToImage().toPixelMap()
    assertThat(pixels[50, 50]).isEqualTo(Color.Red)
  }

  @Test
  fun sourcesWhere_filtersStableInfoAndComposesWithAnd() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()
    val selection = HazeSourceSelection.Behind
      .where { info -> info.zIndex > 0f }
      .where { info -> (info.key as? String)?.startsWith("keep") == true }

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 0f, key = "keep-low")
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 1f, key = "drop-high")
            .background(Color.Blue),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 2f, key = "keep-high")
            .background(Color.Green),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(
              input = HazeInput.Sources(state, selection = selection),
            ) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    assertThat(effect.areaKeys).containsExactly("keep-high")
  }

  @Test
  fun sourcesWhere_reactsToPredicateStateChanges() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()
    val selectedKey = mutableStateOf("first")
    val selection = HazeSourceSelection.All.where { info -> info.key == selectedKey.value }

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.fillMaxSize().hazeSource(state, key = "first"))
        Spacer(Modifier.fillMaxSize().hazeSource(state, key = "second"))
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeEffect(input = HazeInput.Sources(state, selection = selection)) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()
    assertThat(effect.areaKeys).containsExactly("first")

    selectedKey.value = "second"
    waitForIdle()

    assertThat(effect.areaKeys).containsExactly("second")
  }

  @Test
  fun sourcesWhere_doesNotReevaluateForUnrelatedObservedState() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()
    val drawContentBehind = mutableStateOf(false)
    var predicateCalls = 0
    val selection = HazeSourceSelection.All.where {
      predicateCalls++
      true
    }

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.fillMaxSize().hazeSource(state, key = "first"))
        Spacer(Modifier.fillMaxSize().hazeSource(state, key = "second"))
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeEffect(input = HazeInput.Sources(state, selection = selection)) {
              this.drawContentBehind = drawContentBehind.value
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val callsBeforeBlockChange = predicateCalls
    drawContentBehind.value = true
    waitForIdle()

    assertThat(predicateCalls).isEqualTo(callsBeforeBlockChange)
  }

  @Test
  fun sourcesWhere_evaluatesOnceForSourceMetadataChanges() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()
    val sourceKey = mutableStateOf("first")
    var predicateCalls = 0
    val selection = HazeSourceSelection.All.where {
      predicateCalls++
      true
    }

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.fillMaxSize().hazeSource(state, key = sourceKey.value))
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeEffect(input = HazeInput.Sources(state, selection = selection)) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    predicateCalls = 0
    sourceKey.value = "renamed"
    waitForIdle()

    assertThat(predicateCalls).isEqualTo(1)
    assertThat(effect.areaKeys).containsExactly("renamed")
  }

  @Test
  fun sources_reactsToKeyZIndexAndMembershipChanges() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()
    val firstKey = mutableStateOf("first")
    val firstZIndex = mutableStateOf(0f)
    val showThird = mutableStateOf(false)

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = firstZIndex.value, key = firstKey.value),
        )
        Spacer(Modifier.fillMaxSize().hazeSource(state, zIndex = 1f, key = "second"))
        if (showThird.value) {
          Spacer(Modifier.fillMaxSize().hazeSource(state, zIndex = 2f, key = "third"))
        }
        Spacer(
          Modifier
            .fillMaxSize()
            .hazeEffect(input = HazeInput.Sources(state)) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()
    assertThat(effect.areaKeys).containsExactly("first", "second")

    firstKey.value = "renamed"
    firstZIndex.value = 3f
    showThird.value = true
    waitForIdle()

    assertThat(effect.areaKeys).containsExactly("second", "third", "renamed")
  }

  @Test
  fun sourcesBehind_usesSameStateAncestorRelationship() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 0f, key = "behind")
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 2f, key = "ahead")
            .background(Color.Green),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 1f, key = "ancestor")
            .background(Color.Blue),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeEffect(input = HazeInput.Sources(state)) {
                visualEffect = effect
              },
          )
        }
      }
    }
    waitForIdle()

    assertThat(effect.areaKeys).containsExactly("behind")
  }

  @Test
  fun sourcesAll_bypassesAncestorRelationship() = runComposeUiTest {
    val state = HazeState()
    val effect = RecordingSourceKeysVisualEffect()
    val selection = HazeSourceSelection.All.where { info -> info.key != "ancestor" }

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 0f, key = "behind")
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 2f, key = "ahead")
            .background(Color.Green),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(state, zIndex = 1f, key = "ancestor")
            .background(Color.Blue),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeEffect(
                input = HazeInput.Sources(state, selection = selection),
              ) {
                visualEffect = effect
              },
          )
        }
      }
    }
    waitForIdle()

    assertThat(effect.areaKeys).containsExactly("behind", "ahead")
  }

  @Test
  fun sampling_mapsAllChoicesToExistingEffectSemantics() = runComposeUiTest {
    val sampling = mutableStateOf<HazeSampling>(HazeSampling.Default)
    val effect = SamplingRecordingVisualEffect()

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            input = HazeInput.Content,
            sampling = sampling.value,
          ) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      )
    }
    waitForIdle()

    assertThat(effect.inputScale).isEqualTo(HazeInputScale.Default)

    sampling.value = HazeSampling.FullResolution
    waitForIdle()
    assertThat(effect.inputScale).isEqualTo(HazeInputScale.None)

    sampling.value = HazeSampling.Adaptive
    waitForIdle()
    assertThat(effect.inputScale).isEqualTo(HazeInputScale.Auto)

    sampling.value = HazeSampling.Fixed(0.6f)
    waitForIdle()
    assertThat(effect.inputScale).isEqualTo(HazeInputScale.Fixed(0.6f))
  }

  @Test
  fun sourcesClearWhenUnavailable_clearsRetainedOutput() = runComposeUiTest {
    val state = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val showSource = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Spacer(Modifier.size(100.dp).hazeSource(state))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(
              input = HazeInput.Sources(
                state = state,
                retention = HazeSourceRetention.ClearWhenUnavailable,
              ),
            ) {
              retainOutputWhenSourceUnavailable = true
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val drawsBeforeRemoval = effect.drawCalls
    val clearsBeforeRemoval = effect.clearCalls
    showSource.value = false
    waitForIdle()

    assertThat(effect.clearCalls).isGreaterThan(clearsBeforeRemoval)
    assertThat(effect.drawCalls).isEqualTo(drawsBeforeRemoval)
  }

  @Test
  fun sourcesKeepLastFrame_preservesRetainedOutput() = runComposeUiTest {
    val state = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val showSource = mutableStateOf(true)

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Spacer(Modifier.size(100.dp).hazeSource(state))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(input = HazeInput.Sources(state)) {
              retainOutputWhenSourceUnavailable = false
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val drawsBeforeRemoval = effect.drawCalls
    val clearsBeforeRemoval = effect.clearCalls
    showSource.value = false
    waitForIdle()

    assertThat(effect.clearCalls).isEqualTo(clearsBeforeRemoval)
    assertThat(effect.drawCalls).isGreaterThan(drawsBeforeRemoval)
    assertThat(effect.lastDrawAreaCount).isEqualTo(0)
  }

  @Test
  fun sourcesKeepLastFrame_preservesOutputWhenEquivalentSelectionIsRebuilt() = runComposeUiTest {
    val state = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val recomposition = mutableStateOf(0)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testTag("root-${recomposition.value}"),
      ) {
        Spacer(Modifier.size(100.dp).hazeSource(state, key = "source"))
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(
              input = HazeInput.Sources(
                state = state,
                selection = HazeSourceSelection.All.where { info -> info.key == "source" },
                retention = HazeSourceRetention.KeepLastFrame,
              ),
            ) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val clearsBeforeRecomposition = effect.clearCalls
    recomposition.value++
    waitForIdle()

    assertThat(effect.clearCalls).isEqualTo(clearsBeforeRecomposition)
  }

  @Test
  fun sourcesStateChange_clearsRetainedOutput() = runComposeUiTest {
    val inputState = mutableStateOf(HazeState())
    val effect = RetainedOutputRecordingVisualEffect()

    setContent {
      val currentState = inputState.value
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.size(100.dp).hazeSource(currentState))
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(input = HazeInput.Sources(currentState)) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val clearsBeforeStateChange = effect.clearCalls
    inputState.value = HazeState()
    waitForIdle()

    assertThat(effect.clearCalls).isGreaterThan(clearsBeforeStateChange)
  }

  @Test
  fun inputModeChange_clearsRetainedOutput() = runComposeUiTest {
    val state = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val useContentInput = mutableStateOf(false)

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.size(100.dp).hazeSource(state))
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(
              input = if (useContentInput.value) {
                HazeInput.Content
              } else {
                HazeInput.Sources(state)
              },
            ) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val clearsBeforeModeChange = effect.clearCalls
    useContentInput.value = true
    waitForIdle()

    assertThat(effect.clearCalls).isGreaterThan(clearsBeforeModeChange)
  }

  @Test
  fun sourcesKeepLastFrame_preservesOutputWhenSelectionBecomesEmpty() = runComposeUiTest {
    val state = HazeState()
    val effect = RetainedOutputRecordingVisualEffect()
    val includeSource = mutableStateOf(true)
    val selection = HazeSourceSelection.All.where { includeSource.value }

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(Modifier.size(100.dp).hazeSource(state))
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(
              input = HazeInput.Sources(
                state = state,
                selection = selection,
              ),
            ) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    val drawsBeforeExclusion = effect.drawCalls
    val clearsBeforeExclusion = effect.clearCalls
    includeSource.value = false
    waitForIdle()

    assertThat(effect.clearCalls).isEqualTo(clearsBeforeExclusion)
    assertThat(effect.drawCalls).isGreaterThan(drawsBeforeExclusion)
    assertThat(effect.lastDrawAreaCount).isEqualTo(0)
  }

  @Test
  fun expandLayerBounds_defaultsEnabled() = runComposeUiTest {
    val effect = BoundsExpandingVisualEffect()

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(input = HazeInput.Content) {
            expandLayerBounds = false
            visualEffect = effect
          },
      )
    }
    waitForIdle()

    assertThat(effect.layerSize).isEqualTo(
      Size(
        width = effect.size.width + 20f,
        height = effect.size.height + 20f,
      ),
    )
  }

  @Test
  fun expandLayerBounds_disabledSkipsEffectExpansion() = runComposeUiTest {
    val effect = BoundsExpandingVisualEffect()

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .hazeEffect(
            input = HazeInput.Content,
            expandLayerBounds = false,
          ) {
            expandLayerBounds = true
            visualEffect = effect
          },
      )
    }
    waitForIdle()

    assertThat(effect.layerSize).isEqualTo(effect.size)
  }
}

private data object DrawSourceLayersVisualEffect : VisualEffect {
  override fun DrawScope.draw(context: VisualEffectContext) {
    context.areas.forEach { area ->
      area.contentLayer?.let { drawLayer(it) }
    }
  }
}

private class RecordingSourceKeysVisualEffect : VisualEffect {
  var areaKeys: List<Any?> = emptyList()

  override fun DrawScope.draw(context: VisualEffectContext) {
    areaKeys = context.areas.map(HazeArea::key)
  }
}

private class SamplingRecordingVisualEffect : VisualEffect {
  var inputScale: HazeInputScale? = null

  override fun DrawScope.draw(context: VisualEffectContext) {
    inputScale = context.inputScale
  }
}

private class BoundsExpandingVisualEffect : VisualEffect {
  var size: Size = Size.Unspecified
  var layerSize: Size = Size.Unspecified

  override fun calculateLayerBounds(rect: Rect, density: Density): Rect = rect.inflate(10f)

  override fun DrawScope.draw(context: VisualEffectContext) {
    this@BoundsExpandingVisualEffect.size = context.size
    layerSize = context.layerSize
  }
}
