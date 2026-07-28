// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class, ExperimentalTestApi::class)
class HazeSourceNodeTest : ContextTest() {

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
