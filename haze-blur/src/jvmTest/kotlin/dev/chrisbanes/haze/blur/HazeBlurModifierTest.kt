// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalTestApi::class, InternalHazeApi::class)
class HazeBlurModifierTest {

  @Test
  fun sourceInput_zeroAlphaDefersInvalidationAndKeepsLastFrame() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val showSource = mutableStateOf(true)
    val alpha = mutableStateOf(0.5f)
    val renderer = RecordingBlurRenderer()
    val factory = HazeEffectFactory<HazeBlurStyle> { renderer }

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Box(
            Modifier
              .fillMaxSize()
              .background(sourceColor.value)
              .hazeSource(hazeState),
          )
        }
        Box(
          Modifier
            .fillMaxSize()
            .testTag("effect")
            .hazeEffect(
              factory = factory,
              input = HazeInput.Sources(
                state = hazeState,
                retention = HazeSourceRetention.KeepLastFrame,
              ),
              style = testBlurStyle(alpha.value),
              sampling = HazeSampling.FullResolution,
            ),
        )
      }
    }
    waitForIdle()

    val visibleDraws = renderer.drawCount
    assertThat(renderer.canDrawRetainedOutput()).isTrue()

    alpha.value = 0f
    waitForIdle()
    val drawsBeforeInvalidation = renderer.drawCount
    sourceColor.value = Color.Blue
    waitForIdle()
    showSource.value = false
    waitForIdle()

    assertThat(drawsBeforeInvalidation).isEqualTo(visibleDraws)
    assertThat(renderer.drawCount).isEqualTo(drawsBeforeInvalidation)
    assertThat(renderer.canDrawRetainedOutput()).isTrue()

    alpha.value = 0.5f
    waitForIdle()

    assertThat(renderer.drawCount).isGreaterThan(drawsBeforeInvalidation)
    val drawsWithoutSource = renderer.drawCount

    showSource.value = true
    waitForIdle()

    assertThat(renderer.drawCount).isGreaterThan(drawsWithoutSource)
    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
  }

  @Test
  fun sourceInput_clearWhenUnavailableStillReleasesWhileAlphaIsZero() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val alpha = mutableStateOf(0.5f)
    val renderer = RecordingBlurRenderer()
    val factory = HazeEffectFactory<HazeBlurStyle> { renderer }

    setContent {
      Box(Modifier.size(100.dp)) {
        if (showSource.value) {
          Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        }
        Box(
          Modifier
            .fillMaxSize()
            .hazeEffect(
              factory = factory,
              input = HazeInput.Sources(
                state = hazeState,
                retention = HazeSourceRetention.ClearWhenUnavailable,
              ),
              style = testBlurStyle(alpha.value),
              sampling = HazeSampling.FullResolution,
            ),
        )
      }
    }
    waitForIdle()

    assertThat(renderer.canDrawRetainedOutput()).isTrue()
    val clearsBeforeUnavailable = renderer.clearCount

    alpha.value = 0f
    waitForIdle()
    showSource.value = false
    waitForIdle()

    assertThat(renderer.clearCount).isGreaterThan(clearsBeforeUnavailable)
    assertThat(renderer.canDrawRetainedOutput()).isFalse()
  }

  @Test
  fun contentInput_zeroAlphaRetainsEffectLayerAndRefreshesCurrentContentWhenVisible() =
    runComposeUiTest {
      val contentColor = mutableStateOf(Color.Red)
      val alpha = mutableStateOf(0.5f)
      val renderer = RecordingBlurRenderer()
      val factory = HazeEffectFactory<HazeBlurStyle> { renderer }

      setContent {
        Box(
          Modifier
            .size(100.dp)
            .testTag("effect")
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = testBlurStyle(alpha.value),
              sampling = HazeSampling.FullResolution,
            )
            .background(contentColor.value),
        )
      }
      waitForIdle()

      val visibleDraws = renderer.drawCount
      assertThat(renderer.canDrawRetainedOutput()).isTrue()

      alpha.value = 0f
      waitForIdle()
      val drawsBeforeInvalidation = renderer.drawCount
      contentColor.value = Color.Blue
      waitForIdle()

      assertThat(drawsBeforeInvalidation).isEqualTo(visibleDraws)
      assertThat(renderer.drawCount).isEqualTo(drawsBeforeInvalidation)
      assertThat(renderer.canDrawRetainedOutput()).isTrue()
      assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
        .isEqualTo(Color.Blue)

      alpha.value = 0.5f
      waitForIdle()

      assertThat(renderer.drawCount).isGreaterThan(drawsBeforeInvalidation)
      assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
        .isEqualTo(Color.Blue)
    }

  @Test
  fun zeroAlpha_closesPreparationGateWithoutClearingRetainedOutput() {
    val effect = BlurVisualEffect()
    val delegate = ModifierTrackingDelegate(retainedOutputAvailable = true)
    effect.delegate = delegate

    effect.update(
      TestLifecycleScope,
      HazeBlurStyle { alpha(0f) },
      HazeSampling.Default,
    )

    assertThat(effect.shouldPrepareDraw(HazeBlurStyle)).isFalse()
    assertThat(effect.dirtyTracker).isEqualTo(Bitmask())
    assertThat(effect.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.clearCount).isEqualTo(0)

    effect.update(
      TestLifecycleScope,
      HazeBlurStyle { alpha(0.5f) },
      HazeSampling.Default,
    )

    assertThat(effect.shouldPrepareDraw(HazeBlurStyle)).isTrue()
    assertThat(effect.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.clearCount).isEqualTo(0)
  }

  @Test
  fun zeroAlpha_directRenderEffectEntryDoesNoWork() {
    val effect = BlurVisualEffect()
    effect.update(
      TestLifecycleScope,
      HazeBlurStyle { alpha(0f) },
      HazeSampling.Default,
    )
    val delegate = RenderEffectBlurVisualEffectDelegate(effect)
    val context = ZeroAlphaDrawContext()

    context.draw {
      with(delegate) { draw(context) }
    }

    assertThat(context.graphicsContextRequests).isEqualTo(0)
  }

  @Test
  fun contentInput_resolvesDefaultsLocalAndExplicitStyles() = runComposeUiTest {
    val localStyle = HazeBlurStyle {
      blurEnabled(false)
      fallbackColorEffect(HazeColorEffect.tint(Color.Red))
    }
    val explicitStyle = mutableStateOf(
      HazeBlurStyle {
        fallbackColorEffect(HazeColorEffect.tint(Color.Blue))
      },
    )

    setContent {
      CompositionLocalProvider(LocalHazeBlurStyle provides localStyle) {
        Box(
          Modifier
            .size(100.dp)
            .testTag("effect")
            .hazeBlur(
              input = HazeInput.Content,
              style = explicitStyle.value,
            )
            .background(Color.White),
        )
      }
    }

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)

    explicitStyle.value = HazeBlurStyle
    waitForIdle()

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Red)
  }

  @Test
  fun sourceInput_drawsTheSelectedSourceWithFallbackStyle() = runComposeUiTest {
    val state = HazeState()
    val style = HazeBlurStyle {
      blurEnabled(false)
      fallbackColorEffect(HazeColorEffect.tint(Color.Blue))
    }

    setContent {
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
            .testTag("effect")
            .hazeBlur(
              input = HazeInput.Sources(state),
              style = style,
              sampling = HazeSampling.FullResolution,
              expandLayerBounds = false,
            ),
        )
      }
    }

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
  }

  @Test
  fun sharedFactory_createsIsolatedRuntimesAndStyleReplacementReusesRuntime() {
    val sharedStyle = HazeBlurStyle {
      blurRadius(12.dp)
    }
    val first = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val second = HazeBlurFactory.createRenderer() as BlurVisualEffect
    first.update(TestLifecycleScope, sharedStyle, HazeSampling.Default)
    second.update(TestLifecycleScope, sharedStyle, HazeSampling.Default)

    assertThat(first).isNotSameInstanceAs(second)

    val firstRuntime = first
    first.update(
      scope = TestLifecycleScope,
      style = HazeBlurStyle {
        blurRadius(24.dp)
      },
      sampling = HazeSampling.FullResolution,
    )

    assertThat(first).isSameInstanceAs(firstRuntime)
    assertThat(first.blurRadius).isEqualTo(24.dp)
    assertThat(second.blurRadius).isEqualTo(12.dp)
  }

  @Test
  fun detachingOneSharedFactoryRuntime_releasesOnlyThatRuntime() {
    val style = HazeBlurStyle
    val first = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val second = HazeBlurFactory.createRenderer() as BlurVisualEffect
    first.update(TestLifecycleScope, style, HazeSampling.Default)
    second.update(TestLifecycleScope, style, HazeSampling.Default)
    val firstDelegate = ModifierTrackingDelegate()
    val secondDelegate = ModifierTrackingDelegate()
    first.delegate = firstDelegate
    second.delegate = secondDelegate

    first.attach(TestLifecycleScope)
    second.attach(TestLifecycleScope)
    first.detach()

    assertThat(firstDelegate.attachCount).isEqualTo(1)
    assertThat(firstDelegate.detachCount).isEqualTo(1)
    assertThat(secondDelegate.attachCount).isEqualTo(1)
    assertThat(secondDelegate.detachCount).isEqualTo(0)

    second.detach()
    assertThat(secondDelegate.detachCount).isEqualTo(1)
  }
}

private fun testBlurStyle(alpha: Float): HazeBlurStyle = HazeBlurStyle {
  blurRadius(0.dp)
  noiseFactor(0f)
  alpha(alpha)
}

private class RecordingBlurRenderer :
  HazeEffectRenderer<HazeBlurStyle>,
  HazeEffectRendererLifecycle<HazeBlurStyle>,
  HazeEffectRendererDrawHooks<HazeBlurStyle>,
  HazeEffectRendererRetainedOutput {
  private val effect = BlurVisualEffect()
  private var retainedOutputAvailable = false
  var drawCount = 0
    private set
  var clearCount = 0
    private set

  override fun attach(scope: HazeEffectLifecycleScope) = effect.attach(scope)

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: HazeBlurStyle,
    sampling: HazeSampling,
  ) = effect.update(scope, style, sampling)

  override fun shouldPrepareDraw(style: HazeBlurStyle): Boolean =
    effect.shouldPrepareDraw(style)

  override fun HazeEffectDrawScope.draw(style: HazeBlurStyle) {
    drawCount++
    drawInput()
    retainedOutputAvailable = true
  }

  override fun canDrawRetainedOutput(): Boolean = retainedOutputAvailable

  override fun clearRetainedOutput() {
    clearCount++
    retainedOutputAvailable = false
  }

  override fun detach() = effect.detach()
}

private class ModifierTrackingDelegate(
  private val retainedOutputAvailable: Boolean = false,
) : BlurVisualEffect.Delegate, RetainedOutputDelegate {
  var attachCount = 0
    private set
  var detachCount = 0
    private set
  var clearCount = 0
    private set

  override fun attach() {
    attachCount++
  }

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) = Unit

  override fun detach() {
    detachCount++
  }

  override fun canDrawRetainedOutput(): Boolean = retainedOutputAvailable

  override fun clearRetainedOutput() {
    clearCount++
  }
}

@OptIn(InternalHazeApi::class)
private class ZeroAlphaDrawContext(
  private val drawScope: CanvasDrawScope = CanvasDrawScope(),
) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
  var graphicsContextRequests = 0
    private set

  override val modifierSize: Size = Size(10f, 10f)
  override val modifierBounds: Rect = Rect(Offset.Zero, modifierSize)
  override val sampling: HazeSampling = HazeSampling.FullResolution
  override val layerSize: Size = modifierSize
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = true
  override val inputSnapshot: HazeEffectInputSnapshot? = null
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  fun draw(block: DrawScope.() -> Unit) {
    drawScope.draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(ImageBitmap(10, 10)),
      size = modifierSize,
      block = block,
    )
  }

  override fun requirePlatformContext(): PlatformContext = error("Unexpected platform access")
  override fun requireGraphicsContext(): GraphicsContext {
    graphicsContextRequests++
    error("Unexpected graphics context access")
  }
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unexpected local read")
  override fun invalidateDraw() = Unit
  override fun drawInput() = error("Unexpected input capture")
  override fun DrawScope.drawInput() = error("Unexpected input capture")
}

@OptIn(InternalHazeApi::class)
private data object TestLifecycleScope : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in modifier tests")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T =
    if (local === LocalHazeBlurStyle) HazeBlurStyle as T else error("Unused local")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in modifier tests")
  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}

@Suppress("unused")
// HazeEffectInputTest behaviorally verifies these structural policies at the shared core seam.
// This compile inventory verifies that the typed Blur entry point forwards every policy shape.
private fun everyTypedBlurPolicyCompiles(
  state: HazeState,
  style: HazeBlurStyle,
): Modifier = Modifier
  .hazeBlur(
    input = HazeInput.Sources(
      state = state,
      retention = HazeSourceRetention.KeepLastFrame,
    ),
    style = style,
    sampling = HazeSampling.Default,
    expandLayerBounds = true,
  )
  .hazeBlur(
    input = HazeInput.Sources(
      state = state,
      retention = HazeSourceRetention.ClearWhenUnavailable,
    ),
    sampling = HazeSampling.Adaptive,
  )
  .hazeBlur(
    input = HazeInput.Content,
    sampling = HazeSampling.Fixed(0.6f),
  )
