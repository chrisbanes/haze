// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import java.awt.Frame
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class, ExperimentalComposeUiApi::class)
class HazeMultiWindowTest : ContextTest() {

  @Test
  fun crossWindowEffect_capturesLocalAwtWindowAndPromotesToScreenStrategy() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceWindow = Frame("SourceWindow")
    val effectWindow = Frame("EffectWindow")
    var sourceWindowId: Any? = null
    var effectWindowId: Any? = null

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides sourceWindow) {
          Box(
            Modifier
              .size(100.dp)
              .captureWindowId { sourceWindowId = it }
              .hazeSource(hazeState),
          )
        }

        CompositionLocalProvider(LocalAwtWindow provides effectWindow) {
          Spacer(
            Modifier
              .size(100.dp)
              .captureWindowId { effectWindowId = it }
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              ),
          )
        }
      }

      waitForIdle()

      assertThat(hazeState.areas).hasSize(1)
      assertThat(hazeState.areas.single().windowId).isSameInstanceAs(sourceWindow)
      assertThat(sourceWindowId).isSameInstanceAs(sourceWindow)
      assertThat(effectWindowId).isSameInstanceAs(effectWindow)
      assertThat(
        resolvePositionStrategy(
          configured = HazePositionStrategy.Auto,
          areas = hazeState.areas,
          windowId = effectWindowId,
        ),
      ).isEqualTo(HazePositionStrategy.Screen)
    } finally {
      sourceWindow.dispose()
      effectWindow.dispose()
    }
  }

  @Test
  fun sameWindowEffect_staysInLocalStrategy() = runComposeUiTest {
    val hazeState = HazeState()
    val window = Frame("SingleWindow")
    var sourceWindowId: Any? = null
    var effectWindowId: Any? = null

    try {
      setContent {
        CompositionLocalProvider(LocalAwtWindow provides window) {
          Box(
            Modifier
              .size(100.dp)
              .captureWindowId { sourceWindowId = it }
              .hazeSource(hazeState),
          )

          Spacer(
            Modifier
              .size(100.dp)
              .captureWindowId { effectWindowId = it }
              .hazeEffect(
                factory = TestEffectFactory,
                input = HazeInput.Sources(hazeState),
                style = Unit,
              ),
          )
        }
      }

      waitForIdle()

      assertThat(hazeState.areas).hasSize(1)
      assertThat(hazeState.areas.single().windowId).isSameInstanceAs(window)
      assertThat(sourceWindowId).isSameInstanceAs(window)
      assertThat(effectWindowId).isSameInstanceAs(window)
      assertThat(
        resolvePositionStrategy(
          configured = HazePositionStrategy.Auto,
          areas = hazeState.areas,
          windowId = effectWindowId,
        ),
      ).isEqualTo(HazePositionStrategy.Local)
    } finally {
      window.dispose()
    }
  }
}

private object TestEffectFactory : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
  }
}

private fun Modifier.captureWindowId(
  onWindowId: (Any?) -> Unit,
): Modifier = this then CaptureWindowIdElement(onWindowId)

private data class CaptureWindowIdElement(
  val onWindowId: (Any?) -> Unit,
) : ModifierNodeElement<CaptureWindowIdNode>() {

  override fun create(): CaptureWindowIdNode = CaptureWindowIdNode(onWindowId)

  override fun update(node: CaptureWindowIdNode) {
    node.onWindowId = onWindowId
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "captureWindowId"
  }
}

private class CaptureWindowIdNode(
  var onWindowId: (Any?) -> Unit,
) : Modifier.Node(), CompositionLocalConsumerModifierNode {

  override fun onAttach() {
    onWindowId(getWindowId())
  }
}
