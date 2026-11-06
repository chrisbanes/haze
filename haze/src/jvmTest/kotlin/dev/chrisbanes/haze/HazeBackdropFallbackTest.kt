// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
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
  fun emptyLayer_retriesNativeAfterGrowthWithoutFallbackCapture() = runComposeUiTest {
    val state = HazeState()
    val nativeRenderer = TestBackdropRenderer().apply { rejectEmptyBounds = true }
    val effectRenderer = HealthyBackdropRenderer().apply { emptyBounds = true }
    val height = mutableStateOf(50.dp)
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
              .size(width = 100.dp, height = height.value)
              .then(
                FaultInjectedEffectElement(
                  input = HazeInput.Backdrop(state),
                  lifecycle = lifecycle,
                  createRenderer = { nativeRenderer },
                  renderer = effectRenderer,
                ),
              ),
          )
        }
      }
      waitForIdle()

      assertThat(nativeRenderer.emptyConfigureCalls).isGreaterThan(0)
      assertThat(nativeRenderer.releaseCalls).isEqualTo(0)
      assertThat(state.areas.single().captureConsumerCount).isEqualTo(0)

      effectRenderer.emptyBounds = false
      height.value = 100.dp
      waitForIdle()

      val area = state.areas.single()
      assertThat(area.captureConsumerCount).isEqualTo(0)
      assertThat(area.contentVersion).isEqualTo(0L)
      assertThat(area.contentLayer).isNull()
      assertThat(nativeRenderer.drawCalls).isGreaterThan(0)
      assertThat(nativeRenderer.releaseCalls).isEqualTo(0)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }

  @Test
  fun backdropClip_zeroOutset_isNodeRect() {
    assertThat(captureBackdropClip(outsetPx = 0f))
      .isEqualTo(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f))
  }

  @Test
  fun backdropClip_positiveOutset_inflatesNodeRect() {
    assertThat(captureBackdropClip(outsetPx = 6f))
      .isEqualTo(androidx.compose.ui.geometry.Rect(-6f, -6f, 106f, 106f))
  }

  @Test
  fun backdropClip_nonFiniteOutset_isNodeRect() {
    assertThat(captureBackdropClip(outsetPx = Float.NaN))
      .isEqualTo(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f))
  }

  @Test
  fun backdropLayer_expansionDisabled_positiveOutset_inflatesByOutset() {
    val renderer = TestBackdropRenderer()
    captureBackdropClip(outsetPx = 6f, expandLayerBounds = false, nativeRenderer = renderer)
    assertThat(renderer.lastBounds).isEqualTo(androidx.compose.ui.geometry.Rect(-6f, -6f, 106f, 106f))
  }

  @Test
  fun backdropLayer_expansionDisabled_zeroOutset_isNodeRect() {
    val renderer = TestBackdropRenderer()
    captureBackdropClip(outsetPx = 0f, expandLayerBounds = false, nativeRenderer = renderer)
    assertThat(renderer.lastBounds).isEqualTo(androidx.compose.ui.geometry.Rect(0f, 0f, 100f, 100f))
  }

  @Test
  fun contentLayer_noClipNoExpansion_positiveOutset_inflatesByOutset() {
    assertThat(captureContentLayerSize(outsetPx = 6f)).isEqualTo(Size(112f, 112f))
    assertThat(captureContentLayerSize(outsetPx = 0f)).isEqualTo(Size(100f, 100f))
  }

  private fun captureContentLayerSize(outsetPx: Float): Size? {
    val renderer = ContentLayerSizeRenderer(outsetPx)
    runComposeUiTest {
      setContent {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        Box(
          Modifier
            .padding(20.dp)
            .size(100.dp)
            .then(
              FaultInjectedEffectElement(
                input = HazeInput.Content,
                lifecycle = lifecycle,
                createRenderer = { null },
                renderer = renderer,
                expandLayerBounds = false,
              ),
            ),
        )
      }
      waitForIdle()
    }
    return renderer.layerSize
  }

  private fun captureBackdropClip(
    outsetPx: Float,
    expandLayerBounds: Boolean = true,
    nativeRenderer: TestBackdropRenderer = TestBackdropRenderer(),
  ): androidx.compose.ui.geometry.Rect? {
    val previousFlag = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    try {
      runComposeUiTest {
        val state = HazeState()
        setContent {
          val lifecycle = LocalLifecycleOwner.current.lifecycle
          // The padding leaves room around the node for an inflated layer.
          Box(Modifier.padding(20.dp).size(100.dp)) {
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
                    renderer = ClippingBackdropRenderer(outsetPx),
                    expandLayerBounds = expandLayerBounds,
                  ),
                ),
            )
          }
        }
        waitForIdle()
      }
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
    assertThat(nativeRenderer.drawCalls).isGreaterThan(0)
    return nativeRenderer.lastClip
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
  fun rendererCreationFailure_withoutFallbackDrawsContentWithoutCaptureDemand() = runComposeUiTest {
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
              .testTag("no-fallback-effect")
              .then(
                FaultInjectedEffectElement(
                  input = HazeInput.Backdrop(),
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

      assertThat(onNodeWithTag("no-fallback-effect").captureToImage().toPixelMap()[50, 50])
        .isEqualTo(Color.Red)
      assertThat(state.areas.single().captureConsumerCount).isEqualTo(0)
      assertThat(creationAttempts).isEqualTo(1)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }

  @Test
  fun declinedFrameAfterNativeDraw_resumesNativeWithoutFallback() = runComposeUiTest {
    val state = HazeState()
    val nativeRenderer = TestBackdropRenderer()
    val effectRenderer = DecliningBackdropRenderer()
    val invalidation = mutableStateOf(0)
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
              .drawWithContent {
                invalidation.value
                drawContent()
              }
              .then(
                FaultInjectedEffectElement(
                  input = HazeInput.Backdrop(state),
                  lifecycle = lifecycle,
                  createRenderer = { nativeRenderer },
                  renderer = effectRenderer,
                ),
              ),
          )
        }
      }
      waitForIdle()
      val nativeDraws = nativeRenderer.drawCalls
      assertThat(nativeDraws).isGreaterThan(0)

      effectRenderer.declines = true
      invalidation.value++
      waitForIdle()
      assertThat(nativeRenderer.drawCalls).isEqualTo(nativeDraws)

      effectRenderer.declines = false
      invalidation.value++
      waitForIdle()
      assertThat(nativeRenderer.drawCalls).isGreaterThan(nativeDraws)
      assertThat(nativeRenderer.releaseCalls).isEqualTo(0)
      assertThat(state.areas.single().captureConsumerCount).isEqualTo(0)
    } finally {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousFlag
    }
  }

  @Test
  fun declinedFirstFrameWithoutFallback_retriesNative() = runComposeUiTest {
    val nativeRenderer = TestBackdropRenderer()
    val effectRenderer = DecliningBackdropRenderer().apply { declines = true }
    val invalidation = mutableStateOf(0)
    val previousFlag = HazeFeatureFlags.isPlatformBackdropEnabled
    HazeFeatureFlags.isPlatformBackdropEnabled = true
    try {
      setContent {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        Box(
          Modifier
            .size(100.dp)
            .drawWithContent {
              invalidation.value
              drawContent()
            }
            .then(
              FaultInjectedEffectElement(
                input = HazeInput.Backdrop(),
                lifecycle = lifecycle,
                createRenderer = { nativeRenderer },
                renderer = effectRenderer,
              ),
            ),
        )
      }
      waitForIdle()
      assertThat(nativeRenderer.drawCalls).isEqualTo(0)

      effectRenderer.declines = false
      invalidation.value++
      waitForIdle()
      assertThat(nativeRenderer.drawCalls).isGreaterThan(0)
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
  private val expandLayerBounds: Boolean = true,
) : ModifierNodeElement<HazeEffectNode>() {
  override fun create(): HazeEffectNode = HazeEffectNode(createRenderer).also {
    it.explicitInput = input
    it.expandLayerBounds = expandLayerBounds
    it.updateTypedEffect(StaticRendererFactory(renderer), Unit, HazeSampling.Default)
    it.updateLifecycle(lifecycle)
  }

  override fun update(node: HazeEffectNode) {
    node.explicitInput = input
    node.expandLayerBounds = expandLayerBounds
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
  var emptyBounds = false

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: Unit): androidx.compose.ui.geometry.Rect =
    if (emptyBounds) androidx.compose.ui.geometry.Rect.Zero else modifierBounds

  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()

  override fun HazeEffectRuntimeDrawScope.backdropEffect(style: Unit): HazeEffectBackdrop =
    HazeEffectBackdrop(ImageFilter.makeBlur(0f, 0f, FilterTileMode.CLAMP))
}

private class ClippingBackdropRenderer(
  private val outsetPx: Float,
) : HazeEffectRenderer<Unit>,
  HazeEffectRendererBackdrop<Unit>,
  HazeEffectRendererDrawHooks<Unit> {
  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()

  override fun HazeEffectRuntimeDrawScope.backdropEffect(style: Unit): HazeEffectBackdrop =
    HazeEffectBackdrop(ImageFilter.makeBlur(0f, 0f, FilterTileMode.CLAMP))

  override fun shouldClipToNodeBounds(): Boolean = true

  override fun nodeClipOutsetPx(): Float = outsetPx
}

private class ContentLayerSizeRenderer(
  private val outsetPx: Float,
) : HazeEffectRenderer<Unit>,
  HazeEffectRendererDrawHooks<Unit> {
  var layerSize: Size? = null

  override fun HazeEffectDrawScope.draw(style: Unit) {
    layerSize = (this as HazeEffectRuntimeDrawScope).layerSize
    drawInput()
  }

  override fun nodeClipOutsetPx(): Float = outsetPx
}

private class DecliningBackdropRenderer :
  HazeEffectRenderer<Unit>,
  HazeEffectRendererBackdrop<Unit> {
  var declines = false

  override fun HazeEffectDrawScope.draw(style: Unit) = drawInput()

  override fun HazeEffectRuntimeDrawScope.backdropEffect(style: Unit): HazeEffectBackdrop? =
    if (declines) null else HazeEffectBackdrop(ImageFilter.makeBlur(0f, 0f, FilterTileMode.CLAMP))
}

private class TestBackdropRenderer : HazeBackdropRenderer {
  var rejectEmptyBounds = false
  var emptyConfigureCalls = 0
  var drawCalls = 0
  var releaseCalls = 0
  var lastClip: androidx.compose.ui.geometry.Rect? = null
  var lastBounds: androidx.compose.ui.geometry.Rect? = null

  override fun isSupported(canvas: Canvas): Boolean = true

  override fun configure(
    bounds: androidx.compose.ui.geometry.Rect,
    clip: androidx.compose.ui.geometry.Rect?,
    effect: PlatformRenderEffect,
    alpha: Float,
  ): Boolean {
    lastClip = clip
    lastBounds = bounds
    if (rejectEmptyBounds && (bounds.width <= 0f || bounds.height <= 0f)) {
      emptyConfigureCalls++
      return false
    }
    return true
  }

  override fun draw(canvas: Canvas): Boolean {
    drawCalls++
    return true
  }

  override fun release() {
    releaseCalls++
  }
}
