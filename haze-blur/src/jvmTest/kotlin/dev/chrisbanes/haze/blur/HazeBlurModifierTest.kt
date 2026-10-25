// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLayoutScope
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
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
      BlurConfiguration(HazeBlurStyle { alpha(0f) }, HazePerformanceMode.Default),
      HazeSampling.Default,
    )

    assertThat(effect.shouldPrepareDraw(BlurConfiguration(HazeBlurStyle, HazePerformanceMode.Default))).isFalse()
    assertThat(effect.styleChanged).isFalse()
    assertThat(effect.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.clearCount).isEqualTo(0)

    effect.update(
      TestLifecycleScope,
      BlurConfiguration(HazeBlurStyle { alpha(0.5f) }, HazePerformanceMode.Default),
      HazeSampling.Default,
    )

    assertThat(effect.shouldPrepareDraw(BlurConfiguration(HazeBlurStyle, HazePerformanceMode.Default))).isTrue()
    assertThat(effect.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.clearCount).isEqualTo(0)
  }

  @Test
  fun zeroAlpha_directRenderEffectEntryDoesNoWork() {
    val effect = BlurVisualEffect()
    effect.update(
      TestLifecycleScope,
      BlurConfiguration(HazeBlurStyle { alpha(0f) }, HazePerformanceMode.Default),
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
              performanceMode = HazePerformanceMode.Quality,
              expandLayerBounds = false,
            ),
        )
      }
    }

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
  }

  @Test
  fun rememberedStyleStateReads_drawUpdatedValuesOnNextFrame() = runComposeUiTest {
    val radius = mutableStateOf(8.dp)
    val alpha = mutableStateOf(0.4f)
    val renderer = RecordingBlurRenderer()

    setContent { RecordingBlurBox(renderer, remember { stateStyle(radius, alpha) }) }
    waitForIdle()

    assertThat(renderer.blurRadius).isEqualTo(8.dp)
    assertThat(renderer.alpha).isEqualTo(0.4f)
    assertThat(renderer.drawnBlurRadius).isEqualTo(8.dp)
    assertThat(renderer.drawnAlpha).isEqualTo(0.4f)

    mainClock.autoAdvance = false
    val boundsBefore = renderer.layerBoundsInvalidations
    val drawsBefore = renderer.drawCount
    radius.value = 24.dp
    alpha.value = 0.7f
    mainClock.advanceTimeByFrame()

    assertThat(renderer.blurRadius).isEqualTo(24.dp)
    assertThat(renderer.alpha).isEqualTo(0.7f)
    assertThat(renderer.layerBoundsInvalidations).isEqualTo(boundsBefore + 1)
    assertThat(renderer.drawCount).isGreaterThan(drawsBefore)
    assertThat(renderer.drawnBlurRadius).isEqualTo(24.dp)
    assertThat(renderer.drawnAlpha).isEqualTo(0.7f)

    radius.value = (-4).dp
    mainClock.advanceTimeByFrame()

    assertThat(renderer.blurRadius).isEqualTo(0.dp)
    assertThat(renderer.drawnBlurRadius).isEqualTo(0.dp)
  }

  @Test
  fun rememberedStyleRadiusChange_expandsAndCollapsesCaptureLayer() = runComposeUiTest {
    val radius = mutableStateOf(8.dp)
    val renderer = RecordingBlurRenderer()

    setContent {
      RecordingBlurBox(renderer, remember { stateStyle(radius, mutableStateOf(1f)) })
    }
    waitForIdle()

    fun assertGeometry(expectedRadiusPx: Float) {
      val modifier = renderer.lastModifierBounds
      assertThat(renderer.lastRadiusPx).isEqualTo(expectedRadiusPx)
      val expected = if (expectedRadiusPx >= 1f) modifier.inflate(expectedRadiusPx) else modifier
      assertThat(renderer.lastLayerBounds).isEqualTo(expected)
      assertThat(renderer.drawnLayerSize).isEqualTo(expected.size)
      assertThat(renderer.drawnLayerOffset).isEqualTo(Offset.Zero - expected.topLeft)
    }

    val smallPx = renderer.lastRadiusPx
    assertThat(smallPx).isGreaterThan(0f)
    assertGeometry(smallPx)

    mainClock.autoAdvance = false
    radius.value = 24.dp
    repeat(3) { mainClock.advanceTimeByFrame() }
    val largePx = renderer.lastRadiusPx
    assertThat(largePx).isGreaterThan(smallPx)
    assertGeometry(largePx)

    radius.value = 0.dp
    repeat(3) { mainClock.advanceTimeByFrame() }
    assertGeometry(0f)
  }

  @Test
  fun rememberedStyleAlphaFromZero_drawsOnNextFrame() = runComposeUiTest {
    val alpha = mutableStateOf(0f)
    val renderer = RecordingBlurRenderer()

    setContent { RecordingBlurBox(renderer, remember { stateStyle(mutableStateOf(8.dp), alpha) }) }
    waitForIdle()
    assertThat(renderer.alpha).isEqualTo(0f)
    assertThat(renderer.drawCount).isEqualTo(0)

    mainClock.autoAdvance = false
    alpha.value = 0.5f
    mainClock.advanceTimeByFrame()

    assertThat(renderer.alpha).isEqualTo(0.5f)
    assertThat(renderer.drawCount).isGreaterThan(0)
    assertThat(renderer.drawnAlpha).isEqualTo(0.5f)
  }

  @Test
  fun rememberedStyleSharedAcrossNodes_updatesEveryNode() = runComposeUiTest {
    val radius = mutableStateOf(8.dp)
    val first = RecordingBlurRenderer()
    val second = RecordingBlurRenderer()

    setContent {
      val style = remember { stateStyle(radius, mutableStateOf(1f)) }
      RecordingBlurBox(first, style)
      RecordingBlurBox(second, style)
    }
    waitForIdle()
    val firstBounds = first.layerBoundsInvalidations
    val secondBounds = second.layerBoundsInvalidations

    mainClock.autoAdvance = false
    radius.value = 20.dp
    mainClock.advanceTimeByFrame()

    assertThat(first.blurRadius).isEqualTo(20.dp)
    assertThat(second.blurRadius).isEqualTo(20.dp)
    assertThat(first.layerBoundsInvalidations).isEqualTo(firstBounds + 1)
    assertThat(second.layerBoundsInvalidations).isEqualTo(secondBounds + 1)
  }

  @Test
  fun rememberedStyleConditionalDependency_tracksTheBranchLastRead() = runComposeUiTest {
    val useFirst = mutableStateOf(true)
    val first = mutableStateOf(8.dp)
    val second = mutableStateOf(16.dp)
    val renderer = RecordingBlurRenderer()

    setContent {
      val style = remember {
        HazeBlurStyle { blurRadius(if (useFirst.value) first.value else second.value) }
      }
      RecordingBlurBox(renderer, style)
    }
    waitForIdle()
    mainClock.autoAdvance = false

    val boundsBefore = renderer.layerBoundsInvalidations
    second.value = 18.dp
    mainClock.advanceTimeByFrame()
    assertThat(renderer.blurRadius).isEqualTo(8.dp)
    assertThat(renderer.layerBoundsInvalidations).isEqualTo(boundsBefore)

    useFirst.value = false
    mainClock.advanceTimeByFrame()
    assertThat(renderer.blurRadius).isEqualTo(18.dp)

    first.value = 30.dp
    mainClock.advanceTimeByFrame()
    assertThat(renderer.blurRadius).isEqualTo(18.dp)

    second.value = 22.dp
    mainClock.advanceTimeByFrame()
    assertThat(renderer.blurRadius).isEqualTo(22.dp)
  }

  @Test
  fun unchangedStyleInstance_doesNotInvalidateBoundsOnUnrelatedRecomposition() = runComposeUiTest {
    val tick = mutableStateOf(0)
    val renderer = RecordingBlurRenderer()
    val style = HazeBlurStyle { blurRadius(12.dp) }

    setContent {
      tick.value
      RecordingBlurBox(renderer, style)
    }
    waitForIdle()
    val boundsBefore = renderer.layerBoundsInvalidations

    tick.value++
    waitForIdle()

    assertThat(renderer.layerBoundsInvalidations).isEqualTo(boundsBefore)
  }

  @Test
  fun hazeBlur_rememberedStyleStateChangeRedrawsWithoutRecomposition() = runComposeUiTest {
    val hazeState = HazeState()
    val tint = mutableStateOf(Color.Blue)
    var compositions = 0

    setContent {
      Box(Modifier.size(100.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(Color.Red),
        )
        compositions++
        Box(
          Modifier
            .fillMaxSize()
            .testTag("effect")
            .hazeBlur(
              input = HazeInput.Sources(hazeState),
              style = remember {
                HazeBlurStyle {
                  blurEnabled(false)
                  fallbackColorEffect(HazeColorEffect.tint(tint.value))
                }
              },
              performanceMode = HazePerformanceMode.Quality,
              expandLayerBounds = false,
            ),
        )
      }
    }
    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
    val compositionsBefore = compositions

    tint.value = Color.Green
    waitForIdle()

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Green)
    assertThat(compositions).isEqualTo(compositionsBefore)
  }

  @Test
  fun localStyleStateReads_updateWithoutRecomposition() = runComposeUiTest {
    val hazeState = HazeState()
    val tint = mutableStateOf(Color.Blue)
    var compositions = 0

    setContent {
      // The local style is remembered, so only its observed state read can change the output.
      val localStyle = remember {
        HazeBlurStyle {
          blurEnabled(false)
          fallbackColorEffect(HazeColorEffect.tint(tint.value))
        }
      }
      CompositionLocalProvider(LocalHazeBlurStyle provides localStyle) {
        Box(Modifier.size(100.dp)) {
          Box(
            Modifier
              .fillMaxSize()
              .hazeSource(hazeState)
              .background(Color.Red),
          )
          compositions++
          Box(
            Modifier
              .fillMaxSize()
              .testTag("effect")
              .hazeBlur(
                input = HazeInput.Sources(hazeState),
                performanceMode = HazePerformanceMode.Quality,
                expandLayerBounds = false,
              ),
          )
        }
      }
    }
    waitForIdle()
    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Blue)
    val compositionsBefore = compositions

    mainClock.autoAdvance = false
    tint.value = Color.Green
    mainClock.advanceTimeByFrame()

    assertThat(onNodeWithTag("effect").captureToImage().toPixelMap()[50, 50])
      .isEqualTo(Color.Green)
    assertThat(compositions).isEqualTo(compositionsBefore)
  }

  @Test
  fun sharedFactory_createsIsolatedRuntimesAndStyleReplacementReusesRuntime() {
    val sharedStyle = HazeBlurStyle {
      blurRadius(12.dp)
    }
    val first = HazeBlurFactory.createRenderer() as BlurVisualEffect
    val second = HazeBlurFactory.createRenderer() as BlurVisualEffect
    first.update(TestLifecycleScope, BlurConfiguration(sharedStyle, HazePerformanceMode.Default), HazeSampling.Default)
    second.update(TestLifecycleScope, BlurConfiguration(sharedStyle, HazePerformanceMode.Default), HazeSampling.Default)

    assertThat(first).isNotSameInstanceAs(second)

    val firstRuntime = first
    first.update(
      scope = TestLifecycleScope,
      style = BlurConfiguration(
        HazeBlurStyle {
          blurRadius(24.dp)
        },
        HazePerformanceMode.Quality,
      ),
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
    first.update(TestLifecycleScope, BlurConfiguration(style, HazePerformanceMode.Default), HazeSampling.Default)
    second.update(TestLifecycleScope, BlurConfiguration(style, HazePerformanceMode.Default), HazeSampling.Default)
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

private fun stateStyle(
  radius: State<Dp>,
  alpha: State<Float>,
): HazeBlurStyle = HazeBlurStyle {
  blurRadius(radius.value)
  alpha(alpha.value)
}

@Composable
private fun RecordingBlurBox(renderer: RecordingBlurRenderer, style: HazeBlurStyle) {
  Box(
    Modifier
      .size(100.dp)
      .hazeEffect(
        factory = remember(renderer) { HazeEffectFactory<HazeBlurStyle> { renderer } },
        input = HazeInput.Content,
        style = style,
        sampling = HazeSampling.FullResolution,
        expandLayerBounds = true,
      ),
  )
}

private class RecordingBlurRenderer :
  HazeEffectRenderer<HazeBlurStyle>,
  HazeEffectRendererLifecycle<HazeBlurStyle>,
  HazeEffectRendererDrawHooks<HazeBlurStyle>,
  HazeEffectRendererRetainedOutput {
  private val effect = BlurVisualEffect()
  private var retainedOutputAvailable = false
  val blurRadius get() = effect.blurRadius
  val alpha get() = effect.alpha
  var drawnBlurRadius: Dp? = null
    private set
  var drawnAlpha: Float? = null
    private set
  var drawCount = 0
    private set
  var layerBoundsInvalidations = 0
    private set
  var clearCount = 0
    private set
  var lastModifierBounds: Rect = Rect.Zero
    private set
  var lastLayerBounds: Rect = Rect.Zero
    private set
  var lastRadiusPx = 0f
    private set
  var drawnLayerSize: Size = Size.Zero
    private set
  var drawnLayerOffset: Offset = Offset.Zero
    private set

  override fun attach(scope: HazeEffectLifecycleScope) = effect.attach(scope)

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: HazeBlurStyle,
    sampling: HazeSampling,
  ) {
    val counting = object : HazeEffectLifecycleScope by scope {
      override fun invalidateLayerBounds() {
        layerBoundsInvalidations++
        scope.invalidateLayerBounds()
      }
    }
    effect.update(counting, BlurConfiguration(style, HazePerformanceMode.Default), sampling)
  }

  override fun shouldPrepareDraw(style: HazeBlurStyle): Boolean =
    effect.shouldPrepareDraw(BlurConfiguration(style, HazePerformanceMode.Default))

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: HazeBlurStyle): Rect {
    val bounds = with(effect) {
      calculateLayerBounds(BlurConfiguration(style, HazePerformanceMode.Default))
    }
    lastModifierBounds = modifierBounds
    lastLayerBounds = bounds
    lastRadiusPx = effect.blurRadius.toPx()
    return bounds
  }

  override fun HazeEffectDrawScope.draw(style: HazeBlurStyle) {
    drawCount++
    (this as? HazeEffectRuntimeDrawScope)?.let {
      drawnLayerSize = it.layerSize
      drawnLayerOffset = it.layerOffset
    }
    drawnBlurRadius = effect.blurRadius
    drawnAlpha = effect.alpha
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
) : BlurVisualEffect.Delegate {
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
    performanceMode = HazePerformanceMode.Default,
    expandLayerBounds = true,
  )
  .hazeBlur(
    input = HazeInput.Sources(
      state = state,
      retention = HazeSourceRetention.ClearWhenUnavailable,
    ),
    performanceMode = HazePerformanceMode.Default,
  )
  .hazeBlur(
    input = HazeInput.Content,
    performanceMode = HazePerformanceMode.Fixed(0.6f),
  )
