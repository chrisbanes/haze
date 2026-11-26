// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReusableContent
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeEffectReactivationTest : ContextTest() {

  @Test
  fun reactivation_unchangedLayout_restoresEffectCoordinates() = runComposeUiTest {
    val factory = ReactivationRecordingFactory()
    val active = mutableStateOf(true)
    var node: HazeEffectNode? = null

    setContent {
      Row {
        Box(Modifier.size(50.dp))
        ReusableContentHost(active.value) {
          Box(
            Modifier
              .size(50.dp)
              .hazeEffect(factory, HazeInput.Content, Unit)
              .captureEffectNode { node = it },
          )
        }
      }
    }
    waitForIdle()

    val effect = checkNotNull(node)
    val root = effect.requireLayoutCoordinates().findRootCoordinates()
    val before = effect.surfaceBounds(root)
    assertThat(before).isNotNull()

    runOnIdle { active.value = false }
    waitForIdle()
    runOnIdle { active.value = true }
    waitForIdle()

    assertThat(factory.attachCount).isEqualTo(2)
    assertThat(effect.surfaceBounds(root)).isEqualTo(before)
  }

  @Test
  fun reactivation_sameNodeResubscribesToCurrentLocals() = runComposeUiTest {
    val static = staticCompositionLocalOf { 0 }
    val dynamic = compositionLocalOf { 0 }
    val first = mutableStateOf(1)
    val second = mutableStateOf(2)
    val active = mutableStateOf(true)
    var node: HazeEffectNode? = null
    var value = 0
    var updates = 0
    var detaches = 0
    var disposals = 0
    val factory = HazeEffectFactory<Unit> {
      object : HazeEffectRenderer<Unit>, HazeEffectRendererLifecycle<Unit> {
        override fun update(scope: HazeEffectLifecycleScope, style: Unit, sampling: HazeSampling) {
          updates++
          value = scope.currentValueOf(static) + scope.currentValueOf(dynamic)
        }
        override fun detach() {
          detaches++
        }
        override fun dispose() {
          disposals++
        }
        override fun HazeEffectDrawScope.draw(style: Unit) {
          drawInput()
        }
      }
    }
    setContent {
      CompositionLocalProvider(static provides first.value, dynamic provides second.value) {
        ReusableContentHost(active.value) {
          Box(Modifier.size(50.dp).hazeEffect(factory, HazeInput.Content, Unit).captureEffectNode { node = it })
        }
      }
    }
    waitForIdle()
    val original = checkNotNull(node)
    assertThat(value).isEqualTo(3)
    runOnIdle { active.value = false }
    waitForIdle()
    val detachedUpdates = updates
    runOnIdle {
      first.value = 4
      second.value = 5
    }
    waitForIdle()
    assertThat(updates).isEqualTo(detachedUpdates)
    assertThat(detaches).isEqualTo(1)
    assertThat(disposals).isEqualTo(1)
    runOnIdle { active.value = true }
    waitForIdle()
    assertThat(node).isSameInstanceAs(original)
    assertThat(value).isEqualTo(9)
    runOnIdle { second.value = 7 }
    waitForIdle()
    assertThat(value).isEqualTo(11)
  }

  @Test
  fun firstAttach_neverUpdatesWithZeroSize() = runComposeUiTest {
    val factory = ReactivationRecordingFactory()

    setContent {
      Box(Modifier.size(50.dp).hazeEffect(factory, HazeInput.Content, Unit))
    }
    waitForIdle()

    assertThat(factory.updateSizes).isNotEmpty()
    assertThat(factory.updateSizes).doesNotContain(Size.Zero)
  }

  @Test
  fun lazyReuse_reattachedItemsKeepBounds() = runComposeUiTest {
    val state = LazyListState()
    val attachCounts = mutableMapOf<HazeEffectNode, Int>()

    setContent {
      LazyColumn(Modifier.size(100.dp, 200.dp), state) {
        items(100) {
          Box(
            Modifier
              .size(100.dp, 50.dp)
              .hazeEffect(TestEffectFactory, HazeInput.Content, Unit)
              .captureEffectNode { attachCounts[it] = (attachCounts[it] ?: 0) + 1 },
          )
        }
      }
    }
    waitForIdle()
    repeat(12) {
      runOnIdle { state.dispatchRawDelta(60f) }
      waitForIdle()
    }

    // A pooled item stays attached but is unplaced, so it has no bounds and its coordinates are stale.
    // Only a node at the offset of a visible item is placed.
    val placedOffsets = state.layoutInfo.visibleItemsInfo.map { it.offset.toFloat() }
    val reattached = attachCounts.filter { it.value > 1 }.keys
      .filter { it.isAttached && it.requireLayoutCoordinates().positionInRoot().y in placedOffsets }
    assertThat(attachCounts.values.max()).isGreaterThan(1)
    assertThat(reattached).isNotEmpty()
    for (node in reattached) {
      val root = node.requireLayoutCoordinates().findRootCoordinates()
      assertThat(node.surfaceBounds(root)).isNotNull()
    }
  }

  @Test
  fun reusableContentKeyChange_cancelsHeldPressAndReplacesRenderer() = runComposeUiTest {
    val factory = PointerRecordingFactory()
    val key = mutableIntStateOf(0)
    val nodes = mutableListOf<HazeEffectNode>()
    setContent {
      ReusableContent(key.intValue) {
        Box(
          Modifier.size(50.dp).testTag("effect")
            .hazeEffect(factory, HazeInput.Content, Unit)
            .captureEffectNode { nodes += it },
        )
      }
    }
    waitForIdle()

    onNodeWithTag("effect").performTouchInput { down(center) }
    waitForIdle()
    assertThat(factory.pointerEvents, "the press reaches the renderer").isGreaterThan(0)
    runOnIdle { key.intValue++ }
    waitForIdle()

    assertThat(factory.cancels).isEqualTo(1)
    assertThat(factory.detaches).isEqualTo(1)
    assertThat(factory.disposes).isEqualTo(1)
    assertThat(factory.renderers).isEqualTo(2)
    assertThat(factory.attaches).isEqualTo(2)
    assertThat(nodes.distinct(), "the node is reused, not recreated").hasSize(1)

    // The press began before reuse, so its release reaches no renderer.
    val events = factory.pointerEvents
    onNodeWithTag("effect").performTouchInput { up() }
    waitForIdle()
    assertThat(factory.pointerEvents).isEqualTo(events)
  }

  @Test
  fun deactivation_cancelsHeldPressAndReactivationAttachesFreshRenderer() = runComposeUiTest {
    val factory = PointerRecordingFactory()
    val active = mutableStateOf(true)
    setContent {
      ReusableContentHost(active.value) {
        Box(Modifier.size(50.dp).testTag("effect").hazeEffect(factory, HazeInput.Content, Unit))
      }
    }
    waitForIdle()

    onNodeWithTag("effect").performTouchInput { down(center) }
    waitForIdle()
    assertThat(factory.pointerEvents, "the press reaches the renderer").isGreaterThan(0)
    runOnIdle { active.value = false }
    waitForIdle()

    assertThat(factory.cancels).isEqualTo(1)
    assertThat(factory.detaches).isEqualTo(1)
    assertThat(factory.disposes).isEqualTo(1)

    runOnIdle { active.value = true }
    waitForIdle()

    assertThat(factory.renderers).isEqualTo(2)
    assertThat(factory.attaches).isEqualTo(2)
  }

  @Test
  fun detach_sourcesInput_doesNotCreateContentArea() = runComposeUiTest {
    val state = HazeState()
    val shown = mutableStateOf(true)
    var node: HazeEffectNode? = null
    setContent {
      Box(Modifier.size(100.dp).hazeSource(state).background(Color.Blue))
      if (shown.value) {
        Box(
          Modifier.size(50.dp)
            .hazeEffect(TestEffectFactory, HazeInput.Sources(state), Unit)
            .captureEffectNode { node = it },
        )
      }
    }
    waitForIdle()
    val effect = checkNotNull(node)

    runOnIdle { shown.value = false }
    waitForIdle()

    assertThat(effect.isAttached).isFalse()
    assertThat(effect.hasContentDrawArea).isFalse()
  }

  @Test
  fun reusableContentKeyChange_clearsRetainedOutputBeforeNextDraw() = runComposeUiTest {
    val events = mutableListOf<String>()
    val factory = RetainedOutputRecordingFactory(events)
    val state = HazeState()
    val key = mutableIntStateOf(0)
    setContent {
      Box(Modifier.size(100.dp).hazeSource(state).background(Color.Blue))
      ReusableContent(key.intValue) {
        Box(Modifier.size(50.dp).hazeEffect(factory, HazeInput.Sources(state), Unit))
      }
    }
    waitForIdle()
    val settled = events.size

    runOnIdle { key.intValue++ }
    waitForIdle()

    val afterReuse = events.drop(settled)
    val clear = afterReuse.indexOf("clear")
    assertThat(clear, "reuse clears retained output").isGreaterThan(-1)
    val draw = afterReuse.indexOf("draw")
    assertThat(draw, "the reused effect draws again").isGreaterThan(-1)
    assertThat(clear, "retained output is cleared before the next draw").isLessThan(draw)
  }
}

private class ReactivationRecordingFactory : HazeEffectFactory<Unit> {
  var attachCount = 0
  val updateSizes = mutableListOf<Size>()

  override fun createRenderer(): HazeEffectRenderer<Unit> = object :
    HazeEffectRenderer<Unit>,
    HazeEffectRendererLifecycle<Unit> {
    override fun attach(scope: HazeEffectLifecycleScope) {
      attachCount++
    }

    override fun update(scope: HazeEffectLifecycleScope, style: Unit, sampling: HazeSampling) {
      updateSizes += scope.modifierSize
    }

    override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
  }
}

private class PointerRecordingFactory : HazeEffectFactory<Unit> {
  var renderers = 0
  var attaches = 0
  var detaches = 0
  var disposes = 0
  var pointerEvents = 0
  var cancels = 0

  override fun createRenderer(): HazeEffectRenderer<Unit> {
    renderers++
    return object :
      HazeEffectRenderer<Unit>,
      HazeEffectRendererLifecycle<Unit>,
      HazeEffectRendererInteraction {
      override val observesPointerEvents = true

      override fun attach(scope: HazeEffectLifecycleScope) {
        attaches++
      }

      override fun detach() {
        detaches++
      }

      override fun dispose() {
        disposes++
      }

      override fun onPointerEvent(event: PointerEvent, scope: HazeEffectLifecycleScope) {
        pointerEvents++
      }

      override fun onCancelPointerInput(scope: HazeEffectLifecycleScope) {
        cancels++
      }

      override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()
    }
  }
}

private class RetainedOutputRecordingFactory(private val events: MutableList<String>) : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = object :
    HazeEffectRenderer<Unit>,
    HazeEffectRendererLifecycle<Unit>,
    HazeEffectRendererRetainedOutput {
    private var retained = false

    override fun canDrawRetainedOutput(): Boolean = retained

    override fun clearRetainedOutput() {
      events += "clear"
      retained = false
    }

    override fun HazeEffectDrawScope.draw(style: Unit) {
      events += "draw"
      retained = true
      drawInput()
    }
  }
}
