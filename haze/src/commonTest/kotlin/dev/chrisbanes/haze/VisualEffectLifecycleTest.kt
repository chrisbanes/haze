// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import assertk.assertions.isNotNull
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
    assertThat(effect1.lastDetachContext).isNotNull()
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
  fun visualEffect_attachMayRunBeforeGeometryResolved() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = RecordingVisualEffect()

    setContent {
      Box(Modifier.size(100.dp).hazeSource(hazeState)) {
        Spacer(Modifier.size(100.dp).hazeEffect(hazeState) { visualEffect = effect })
      }
    }

    waitForIdle()
    assertThat(effect.attachSawUnspecifiedSize).isEqualTo(true)
  }

  @Test
  fun visualEffect_drawBehindScopeFlagShortCircuitsEffectHook() = runComposeUiTest {
    val effect = DrawBehindProbeVisualEffect(returnValue = true)

    setContent {
      Box(Modifier.size(100.dp)) {
        Spacer(
          Modifier
            .size(100.dp)
            .hazeEffect {
              drawContentBehind = true
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    assertThat(effect.shouldDrawContentBehindCalls).isEqualTo(0)
  }

  @Test
  fun visualEffect_calculateLayerBounds_usesLocalRectInForegroundMode() = runComposeUiTest {
    val effect = LayerBoundsRecordingVisualEffect()

    setContent {
      Box(
        Modifier
          .size(80.dp)
          .hazeEffect {
            visualEffect = effect
          },
      )
    }

    waitForIdle()
    val rect = effect.lastForegroundRect
    assertThat(rect).isNotNull()
    assertThat(rect!!.topLeft).isEqualTo(Offset.Zero)
  }

  @Test
  fun visualEffect_calculateLayerBounds_usesScreenAlignedRectInBackgroundMode() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = LayerBoundsRecordingVisualEffect()

    setContent {
      Box(
        Modifier
          .size(200.dp)
          .padding(20.dp),
      ) {
        Spacer(
          Modifier
            .size(120.dp)
            .hazeSource(hazeState),
        )
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
    val rect = effect.lastBackgroundRect
    assertThat(rect).isNotNull()
    assertThat(rect!!.topLeft == Offset.Zero).isEqualTo(false)
  }
}

internal class RecordingVisualEffect : VisualEffect {
  var attachCalls = 0
  var detachCalls = 0
  var updateCalls = 0
  var drawCalls = 0
  var trimMemoryCalls = 0
  var attachSawUnspecifiedSize = false
  var lastDetachContext: VisualEffectContext? = null

  override fun attach(context: VisualEffectContext) {
    attachCalls++
    attachSawUnspecifiedSize = context.size == Size.Unspecified || context.size == Size.Zero
  }

  override fun update(context: VisualEffectContext) {
    updateCalls++
  }

  override fun detach(context: VisualEffectContext) {
    detachCalls++
    lastDetachContext = context
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    drawCalls++
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    trimMemoryCalls++
  }
}

internal class DrawBehindProbeVisualEffect(
  private val returnValue: Boolean,
) : VisualEffect {
  var shouldDrawContentBehindCalls: Int = 0

  override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
    shouldDrawContentBehindCalls++
    return returnValue
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}

internal class LayerBoundsRecordingVisualEffect : VisualEffect {
  private var isBackgroundMode: Boolean = false
  var lastForegroundRect: Rect? = null
  var lastBackgroundRect: Rect? = null

  override fun update(context: VisualEffectContext) {
    isBackgroundMode = context.state != null
  }

  override fun calculateLayerBounds(rect: Rect, density: androidx.compose.ui.unit.Density): Rect {
    if (isBackgroundMode) {
      lastBackgroundRect = rect
    } else {
      lastForegroundRect = rect
    }
    return rect
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
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
  override val coroutineScope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext)

  override fun requireDensity(): androidx.compose.ui.unit.Density = error("Fake")
  override fun <T> currentValueOf(local: androidx.compose.runtime.CompositionLocal<T>): T = error("Fake")
  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle tests")
  override fun requireGraphicsContext(): androidx.compose.ui.graphics.GraphicsContext = error("Fake")
  override fun invalidateDraw() {}
}
