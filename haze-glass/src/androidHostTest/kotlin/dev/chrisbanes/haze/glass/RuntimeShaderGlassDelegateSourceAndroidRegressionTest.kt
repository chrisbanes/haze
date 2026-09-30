// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
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
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)
@Config(sdk = [37])
class RuntimeShaderGlassDelegateSourceAndroidRegressionTest : ScreenshotTest() {
  @Test
  fun sourceBackedHardwareCapture_producesFusedMaterial() = assertHardwareSource(reuse = false)

  @Test
  fun stableSourceCallback_producesFusedMaterialAndReusesRecordings() = assertHardwareSource(reuse = true)

  @Test
  fun unchangedSourceCallback_reusesRecordedStages() = assertHardwareSource(reuse = true)

  private fun assertHardwareSource(reuse: Boolean) = runScreenshotTest(size = Size(240f, 120f)) {
    val effect = GlassRuntimeEffect()
    val factory = StableSourceGlassRuntimeFactory(effect)
    setContent { SourceGlassContent(factory) }
    composeTestRule.runOnIdle {
      factory.renderer.measureReuse = reuse
      factory.invalidateDraw()
      composeTestRule.activity.window.decorView.invalidate()
    }
    waitForIdle()
    val pixels = captureRootPixels()
    assertThat(factory.renderer.completedBatches).isGreaterThan(0)
    if (reuse) assertThat(factory.renderer.measuredBatches).isGreaterThan(0)
    val control = pixels[pixels.width / 4, pixels.height / 2]
    val bounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
    val material = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
    assertThat(control.alpha).isGreaterThan(0.9f)
    assertThat(control.red - control.blue).isGreaterThan(0.5f)
    assertThat(material.alpha).isGreaterThan(0.9f)
    assertThat(material.blue - material.red).isGreaterThan(0.2f)
  }

  @Test
  fun sourceGeometryAndStyleChanges_refreshRequiredStagesThenReuse() = runScreenshotTest {
    val effect = GlassRuntimeEffect()
    val factory = StableSourceGlassRuntimeFactory(effect)
    val color = mutableStateOf(Color.Red)
    val size = mutableStateOf(120.dp)
    val style = hardwareStyle().then { ambientResponse(0.5f) }
    setContent { SourceGlassContent(factory, color.value, size.value, style) }
    val initialPixels = captureHardware(factory)
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    var before = delegate.stageRecordCounts
    composeTestRule.runOnIdle { color.value = Color.Green }
    waitForIdle()
    val greenPixels = captureHardware(factory)
    assertThat(greenPixels.green - initialPixels.green).isGreaterThan(0.05f)
    assertThat(delegate.stageRecordCounts.source).isGreaterThan(before.source)
    assertThat(delegate.stageRecordCounts.optical).isGreaterThan(before.optical)
    before = delegate.stageRecordCounts
    composeTestRule.runOnIdle { size.value = 140.dp }
    waitForIdle()
    captureHardware(factory)
    assertThat(delegate.stageRecordCounts.source).isGreaterThan(before.source)
    assertThat(delegate.stageRecordCounts.optical).isGreaterThan(before.optical)
    composeTestRule.runOnIdle {
      factory.renderer.pendingAction = { context, runtime, current ->
        val input = checkNotNull(context.inputSnapshot)
        val counts = current.stageRecordCounts
        runtime.style = runtime.style.then { ambientResponse(0.6f) }
        prepareAndDraw(context, runtime, current)
        assertThat(context.inputSnapshot).isSameInstanceAs(input)
        assertThat(current.stageRecordCounts).isEqualTo(counts.copy(optical = counts.optical + 1))
      }
    }
    captureHardware(factory)
    assertThat(factory.renderer.completedActions).isEqualTo(1)
  }

  @Test
  fun refractionTopologyChanges_refreshRequiredStagesThenReuse() = runScreenshotTest {
    val effect = GlassRuntimeEffect()
    val factory = StableSourceGlassRuntimeFactory(effect)
    val optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      refractionProfile = RefractionProfile.Edge(14.dp),
      depth = OpticalSizeValue.Fixed(0.5f),
      blurRadius = OpticalSizeValue.Fixed(14.dp),
    )
    val initialStyle = hardwareStyle().then { optics(optics) }
    val authoredStyle = mutableStateOf(initialStyle)
    setContent { SourceGlassContent(factory, style = authoredStyle.value) }
    captureHardware(factory)
    for ((index, width) in listOf(0.dp, 14.dp).withIndex()) {
      composeTestRule.runOnIdle {
        factory.renderer.pendingAction = { context, runtime, delegate ->
          val previous = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
          val counts = delegate.stageRecordCounts
          val key = runtime.preparedRender?.opticalKey
          val renderEffect = delegate.layers.optical?.renderEffect
          runtime.style = runtime.style.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(width))) }
          prepareAndDraw(context, runtime, delegate)
          assertThat(key?.edgeRefractionWidthPx, "profile before phase $index").isEqualTo(with(context.requireDensity()) { (if (index == 0) 14.dp else 0.dp).toPx() })
          assertThat(runtime.preparedRender?.opticalKey?.edgeRefractionWidthPx).isEqualTo(with(context.requireDensity()) { width.toPx() })
          assertThat(runtime.preparedRender?.opticalKey).isNotEqualTo(key)
          assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(renderEffect)
          assertThat(delegate.stageRecordCounts.optical).isGreaterThan(counts.optical)
          val current = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
          assertThat(current.inputSnapshot).isSameInstanceAs(previous.inputSnapshot)
          if (current.layerSize == previous.layerSize && current.layerOffset == previous.layerOffset) {
            assertThat(delegate.stageRecordCounts.source).isEqualTo(counts.source)
          } else {
            assertThat(delegate.stageRecordCounts.source).isGreaterThan(counts.source)
          }
        }
      }
      captureHardware(factory)
      assertThat(factory.renderer.completedActions).isEqualTo(index + 1)
      composeTestRule.runOnIdle { authoredStyle.value = initialStyle.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(width))) } }
      waitForIdle()
    }
  }

  @Test
  fun releasedResources_uiHidden_rebuildOnceThenReuse() = assertReleasedResources(TrimMemoryLevel.UI_HIDDEN)

  @Test
  fun releasedResources_moderate_rebuildOnceThenReuse() = assertReleasedResources(TrimMemoryLevel.MODERATE)

  @Test
  fun releasedResources_complete_rebuildOnceThenReuse() = assertReleasedResources(TrimMemoryLevel.COMPLETE)

  @Test
  fun releasedResources_clear_rebuildOnceThenReuse() = assertReleasedResources(null)

  private fun assertReleasedResources(level: TrimMemoryLevel?) = runScreenshotTest {
    val effect = GlassRuntimeEffect()
    val factory = StableSourceGlassRuntimeFactory(effect)
    setContent { SourceGlassContent(factory) }
    composeTestRule.runOnIdle {
      factory.renderer.pendingAction = { context, runtime, delegate ->
        val source = checkNotNull(delegate.layers.source)
        val optical = checkNotNull(delegate.layers.optical)
        val counts = delegate.stageRecordCounts
        val input = checkNotNull(context.inputSnapshot)
        if (level == null) runtime.clearRetainedOutput() else runtime.onTrimMemory(level)
        assertThat(source.isReleased).isTrue()
        assertThat(optical.isReleased).isTrue()
        assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
        assertThat(delegate.lastSuccessfulStageInputs).isNull()
        prepareAndDraw(context, runtime, delegate)
        assertThat(delegate.layers.source).isNotSameInstanceAs(source)
        assertThat(delegate.layers.optical).isNotSameInstanceAs(optical)
        assertThat(delegate.stageRecordCounts.source).isGreaterThan(counts.source)
        assertThat(delegate.stageRecordCounts.optical).isGreaterThan(counts.optical)
        assertThat(context.inputSnapshot).isSameInstanceAs(input)
        assertThat(runtime.canDrawRetainedOutput()).isTrue()
      }
    }
    captureHardware(factory)
    assertThat(factory.renderer.completedActions).isEqualTo(1)
  }

  private fun ScreenshotUiTest.captureHardware(factory: StableSourceGlassRuntimeFactory): Color {
    val before = factory.renderer.measuredBatches
    composeTestRule.runOnIdle {
      factory.renderer.measureReuse = true
      factory.invalidateDraw()
      composeTestRule.activity.window.decorView.invalidate()
    }
    waitForIdle()
    val pixels = captureRootPixels()
    assertThat(factory.renderer.measuredBatches).isGreaterThan(before)
    val control = pixels[pixels.width / 4, pixels.height / 2]
    assertThat(control.alpha).isGreaterThan(0.9f)
    assertThat(maxOf(control.red, control.green) - control.blue).isGreaterThan(0.5f)
    val bounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
    val material = pixels[bounds.center.x.roundToInt(), bounds.center.y.roundToInt()]
    assertThat(material.alpha).isGreaterThan(0.9f)
    assertThat(material.blue - material.red).isGreaterThan(0.2f)
    return material
  }

  private fun prepareAndDraw(
    context: HazeEffectRuntimeDrawScope,
    effect: GlassRuntimeEffect,
    delegate: RuntimeShaderGlassDelegate,
  ) {
    effect.prepareRenderBudget(context, runtimeShaderSupported = true)
    with(context) {
      with(delegate) {
        prepareDraw(context)
        draw(context)
      }
    }
  }

  private fun hardwareStyle() = GlassStyle.regular.then {
    tint(Color.Blue.copy(alpha = 0.75f))
    specularIntensity(0f)
    edgeShadow(Color.Transparent)
  }

  @Composable
  private fun SourceGlassContent(
    factory: StableSourceGlassRuntimeFactory,
    color: Color = Color.Red,
    size: Dp = 120.dp,
    style: GlassStyle = hardwareStyle(),
  ) {
    val state = rememberHazeState()
    Box(Modifier.size(240.dp, 120.dp)) {
      Box(Modifier.fillMaxSize().hazeSource(state).background(color))
      Box(
        Modifier.align(Alignment.CenterEnd).size(size).testTag("material").hazeEffect(
          factory = factory,
          input = HazeInput.Sources(state),
          style = GlassNodeConfiguration(
            style = style,
            performanceMode = HazePerformanceMode.Quality,
            interactionSource = null,
          ),
          expandLayerBounds = true,
        ),
      )
    }
  }
}

@OptIn(InternalHazeApi::class)
private class StableSourceGlassRuntimeFactory(
  private val effect: GlassRuntimeEffect,
) : HazeEffectFactory<GlassNodeConfiguration> {
  lateinit var renderer: StableSourceGlassRuntimeRenderer

  fun invalidateDraw() = checkNotNull(effect.attachedContextForTest).invalidateDraw()

  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> =
    StableSourceGlassRuntimeRenderer(effect).also { renderer = it }
}

@OptIn(InternalHazeApi::class)
private class StableSourceGlassRuntimeRenderer(
  private val effect: GlassRuntimeEffect,
) : HazeEffectRenderer<GlassNodeConfiguration>,
  HazeEffectRendererLifecycle<GlassNodeConfiguration>,
  HazeEffectRendererDrawHooks<GlassNodeConfiguration>,
  HazeEffectRendererRetainedOutput {
  var measureReuse = false
  var completedBatches = 0
  var measuredBatches = 0
  var pendingAction: ((HazeEffectRuntimeDrawScope, GlassRuntimeEffect, RuntimeShaderGlassDelegate) -> Unit)? = null
  var completedActions = 0

  override fun attach(scope: HazeEffectLifecycleScope) = effect.attach(scope)

  override fun update(scope: HazeEffectLifecycleScope, style: GlassNodeConfiguration, sampling: HazeSampling) =
    effect.update(scope, style, sampling)

  override fun detach() {
    measureReuse = false
    pendingAction = null
    effect.detach()
  }

  override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
    val context = this as HazeEffectRuntimeDrawScope
    assertThat(drawContext.canvas.nativeCanvas.isHardwareAccelerated).isTrue()
    with(effect) { draw(style) }
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    pendingAction?.also { action ->
      pendingAction = null
      action(context, effect, delegate)
      completedActions++
    }
    val input = checkNotNull(context.inputSnapshot)
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot).inputSnapshot).isSameInstanceAs(input)
    assertThat(delegate.fusedShader).isNotNull()
    assertThat(delegate.layers.source).isNotNull()
    assertThat(delegate.layers.optical?.renderEffect).isNotNull()
    assertThat(delegate.layers.source?.renderEffect).isNull()
    assertThat(delegate.layers.blurHorizontal).isNull()
    assertThat(delegate.layers.blurred).isNull()
    assertThat(delegate.layers.depthMixed).isNull()
    assertThat(delegate.layers.refractionDetail).isNull()
    assertThat(delegate.layers.rim).isNull()
    assertThat(effect.preparedRender?.rimKey).isNull()
    val before = delegate.stageRecordCounts
    assertThat(before.source).isGreaterThan(0)
    assertThat(before.optical).isGreaterThan(0)
    assertThat(before.blur).isEqualTo(0)
    assertThat(before.depth).isEqualTo(0)
    assertThat(before.detail).isEqualTo(0)
    assertThat(before.rim).isEqualTo(0)
    if (measureReuse) {
      measuredBatches++
      repeat(3) {
        assertThat(context.inputSnapshot).isSameInstanceAs(input)
        with(delegate) { draw(context) }
        assertThat(context.inputSnapshot).isSameInstanceAs(input)
        assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot).inputSnapshot).isSameInstanceAs(input)
        assertThat(delegate.stageRecordCounts).isEqualTo(before)
      }
    }
    completedBatches++
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(style: GlassNodeConfiguration): Rect =
    with(effect) { calculateLayerBounds(style) }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) =
    with(effect) { prepareDraw(style) }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: GlassNodeConfiguration) =
    with(effect) { drawForeground(style) }

  override fun shouldDrawContentBehind(): Boolean = effect.shouldDrawContentBehind()
  override fun shouldClipToNodeBounds(): Boolean = effect.shouldClipToNodeBounds()
  override fun shouldPreferClipToInputBounds(): Boolean = effect.shouldPreferClipToInputBounds()
  override fun onTrimMemory(level: TrimMemoryLevel) = effect.onTrimMemory(level)
  override fun canDrawRetainedOutput(): Boolean = effect.canDrawRetainedOutput()
  override fun shouldDrawRetainedOutput(): Boolean = effect.shouldDrawRetainedOutput()
  override fun clearRetainedOutput() = effect.clearRetainedOutput()
  override fun invalidateRetainedOutput() = effect.invalidateRetainedOutput()
}
