// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class HazeInputPresentationLayerTest {
  @Test fun inputAndCapabilityTransitions_removeBoundaryAndSourceLossRetainsIt() = checkTransitions(false)

  @Test fun capabilityChangesDuringPrepare_reconcileBoundaryAndSourceListeners() = checkTransitions(true)

  private fun checkTransitions(duringPreparation: Boolean) = runComposeUiTest {
    val inputs = HazeState()
    val outputs = HazeState()
    val input = mutableStateOf<HazeInput>(HazeInput.Sources(inputs))
    val observes = mutableStateOf(true)
    val sourceVisible = mutableStateOf(true)
    val effectVisible = mutableStateOf(true)
    val probe = ProbeElement()
    val flat = ProbeElement()
    val factory = HazeEffectFactory { Renderer(duringPreparation) }
    setContent {
      Box(Modifier.size(100.dp)) {
        if (sourceVisible.value) {
          Box(Modifier.fillMaxSize().hazeSource(inputs).background(Color.Red))
        }
        Box(Modifier.fillMaxSize().hazeEffect(factory, input.value, observes.value).then(flat))
        if (effectVisible.value) {
          Box(
            Modifier.fillMaxSize().hazeSource(outputs)
              .hazeEffect(factory, input.value, observes.value).then(probe),
          )
        }
      }
    }
    fun settle() {
      repeat(3) {
        mainClock.advanceTimeByFrame()
        onRoot().captureToImage()
        waitForIdle()
      }
      assertThat(flat.current.enabled).isFalse()
    }
    settle()
    assertThat(probe.current.enabled).isTrue()
    assertThat(inputs.areas.single().inputPresentationListeners.size).isEqualTo(2)
    runOnIdle { observes.value = false }
    settle()
    assertThat(probe.current.enabled).isFalse()
    assertThat(inputs.areas.single().inputPresentationListeners.size).isEqualTo(0)
    runOnIdle { observes.value = true }
    settle()
    assertThat(probe.current.enabled).isTrue()
    assertThat(inputs.areas.single().inputPresentationListeners.size).isEqualTo(2)
    runOnIdle { sourceVisible.value = false }
    settle()
    assertThat(probe.current.enabled).isTrue()
    runOnIdle { input.value = HazeInput.Content }
    settle()
    assertThat(probe.current.enabled).isFalse()
    runOnIdle { input.value = HazeInput.Sources(inputs) }
    settle()
    assertThat(probe.current.enabled).isTrue()
    val previous = probe.current
    runOnIdle { effectVisible.value = false }
    settle()
    assertThat(previous.enabled).isFalse()
    runOnIdle {
      sourceVisible.value = true
      effectVisible.value = true
    }
    settle()
    assertThat(probe.current.enabled).isTrue()
    assertThat(previous.enabled).isFalse()
  }

  private class ProbeElement : ModifierNodeElement<Probe>() {
    lateinit var current: Probe
    override fun create() = Probe().also { current = it }
    override fun update(node: Probe) = Unit
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun InspectorInfo.inspectableProperties() {
      name = "presentationLayerProbe"
    }
  }

  private class Probe : Modifier.Node(), LayoutAwareModifierNode {
    private var effect: HazeEffectNode? = null
    private var anchor: InputPresentationLayerNode? = null
    val enabled: Boolean get() = effect?.let { anchor?.matches(it, true) } == true
    override fun onPlaced(coordinates: LayoutCoordinates) {
      effect = findNearestAncestor(HazeTraversableNodeKeys.Effect) as? HazeEffectNode
      anchor = findNearestAncestor(InputPresentationLayerNode.Key) as? InputPresentationLayerNode
    }
  }

  private class Renderer(private val duringPreparation: Boolean) :
    HazeEffectRenderer<Boolean>,
    HazeEffectRendererDrawHooks<Boolean>,
    HazeEffectRendererLifecycle<Boolean> {
    override var observesSourceSnapshotChanges = false
      private set
    override fun update(scope: HazeEffectLifecycleScope, style: Boolean, sampling: HazeSampling) {
      if (!duringPreparation) observesSourceSnapshotChanges = style
    }
    override fun HazeEffectRuntimeDrawScope.prepareDraw(style: Boolean) {
      if (duringPreparation) observesSourceSnapshotChanges = style
    }
    override fun HazeEffectDrawScope.draw(style: Boolean) {
      drawInput()
    }
  }
}
