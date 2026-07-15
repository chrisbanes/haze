// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class VisualEffectContentVersionTest : ContextTest() {

  @Test
  fun contentVersionIncreasesAfterSourceContentChanges() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceContent = mutableStateOf(0)
    val effect = ContentVersionRecordingVisualEffect()

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(Modifier.size(100.dp).hazeSource(hazeState)) {
          Spacer(Modifier.size(sourceContent.value.dp))
        }
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val initialVersion = requireNotNull(effect.versions.lastOrNull())

    sourceContent.value = 1
    waitForIdle()

    assertThat(requireNotNull(effect.versions.lastOrNull())).isGreaterThan(initialVersion)
  }
}

private class ContentVersionRecordingVisualEffect : VisualEffect {
  val versions = mutableListOf<Long?>()

  override fun DrawScope.draw(context: VisualEffectContext) {
    versions += context.areas.singleOrNull()?.let(context::contentVersionOf)
  }
}
