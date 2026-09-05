// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNull
import kotlin.test.Test
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class HazeBackdropFallbackTest {
  @Test
  fun healthyNativeBackdrop_doesNotDemandFallbackCapture() = runComposeUiTest {
    val state = HazeState()
    val nativeRenderer = TestBackdropRenderer()
    val previousFlag = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    try {
      setContent {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        Box(Modifier.size(100.dp)) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeSource(state)
              .background(Color.Red),
          )
          Box(
            Modifier
              .fillMaxSize()
              .then(
                FaultInjectedEffectElement(
                  input = HazeInput.Backdrop(state),
                  lifecycle = lifecycle,
                  createRenderer = { nativeRenderer },
                  renderer = HealthyBackdropRenderer(),
                ),
              ),
          )
        }
      }
      waitForIdle()

      val area = state.areas.single()
      assertThat(area.captureConsumerCount).isEqualTo(0)
      assertThat(area.contentVersion).isEqualTo(0L)
      assertThat(area.contentLayer).isNull()
      assertThat(nativeRenderer.drawCalls).isGreaterThan(0)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }

  @Test
  fun rendererCreationFailure_activatesFallbackWithoutEscapingDraw() = runComposeUiTest {
    val state = HazeState()
    var creationAttempts = 0
    val previousFlag = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    try {
      setContent {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        Box(Modifier.size(100.dp)) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeSource(state)
              .background(Color.Red),
          )
          Box(
            Modifier
              .fillMaxSize()
              .testTag("creation-effect")
              .then(
                FaultInjectedEffectElement(
                  input = HazeInput.Backdrop(state),
                  lifecycle = lifecycle,
                  createRenderer = {
                    creationAttempts++
                    error("test renderer creation failure")
                  },
                  renderer = ThrowingBackdropRenderer(),
                ),
              ),
          )
        }
      }
      waitForIdle()

      assertThat(onNodeWithTag("creation-effect").captureToImage().toPixelMap()[50, 50])
        .isEqualTo(Color.Red)
      assertThat(state.areas.single().captureConsumerCount).isEqualTo(1)
      assertThat(creationAttempts).isEqualTo(1)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }

  @Test
  fun preparationFailure_activatesStickyFallbackAndDemandsCapture() = runComposeUiTest {
    val state = HazeState()
    var creationAttempts = 0
    val attached = mutableStateOf(true)
    val invalidation = mutableStateOf(0)
    val renderers = mutableListOf<TestBackdropRenderer>()
    val input = HazeInput.Backdrop(state)
    val previousFlag = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    try {
      setContent {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        Box(Modifier.size(100.dp)) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeSource(state)
              .background(Color.Red),
          )
          if (attached.value) {
            Box(
              Modifier
                .fillMaxSize()
                .testTag("effect")
                .drawWithContent {
                  invalidation.value
                  drawContent()
                }
                .then(
                  FaultInjectedEffectElement(
                    input = input,
                    lifecycle = lifecycle,
                    createRenderer = {
                      creationAttempts++
                      TestBackdropRenderer().also(renderers::add)
                    },
                    renderer = ThrowingBackdropRenderer(),
                  ),
                ),
            )
          }
        }
      }
      waitForIdle()

      assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
        .isEqualTo(Color.Red)
      assertThat(state.areas.single().captureConsumerCount).isEqualTo(1)
      assertThat(creationAttempts).isEqualTo(1)
      assertThat(renderers.size).isEqualTo(1)
      assertThat(renderers.single().releaseCalls).isEqualTo(1)

      repeat(3) {
        invalidation.value++
        waitForIdle()
      }
      assertThat(creationAttempts).isEqualTo(1)
      assertThat(renderers.single().releaseCalls).isEqualTo(1)

      attached.value = false
      waitForIdle()
      attached.value = true
      waitForIdle()
      assertThat(creationAttempts).isEqualTo(2)
      assertThat(renderers.size).isEqualTo(2)
      assertThat(renderers.first().releaseCalls).isEqualTo(1)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }
}

private class FaultInjectedEffectElement(
  private val input: HazeInput,
  private val lifecycle: Lifecycle,
  private val createRenderer: () -> HazeBackdropRenderer?,
  private val renderer: HazeEffectRenderer<Unit>,
) : ModifierNodeElement<HazeEffectNode>() {
  override fun create(): HazeEffectNode = HazeEffectNode(createRenderer).also {
    it.explicitInput = input
    it.updateTypedEffect(StaticRendererFactory(renderer), Unit, HazeSampling.Default)
    it.updateLifecycle(lifecycle)
  }

  override fun update(node: HazeEffectNode) {
    node.explicitInput = input
    node.updateLifecycle(lifecycle)
    node.update()
  }

  override fun hashCode(): Int = input.hashCode()

  override fun equals(other: Any?): Boolean =
    other is FaultInjectedEffectElement && other.input == input
}

private class StaticRendererFactory(
  private val renderer: HazeEffectRenderer<Unit>,
) : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = renderer
}

private class ThrowingBackdropRenderer :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererBackdrop<Unit> {
  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()

  override fun HazeEffectRuntimeDrawScope.backdropEffect(style: Unit): HazeEffectBackdrop {
    throw IllegalStateException("test backdrop preparation failure")
  }
}

private class HealthyBackdropRenderer :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererBackdrop<Unit> {
  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()

  override fun HazeEffectRuntimeDrawScope.backdropEffect(style: Unit): HazeEffectBackdrop =
    HazeEffectBackdrop(ImageFilter.makeBlur(0f, 0f, FilterTileMode.CLAMP))
}

private class TestBackdropRenderer : HazeBackdropRenderer {
  var drawCalls = 0
  var releaseCalls = 0

  override fun isSupported(canvas: Canvas): Boolean = true

  override fun configure(
    bounds: androidx.compose.ui.geometry.Rect,
    clip: androidx.compose.ui.geometry.Rect?,
    effect: PlatformRenderEffect,
    alpha: Float,
  ): Boolean = true

  override fun draw(canvas: Canvas): Boolean {
    drawCalls++
    return true
  }

  override fun release() {
    releaseCalls++
  }
}
