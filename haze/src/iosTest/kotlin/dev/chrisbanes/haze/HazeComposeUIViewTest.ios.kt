// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.uikit.LocalUIView
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import platform.UIKit.UIView

@OptIn(ExperimentalTestApi::class)
class HazeComposeUIViewTest {

  @Test
  fun autoPositionStrategyPromotesToScreenForDifferentUIKitHostViews() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceView = UIView()
    val effectView = UIView()
    var effectWindowId: Any? = null

    setContent {
      Box(Modifier.size(100.dp)) {
        CompositionLocalProvider(LocalUIView provides sourceView) {
          Spacer(
            Modifier
              .size(100.dp)
              .hazeSource(hazeState),
          )
        }

        CompositionLocalProvider(LocalUIView provides effectView) {
          Spacer(
            Modifier
              .size(100.dp)
              .captureWindowId { effectWindowId = it },
          )
        }
      }
    }

    waitForIdle()

    assertThat(hazeState.areas).hasSize(1)
    assertThat(hazeState.areas.single().windowId).isSameInstanceAs(sourceView)
    assertThat(effectWindowId).isSameInstanceAs(effectView)
    assertThat(
      resolvePositionStrategy(
        configured = HazePositionStrategy.Auto,
        areas = hazeState.areas,
        windowId = effectWindowId,
      ),
    ).isEqualTo(HazePositionStrategy.Screen)
  }

  @Test
  fun crossUIViewEffect_refreshesWhenNestedSourceLayerChanges() = runComposeUiTest {
    val hazeState = HazeState()
    val overlayAlpha = mutableFloatStateOf(1f)
    val factory = InputSnapshotRecordingRendererFactory()
    val sourceView = UIView()
    val effectView = UIView()
    var sourceWindowId: Any? = null
    var effectWindowId: Any? = null

    setContent {
      CompositionLocalProvider(LocalUIView provides sourceView) {
        Box(
          Modifier
            .size(100.dp)
            .captureWindowId { sourceWindowId = it }
            .hazeSource(hazeState),
        ) {
          Spacer(
            Modifier
              .fillMaxSize()
              .graphicsLayer { alpha = overlayAlpha.floatValue },
          )
        }
      }

      CompositionLocalProvider(LocalUIView provides effectView) {
        Spacer(
          Modifier
            .size(100.dp)
            .captureWindowId { effectWindowId = it }
            .hazeEffect(
              factory = factory,
              input = HazeInput.Sources(hazeState),
              style = Unit,
            ),
        )
      }
    }
    waitForIdle()

    val renderer = factory.renderer
    val initialSnapshot = renderer.snapshots.last()
    val initialContentVersion = hazeState.areas.single().contentVersion
    assertThat(sourceWindowId).isSameInstanceAs(sourceView)
    assertThat(effectWindowId).isSameInstanceAs(effectView)
    assertThat(effectWindowId).isNotSameInstanceAs(sourceWindowId)

    overlayAlpha.floatValue = 0f
    waitForIdle()

    assertThat(hazeState.areas.single().contentVersion).isEqualTo(initialContentVersion)
    assertThat(renderer.snapshots.last()).isNotSameInstanceAs(initialSnapshot)
  }
}

private class InputSnapshotRecordingRendererFactory : HazeEffectFactory<Unit> {
  val renderer = InputSnapshotRecordingRenderer()

  override fun createRenderer(): HazeEffectRenderer<Unit> = renderer
}

@OptIn(InternalHazeApi::class)
private class InputSnapshotRecordingRenderer : HazeEffectRenderer<Unit> {
  val snapshots = mutableListOf<HazeEffectInputSnapshot?>()

  override fun HazeEffectDrawScope.draw(style: Unit) {
    snapshots += (this as HazeEffectRuntimeDrawScope).inputSnapshot
    drawInput()
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
