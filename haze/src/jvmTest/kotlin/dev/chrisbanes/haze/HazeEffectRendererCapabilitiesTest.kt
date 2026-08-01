// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeEffectRendererCapabilitiesTest : ContextTest() {

  @Test
  fun closedPreparationGate_drawsContentWithoutInvokingRendererHooks() = runComposeUiTest {
    val factory = RecordingCapabilityFactory(shouldPrepareDraw = false)

    setContent {
      Box(
        Modifier
          .size(10.dp)
          .testTag("effect")
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = "transparent",
          )
          .background(Color.Red),
      )
    }
    waitForIdle()

    val renderer = factory.renderers.single()
    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[5, 5]).isEqualTo(Color.Red)
    assertThat(renderer.prepareDrawCalls).isEqualTo(0)
    assertThat(renderer.drawCalls).isEqualTo(0)
    assertThat(renderer.drawForegroundCalls).isEqualTo(0)
  }

  @Test
  fun nodeOwnedCapabilities_preserveExactLifecycleAndStyleOwnership() = runComposeUiTest {
    val firstFactory = RecordingCapabilityFactory()
    val secondFactory = RecordingCapabilityFactory()
    val factory = mutableStateOf<HazeEffectFactory<String>>(firstFactory)
    val style = mutableStateOf("first")
    val show = mutableStateOf(true)

    setContent {
      if (show.value) {
        Spacer(
          Modifier
            .size(10.dp)
            .testTag("effect")
            .hazeEffect(
              factory = factory.value,
              input = HazeInput.Content,
              style = style.value,
            ),
        )
      }
    }
    waitForIdle()

    val renderer = firstFactory.renderers.single()
    assertThat(renderer.attachCalls).isEqualTo(1)
    onNodeWithTag("effect").performTouchInput { click() }
    waitForIdle()

    assertThat(renderer.updateCalls).isGreaterThan(0)
    assertThat(renderer.prepareDrawCalls).isGreaterThan(0)
    assertThat(renderer.drawCalls).isGreaterThan(0)
    assertThat(renderer.drawForegroundCalls).isGreaterThan(0)
    assertThat(renderer.pointerEventCalls).isGreaterThan(0)

    style.value = "second"
    waitForIdle()

    assertThat(firstFactory.renderers.size).isEqualTo(1)
    assertThat(renderer.styles).containsExactly("first", "second")

    factory.value = secondFactory
    waitForIdle()

    val replacement = secondFactory.renderers.single()
    assertThat(renderer.cancelPointerInputCalls).isEqualTo(1)
    assertThat(renderer.detachCalls).isEqualTo(1)
    assertThat(renderer.disposeCalls).isEqualTo(1)
    assertThat(replacement.attachCalls).isEqualTo(1)

    show.value = false
    waitForIdle()

    assertThat(renderer.detachCalls).isEqualTo(1)
    assertThat(replacement.detachCalls).isEqualTo(1)
    assertThat(replacement.disposeCalls).isEqualTo(1)
  }
}

private class RecordingCapabilityFactory(
  private val shouldPrepareDraw: Boolean = true,
) : HazeEffectFactory<String> {
  val renderers = mutableListOf<RecordingCapabilityRenderer>()

  override fun createRenderer(): HazeEffectRenderer<String> =
    RecordingCapabilityRenderer(shouldPrepareDraw).also(renderers::add)
}

private class RecordingCapabilityRenderer(
  private val shouldPrepareDraw: Boolean,
) :
  HazeEffectRenderer<String>,
  HazeEffectRendererLifecycle<String>,
  HazeEffectRendererDrawHooks<String>,
  HazeEffectRendererInteraction {
  val styles = mutableListOf<String>()
  var attachCalls = 0
  var updateCalls = 0
  var prepareDrawCalls = 0
  var drawCalls = 0
  var drawForegroundCalls = 0
  var pointerEventCalls = 0
  var cancelPointerInputCalls = 0
  var detachCalls = 0
  var disposeCalls = 0

  override fun attach(scope: HazeEffectLifecycleScope) {
    attachCalls++
  }

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: String,
    sampling: HazeSampling,
  ) {
    updateCalls++
    if (styles.lastOrNull() != style) styles += style
  }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: String) {
    prepareDrawCalls++
  }

  override fun shouldPrepareDraw(style: String): Boolean = shouldPrepareDraw

  override fun HazeEffectDrawScope.draw(style: String) {
    drawCalls++
  }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: String) {
    drawForegroundCalls++
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: String): Rect = modifierBounds

  override val observesPointerEvents: Boolean = true

  override fun onPointerEvent(event: PointerEvent, scope: HazeEffectLifecycleScope) {
    pointerEventCalls++
  }

  override fun onCancelPointerInput(scope: HazeEffectLifecycleScope) {
    cancelPointerInputCalls++
  }

  override fun detach() {
    detachCalls++
  }

  override fun dispose() {
    disposeCalls++
  }
}
