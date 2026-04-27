// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class VisualEffectLifecycleTest : ContextTest() {

  @Test
  fun visualEffect_attachCalledWhenSet() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RecordingVisualEffect()

    setContent {
      Box(
        modifier = Modifier
          .size(100.dp)
          .hazeSource(hazeState),
      ) {
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
    assertThat(effect.attachCalls).isEqualTo(1)
    assertThat(effect.detachCalls).isEqualTo(0)
  }

  @Test
  fun visualEffect_detachCalledOnNodeDetach() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RecordingVisualEffect()
    val showContent = mutableStateOf(true)

    setContent {
      if (showContent.value) {
        Box(
          modifier = Modifier
            .size(100.dp)
            .hazeSource(hazeState),
        ) {
          Spacer(
            Modifier
              .size(100.dp)
              .hazeEffect(hazeState) {
                visualEffect = effect
              },
          )
        }
      }
    }

    waitForIdle()
    assertThat(effect.attachCalls).isEqualTo(1)

    showContent.value = false
    waitForIdle()

    assertThat(effect.detachCalls).isEqualTo(1)
  }

  @Test
  fun visualEffect_detachOldAndAttachNewWhenReplaced() = runComposeUiTest {
    val hazeState = HazeState()
    val effect1 = RecordingVisualEffect()
    val effect2 = RecordingVisualEffect()
    val useSecondEffect = mutableStateOf(false)

    setContent {
      Box(
        modifier = Modifier
          .size(100.dp)
          .hazeSource(hazeState),
      ) {
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              visualEffect = if (useSecondEffect.value) effect2 else effect1
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect1.attachCalls).isEqualTo(1)
    assertThat(effect1.detachCalls).isEqualTo(0)

    useSecondEffect.value = true
    waitForIdle()

    assertThat(effect1.detachCalls).isEqualTo(1)
    assertThat(effect2.attachCalls).isEqualTo(1)
    assertThat(effect2.detachCalls).isEqualTo(0)
  }

  @Test
  fun visualEffect_updateCalledWhenRecomposed() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RecordingVisualEffect()
    val drawBehind = mutableStateOf(false)

    setContent {
      Box(
        modifier = Modifier
          .size(100.dp)
          .hazeSource(hazeState),
      ) {
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect(hazeState) {
              drawContentBehind = drawBehind.value
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val initialUpdateCount = effect.updateCalls

    drawBehind.value = true
    waitForIdle()

    assertThat(effect.updateCalls).isGreaterThan(initialUpdateCount)
  }

  @Test
  fun emptyVisualEffect_doesNothing() {
    val empty = VisualEffect.Empty
    empty.attach(FakeVisualEffectContext)
    empty.update(FakeVisualEffectContext)
    empty.detach()
    assertThat(empty.shouldClip()).isEqualTo(false)
    assertThat(empty.requireInvalidation()).isEqualTo(false)
    assertThat(empty.preferClipToAreaBounds()).isEqualTo(false)
  }
}

internal class RecordingVisualEffect : VisualEffect {
  var attachCalls = 0
  var detachCalls = 0
  var updateCalls = 0
  var drawCalls = 0
  var trimMemoryCalls = 0

  override fun attach(context: VisualEffectContext) {
    attachCalls++
  }

  override fun update(context: VisualEffectContext) {
    updateCalls++
  }

  override fun detach() {
    detachCalls++
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    drawCalls++
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    trimMemoryCalls++
  }
}

internal data object FakeVisualEffectContext : VisualEffectContext {
  override val position: Offset = Offset.Zero
  override val size: Size = Size.Zero
  override val layerSize: Size = Size.Zero
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect.Zero
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val visualEffect: VisualEffect = VisualEffect.Empty
  override val coroutineScope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)

  override fun requireDensity(): androidx.compose.ui.unit.Density = error("Fake")
  override fun <T> currentValueOf(local: androidx.compose.runtime.CompositionLocal<T>): T = error("Fake")
  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle tests")
  override fun requireGraphicsContext(): androidx.compose.ui.graphics.GraphicsContext = error("Fake")
  override fun invalidateDraw() {}
}
