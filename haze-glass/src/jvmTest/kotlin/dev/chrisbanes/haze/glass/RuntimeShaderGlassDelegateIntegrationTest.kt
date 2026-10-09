// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalHazeApi::class,
  InternalHazeApi::class,
)
class RuntimeShaderGlassDelegateIntegrationTest : ContextTest() {
  private val attachedRuntimes = mutableMapOf<GlassRuntimeEffect, GlassRuntimeEffect>()
  private val rendererFactories =
    mutableMapOf<GlassRuntimeEffect, HazeEffectFactory<GlassNodeConfiguration>>()

  @Test
  fun unchangedSourceRedraw_reusesRecordedStages() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    val style = GlassStyle.regular
    setContent { SourceBackedGlassTestContent(effect, style = style) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val before = delegate.stageRecordCounts
    assertThat(before.source).isGreaterThan(0)
    assertThat(before.optical).isGreaterThan(0)
    repeat(3) {
      runOnIdle { checkNotNull(effect.attachedContextForTest).invalidateDraw() }
      waitForIdle()

      assertThat(delegate.stageRecordCounts).isEqualTo(before)
    }
  }

  @Test
  fun growAndShrink_balancedRetainsLayersAndMatchesFreshPixels() = assertResizeMatchesFreshPixels(HazePerformanceMode.Balanced)

  @Test
  fun growAndShrink_qualityRetainsLayersAndMatchesFreshPixels() = assertResizeMatchesFreshPixels(HazePerformanceMode.Quality)

  private fun assertResizeMatchesFreshPixels(mode: HazePerformanceMode) = runSkikoComposeUiTest(
    size = Size(400f, 400f),
    density = Density(2.75f),
  ) {
    val reused = GlassRuntimeEffect()
    val displayedEffect = mutableStateOf(GlassRuntimeEffect())
    val physicalSize = mutableStateOf(203)
    val style = GlassStyle.regular.then {
      shape(RoundedCornerShape(0.dp))
      edgeSoftness(0.dp)
      specularIntensity(0f)
      alpha(0.5f)
      optics(
        GlassOptics(
          refractionStrength = 0f,
          refractionDisplacement = 0.dp,
          depth = OpticalSizeValue.Fixed(0f),
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
    }
    setContent {
      ResizeGlassTestContent(displayedEffect.value, physicalSize.value, mode, style, "glass")
    }
    waitForIdle()
    // References use the same screen origin: fractional input scaling can otherwise change
    // sampling phase between two side-by-side panels even without retained layers.
    val references = mutableMapOf<Int, PixelMap>()
    for (targetSize in listOf(208, 203)) {
      runOnIdle {
        physicalSize.value = targetSize
        displayedEffect.value = GlassRuntimeEffect()
      }
      waitForIdle()
      references[targetSize] = onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    runOnIdle { displayedEffect.value = reused }
    waitForIdle()
    val delegate = runtime(reused).delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val groupAlpha = checkNotNull(delegate.layers.groupAlpha.layer)
    // Known fixture dimensions: Balanced samples sqrt(0.625) of each axis. The capture
    // allocations round to 160/164px while the independent outlines cover 161/165px.
    val sourceAllocations = if (mode == HazePerformanceMode.Balanced) listOf(160, 164, 160, 164) else listOf(203, 208, 203, 208)
    val sourceOutlines = if (mode == HazePerformanceMode.Balanced) listOf(161f, 165f, 161f, 165f) else listOf(203f, 208f, 203f, 208f)
    assertThat(source.size).isEqualTo(IntSize(sourceAllocations[0], sourceAllocations[0]))
    val initialSourceOutline = source.outline.bounds
    val initialGroupOutline = groupAlpha.outline.bounds
    assertThat(initialGroupOutline).isEqualTo(Rect(0f, 0f, 203f, 203f))
    for ((index, targetSize) in listOf(208, 203, 208).withIndex()) {
      val before = delegate.stageRecordCounts
      runOnIdle {
        physicalSize.value = targetSize
      }
      waitForIdle()
      val actual = onNodeWithTag("glass").captureToImage().toPixelMap()
      val expected = checkNotNull(references[targetSize])
      assertThat(actual.width).isEqualTo(targetSize)
      assertThat(actual.height).isEqualTo(targetSize)
      assertThat(expected.width).isEqualTo(targetSize)
      assertThat(expected.height).isEqualTo(targetSize)
      // Compare every pixel, including the last row/column where stale outlines clip growth.
      var mismatches = 0
      for (y in 0 until targetSize) {
        for (x in 0 until targetSize) {
          if (actual[x, y] != expected[x, y]) mismatches++
        }
      }
      assertThat(
        mismatches,
        "$mode at $targetSize physical pixels: source ${source.size}, ${source.outline.bounds}; " +
          "optical ${optical.size}, ${optical.outline.bounds}; " +
          "group alpha ${groupAlpha.size}, ${groupAlpha.outline.bounds}",
      ).isEqualTo(0)
      val allocation = sourceAllocations[index + 1]
      val outline = sourceOutlines[index + 1]
      assertThat(source.size).isEqualTo(IntSize(allocation, allocation))
      assertThat(source.outline.bounds, "source outline after $initialSourceOutline").isEqualTo(Rect(0f, 0f, outline, outline))
      val outputExtent = targetSize.toFloat()
      assertThat(groupAlpha.size).isEqualTo(IntSize(targetSize, targetSize))
      assertThat(groupAlpha.outline.bounds, "output outline after $initialGroupOutline").isEqualTo(Rect(0f, 0f, outputExtent, outputExtent))
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.groupAlpha.layer).isSameInstanceAs(groupAlpha)
      assertThat(source.isReleased).isFalse()
      assertThat(optical.isReleased).isFalse()
      assertThat(groupAlpha.isReleased).isFalse()
      assertThat(delegate.stageRecordCounts.source).isGreaterThan(before.source)
      assertThat(delegate.stageRecordCounts.optical).isGreaterThan(before.optical)
      val recorded = delegate.stageRecordCounts
      runOnIdle { checkNotNull(reused.attachedContextForTest).invalidateDraw() }
      waitForIdle()
      assertThat(delegate.stageRecordCounts).isEqualTo(recorded)
      assertExactResizePixels(onNodeWithTag("glass").captureToImage().toPixelMap(), expected)
    }
  }

  @Test
  fun resize_paddedBalancedRetainsEveryStageAndMatchesFreshPixels() = assertPipelineResize()

  @Test
  fun resize_fractionalAlphaRetainsOutputLayerAndMatchesFreshPixels() = assertPipelineResize(effectAlpha = 0.5f)

  @Test
  fun resize_paddedQualityRetainsEveryStageAndMatchesFreshPixels() = assertPipelineResize(mode = HazePerformanceMode.Quality)

  @Test
  fun resize_activeInteractionRetainsLocalPatchesAndMatchesFreshPixels() = assertPipelineResize(interaction = true)

  private fun assertPipelineResize(
    mode: HazePerformanceMode = HazePerformanceMode.Balanced,
    effectAlpha: Float = 1f,
    interaction: Boolean = false,
  ) = runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
    fun newEffect() = animatedStageEffect().apply {
      if (interaction) interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    val physicalSize = mutableStateOf(140)
    val displayedEffect = mutableStateOf(newEffect())
    val style = animatedStageEffect().style.then {
      alpha(effectAlpha)
      if (interaction) {
        pressed {
          animate(toSpec = tween(1), fromSpec = tween(1)) {
            lightingIntensity(1f)
            refractionMultiplier(1.08f)
            whitePointDelta(0.04f)
          }
        }
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
    }
    fun settleInteraction() {
      if (interaction) {
        runOnIdle { runtime(displayedEffect.value).setPressedForTest(Offset(40f, 40f)) }
        mainClock.advanceTimeBy(64)
        waitForIdle()
      }
    }
    setContent {
      ResizeGlassTestContent(displayedEffect.value, physicalSize.value, mode, style, "glass")
    }
    waitForIdle()
    val references = mutableMapOf<Int, PixelMap>()
    for (target in listOf(210, 100)) {
      runOnIdle {
        physicalSize.value = target
        displayedEffect.value = newEffect()
      }
      waitForIdle()
      settleInteraction()
      references[target] = onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    val reused = newEffect()
    runOnIdle {
      physicalSize.value = 140
      displayedEffect.value = reused
    }
    waitForIdle()
    settleInteraction()
    val delegate = runtime(reused).delegate as RuntimeShaderGlassDelegate
    val activeLayers = delegate.layers.activeLayersForResize()
    assertThat(delegate.layers.blurred).isNotNull()
    assertThat(delegate.layers.depthMixed).isNotNull()
    assertThat(delegate.layers.refractionComposite).isNotNull()
    if (effectAlpha < 1f) assertThat(delegate.layers.groupAlpha.layer).isNotNull()
    if (interaction) {
      assertThat(delegate.layers.interactionOptical).isNotNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNotNull()
      assertThat(delegate.layers.interactionRefractionDetailCoverage).isNotNull()
      assertThat(delegate.layers.interactionRefractionComposite).isNotNull()
      assertThat(delegate.layers.interactionLighting).isNotNull()
    }
    // Observe every initial outline so cached geometry is exercised by the next recordings.
    activeLayers.forEach { it.outline.bounds }
    for (target in listOf(210, 100, 210)) {
      val before = delegate.stageRecordCounts
      val beforeInteraction = listOf(delegate.interactionOpticalRecordCount, delegate.interactionDetailRecordCount, delegate.interactionCompositeRecordCount, delegate.interactionLightingRecordCount)
      runOnIdle { physicalSize.value = target }
      waitForIdle()
      settleInteraction()
      val actual = onNodeWithTag("glass").captureToImage().toPixelMap()
      assertExactResizePixels(actual, checkNotNull(references[target]))
      val current = delegate.layers.activeLayersForResize()
      assertThat(current.size).isEqualTo(activeLayers.size)
      current.zip(activeLayers).forEach { (layer, original) ->
        assertThat(layer).isSameInstanceAs(original)
        assertThat(layer.isReleased).isFalse()
        if (layer !== delegate.layers.source) {
          assertThat(layer.outline.bounds.size).isEqualTo(layer.size.toSize())
        }
      }
      val after = delegate.stageRecordCounts
      assertThat(after.source).isGreaterThan(before.source)
      assertThat(after.blur).isGreaterThan(before.blur)
      assertThat(after.depth).isGreaterThan(before.depth)
      assertThat(after.optical).isGreaterThan(before.optical)
      assertThat(after.detail).isGreaterThan(before.detail)
      assertThat(after.rim).isGreaterThan(before.rim)
      val afterInteraction = listOf(delegate.interactionOpticalRecordCount, delegate.interactionDetailRecordCount, delegate.interactionCompositeRecordCount, delegate.interactionLightingRecordCount)
      if (interaction) {
        afterInteraction.zip(beforeInteraction).forEach { (recorded, previous) -> assertThat(recorded).isGreaterThan(previous) }
      }
      repeat(2) {
        runOnIdle { checkNotNull(reused.attachedContextForTest).invalidateDraw() }
        waitForIdle()
        assertThat(delegate.stageRecordCounts).isEqualTo(after)
        assertThat(listOf(delegate.interactionOpticalRecordCount, delegate.interactionDetailRecordCount, delegate.interactionCompositeRecordCount, delegate.interactionLightingRecordCount)).isEqualTo(afterInteraction)
        assertExactResizePixels(onNodeWithTag("glass").captureToImage().toPixelMap(), checkNotNull(references[target]))
      }
    }
  }

  @Test
  fun resize_unavailableSourceNeverReplaysStaleOutputAndRecovers() = runSkikoComposeUiTest(size = Size(400f, 400f), density = Density(1f)) {
    val displayedEffect = mutableStateOf(animatedStageEffect())
    val physicalSize = mutableStateOf(140)
    val showSource = mutableStateOf(false)
    val style = animatedStageEffect().style
    setContent {
      ResizeGlassTestContent(displayedEffect.value, physicalSize.value, HazePerformanceMode.Balanced, style, "glass", showSource.value)
    }
    waitForIdle()
    val freshWithoutSource = mutableMapOf<Int, PixelMap>()
    for (target in listOf(210, 100)) {
      runOnIdle {
        physicalSize.value = target
        displayedEffect.value = animatedStageEffect()
      }
      waitForIdle()
      freshWithoutSource[target] = onNodeWithTag("glass").captureToImage().toPixelMap()
    }
    val reused = animatedStageEffect()
    runOnIdle {
      physicalSize.value = 140
      displayedEffect.value = reused
      showSource.value = true
    }
    waitForIdle()
    val delegate = runtime(reused).delegate as RuntimeShaderGlassDelegate
    val recorded = delegate.stageRecordCounts
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    runOnIdle { showSource.value = false }
    waitForIdle()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    for (target in listOf(210, 100, 210)) {
      runOnIdle { physicalSize.value = target }
      waitForIdle()
      assertThat(reused.canDrawRetainedOutput()).isFalse()
      assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
      assertThat(delegate.lastSuccessfulStageInputs).isNull()
      assertExactResizePixels(onNodeWithTag("glass").captureToImage().toPixelMap(), checkNotNull(freshWithoutSource[target]))
      runOnIdle { checkNotNull(reused.attachedContextForTest).invalidateDraw() }
      waitForIdle()
      assertThat(reused.canDrawRetainedOutput()).isFalse()
    }
    runOnIdle { showSource.value = true }
    waitForIdle()
    assertThat(reused.canDrawRetainedOutput()).isTrue()
    assertThat((runtime(reused).delegate as RuntimeShaderGlassDelegate).stageRecordCounts.source).isGreaterThan(recorded.source)
  }

  private fun assertExactResizePixels(actual: PixelMap, expected: PixelMap) {
    assertThat(actual.width).isEqualTo(expected.width)
    assertThat(actual.height).isEqualTo(expected.height)
    var mismatches = 0
    for (y in 0 until actual.height) {
      for (x in 0 until actual.width) {
        if (actual[x, y] != expected[x, y]) mismatches++
      }
    }
    assertThat(mismatches).isEqualTo(0)
  }

  private fun GlassLayers.activeLayersForResize(): List<GraphicsLayer> = listOfNotNull(
    groupAlpha.layer,
    source,
    blurHorizontal,
    blurred,
    depthMixed,
    optical,
    refractionDetail,
    refractionDetailCoverage,
    refractionComposite,
    interactionOptical,
    interactionRefractionDetail,
    interactionRefractionDetailCoverage,
    interactionRefractionComposite,
    interactionLighting,
    rim,
  )

  @Composable
  private fun ResizeGlassTestContent(
    effect: GlassRuntimeEffect,
    physicalSize: Int,
    mode: HazePerformanceMode,
    style: GlassStyle,
    tag: String,
    showSource: Boolean = true,
  ) {
    val state = remember { HazeState() }
    val density = LocalDensity.current
    Box(Modifier.size(with(density) { 240.toDp() })) {
      // The source stays stationary and larger than either effect size. The opaque cover
      // prevents a missing effect pixel from accidentally matching the source below it.
      if (showSource) {
        Canvas(Modifier.fillMaxSize().hazeSource(state)) {
          drawRect(Color.Blue)
          for (x in 0 until 240 step 8) {
            drawRect(Color.Red, Offset(x.toFloat(), 0f), Size(4f, 240f))
          }
        }
      }
      Box(Modifier.fillMaxSize().background(Color.Black))
      Box(
        Modifier
          .size(with(density) { physicalSize.toDp() })
          .testTag(tag)
          .testGlass(effect, HazeInput.Sources(state), mode, style),
      )
    }
  }

  @Test
  fun sourceGeometryAndStyleChanges_refreshRequiredStagesThenReuse() = runComposeUiTest {
    val effect = animatedStageEffect()
    val color = mutableStateOf(Color.Red)
    val size = mutableStateOf(120.dp)
    // Detail composites have their own recording counter; isolate optical-only invalidation here.
    val style = mutableStateOf(effect.style.then { optics(effect.optics.copy(refractionDetailIntensity = 0f)) })
    setContent { SourceBackedGlassTestContent(effect, color.value, size.value, style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(effect.preparedRender?.refractionDetailKey).isNull()
    assertThat(delegate.layers.refractionDetail).isNull()
    assertThat(delegate.stageRecordCounts.detail).isEqualTo(0)
    fun assertReuse() {
      val counts = delegate.stageRecordCounts
      repeat(2) {
        runOnIdle { checkNotNull(effect.attachedContextForTest).invalidateDraw() }
        waitForIdle()
        assertThat(delegate.stageRecordCounts).isEqualTo(counts)
      }
    }
    var before = delegate.stageRecordCounts
    runOnIdle { color.value = Color.Blue }
    waitForIdle()
    runOnIdle { checkNotNull(effect.attachedContextForTest).invalidateDraw() }
    waitForIdle()
    assertThat(delegate.stageRecordCounts.source).isGreaterThan(before.source)
    assertThat(delegate.stageRecordCounts.blur).isGreaterThan(before.blur)
    assertThat(delegate.stageRecordCounts.depth).isGreaterThan(before.depth)
    assertThat(delegate.stageRecordCounts.optical).isGreaterThan(before.optical)
    assertThat(delegate.stageRecordCounts.rim).isEqualTo(before.rim)
    assertReuse()
    before = delegate.stageRecordCounts
    runOnIdle { size.value = 140.dp }
    waitForIdle()
    assertThat(delegate.stageRecordCounts.source).isGreaterThan(before.source)
    assertThat(delegate.stageRecordCounts.optical).isGreaterThan(before.optical)
    assertReuse()
    before = delegate.stageRecordCounts
    runOnIdle { style.value = style.value.then { ambientResponse(0.6f) } }
    waitForIdle()
    assertThat(delegate.stageRecordCounts).isEqualTo(before.copy(optical = before.optical + 1))
    assertReuse()
    before = delegate.stageRecordCounts
    runOnIdle { style.value = style.value.then { lightPosition(exactLightAlignment(Offset(10f, 20f))) } }
    waitForIdle()
    assertThat(delegate.stageRecordCounts).isEqualTo(before.copy(rim = before.rim + 1))
    assertReuse()
  }

  @Test
  fun refractionTopologyChanges_refreshRequiredStagesThenReuse() = runComposeUiTest {
    val effect = animatedStageEffect()
    val optics = effect.optics.copy(refractionProfile = RefractionProfile.Edge(14.dp))
    val style = mutableStateOf(effect.style.then { optics(optics) })
    setContent { SourceBackedGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val initialDetail = checkNotNull(delegate.layers.refractionDetail)
    val initialCount = delegate.stageRecordCounts.detail
    for (width in listOf(0.dp, 14.dp)) {
      runOnIdle { style.value = effect.style.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(width))) } }
      waitForIdle()
      if (width == 0.dp) {
        assertThat(initialDetail.isReleased).isTrue()
        assertThat(delegate.layers.refractionDetail).isNull()
      } else {
        assertThat(delegate.layers.refractionDetail).isNotNull()
        assertThat(delegate.layers.refractionDetail).isNotSameInstanceAs(initialDetail)
        assertThat(delegate.stageRecordCounts.detail).isGreaterThan(initialCount)
      }
      val counts = delegate.stageRecordCounts
      repeat(2) {
        runOnIdle { checkNotNull(effect.attachedContextForTest).invalidateDraw() }
        waitForIdle()
        assertThat(delegate.stageRecordCounts).isEqualTo(counts)
      }
    }
  }

  @Test
  fun releasedResources_rebuildOnceThenReuse() {
    for (level in listOf(TrimMemoryLevel.UI_HIDDEN, TrimMemoryLevel.MODERATE, TrimMemoryLevel.COMPLETE, null)) {
      runComposeUiTest {
        val effect = GlassRuntimeEffect()
        setContent { SourceBackedGlassTestContent(effect, style = GlassStyle.regular) }
        waitForIdle()
        val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
        val source = checkNotNull(delegate.layers.source)
        val optical = checkNotNull(delegate.layers.optical)
        val before = delegate.stageRecordCounts
        runOnIdle {
          if (level == null) effect.clearRetainedOutput() else effect.onTrimMemory(level)
          assertThat(source.isReleased).isTrue()
          assertThat(optical.isReleased).isTrue()
          assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()
          assertThat(delegate.lastSuccessfulStageInputs).isNull()
          checkNotNull(effect.attachedContextForTest).invalidateDraw()
        }
        waitForIdle()
        assertThat(delegate.layers.source).isNotSameInstanceAs(source)
        assertThat(delegate.layers.optical).isNotSameInstanceAs(optical)
        assertThat(delegate.stageRecordCounts.source).isGreaterThan(before.source)
        assertThat(delegate.stageRecordCounts.optical).isGreaterThan(before.optical)
        assertThat(effect.canDrawRetainedOutput()).isTrue()
        val recovered = delegate.stageRecordCounts
        repeat(2) {
          runOnIdle { checkNotNull(effect.attachedContextForTest).invalidateDraw() }
          waitForIdle()
          assertThat(delegate.stageRecordCounts).isEqualTo(recovered)
        }
      }
    }
  }

  @Test
  fun backgroundOpacityChange_updatesRetainedBlurNormalization() = runComposeUiTest {
    val base = GlassStyle.regular.then {
      optics(GlassDefaults.optics.copy(progressive = HazeProgressive.verticalGradient()))
    }
    val effect = GlassRuntimeEffect()
    val style = mutableStateOf(base.then { backgroundColor(Color.White) })
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()
    val before = checkNotNull(runtime(effect).preparedRender?.blurKey)
    assertThat(before.sourceIsOpaque).isTrue()

    style.value = base.then { backgroundColor(Color.White.copy(alpha = 0.5f)) }
    waitForIdle()
    val translucent = checkNotNull(runtime(effect).preparedRender?.blurKey)
    assertThat(translucent.sourceIsOpaque).isFalse()
    assertThat(translucent).isNotEqualTo(before)

    style.value = base.then { backgroundColor(Color.Black) }
    waitForIdle()
    assertThat(checkNotNull(runtime(effect).preparedRender?.blurKey).sourceIsOpaque).isTrue()
  }

  @Test
  fun edgeWidthChange_refreshesBothSamplingPassesAndZeroReleasesDetail() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    val optics = GlassStyle.clearOptics
    val style = mutableStateOf(effect.style.then { optics(optics) })
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()
    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val before = checkNotNull(runtime(effect).preparedRender)
    val interactionRecords = delegate.interactionDetailRecordCount
    style.value = effect.style.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(14.dp))) }
    waitForIdle()
    val after = checkNotNull(runtime(effect).preparedRender)
    assertThat(after.opticalKey).isNotEqualTo(before.opticalKey)
    assertThat(after.refractionDetailKey).isNotEqualTo(before.refractionDetailKey)
    assertThat(after.blurKey).isEqualTo(before.blurKey)
    assertThat(after.rimKey).isEqualTo(before.rimKey)
    assertThat(delegate.interactionDetailRecordCount).isGreaterThan(interactionRecords)

    style.value = effect.style.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(0.dp))) }
    waitForIdle()
    val disabled = checkNotNull(runtime(effect).preparedRender)
    assertThat(disabled.refractionDetailKey).isNull()
    assertThat(disabled.plan.layers.any { it.kind == GlassRetainedLayerKind.RefractionDetail }).isFalse()
    assertThat(delegate.layers.refractionDetail).isNull()
  }

  @Test
  fun firstAlphaZeroFrame_skipsRuntimeShaderAndLayerGraphPreparation() = runComposeUiTest {
    var creationAttempts = 0
    val effect = activeDetailEffect().apply {
      style = style.then { alpha(0f) }
      runtimeEffectFactory = GlassRuntimeEffectFactory { create ->
        creationAttempts++
        create()
      }
    }

    val style = mutableStateOf(effect.style)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    assertThat(runtime(effect).preparedRender).isNull()
    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(runtime(effect).dirtyTracker).isEqualTo(Bitmask())
    assertThat(creationAttempts).isEqualTo(0)

    style.value = style.value.then { alpha(0.5f) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(creationAttempts).isGreaterThan(0)
    assertThat(delegate.sourceRecordCount).isGreaterThan(0)
  }

  @Test
  fun alphaZero_retainsOutputAndDefersSourceRefreshUntilVisible() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }
    val style = mutableStateOf(effect.style)
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(sourceColor.value),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState), style = style.value),
        )
      }
    }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val recordsBeforeZero = delegate.stageRecordCounts
    val sourceSnapshotBeforeZero = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    style.value = style.value.then { alpha(0f) }
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.stageRecordCounts).isEqualTo(recordsBeforeZero)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshotBeforeZero)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.stageRecordCounts).isEqualTo(recordsBeforeZero)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshotBeforeZero)

    style.value = style.value.then { alpha(0.5f) }
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.sourceRecordCount).isGreaterThan(recordsBeforeZero.source)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNotSameInstanceAs(sourceSnapshotBeforeZero)
  }

  @Test
  fun nonFiniteCornerShape_runtimeUsesCanonicalSafeRadii() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      style = style.then { shape(invalidCornerShape(Float.NaN)) }
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .testGlass(effect),
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    assertThat(runtime(effect).delegate is RuntimeShaderGlassDelegate).isTrue()
    val radii = checkNotNull(runtime(effect).preparedRender).params.cornerRadii
    assertThat(radii.values().all { it.isFinite() && it >= 0f }).isTrue()
  }

  @Test
  fun nonFiniteCornerShape_fallbackDrawUsesCanonicalSafeRadii() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      style = style.then { shape(invalidCornerShape(Float.POSITIVE_INFINITY)) }
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .testGlass(effect),
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()
    runtime(effect).delegate = FallbackGlassDelegate(runtime(effect))

    assertThat(runtime(effect).delegate is FallbackGlassDelegate).isTrue()
    onNodeWithTag("glass").captureToImage()
  }

  @Test
  fun configuredInteractiveEffect_allocatesStableInteractionStagesWhileIdle() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()

    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
  }

  @Test
  fun maximumRefraction_foregroundContentSelectsFallbackDelegate() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      style = style.then {
        optics(
          GlassOptics(
            refractionStrength = 1f,
            refractionDisplacement = 16_384.dp,
            blurRadius = OpticalSizeValue.Fixed(0.dp),
          ),
        )
        edgeSoftness(0.dp)
        shape(RoundedCornerShape(0.dp))
      }
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .testGlass(effect),
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    assertThat(runtime(effect).delegate is FallbackGlassDelegate).isTrue()
    assertThat(runtime(effect).preparedRender).isNull()
  }

  @Test
  fun interactionFrames_updateDynamicStagesWithoutRecreatingBaseOpticalEffect() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val opticalEffect = checkNotNull(delegate.opticalEffect)

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
      moveTo(Offset(80f, 60f))
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    assertThat(delegate.opticalEffect).isSameInstanceAs(opticalEffect)
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
  }

  @Test
  fun interactionFramesWithoutDetail_reuseInteractionOutputEffect() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      style = style.then { optics(optics.copy(refractionDetailIntensity = 0f)) }
    }
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
    mainClock.advanceTimeBy(100)
    waitForIdle()
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isFalse()
    val outputEffect = checkNotNull(delegate.interactionOutputEffect)

    repeat(3) { step ->
      onNodeWithTag("glass").performTouchInput { moveTo(Offset(40f + step * 20f, 50f)) }
      mainClock.advanceTimeBy(32)
      waitForIdle()
    }

    assertThat(delegate.interactionOutputEffect).isSameInstanceAs(outputEffect)
  }

  @Test
  fun interactionOpticalEffect_reusesStableLayerEffectAndUpdatesNewTargets() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    val style = mutableStateOf(effect.style)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val layer = checkNotNull(delegate.layers.interactionOptical)
    val stableEffect = checkNotNull(layer.renderEffect)

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    waitForIdle()

    assertThat(layer.renderEffect).isSameInstanceAs(stableEffect)

    style.value = style.value.then { ambientResponse(0.6f) }
    waitForIdle()

    assertThat(layer.renderEffect).isNotSameInstanceAs(stableEffect)

    delegate.layers.interactionOptical = null
    style.value = style.value.then { ambientResponse(0.5f) }
    waitForIdle()

    assertThat(checkNotNull(delegate.layers.interactionOptical).renderEffect).isNotNull()
  }

  @Test
  fun largePanel_interactionPatchRetainsBaseLayersAcrossFrames() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      style = style.then {
        pressed {
          animate(toSpec = tween(1), fromSpec = tween(1)) {
            lightingIntensity(1f)
            refractionMultiplier(1.08f)
            whitePointDelta(0.04f)
          }
        }
      }
      style = style.then {
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()
    mainClock.autoAdvance = false

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val detail = checkNotNull(delegate.layers.refractionDetail)
    val materialSize = checkNotNull(runtime(effect).attachedContextForTest).modifierSize
    assertThat(materialSize.width).isGreaterThan(1000f)
    assertThat(materialSize.height).isGreaterThan(600f)
    val decision = runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime
    val plannedKinds = checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }
    val positions = listOf(Offset(200f, 150f), Offset(500f, 300f), Offset(800f, 450f))

    positions.forEach { position ->
      runtime(effect).setPressedForTest(position)
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()

      assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      assertThat((runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
        .isEqualTo(decision.scaleFactor)
      assertThat(checkNotNull(delegate.layers.interactionOptical).size.width).isLessThan(source.size.width)
      assertThat(checkNotNull(delegate.layers.interactionOptical).size.height).isLessThan(source.size.height)
      assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }).isEqualTo(plannedKinds)
    }

    runtime(effect).setPressedForTest(positions.last(), pressed = false)
    repeat(3) {
      mainClock.advanceTimeByFrame()
      assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      assertThat((runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
        .isEqualTo(decision.scaleFactor)
      assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }).isEqualTo(plannedKinds)
    }
    mainClock.autoAdvance = true
    setContent {}
    waitForIdle()
  }

  @Test
  fun defaultProfile_usesBalancedScaleAcrossRetainedWorkloads() = runComposeUiTest {
    val smallEffect = activeDetailEffect()
    setContent {
      RuntimeGlassTestContent(
        effect = smallEffect,
        tag = "small",
        performanceMode = HazePerformanceMode.Default,
      )
    }
    waitForIdle()

    assertThat(
      (runtime(smallEffect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
    ).isEqualTo(HazePerformanceMode.Balanced.resolveGlassInputScale())

    val largeEffect = activeDetailEffect()
    setContent {
      RuntimeLargeGlassTestContent(
        effect = largeEffect,
        performanceMode = HazePerformanceMode.Default,
      )
    }
    waitForIdle()

    assertThat(
      (runtime(largeEffect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
    ).isEqualTo(HazePerformanceMode.Balanced.resolveGlassInputScale())
  }

  @Test
  fun movingInteractionWithinSamePatchSize_doesNotRerecordLightingContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      style = style.then {
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(300f, 240f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val recordsAfterPress = delegate.interactionLightingRecordCount

    runtime(effect).setPressedForTest(Offset(500f, 320f))
    waitForIdle()

    assertThat(delegate.interactionLightingRecordCount).isEqualTo(recordsAfterPress)
  }

  @Test
  fun movingInteractionWithinSamePatchSize_rerecordsLocalizedContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      style = style.then {
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(300f, 240f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val interactionOpticalRecords = delegate.interactionOpticalRecordCount
    val interactionDetailRecords = delegate.interactionDetailRecordCount
    val interactionCompositeRecords = delegate.interactionCompositeRecordCount

    runtime(effect).setPressedForTest(Offset(500f, 320f))
    waitForIdle()

    assertThat(delegate.layers.source).isSameInstanceAs(source)
    assertThat(delegate.layers.optical).isSameInstanceAs(optical)
    assertThat(delegate.interactionOpticalRecordCount).isGreaterThan(interactionOpticalRecords)
    assertThat(delegate.interactionDetailRecordCount).isGreaterThan(interactionDetailRecords)
    assertThat(delegate.interactionCompositeRecordCount).isGreaterThan(interactionCompositeRecords)
  }

  @Test
  fun activeInteractionWithoutPatch_retainsBaseOutput() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    RuntimeShaderGlassDelegate::class.java.getDeclaredField("preparedInteractionPatch").apply {
      isAccessible = true
      set(delegate, null)
    }
    delegate.layers.interactionOptical = null
    delegate.layers.interactionRefractionDetail = null
    delegate.layers.interactionLighting = null

    assertThat(runtime(effect).currentInteractionSignals.pressed).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun activeInteraction_liveAndBaseUniformChangesRetainInteractionShaderHandles() =
    runComposeUiTest {
      val effect = runtimeInteractiveEffect()
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
      waitForIdle()

      onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
      mainClock.advanceTimeBy(500)
      waitForIdle()

      val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
      val opticalEffect = delegate.interactionShaderHandle("interactionOpticalEffect")
      val detailEffect = delegate.interactionShaderHandle("interactionDetailEffect")
      val lightingEffect = delegate.interactionShaderHandle("interactionLightingEffect")

      runtime(effect).setPressedForTest(Offset(80f, 60f))
      mainClock.advanceTimeBy(16)
      waitForIdle()
      style.value = style.value.then {
        ambientResponse(0.6f)
        optics(effect.optics.copy(refractionDisplacement = 18.dp))
      }
      waitForIdle()

      assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
        .isSameInstanceAs(opticalEffect)
      assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
        .isSameInstanceAs(detailEffect)
      assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
        .isSameInstanceAs(lightingEffect)

      assertThat(delegate.rimBrushProvider).isNotNull()
      runtime(effect).onTrimMemory(TrimMemoryLevel.UI_HIDDEN)
      assertThat(delegate.rimBrushProvider).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionOpticalEffect")).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionDetailEffect")).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionLightingEffect")).isNull()
    }

  @Test
  fun runtimeConstructionFailure_preparesFallbackBeforeContentBehindDecision() = runComposeUiTest {
    var creationAttempts = 0
    val effect = activeDetailEffect().apply {
      runtimeEffectFactory = GlassRuntimeEffectFactory {
        creationAttempts++
        throw RuntimeShaderRenderEffectException(
          IllegalArgumentException("broken runtime effect"),
        )
      }
    }
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(1)
    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(failureFrameCenter.alpha).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.red).isGreaterThan(0.9f)
    val attemptsAfterDowngrade = creationAttempts

    style.value = style.value.then { tint(Color.Blue.copy(alpha = 0.5f)) }
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(attemptsAfterDowngrade)
    onNodeWithTag("glass").captureToImage()
  }

  @Test
  fun runtimeDrawFailure_drawsFallbackInTheFailureFrame() = runComposeUiTest {
    val effect = activeDetailEffect().apply { style = style.then { tint(Color.Blue) } }
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val runtime = runtime(effect)
    assertThat(runtime.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    runtime.delegate = FailingRuntimeShaderGlassDelegate()
    checkNotNull(runtime.attachedContextForTest).invalidateDraw()
    waitForIdle()

    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failureFrameCenter.blue).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.red).isLessThan(0.1f)
  }

  @Test
  fun runtimeDrawFailure_preservesFullOpacityContentInputInTheFailureFrame() = runComposeUiTest {
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }
    setContent { RuntimeContentGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val runtime = runtime(effect)
    assertThat(runtime.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    runtime.delegate = FailingRuntimeShaderGlassDelegate()
    checkNotNull(runtime.attachedContextForTest).invalidateDraw()
    waitForIdle()

    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failureFrameCenter.red).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.alpha).isGreaterThan(0.9f)
  }

  @Test
  fun interactionRuntimeEffects_constructBeforeDraw() = runComposeUiTest {
    var creationAttempts = 0
    val effect = runtimeInteractiveEffect().apply {
      runtimeEffectFactory = GlassRuntimeEffectFactory { create ->
        creationAttempts++
        create()
      }
    }
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val attemptsAfterPreparation = creationAttempts

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    mainClock.advanceTimeBy(500)
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(attemptsAfterPreparation)
  }

  @Test
  fun heldInteraction_sourceContentChangeUpdatesPixels() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val effect = runtimeInteractiveEffect().apply {
      style = style.then {
        ambientResponse(0f)
        contrast(0f)
        contentNormalBlend(0f)
        whitePoint(0f)
        chromaMultiplier(1f)
      }
    }
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(sourceColor.value),
        )
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }
    waitForIdle()

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.interactionOpticalRecordCount).isGreaterThan(0)
    assertThat(delegate.interactionDetailRecordCount).isGreaterThan(0)
    assertThat(delegate.interactionCompositeRecordCount).isGreaterThan(0)

    fun centerPixel(): Color = onNodeWithTag("glass").captureToImage().toPixelMap().let {
      it[it.width / 2, it.height / 2]
    }
    val initial = centerPixel()
    assertThat(initial.red).isGreaterThan(initial.blue + 0.5f)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(runtime(effect).currentInteractionSignals.pressed).isTrue()
    // Retained layers may propagate new source pixels without recording every stage again.
    val updated = centerPixel()
    assertThat(updated.blue).isGreaterThan(updated.red + 0.5f)

    sourceColor.value = Color.Red
    waitForIdle()
    val restored = centerPixel()
    assertThat(restored.red).isGreaterThan(restored.blue + 0.5f)
  }

  @Test
  fun backgroundColorStyleChangeRecordsSource() = runComposeUiTest {
    val effect = activeDetailEffect()
    val style = mutableStateOf<GlassStyle>(GlassStyle)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val sourceRecordsBeforeChange = delegate.sourceRecordCount
    val snapshotBeforeChange = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    style.value = GlassStyle { backgroundColor(Color.White) }
    waitForIdle()

    assertThat(delegate.sourceRecordCount).isGreaterThan(sourceRecordsBeforeChange)
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot).backgroundColor)
      .isEqualTo(Color.White)
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot))
      .isNotEqualTo(snapshotBeforeChange)
  }

  @Test
  fun backgroundColorStyleChangeDuringSourceGapClearsRetainedOutput() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val style = mutableStateOf(GlassStyle { backgroundColor(Color.Red) })
    val effect = activeDetailEffect()

    setContent {
      Box(Modifier.size(120.dp)) {
        if (showSource.value) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Red)
              .hazeSource(hazeState),
          )
        }
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(
              effect = effect,
              input = HazeInput.Sources(hazeState),
              style = style.value,
            ),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    showSource.value = false
    waitForIdle()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    style.value = GlassStyle { backgroundColor(Color.Blue) }
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isFalse()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()

    showSource.value = true
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot).backgroundColor)
      .isEqualTo(Color.Blue)
  }

  @Test
  fun activeDetail_recordsAndSurvivesRetainedSourceGap() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val effect = activeDetailEffect()

    setContent {
      Box(Modifier.size(120.dp)) {
        if (showSource.value) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Red)
              .hazeSource(hazeState),
          )
        }
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val detailLayer = checkNotNull(delegate.layers.refractionDetail)
    val detailKey = checkNotNull(delegate.lastSuccessfulStageInputs?.detail)
    val sourceSnapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    showSource.value = false
    waitForIdle()

    assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detailLayer)
    assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshot)
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNotNull().isEqualTo(detailKey)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun activeDetail_recordsAllDetailPipelineLayers() = runComposeUiTest {
    val effect = activeDetailEffect()

    val style = mutableStateOf(effect.style)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val beforeDetail = delegate.detailRecordCount

    style.value = style.value.then { optics(effect.optics.copy(refractionDisplacement = 18.dp)) }
    waitForIdle()

    assertThat(delegate.detailRecordCount).isEqualTo(beforeDetail + 3)
  }

  @Test
  fun zeroRefractionScale_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      style = style.then { optics(optics.copy(refractionDisplacement = 0.dp)) }
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Red)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun epsilonRefractionStrength_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      style = style.then { optics(optics.copy(refractionStrength = 1e-6f)) }
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun lowVisibleRefractionStrength_allocatesAndRecordsDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      style = style.then { optics(optics.copy(refractionStrength = .1f)) }
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNotNull()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun effectAlpha_isAppliedToOneGroupedOpticalAndDetailOutput() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Red)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(checkNotNull(delegate.layers.optical).alpha).isEqualTo(1f)
    assertThat(checkNotNull(delegate.layers.refractionDetail).alpha).isEqualTo(1f)
  }

  @Test
  fun fractionalAlpha_inputScalingUsesOutputSizedGroupLayerAndPlan() = runComposeUiTest {
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }

    setContent {
      RuntimeGlassTestContent(
        effect = effect,
        tag = "glass",
        performanceMode = HazePerformanceMode.Fixed(0.25f),
      )
    }
    waitForIdle()

    val outputSize = checkNotNull(runtime(effect).attachedContextForTest)
      .modifierSize
      .roundToIntSize()
    val groupLayer = checkNotNull((runtime(effect).delegate as RuntimeShaderGlassDelegate).layers.groupAlpha.layer)
    val groupPlan = checkNotNull(runtime(effect).preparedRender).plan.layers.single {
      it.kind == GlassRetainedLayerKind.GroupComposite
    }

    assertThat(groupLayer.size).isEqualTo(outputSize)
    assertThat(groupPlan.size).isEqualTo(outputSize)
  }

  @Test
  fun foregroundUniformChanges_retainShadersAndRecordOnlyAffectedStages() = runComposeUiTest {
    val effect = animatedStageEffect()
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val beforeAlpha = delegate.stageRecordCounts
    style.value = style.value.then { alpha(0.5f) }
    waitForIdle()

    assertThat(delegate.stageRecordCounts).isEqualTo(beforeAlpha)

    val opticalShader = delegate.opticalShader
    style.value = style.value.then { ambientResponse(0.6f) }
    waitForIdle()

    assertThat(delegate.opticalShader).isSameInstanceAs(opticalShader)
    assertThat(delegate.opticalRecordCount).isEqualTo(beforeAlpha.optical + 1)
    assertThat(delegate.blurRecordCount).isEqualTo(beforeAlpha.blur)
    assertThat(delegate.depthRecordCount).isEqualTo(beforeAlpha.depth)

    val beforeRim = delegate.rimRecordCount
    val rimShader = delegate.rimShader
    val rimBrushProvider = checkNotNull(delegate.rimBrushProvider)
    assertThat(delegate.layers.rim?.renderEffect).isNull()
    style.value = style.value.then { lightPosition(exactLightAlignment(Offset(10f, 20f))) }
    waitForIdle()

    assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
    assertThat(delegate.rimBrushProvider).isSameInstanceAs(rimBrushProvider)
    assertThat(delegate.layers.rim?.renderEffect).isNull()
    assertThat(delegate.rimRecordCount).isEqualTo(beforeRim + 1)
  }

  @Test
  fun uniformNativeBlurAndDetailChanges_cacheAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect()
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val detailShader = checkNotNull(delegate.refractionDetailShader)
    val nativeBlurEffect = checkNotNull(delegate.layers.blurred?.renderEffect)
    val detailEffect = checkNotNull(delegate.layers.refractionDetail?.renderEffect)

    style.value = style.value.then {
      optics(
        effect.optics.copy(
          refractionDisplacement = 18.dp,
        ),
      )
    }
    waitForIdle()

    assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
    assertThat(delegate.layers.blurHorizontal).isNull()
    assertThat(delegate.layers.blurred?.renderEffect).isSameInstanceAs(nativeBlurEffect)
    assertThat(delegate.layers.refractionDetail?.renderEffect).isNotSameInstanceAs(detailEffect)

    style.value = style.value.then {
      optics(
        effect.optics.copy(
          blurRadius = OpticalSizeValue.Fixed(36.dp),
          refractionDisplacement = 18.dp,
        ),
      )
    }
    waitForIdle()

    assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
    assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(nativeBlurEffect)
  }

  @Test
  fun progressiveBlurChanges_retainShadersAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect(
      progressive = HazeProgressive.verticalGradient(
        startIntensity = 0f,
        endIntensity = 1f,
      ),
    )
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val horizontalShader = checkNotNull(delegate.progressiveBlurHorizontalShader)
    val verticalShader = checkNotNull(delegate.progressiveBlurVerticalShader)
    val horizontalEffect = checkNotNull(delegate.layers.blurHorizontal?.renderEffect)
    val verticalEffect = checkNotNull(delegate.layers.blurred?.renderEffect)

    style.value = style.value.then {
      optics(
        effect.optics.copy(
          blurRadius = OpticalSizeValue.Fixed(34.dp),
          progressive = HazeProgressive.verticalGradient(
            startIntensity = 0.1f,
            endIntensity = 0.9f,
          ),
        ),
      )
    }
    waitForIdle()

    assertThat(delegate.progressiveBlurHorizontalShader).isSameInstanceAs(horizontalShader)
    assertThat(delegate.progressiveBlurVerticalShader).isSameInstanceAs(verticalShader)
    assertThat(delegate.layers.blurHorizontal?.renderEffect).isNotSameInstanceAs(horizontalEffect)
    assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(verticalEffect)
  }

  @Test
  fun wideNativeBlur_smoothsCheckerboardAcrossCenterAndInsetEdges() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    fun blurStyle(radius: androidx.compose.ui.unit.Dp) = GlassStyle {
      optics(
        GlassOptics(
          refractionStrength = 0f,
          refractionDisplacement = 0.dp,
          depth = OpticalSizeValue.Fixed(1f),
          blurRadius = OpticalSizeValue.Fixed(radius),
        ),
      )
      backgroundColor(Color.Transparent)
      tint(Color.Transparent)
      ambientResponse(0f)
      contentNormalBlend(0f)
      contrast(0f)
      whitePoint(0f)
      chromaMultiplier(1f)
      shape(RoundedCornerShape(0.dp))
      edgeSoftness(0.dp)
      edgeShadow(Color.Transparent)
      specularIntensity(0f)
    }
    val style = mutableStateOf(blurStyle(0.dp))
    setContent {
      val hazeState = remember { HazeState() }
      Box(Modifier.size(480.dp)) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
          var top = 0f
          while (top < size.height) {
            var left = 0f
            while (left < size.width) {
              val cellSize = when {
                top < size.height * 0.5f && left < size.width * 0.5f -> 8f
                top < size.height * 0.5f -> 24f
                left < size.width * 0.5f -> 32f
                else -> 8f
              }
              val evenCell = ((left / cellSize).toInt() + (top / cellSize).toInt()) % 2 == 0
              drawRect(
                color = if (evenCell) Color.White else Color.Black,
                topLeft = Offset(left, top),
                size = Size(cellSize, cellSize),
              )
              left += cellSize
            }
            top += 8f
          }
        }
        Box(
          Modifier
            .size(120.dp)
            .align(Alignment.Center)
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState), style = style.value),
        )
      }
    }
    waitForIdle()

    fun range(pixels: PixelMap, left: IntRange, top: IntRange): Float {
      val samples = buildList {
        for (y in top step 4) {
          for (x in left step 4) add(pixels[x, y].red)
        }
      }
      return samples.max() - samples.min()
    }
    val sharp = onNodeWithTag("glass").captureToImage().toPixelMap()
    assertThat(range(sharp, 28..48, 28..48)).isGreaterThan(0.5f)

    style.value = blurStyle(84.dp)
    waitForIdle()

    val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()

    val runtime = runtime(effect)
    val delegate = runtime.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.hasBlurHorizontal).isFalse()
    assertThat(delegate.layers.blurred?.renderEffect).isNotNull()
    val prepared = checkNotNull(runtime.preparedRender)
    assertThat(prepared.plan.layers.any { it.kind == GlassRetainedLayerKind.BlurHorizontal }).isFalse()
    listOf(
      range(pixels, 28..48, 28..48), // 8px period
      range(pixels, 76..100, 28..52), // 24px period
      range(pixels, 28..52, 76..100), // 32px period
      range(pixels, 4..12, 28..52), // inset edge strip, outside the antialias rim
    ).forEach { range ->
      assertThat(range).isLessThan(0.08f)
    }
  }

  private fun activeDetailEffect() = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
      specularIntensity(0f)
    }
  }

  private fun runtimeInteractiveEffect() = activeDetailEffect().apply {
    style = style.then {
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.08f)
        whitePointDelta(0.04f)
      }
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  }

  @Composable
  private fun SourceBackedGlassTestContent(
    effect: GlassRuntimeEffect,
    color: Color = Color.Red,
    size: Dp = 120.dp,
    style: GlassStyle = effect.style,
  ) {
    val state = remember { HazeState() }
    Box(Modifier.size(size)) {
      Box(Modifier.fillMaxSize().hazeSource(state).background(color))
      Box(Modifier.fillMaxSize().testGlass(effect, input = HazeInput.Sources(state), style = style))
    }
  }

  private fun animatedStageEffect() = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          depth = OpticalSizeValue.Fixed(0.5f),
          blurRadius = OpticalSizeValue.Fixed(14.dp),
        ),
      )
      specularIntensity(1f)
      ambientResponse(0.5f)
      lightPosition(Alignment.Center)
    }
  }

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
  ) = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          depth = OpticalSizeValue.Fixed(0.5f),
          blurRadius = OpticalSizeValue.Fixed(38.5.dp),
          progressive = progressive,
        ),
      )
      specularIntensity(0f)
    }
  }

  private fun Modifier.testGlass(
    effect: GlassRuntimeEffect,
    input: HazeInput = HazeInput.Content,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
    style: GlassStyle = effect.style,
  ): Modifier = hazeGlass(
    factory = rendererFactories.getOrPut(effect) {
      HazeEffectFactory {
        effect.also { attachedRuntimes[effect] = it }
      }
    },
    input = input,
    style = style,
    performanceMode = performanceMode,
    expandLayerBounds = true,
    interactionSource = effect.interactionSource,
    interactionTransformTarget = effect.interactionTransformTarget,
    interactionTransformPivot = effect.interactionTransformPivot,
    interactionReducedMotionPolicy = effect.interactionReducedMotionPolicy,
  )

  private fun runtime(effect: GlassRuntimeEffect): GlassRuntimeEffect =
    checkNotNull(attachedRuntimes[effect])

  @Composable
  private fun RuntimeForegroundGlassTestContent(
    effect: GlassRuntimeEffect,
    tag: String? = null,
    style: GlassStyle = effect.style,
  ) {
    Box(
      Modifier
        .size(120.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .testGlass(effect, style = style),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  private fun invalidCornerShape(radius: Float) = RoundedCornerShape(
    object : CornerSize {
      override fun toPx(shapeSize: Size, density: Density): Float = radius
    },
  )

  private fun CornerRadii.values(): List<Float> = listOf(
    topLeft,
    topRight,
    bottomRight,
    bottomLeft,
  )

  private class FailingRuntimeShaderGlassDelegate : GlassRuntimeEffect.Delegate {
    override fun DrawScope.prepareDraw(context: HazeEffectRuntimeDrawScope) = Unit

    override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope): Nothing {
      throw RuntimeShaderRenderEffectException(IllegalArgumentException("draw failure"))
    }
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandle(fieldName: String): Any {
    return checkNotNull(interactionShaderHandleOrNull(fieldName))
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandleOrNull(fieldName: String): Any? {
    val field = RuntimeShaderGlassDelegate::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this)
  }

  @Composable
  private fun RuntimeGlassTestContent(
    effect: GlassRuntimeEffect,
    tag: String,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
    style: GlassStyle = effect.style,
  ) {
    val hazeState = remember { HazeState() }
    Box(Modifier.size(120.dp)) {
      Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
      Box(
        Modifier
          .fillMaxSize()
          .testTag(tag)
          .testGlass(
            effect = effect,
            input = HazeInput.Sources(hazeState),
            performanceMode = performanceMode,
            style = style,
          ),
      )
    }
  }

  @Composable
  private fun RuntimeContentGlassTestContent(
    effect: GlassRuntimeEffect,
    tag: String,
  ) {
    Box(
      Modifier
        .size(120.dp)
        .testTag(tag)
        .testGlass(effect),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  @Composable
  private fun RuntimeLargeGlassTestContent(
    effect: GlassRuntimeEffect,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
  ) {
    val hazeState = remember { HazeState() }
    Box(
      Modifier.size(1100.dp, 650.dp).drawWithContent {
        // These tests verify full-size layer retention and recording, not whole-panel pixels.
        // Keep the geometry intact, but rasterize a small sample of the current interaction patch.
        val position = effect.currentInteractionState.position
        clipRect(position.x - 16f, position.y - 16f, position.x + 16f, position.y + 16f) {
          this@drawWithContent.drawContent()
        }
      },
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
      Box(
        Modifier
          .fillMaxSize()
          .testGlass(
            effect = effect,
            input = HazeInput.Sources(hazeState),
            performanceMode = performanceMode,
          ),
      )
    }
  }
}
