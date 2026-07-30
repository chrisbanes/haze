// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
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
class HazeEffectFactoryVisualEffectBridgeTest : ContextTest() {

  @Test
  fun fullLifecycle_styleAndFactoryReplacement_preserveExactOwnership() = runComposeUiTest {
    val firstFactory = RecordingVisualEffectFactory()
    val secondFactory = RecordingVisualEffectFactory()
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

    val effect = firstFactory.effects.single()
    assertThat(effect.attachCalls).isEqualTo(1)
    onNodeWithTag("effect").performTouchInput { click() }
    waitForIdle()

    assertThat(effect.updateCalls).isGreaterThan(0)
    assertThat(effect.prepareDrawCalls).isGreaterThan(0)
    assertThat(effect.drawCalls).isGreaterThan(0)
    assertThat(effect.drawForegroundCalls).isGreaterThan(0)
    assertThat(effect.pointerEventCalls).isGreaterThan(0)

    val directEffect = RecordingFactoryVisualEffect("direct")
    val directBridge = HazeEffectFactoryVisualEffectBridge(directEffect)
    val context = checkNotNull(effect.attachedContext)
    directBridge.attach(context)
    directBridge.canDrawRetainedOutput(context)
    directBridge.onTrimMemory(TrimMemoryLevel.BACKGROUND)
    directBridge.clearRetainedOutput()
    directBridge.detach(context)
    assertThat(directEffect.trimMemoryCalls).isEqualTo(1)
    assertThat(directEffect.retainedAvailabilityCalls).isEqualTo(1)
    assertThat(directEffect.clearRetainedOutputCalls).isEqualTo(1)
    assertThat(directEffect.detachCalls).isEqualTo(1)

    style.value = "second"
    waitForIdle()

    assertThat(firstFactory.effects.size).isEqualTo(1)
    assertThat(effect.styles).containsExactly("first", "second")

    factory.value = secondFactory
    waitForIdle()

    val replacement = secondFactory.effects.single()
    assertThat(effect.detachCalls).isEqualTo(1)
    assertThat(replacement.attachCalls).isEqualTo(1)

    show.value = false
    waitForIdle()

    assertThat(effect.detachCalls).isEqualTo(1)
    assertThat(replacement.detachCalls).isEqualTo(1)
  }
}

private class RecordingVisualEffectFactory :
  HazeEffectFactory<String>,
  HazeEffectVisualEffectFactory<String> {
  val effects = mutableListOf<RecordingFactoryVisualEffect>()

  override fun createRenderer(): HazeEffectRenderer<String> {
    error("The built-in VisualEffect bridge must take precedence")
  }

  override fun createVisualEffect(
    style: String,
    sampling: HazeSampling,
  ): HazeEffectFactoryVisualEffect<String> {
    return RecordingFactoryVisualEffect(style).also(effects::add)
  }
}

private class RecordingFactoryVisualEffect(
  initialStyle: String,
) : HazeEffectFactoryVisualEffect<String>,
  InteractiveVisualEffect,
  RetainedOutputVisualEffect {
  val styles = mutableListOf(initialStyle)
  var attachCalls = 0
  var updateCalls = 0
  var prepareDrawCalls = 0
  var drawCalls = 0
  var drawForegroundCalls = 0
  var pointerEventCalls = 0
  var retainedAvailabilityCalls = 0
  var trimMemoryCalls = 0
  var clearRetainedOutputCalls = 0
  var detachCalls = 0
  var attachedContext: VisualEffectContext? = null

  override fun updateStyle(style: String, sampling: HazeSampling) {
    styles += style
  }

  override fun attach(context: VisualEffectContext) {
    attachCalls++
    attachedContext = context
  }

  override fun update(context: VisualEffectContext) {
    updateCalls++
  }

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    prepareDrawCalls++
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    drawCalls++
  }

  override fun DrawScope.drawForeground(context: VisualEffectContext) {
    drawForegroundCalls++
  }

  override val observesPointerEvents: Boolean = true

  override fun onPointerEvent(
    event: androidx.compose.ui.input.pointer.PointerEvent,
    context: VisualEffectContext,
  ) {
    pointerEventCalls++
  }

  override fun onCancelPointerInput(context: VisualEffectContext) = Unit

  override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean {
    retainedAvailabilityCalls++
    return false
  }

  override fun clearRetainedOutput() {
    clearRetainedOutputCalls++
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    trimMemoryCalls++
  }

  override fun detach(context: VisualEffectContext) {
    detachCalls++
    attachedContext = null
  }
}
