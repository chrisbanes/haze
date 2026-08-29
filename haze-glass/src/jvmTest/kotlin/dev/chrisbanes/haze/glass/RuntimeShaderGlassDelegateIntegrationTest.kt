// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
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
  fun firstAlphaZeroFrame_skipsRuntimeShaderAndLayerGraphPreparation() = runComposeUiTest {
    var creationAttempts = 0
    val effect = activeDetailEffect().apply {
      alpha = 0f
      runtimeEffectFactory = GlassRuntimeEffectFactory { create ->
        creationAttempts++
        create()
      }
    }

    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    assertThat(runtime(effect).preparedRender).isNull()
    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(runtime(effect).dirtyTracker).isEqualTo(Bitmask())
    assertThat(creationAttempts).isEqualTo(0)

    effect.alpha = 0.5f
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(creationAttempts).isGreaterThan(0)
    assertThat(delegate.sourceRecordCount).isGreaterThan(0)
  }

  @Test
  fun alphaZero_retainsOutputAndDefersSourceRefreshUntilVisible() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val effect = activeDetailEffect().apply { alpha = 0.5f }
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(sourceColor.value)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val recordsBeforeZero = delegate.stageRecordCounts
    val sourceSnapshotBeforeZero = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    effect.alpha = 0f
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.stageRecordCounts).isEqualTo(recordsBeforeZero)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshotBeforeZero)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.stageRecordCounts).isEqualTo(recordsBeforeZero)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshotBeforeZero)

    effect.alpha = 0.5f
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.sourceRecordCount).isGreaterThan(recordsBeforeZero.source)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNotSameInstanceAs(sourceSnapshotBeforeZero)
  }

  @Test
  fun nonFiniteCornerShape_runtimeUsesCanonicalSafeRadii() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      shape = invalidCornerShape(Float.NaN)
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
      shape = invalidCornerShape(Float.POSITIVE_INFINITY)
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
      optics = GlassOptics(
        refractionStrength = 1f,
        refractionDisplacement = 16_384.dp,
        blurRadius = SizeValue.Fixed(0.dp),
      )
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
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
  fun interactionOpticalEffect_reusesStableLayerEffectAndUpdatesNewTargets() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val layer = checkNotNull(delegate.layers.interactionOptical)
    val stableEffect = checkNotNull(layer.renderEffect)

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    waitForIdle()

    assertThat(layer.renderEffect).isSameInstanceAs(stableEffect)

    effect.ambientResponse = 0.6f
    waitForIdle()

    assertThat(layer.renderEffect).isNotSameInstanceAs(stableEffect)

    delegate.layers.interactionOptical = null
    effect.ambientResponse = 0.5f
    waitForIdle()

    assertThat(checkNotNull(delegate.layers.interactionOptical).renderEffect).isNotNull()
  }

  @Test
  fun largePanel_interactionPatchRetainsBaseLayersAcrossFrames() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      pressed {
        animate(toSpec = tween(1), fromSpec = tween(1)) {
          lightingIntensity(1f)
          refractionMultiplier(1.08f)
          whitePointDelta(0.04f)
        }
      }
      style = GlassStyle {
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
  fun adaptiveSampling_selectsTierFromPreparedRetainedWorkload() = runComposeUiTest {
    val smallEffect = activeDetailEffect()
    setContent {
      RuntimeGlassTestContent(
        effect = smallEffect,
        tag = "small",
        performanceMode = HazePerformanceMode.Adaptive,
      )
    }
    waitForIdle()

    assertThat(
      (runtime(smallEffect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
    ).isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)

    val largeEffect = activeDetailEffect()
    setContent {
      RuntimeLargeGlassTestContent(
        effect = largeEffect,
        performanceMode = HazePerformanceMode.Adaptive,
      )
    }
    waitForIdle()

    assertThat(
      (runtime(largeEffect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
    ).isEqualTo(0.5f)
  }

  @Test
  fun movingInteractionWithinSamePatchSize_doesNotRerecordLightingContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      style = GlassStyle {
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
      style = GlassStyle {
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
      setContent { RuntimeGlassTestContent(effect, tag = "glass") }
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
      effect.ambientResponse = 0.6f
      effect.optics = effect.optics.copy(refractionDisplacement = 18.dp)
      waitForIdle()

      assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
        .isSameInstanceAs(opticalEffect)
      assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
        .isSameInstanceAs(detailEffect)
      assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
        .isSameInstanceAs(lightingEffect)

      runtime(effect).onTrimMemory(TrimMemoryLevel.UI_HIDDEN)
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
    setContent { RuntimeForegroundGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(1)
    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(failureFrameCenter.alpha).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.red).isGreaterThan(0.9f)
    val attemptsAfterDowngrade = creationAttempts

    effect.tint = Color.Blue.copy(alpha = 0.5f)
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(attemptsAfterDowngrade)
    onNodeWithTag("glass").captureToImage()
  }

  @Test
  fun runtimeDrawFailure_drawsFallbackInTheFailureFrame() = runComposeUiTest {
    val effect = activeDetailEffect().apply { tint = Color.Blue }
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
    val effect = activeDetailEffect().apply { alpha = 0.5f }
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
  fun heldInteraction_sourceContentChangeRecordsSource() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val effect = runtimeInteractiveEffect()
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(sourceColor.value)
            .hazeSource(hazeState),
        )
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
    val sourceRecordsBeforeMutation = delegate.sourceRecordCount
    val interactionOpticalRecordsBeforeMutation = delegate.interactionOpticalRecordCount
    val interactionDetailRecordsBeforeMutation = delegate.interactionDetailRecordCount
    val interactionCompositeRecordsBeforeMutation = delegate.interactionCompositeRecordCount
    val acceptedSnapshotBeforeMutation = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(runtime(effect).currentInteractionSignals.pressed).isTrue()
    assertThat(delegate.sourceRecordCount).isGreaterThan(sourceRecordsBeforeMutation)
    assertThat(delegate.interactionOpticalRecordCount)
      .isGreaterThan(interactionOpticalRecordsBeforeMutation)
    assertThat(delegate.interactionDetailRecordCount)
      .isGreaterThan(interactionDetailRecordsBeforeMutation)
    assertThat(delegate.interactionCompositeRecordCount)
      .isGreaterThan(interactionCompositeRecordsBeforeMutation)
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot))
      .isNotEqualTo(acceptedSnapshotBeforeMutation)
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

    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val beforeDetail = delegate.detailRecordCount

    effect.optics = effect.optics.copy(refractionDisplacement = 18.dp)
    waitForIdle()

    assertThat(delegate.detailRecordCount).isEqualTo(beforeDetail + 3)
  }

  @Test
  fun zeroRefractionScale_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      optics = optics.copy(refractionDisplacement = 0.dp)
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
      optics = optics.copy(refractionStrength = 1e-6f)
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
      optics = optics.copy(refractionStrength = .1f)
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
    val effect = activeDetailEffect().apply { alpha = 0.5f }

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
    val effect = activeDetailEffect().apply { alpha = 0.5f }

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
    setContent { RuntimeForegroundGlassTestContent(effect) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val beforeAlpha = delegate.stageRecordCounts
    effect.alpha = 0.5f
    waitForIdle()

    assertThat(delegate.stageRecordCounts).isEqualTo(beforeAlpha)

    val opticalShader = delegate.opticalShader
    effect.ambientResponse = 0.6f
    waitForIdle()

    assertThat(delegate.opticalShader).isSameInstanceAs(opticalShader)
    assertThat(delegate.opticalRecordCount).isEqualTo(beforeAlpha.optical + 1)
    assertThat(delegate.blurRecordCount).isEqualTo(beforeAlpha.blur)
    assertThat(delegate.depthRecordCount).isEqualTo(beforeAlpha.depth)

    val beforeRim = delegate.rimRecordCount
    val rimShader = delegate.rimShader
    effect.lightPosition = exactLightAlignment(Offset(10f, 20f))
    waitForIdle()

    assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
    assertThat(delegate.rimRecordCount).isEqualTo(beforeRim + 1)
  }

  @Test
  fun uniformBlurAndDetailChanges_retainShadersAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect()
    setContent { RuntimeForegroundGlassTestContent(effect) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val horizontalShader = checkNotNull(delegate.blurHorizontalShader)
    val verticalShader = checkNotNull(delegate.blurVerticalShader)
    val prefilterShader = checkNotNull(delegate.blurPrefilterShader)
    val detailShader = checkNotNull(delegate.refractionDetailShader)
    val horizontalEffect = checkNotNull(delegate.layers.blurHorizontal?.renderEffect)
    val verticalEffect = checkNotNull(delegate.layers.blurred?.renderEffect)
    val prefilterEffect = checkNotNull(delegate.layers.blurPrefiltered?.renderEffect)
    val detailEffect = checkNotNull(delegate.layers.refractionDetail?.renderEffect)

    effect.optics = effect.optics.copy(
      blurRadius = SizeValue.Fixed(36.dp),
      refractionDisplacement = 18.dp,
    )
    waitForIdle()

    assertThat(delegate.blurHorizontalShader).isSameInstanceAs(horizontalShader)
    assertThat(delegate.blurVerticalShader).isSameInstanceAs(verticalShader)
    assertThat(delegate.blurPrefilterShader).isSameInstanceAs(prefilterShader)
    assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
    assertThat(delegate.layers.blurHorizontal?.renderEffect).isNotSameInstanceAs(horizontalEffect)
    assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(verticalEffect)
    assertThat(delegate.layers.blurPrefiltered?.renderEffect).isNotSameInstanceAs(prefilterEffect)
    assertThat(delegate.layers.refractionDetail?.renderEffect).isNotSameInstanceAs(detailEffect)
  }

  @Test
  fun progressiveBlurChanges_retainShadersAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect(
      progressive = HazeProgressive.verticalGradient(
        startIntensity = 0f,
        endIntensity = 1f,
      ),
    )
    setContent { RuntimeForegroundGlassTestContent(effect) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val horizontalShader = checkNotNull(delegate.progressiveBlurHorizontalShader)
    val verticalShader = checkNotNull(delegate.progressiveBlurVerticalShader)
    val horizontalEffect = checkNotNull(delegate.layers.blurHorizontal?.renderEffect)
    val verticalEffect = checkNotNull(delegate.layers.blurred?.renderEffect)

    effect.optics = effect.optics.copy(
      blurRadius = SizeValue.Fixed(34.dp),
      progressive = HazeProgressive.verticalGradient(
        startIntensity = 0.1f,
        endIntensity = 0.9f,
      ),
    )
    waitForIdle()

    assertThat(delegate.progressiveBlurHorizontalShader).isSameInstanceAs(horizontalShader)
    assertThat(delegate.progressiveBlurVerticalShader).isSameInstanceAs(verticalShader)
    assertThat(delegate.layers.blurHorizontal?.renderEffect).isNotSameInstanceAs(horizontalEffect)
    assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(verticalEffect)
  }

  private fun activeDetailEffect() = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      blurRadius = SizeValue.Fixed(0.dp),
    )
    specularIntensity = 0f
  }

  private fun runtimeInteractiveEffect() = activeDetailEffect().apply {
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  }

  private fun animatedStageEffect() = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      depth = SizeValue.Fixed(0.5f),
      blurRadius = SizeValue.Fixed(14.dp),
    )
    specularIntensity = 1f
    ambientResponse = 0.5f
    lightPosition = Alignment.Center
  }

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
  ) = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      depth = SizeValue.Fixed(0.5f),
      blurRadius = SizeValue.Fixed(38.5.dp),
      progressive = progressive,
    )
    specularIntensity = 0f
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
  ) {
    Box(
      Modifier
        .size(120.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .testGlass(effect),
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
    Box(Modifier.size(1100.dp, 650.dp)) {
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
