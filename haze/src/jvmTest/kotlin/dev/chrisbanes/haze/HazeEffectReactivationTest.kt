// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
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

    val reattached = attachCounts.filter { it.value > 1 }.keys.filter { it.isAttached }
    assertThat(attachCounts.values.max()).isGreaterThan(1)
    assertThat(reattached).isNotEmpty()
    for (node in reattached) {
      val root = node.requireLayoutCoordinates().findRootCoordinates()
      assertThat(node.surfaceBounds(root)).isNotNull()
    }
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
