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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RetainedOutputVisualEffect
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RuntimeShaderGlassDelegateIntegrationTest : ContextTest() {

  @Test
  fun alphaZero_clearsRetainedOutputUntilVisibleFrameRefreshesIt() = runComposeUiTest {
    val effect = activeDetailEffect().apply { alpha = 0.5f }
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val sourceRecordsBeforeZero = delegate.sourceRecordCount

    effect.alpha = 0f
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isFalse()

    effect.alpha = 0.5f
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.sourceRecordCount).isGreaterThan(sourceRecordsBeforeZero)
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
          .hazeEffect {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    assertThat(effect.delegate is RuntimeShaderGlassDelegate).isTrue()
    val radii = checkNotNull(effect.preparedRender).params.cornerRadii
    assertThat(radii.values().all { it.isFinite() && it >= 0f }).isTrue()
  }

  @Test
  fun nonFiniteCornerShape_fallbackDrawUsesCanonicalSafeRadii() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      shape = invalidCornerShape(Float.POSITIVE_INFINITY)
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .hazeEffect {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()
    effect.delegate = FallbackGlassDelegate(effect)

    assertThat(effect.delegate is FallbackGlassDelegate).isTrue()
    onNodeWithTag("glass").captureToImage()
  }

  @Test
  fun configuredInteractiveEffect_allocatesStableInteractionStagesWhileIdle() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()

    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
  }

  @Test
  fun maximumRefraction_foregroundContentSelectsFallbackDelegate() = runComposeUiTest {
    val effect = GlassVisualEffect().apply {
      optics = GlassOptics.Absolute(
        refractionStrength = 1f,
        refractionScale = 16_384f,
        blurRadius = 0.dp,
      )
      edgeSoftness = 0.dp
      shape = RoundedCornerShape(0.dp)
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .hazeEffect {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    assertThat(effect.delegate is FallbackGlassDelegate).isTrue()
    assertThat(effect.preparedRender).isNull()
  }

  @Test
  fun interactionFrames_updateDynamicStagesWithoutRecreatingBaseOpticalEffect() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
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
  fun largePanel_interactionPatchRetainsBaseLayersAcrossFrames() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      pressed {
        animate(toSpec = tween(1), fromSpec = tween(1)) {
          lightingIntensity(1f)
          refractionMultiplier(1.08f)
          whitePointDelta(0.04f)
        }
      }
      interactionLightRadiusFraction = 0.25f
      interactionPositionAnimationSpec = tween(1)
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()
    mainClock.autoAdvance = false

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val detail = checkNotNull(delegate.layers.refractionDetail)
    val decision = effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime
    val plannedKinds = checkNotNull(effect.preparedRender).plan.layers.map { it.kind }
    val positions = listOf(Offset(200f, 150f), Offset(500f, 300f), Offset(800f, 450f))

    positions.forEach { position ->
      effect.setPressedForTest(position)
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()

      assertThat(effect.delegate).isSameInstanceAs(delegate)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      assertThat((effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
        .isEqualTo(decision.scaleFactor)
      assertThat(checkNotNull(delegate.layers.interactionOptical).size.width).isLessThan(source.size.width)
      assertThat(checkNotNull(delegate.layers.interactionOptical).size.height).isLessThan(source.size.height)
      assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind }).isEqualTo(plannedKinds)
    }

    effect.setPressedForTest(positions.last(), pressed = false)
    repeat(12) {
      mainClock.advanceTimeByFrame()
      assertThat(effect.delegate).isSameInstanceAs(delegate)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      assertThat((effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
        .isEqualTo(decision.scaleFactor)
      assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind }).isEqualTo(plannedKinds)
    }
    mainClock.autoAdvance = true
    setContent {}
    waitForIdle()
  }

  @Test
  fun movingInteractionWithinSamePatchSize_doesNotRerecordLightingContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      interactionLightRadiusFraction = 0.25f
      interactionPositionAnimationSpec = tween(1)
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()

    effect.setPressedForTest(Offset(300f, 240f))
    waitForIdle()

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val recordsAfterPress = delegate.interactionLightingRecordCount

    effect.setPressedForTest(Offset(500f, 320f))
    waitForIdle()

    assertThat(delegate.interactionLightingRecordCount).isEqualTo(recordsAfterPress)
  }

  @Test
  fun movingInteractionWithinSamePatchSize_rerecordsLocalizedContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      interactionLightRadiusFraction = 0.25f
      interactionPositionAnimationSpec = tween(1)
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()

    effect.setPressedForTest(Offset(300f, 240f))
    waitForIdle()

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val interactionOpticalRecords = delegate.interactionOpticalRecordCount
    val interactionDetailRecords = delegate.interactionDetailRecordCount
    val interactionCompositeRecords = delegate.interactionCompositeRecordCount

    effect.setPressedForTest(Offset(500f, 320f))
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

    effect.setPressedForTest(Offset(60f, 60f))
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    RuntimeShaderGlassDelegate::class.java.getDeclaredField("preparedInteractionPatch").apply {
      isAccessible = true
      set(delegate, null)
    }
    delegate.layers.interactionOptical = null
    delegate.layers.interactionRefractionDetail = null
    delegate.layers.interactionLighting = null

    assertThat(effect.currentInteractionSignals.pressed).isTrue()
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

      val delegate = effect.delegate as RuntimeShaderGlassDelegate
      val opticalEffect = delegate.interactionShaderHandle("interactionOpticalEffect")
      val detailEffect = delegate.interactionShaderHandle("interactionDetailEffect")
      val lightingEffect = delegate.interactionShaderHandle("interactionLightingEffect")

      effect.setPressedForTest(Offset(80f, 60f))
      mainClock.advanceTimeBy(16)
      waitForIdle()
      effect.ambientResponse = 0.6f
      effect.optics = (effect.optics as GlassOptics.Absolute).copy(refractionScale = 18f)
      waitForIdle()

      assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
        .isSameInstanceAs(opticalEffect)
      assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
        .isSameInstanceAs(detailEffect)
      assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
        .isSameInstanceAs(lightingEffect)

      effect.onTrimMemory(checkNotNull(effect.attachedContextForTest), TrimMemoryLevel.UI_HIDDEN)
      assertThat(delegate.interactionShaderHandleOrNull("interactionOpticalEffect")).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionDetailEffect")).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionLightingEffect")).isNull()
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
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }
    waitForIdle()

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val sourceRecordsBeforeMutation = delegate.sourceRecordCount
    val interactionOpticalRecordsBeforeMutation = delegate.interactionOpticalRecordCount
    val interactionDetailRecordsBeforeMutation = delegate.interactionDetailRecordCount
    val interactionCompositeRecordsBeforeMutation = delegate.interactionCompositeRecordCount
    val acceptedSnapshotBeforeMutation = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(effect.currentInteractionSignals.pressed).isTrue()
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
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val detailLayer = checkNotNull(delegate.layers.refractionDetail)
    val detailKey = checkNotNull(delegate.lastSuccessfulStageInputs?.detail)
    val sourceSnapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    showSource.value = false
    waitForIdle()

    assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detailLayer)
    assertThat(effect.delegate).isSameInstanceAs(delegate)
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

    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val beforeDetail = delegate.detailRecordCount

    effect.optics = (effect.optics as GlassOptics.Absolute).copy(refractionScale = 18f)
    waitForIdle()

    assertThat(delegate.detailRecordCount).isEqualTo(beforeDetail + 3)
  }

  @Test
  fun zeroRefractionScale_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      optics = (optics as GlassOptics.Absolute).copy(refractionScale = 0f)
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
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun epsilonRefractionStrength_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      optics = (optics as GlassOptics.Absolute).copy(refractionStrength = 1e-6f)
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun lowVisibleRefractionStrength_allocatesAndRecordsDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      optics = (optics as GlassOptics.Absolute).copy(refractionStrength = .1f)
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
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
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effect
            },
        )
      }
    }

    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
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
        inputScale = HazeInputScale.Fixed(0.5f),
      )
    }
    waitForIdle()

    val outputSize = checkNotNull(effect.attachedContextForTest).size.roundToIntSize()
    val groupLayer = checkNotNull((effect.delegate as RuntimeShaderGlassDelegate).layers.groupAlpha.layer)
    val groupPlan = checkNotNull(effect.preparedRender).plan.layers.single {
      it.kind == GlassRetainedLayerKind.GroupComposite
    }

    assertThat(groupLayer.size).isEqualTo(outputSize)
    assertThat(groupPlan.size).isEqualTo(outputSize)
  }

  @Test
  fun initialBlurWorkingSizeSetup_doesNotInvalidateDraw() = runComposeUiTest {
    val glassEffect = animatedStageEffect().apply { resetDirtyTracker() }
    val effect = InvalidationTrackingVisualEffect(glassEffect)

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .hazeEffect {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    val delegate = glassEffect.delegate as RuntimeShaderGlassDelegate
    assertThat(effect.invalidateDrawCalls).isEqualTo(0)
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun foregroundUniformChanges_retainShadersAndRecordOnlyAffectedStages() = runComposeUiTest {
    val effect = animatedStageEffect()
    setContent { RuntimeForegroundGlassTestContent(effect) }
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate

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
    effect.lightPosition = Offset(10f, 20f)
    waitForIdle()

    assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
    assertThat(delegate.rimRecordCount).isEqualTo(beforeRim + 1)
  }

  @Test
  fun uniformBlurAndDetailChanges_retainShadersAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect()
    setContent { RuntimeForegroundGlassTestContent(effect) }
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate

    val horizontalShader = checkNotNull(delegate.blurHorizontalShader)
    val verticalShader = checkNotNull(delegate.blurVerticalShader)
    val prefilterShader = checkNotNull(delegate.blurPrefilterShader)
    val detailShader = checkNotNull(delegate.refractionDetailShader)
    val horizontalEffect = checkNotNull(delegate.layers.blurHorizontal?.renderEffect)
    val verticalEffect = checkNotNull(delegate.layers.blurred?.renderEffect)
    val prefilterEffect = checkNotNull(delegate.layers.blurPrefiltered?.renderEffect)
    val detailEffect = checkNotNull(delegate.layers.refractionDetail?.renderEffect)

    effect.optics = (effect.optics as GlassOptics.Absolute).copy(
      blurRadius = 36.dp,
      refractionScale = 18f,
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
    val delegate = effect.delegate as RuntimeShaderGlassDelegate

    val horizontalShader = checkNotNull(delegate.progressiveBlurHorizontalShader)
    val verticalShader = checkNotNull(delegate.progressiveBlurVerticalShader)
    val horizontalEffect = checkNotNull(delegate.layers.blurHorizontal?.renderEffect)
    val verticalEffect = checkNotNull(delegate.layers.blurred?.renderEffect)

    effect.optics = (effect.optics as GlassOptics.Absolute).copy(
      blurRadius = 34.dp,
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

  private fun activeDetailEffect() = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = 0.5f,
      refractionScale = 20f,
      blurRadius = 0.dp,
    )
    specularIntensity = 0f
  }

  private fun runtimeInteractiveEffect() = activeDetailEffect().apply {
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
    }
    interactionLightRadiusFraction = 0.7f
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  }

  private fun animatedStageEffect() = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = 0.5f,
      refractionScale = 20f,
      depth = 0.5f,
      blurRadius = 14.dp,
    )
    specularIntensity = 1f
    ambientResponse = 0.5f
    lightPosition = Offset(60f, 60f)
  }

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
  ) = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = 0.5f,
      refractionScale = 20f,
      depth = 0.5f,
      blurRadius = 38.5.dp,
      progressive = progressive,
    )
    specularIntensity = 0f
  }

  @Composable
  private fun RuntimeForegroundGlassTestContent(effect: GlassVisualEffect) {
    Box(
      Modifier
        .size(120.dp)
        .hazeEffect {
          inputScale = HazeInputScale.None
          visualEffect = effect
        },
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

  private fun RuntimeShaderGlassDelegate.interactionShaderHandle(fieldName: String): Any {
    return checkNotNull(interactionShaderHandleOrNull(fieldName))
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandleOrNull(fieldName: String): Any? {
    val field = RuntimeShaderGlassDelegate::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this)
  }

  private class InvalidationTrackingVisualEffect(
    private val delegate: GlassVisualEffect,
  ) : VisualEffect, RetainedOutputVisualEffect {
    var invalidateDrawCalls = 0
      private set

    private var trackingContext: VisualEffectContext? = null

    private fun trackingContext(original: VisualEffectContext): VisualEffectContext {
      return trackingContext ?: object : VisualEffectContext by original {
        override fun invalidateDraw() {
          invalidateDrawCalls++
          original.invalidateDraw()
        }
      }.also { trackingContext = it }
    }

    override fun DrawScope.prepareDraw(context: VisualEffectContext) {
      with(delegate) { prepareDraw(trackingContext(context)) }
    }

    override fun DrawScope.draw(context: VisualEffectContext) {
      with(delegate) { draw(trackingContext(context)) }
    }

    override fun DrawScope.drawForeground(context: VisualEffectContext) {
      with(delegate) { drawForeground(trackingContext(context)) }
    }

    override fun attach(context: VisualEffectContext) {
      delegate.attach(trackingContext(context))
    }

    override fun update(context: VisualEffectContext) {
      delegate.update(trackingContext(context))
    }

    override fun detach(context: VisualEffectContext) {
      delegate.detach(trackingContext(context))
    }

    override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
      delegate.onTrimMemory(trackingContext(context), level)
    }

    override fun shouldDrawContentBehind(context: VisualEffectContext): Boolean {
      return delegate.shouldDrawContentBehind(trackingContext(context))
    }

    override fun shouldClipToNodeBounds(): Boolean = delegate.shouldClipToNodeBounds()

    override fun shouldPreferClipToAreaBounds(): Boolean = delegate.shouldPreferClipToAreaBounds()

    override fun calculateLayerBounds(rect: Rect, density: Density): Rect {
      return delegate.calculateLayerBounds(rect, density)
    }

    override fun canDrawRetainedOutput(context: VisualEffectContext): Boolean {
      return delegate.canDrawRetainedOutput(trackingContext(context))
    }

    override fun shouldDrawRetainedOutput(context: VisualEffectContext): Boolean {
      return delegate.shouldDrawRetainedOutput(trackingContext(context))
    }

    override fun clearRetainedOutput() {
      delegate.clearRetainedOutput()
    }
  }

  @Composable
  private fun RuntimeGlassTestContent(
    effect: GlassVisualEffect,
    tag: String,
    inputScale: HazeInputScale = HazeInputScale.None,
  ) {
    val hazeState = remember { HazeState() }
    Box(Modifier.size(120.dp)) {
      Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
      Box(
        Modifier
          .fillMaxSize()
          .testTag(tag)
          .hazeEffect(hazeState) {
            this.inputScale = inputScale
            visualEffect = effect
          },
      )
    }
  }

  @Composable
  private fun RuntimeLargeGlassTestContent(effect: GlassVisualEffect) {
    val hazeState = remember { HazeState() }
    Box(Modifier.size(1000.dp, 600.dp)) {
      Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
      Box(
        Modifier
          .fillMaxSize()
          .hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effect
          },
      )
    }
  }
}
