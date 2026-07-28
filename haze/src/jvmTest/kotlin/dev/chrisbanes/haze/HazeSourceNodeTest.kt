// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class HazeSourceNodeTest : ContextTest() {

  @Test
  fun sourceElementUpdateWithUnchangedValuesPreservesEqualZOrder() = runComposeUiTest {
    val state = HazeState()
    val firstNode = HazeSourceNode(state, key = "first")
    val secondNode = HazeSourceNode(state, key = "second")
    val effect = SourceLayerVisualEffect()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .testHazeSourceNode(firstNode)
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testHazeSourceNode(secondNode)
            .background(Color.Blue),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testTag("effect")
            .hazeEffect(state) {
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    assertThat(state.areas.map(HazeArea::key)).containsExactly("first", "second")
    assertThat(effect.areaKeys).containsExactly("first", "second")
    val initialPixel = onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50]
    assertThat(initialPixel).isEqualTo(Color.Blue)

    runOnIdle {
      HazeSourceElement(state = state, zIndex = 0f, key = "first").update(firstNode)
    }
    waitForIdle()

    assertThat(state.areas.map(HazeArea::key)).containsExactly("first", "second")
    assertThat(effect.areaKeys).containsExactly("first", "second")
    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(initialPixel)
  }

  @Test
  fun stateChangeMovesAttachedSourceWithoutDuplicates() = runComposeUiTest {
    val firstState = HazeState()
    val secondState = HazeState()
    val node = HazeSourceNode(firstState)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testHazeSourceNode(node),
      )
    }
    waitForIdle()

    runOnIdle {
      node.state = secondState
    }

    assertThat(firstState.areas).isEmpty()
    assertThat(secondState.areas).containsExactly(node.area)
  }

  @Test
  fun keyChangeWithUnchangedStateRefreshesAreaFilter() = runComposeUiTest {
    val state = HazeState()
    val node = HazeSourceNode(state, key = "hidden")
    val effect = SourceLayerVisualEffect()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .testHazeSourceNode(node)
            .background(Color.Red),
        )
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(state) {
              canDrawArea = { area -> area.key != "hidden" }
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    assertThat(effect.areaKeys).isEmpty()

    runOnIdle {
      HazeSourceElement(state = state, zIndex = 0f, key = "visible").update(node)
    }
    waitForIdle()

    assertThat(effect.areaKeys).containsExactly("visible")
  }

  @Test
  fun onResetReleasesLayerAndClearsWindowId() = runComposeUiTest {
    val state = HazeState()
    val node = HazeSourceNode(state)

    setContent {
      Box(
        Modifier
          .size(100.dp)
          .testHazeSourceNode(node),
      )
    }

    waitForIdle()
    assertThat(node.area.contentLayer).isNotNull()

    runOnIdle {
      node.onReset()
    }

    assertThat(node.area.contentLayer).isEqualTo(null)
    assertThat(node.area.windowId).isEqualTo(null)
  }

  @Test
  fun sourceSize_zeroSizedReleasesLayerAndRecordsFreshCaptureWhenRestored() = runComposeUiTest {
    val state = HazeState()
    val node = HazeSourceNode(state)
    var sourceSize by mutableStateOf(100.dp)

    setContent {
      Box(
        Modifier
          .size(sourceSize)
          .testHazeSourceNode(node),
      )
    }

    waitForIdle()
    val initialLayer = node.area.contentLayer
    val initialContentVersion = node.area.contentVersion
    assertThat(initialLayer).isNotNull()

    sourceSize = 0.1.dp
    waitForIdle()

    assertThat(node.area.contentLayer).isNull()

    sourceSize = 100.dp
    waitForIdle()

    assertThat(node.area.contentLayer).isNotNull()
    assertThat(node.area.contentLayer).isNotEqualTo(initialLayer)
    assertThat(node.area.contentVersion).isNotEqualTo(initialContentVersion)
  }
}

private class SourceLayerVisualEffect : VisualEffect {
  var areaKeys: List<Any?> = emptyList()

  override fun DrawScope.draw(context: VisualEffectContext) {
    areaKeys = context.areas.map(HazeArea::key)
    context.areas.forEach { area ->
      area.contentLayer?.let { drawLayer(it) }
    }
  }
}

private fun Modifier.testHazeSourceNode(
  node: HazeSourceNode,
): Modifier = this then TestHazeSourceNodeElement(node)

@OptIn(ExperimentalHazeApi::class)
private data class TestHazeSourceNodeElement(
  val node: HazeSourceNode,
) : ModifierNodeElement<HazeSourceNode>() {

  override fun create(): HazeSourceNode = node

  override fun update(node: HazeSourceNode) = Unit

  override fun InspectorInfo.inspectableProperties() {
    name = "testHazeSourceNode"
  }
}
