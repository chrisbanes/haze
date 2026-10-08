// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class HazeInputPresentationCancellationTest {
  @Test fun detachDuringReconciliation_cancelsOldJobAndRebindsAnchor() = checkCancellation(false)

  @Test fun detachDuringNotification_cancelsOldJobBeforeDelivery() = checkCancellation(true)

  private fun checkCancellation(notification: Boolean) = runComposeUiTest(effectContext = StandardTestDispatcher()) {
    val inputs = HazeState()
    val outputs = HazeState()
    val anchor = AnchorElement()
    val host = HostElement(inputs)
    val afterDraw = AfterDrawElement()
    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(inputs).background(Color.Red))
        Box(Modifier.fillMaxSize().hazeSource(outputs).then(anchor).then(afterDraw).then(host))
      }
    }
    fun settle() {
      repeat(3) {
        mainClock.advanceTimeByFrame()
        onRoot().captureToImage()
        waitForIdle()
      }
    }
    settle()
    val old = host.current.effect
    val oldRenderer = host.current.renderer
    val oldScope = old.coroutineScope.coroutineContext[Job]!!
    assertThat(anchor.current.matches(old, true)).isTrue()
    var replacementRan = false
    val deliveries = mutableListOf<Renderer>()
    outputs.areas.single().inputPresentationListeners += OnPreDrawListener(
      effectWindowId = { null },
      onPreDraw = { deliveries += host.current.renderer },
      presentationOutputAreas = { emptyList() },
    )
    val existingJobs = oldScope.children.toSet()
    fun replaceBeforeContinuation() {
      host.current.coroutineScope.launch {
        // The production launch has yielded, but has not reconciled or notified yet.
        val pending = oldScope.children.filter { it !in existingJobs }.toList()
        assertThat(pending.size).isEqualTo(1)
        assertThat(pending.single().isActive).isTrue()
        assertThat(old.isAttached).isTrue()
        assertThat(anchor.current.matches(old, true)).isTrue()
        assertThat(deliveries.size).isEqualTo(0)
        host.current.replace()
        assertThat(old.isAttached).isFalse()
        assertThat(oldScope.isCancelled).isTrue()
        assertThat(pending.single().isCancelled).isTrue()
        replacementRan = true
      }
    }
    if (notification) {
      runOnIdle {
        afterDraw.current.afterDraw = ::replaceBeforeContinuation
        checkNotNull(host.current.renderer.present)()
      }
      onRoot().captureToImage()
    } else {
      runOnIdle {
        old.updateTypedEffect(HazeEffectFactory { Renderer(false) }, Unit, HazeSampling.Default)
        replaceBeforeContinuation()
      }
    }
    settle()
    assertThat(replacementRan).isTrue()
    val replacement = host.current.effect
    assertThat(anchor.current.matches(replacement, true)).isTrue()
    assertThat(anchor.current.matches(old, true)).isFalse()
    assertThat(anchor.current.release(old)).isFalse()
    assertThat(anchor.current.matches(replacement, true)).isTrue()
    assertThat(deliveries.any { it === oldRenderer }).isFalse()
    assertThat(deliveries.all { it === host.current.renderer }).isTrue()
    assertThat(inputs.areas.single().inputPresentationListeners.size).isEqualTo(1)
  }

  private class AnchorElement : ModifierNodeElement<InputPresentationLayerNode>() {
    lateinit var current: InputPresentationLayerNode
    override fun create() = InputPresentationLayerNode().also { current = it }
    override fun update(node: InputPresentationLayerNode) = Unit
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun InspectorInfo.inspectableProperties() {
      name = "testPresentationAnchor"
    }
  }

  private class HostElement(private val inputs: HazeState) : ModifierNodeElement<Host>() {
    lateinit var current: Host
    override fun create() = Host(inputs).also { current = it }
    override fun update(node: Host) = Unit
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun InspectorInfo.inspectableProperties() {
      name = "testEffectHost"
    }
  }

  private class Host(private val inputs: HazeState) : DelegatingNode() {
    var renderer = Renderer(true)
      private set
    var effect = delegate(createEffect())
      private set

    private fun createEffect() = HazeEffectNode().apply {
      explicitInput = HazeInput.Sources(inputs)
      updateTypedEffect(HazeEffectFactory { renderer }, Unit, HazeSampling.Default)
    }

    fun replace() {
      undelegate(effect)
      renderer = Renderer(true)
      effect = delegate(createEffect())
    }
  }

  private class AfterDrawElement : ModifierNodeElement<AfterDraw>() {
    lateinit var current: AfterDraw
    override fun create() = AfterDraw().also { current = it }
    override fun update(node: AfterDraw) = Unit
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
    override fun InspectorInfo.inspectableProperties() {
      name = "testAfterDraw"
    }
  }

  private class AfterDraw : Modifier.Node(), DrawModifierNode {
    var afterDraw: (() -> Unit)? = null
    override fun ContentDrawScope.draw() {
      drawContent()
      afterDraw?.also { afterDraw = null }?.invoke()
    }
  }

  private class Renderer(override val observesSourceSnapshotChanges: Boolean) :
    HazeEffectRenderer<Unit>, HazeEffectRendererDrawHooks<Unit> {
    var present: (() -> Unit)? = null
    override fun HazeEffectDrawScope.draw(style: Unit) {
      present = (this as HazeEffectRuntimeDrawScope).inputPresentationInvalidation()
      drawInput()
    }
  }
}
