// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class)
class VisualEffectDrawPreparationTest : ContextTest() {

  @Test
  fun visualEffect_foregroundDrawOrderQueryRunsAfterDrawPreparation() = runComposeUiTest {
    val effect = DrawPreparationOrderVisualEffect()

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.events.take(3)).isEqualTo(
      listOf("prepareDraw", "shouldDrawContentBehind", "draw"),
    )
  }
}

private class DrawPreparationOrderVisualEffect : VisualEffect {
  val events = mutableListOf<String>()

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    events += "prepareDraw"
  }

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
    events += "shouldDrawContentBehind"
    return true
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    events += "draw"
  }
}
