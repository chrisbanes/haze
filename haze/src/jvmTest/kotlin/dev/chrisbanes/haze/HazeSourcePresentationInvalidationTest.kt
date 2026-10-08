// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class HazeSourcePresentationInvalidationTest {
  @Test fun diamond_notifiesBothBranchesAndTerminalConsumer() {
    val source = HazeArea()
    val left = HazeArea()
    val right = HazeArea()
    val end = HazeArea()
    val notifications = mutableListOf<String>()
    source.connect("left", listOf(left), notifications)
    source.connect("right", listOf(right), notifications)
    left.connect("end-left", listOf(end), notifications)
    right.connect("end-right", listOf(end), notifications)
    end.connect("terminal", emptyList(), notifications)
    source.notifyInputPresentationListeners()
    left.notifyInputPresentationListeners()
    right.notifyInputPresentationListeners()
    end.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(listOf("left", "right", "end-left", "end-right", "terminal"))
  }

  @Test fun cycles_suppressInternalEdgesButNotifyOutsideConsumers() {
    for (length in listOf(2, 3)) {
      val areas = List(length) { HazeArea() }
      val notifications = mutableListOf<String>()
      areas.forEachIndexed { index, area ->
        area.connect("cycle-$index", listOf(areas[(index + 1) % length]), notifications)
        area.connect("terminal-$index", emptyList(), notifications)
      }
      areas.forEach { it.notifyInputPresentationListeners() }
      assertThat(notifications).isEqualTo(areas.indices.map { "terminal-$it" })
    }
  }

  @Test fun multipleContainingSources_suppressWholeConsumerWhenOnePathReturns() {
    val source = HazeArea()
    val unsafe = HazeArea()
    val safe = HazeArea()
    val notifications = mutableListOf<String>()
    source.connect("mixed", listOf(safe, unsafe), notifications)
    unsafe.connect("return", listOf(source), notifications)
    source.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(emptyList())
  }

  @Test fun currentGraph_cycleRemovalCreationAndDetachChangeEligibility() {
    val source = HazeArea()
    val output = HazeArea()
    val notifications = mutableListOf<String>()
    var outputs: List<HazeArea>? = listOf(output)
    source.inputPresentationListeners += OnPreDrawListener(
      effectWindowId = { null },
      onPreDraw = { notifications += "consumer" },
      presentationOutputAreas = { outputs },
    )
    val returnListener = output.connect("return", listOf(source), notifications)
    source.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(emptyList())
    output.inputPresentationListeners -= returnListener
    source.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(listOf("consumer"))
    output.inputPresentationListeners += returnListener
    source.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(listOf("consumer"))
    outputs = null
    output.inputPresentationListeners -= returnListener
    source.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(listOf("consumer"))
    outputs = emptyList()
    source.notifyInputPresentationListeners()
    assertThat(notifications).isEqualTo(listOf("consumer", "consumer"))
  }

  @Test fun ordinaryListener_doesNotGainPresentationInvalidation() {
    val source = HazeArea()
    var calls = 0
    source.preDrawListeners += OnPreDrawListener({ null }, { calls++ })
    source.notifyInputPresentationListeners()
    assertThat(calls).isEqualTo(0)
    source.notifyPreDrawListeners(regularPreDraw = true, snapshotApplied = false)
    assertThat(calls).isEqualTo(1)
  }

  @Test fun actualDescendantPresentation_refreshesConsumerWithoutSnapshotApply() = runComposeUiTest {
    val inputState = HazeState()
    val outputState = HazeState()
    val producer = PresentationRenderer()
    val consumer = PresentationRenderer()
    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(inputState).background(Color.Red))
        Box(
          Modifier.fillMaxSize().hazeSource(outputState)
            .hazeEffect(HazeEffectFactory { producer }, HazeInput.Sources(inputState), Unit),
        )
        Box(Modifier.fillMaxSize().hazeEffect(HazeEffectFactory { consumer }, HazeInput.Sources(outputState), Unit))
      }
    }
    waitForIdle()
    val before = consumer.snapshot
    val beforeProducerDraws = producer.draws
    val sourceVersion = outputState.areas.single().contentVersion
    assertThat(inputState.areas.single().inputPresentationListeners.single().currentPresentationOutputAreas())
      .isEqualTo(listOf(outputState.areas.single()))
    assertThat(outputState.areas.single().inputPresentationListeners.single().currentPresentationOutputAreas())
      .isEqualTo(emptyList())
    runOnIdle { checkNotNull(producer.present)() }
    repeat(3) {
      mainClock.advanceTimeByFrame()
      onRoot().captureToImage()
      waitForIdle()
    }
    assertThat(producer.draws).isGreaterThan(beforeProducerDraws)
    assertThat(consumer.snapshot).isNotEqualTo(before)
    assertThat(outputState.areas.single().contentVersion).isEqualTo(sourceVersion)
    assertThat(checkNotNull(before).hasSameSourceGeometry(checkNotNull(consumer.snapshot))).isTrue()
    val settled = consumer.draws
    repeat(10) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    assertThat(consumer.draws).isEqualTo(settled)
  }

  private fun HazeArea.connect(
    name: String,
    outputs: List<HazeArea>,
    notifications: MutableList<String>,
  ): OnPreDrawListener = OnPreDrawListener(
    effectWindowId = { null },
    onPreDraw = { invalidatesInput ->
      assertThat(invalidatesInput).isTrue()
      notifications += name
    },
    presentationOutputAreas = { outputs },
  ).also { inputPresentationListeners += it }

  private class PresentationRenderer : HazeEffectRenderer<Unit>, HazeEffectRendererDrawHooks<Unit> {
    override val observesSourceSnapshotChanges = true
    var present: (() -> Unit)? = null
    var snapshot: HazeEffectInputSnapshot? = null
    var draws = 0
    override fun HazeEffectDrawScope.draw(style: Unit) {
      val runtime = this as HazeEffectRuntimeDrawScope
      present = runtime.inputPresentationInvalidation()
      snapshot = runtime.inputSnapshot
      draws++
      drawInput()
    }
  }
}
