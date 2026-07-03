// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
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
