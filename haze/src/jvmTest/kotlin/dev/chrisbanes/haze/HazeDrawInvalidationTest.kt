// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeDrawInvalidationTest : ContextTest() {

  @Test
  fun rendererRequestedInvalidateDraw_duringDrawSchedulesSubsequentFrame() = runComposeUiTest {
    val factory = DuringDrawInvalidatingRendererFactory()
    val shouldInvalidate = mutableStateOf(false)

    setContent {
      Spacer(
        Modifier
          .background(Color.Red)
          .hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = shouldInvalidate.value,
          )
          .size(100.dp),
      )
    }
    waitForIdle()

    val renderer = factory.renderer
    val initialDrawCalls = renderer.drawCalls
    shouldInvalidate.value = true
    waitForIdle()

    assertThat(renderer.drawCalls).isEqualTo(initialDrawCalls + 2)
  }
}

private class DuringDrawInvalidatingRendererFactory : HazeEffectFactory<Boolean> {
  val renderer = DuringDrawInvalidatingRenderer()

  override fun createRenderer(): HazeEffectRenderer<Boolean> = renderer
}

@OptIn(InternalHazeApi::class)
private class DuringDrawInvalidatingRenderer :
  HazeEffectRenderer<Boolean>,
  HazeEffectRendererDrawHooks<Boolean> {
  var drawCalls = 0
  private var invalidationRequested = false

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: Boolean) {
    if (style && !invalidationRequested) {
      invalidationRequested = true
      invalidateDraw()
    }
  }

  override fun HazeEffectDrawScope.draw(style: Boolean) {
    drawCalls++
  }
}
