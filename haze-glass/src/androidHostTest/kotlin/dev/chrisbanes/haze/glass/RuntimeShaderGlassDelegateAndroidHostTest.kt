// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalHazeApi::class,
  InternalHazeApi::class,
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RuntimeShaderGlassDelegateAndroidHostTest : ContextTest() {
  @Test
  fun directRuntimePath_ownsRenderedResources() =
    runAndroidComposeUiTest<ComponentActivity> {
      val configuration = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(configuration) }
      waitForIdle()
      drawFrame()

      val runtime = runtime(configuration)
      val context = checkNotNull(runtime.attachedContextForTest)
      assertThat(runtime.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
      assertThat(runtime.delegate).isNotSameInstanceAs(configuration)
      assertThat(runtime).isSameInstanceAs(configuration)
      assertThat(configuration.canDrawRetainedOutput()).isTrue()
    }

  @Test
  fun runtimeConstructionFailure_usesFallbackWithoutRetryingEveryFrame() =
    runAndroidComposeUiTest<ComponentActivity> {
      var creationAttempts = 0
      val effect = retainedBlurEffect().apply {
        runtimeEffectFactory = GlassRuntimeEffectFactory {
          creationAttempts++
          throw RuntimeShaderRenderEffectException(
            IllegalArgumentException("broken Android runtime effect"),
          )
        }
      }
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
      assertThat(creationAttempts).isEqualTo(1)

      drawFrame()

      assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
      assertThat(creationAttempts).isEqualTo(1)
    }

  @Test
  fun singleStandardBlur_usesFusedBaseRenderer() =
    assertFusedRenderer(retainedBlurEffect()) { delegate ->
      assertThat(delegate.layers.hasDepthMixed).isFalse()
      assertThat(delegate.layers.hasBlurPrefiltered).isFalse()
      assertThat(delegate.layers.hasBlurHorizontal).isFalse()
      assertThat(delegate.layers.hasBlurred).isFalse()
      assertThat(delegate.blurHorizontalShader).isNull()
      assertThat(delegate.blurVerticalShader).isNull()
      assertThat(delegate.opticalShader).isNull()
      assertThat(delegate.refractionDetailShader).isNotNull()
    }

  @Test
  fun singleProgressiveBlur_usesFusedBaseRenderer() =
    assertFusedRenderer(
      retainedBlurEffect(
        progressive = HazeProgressive.verticalGradient(
          startIntensity = 0f,
          endIntensity = 1f,
        ),
      ),
    ) { delegate ->
      assertThat(delegate.layers.hasDepthMixed).isFalse()
      assertThat(delegate.layers.hasBlurHorizontal).isFalse()
      assertThat(delegate.layers.hasBlurred).isFalse()
    }

  @Test
  fun interactiveOptics_usesFusedBaseRendererBeforeInteractionStarts() =
    assertFusedRenderer(
      interactiveEffect().apply {
        optics = optics.copy(
          depth = OpticalSizeValue.Fixed(0.5f),
          blurRadius = OpticalSizeValue.Fixed(38.5.dp),
        )
      },
    ) { delegate ->
      assertThat(delegate.layers.hasDepthMixed).isFalse()
      assertThat(delegate.layers.hasBlurred).isFalse()
    }

  @Test
  fun singleFullChroma_usesFusedBaseRenderer() =
    assertFusedRenderer(
      retainedBlurEffect().apply {
        chromaticAberrationMode = ChromaticAberrationMode.Full
      },
    ) { delegate ->
      assertThat(delegate.layers.hasDepthMixed).isFalse()
      assertThat(delegate.layers.hasBlurred).isFalse()
    }

  @Test
  fun singleNoRefraction_usesFusedBaseRenderer() =
    assertFusedRenderer(retainedBlurEffect(refractionStrength = 0f)) { delegate ->
      assertThat(delegate.layers.refractionDetail).isNull()
    }

  @Test
  fun singleNoBlur_usesFusedBaseRenderer() =
    assertFusedRenderer(
      retainedBlurEffect().apply {
        optics = optics.copy(
          depth = OpticalSizeValue.Fixed(0f),
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        )
      },
    ) { delegate ->
      assertThat(delegate.layers.hasBlurred).isFalse()
    }

  @Test
  fun compatibleSiblingEffects_useIndependentFusedOutputsWithoutIntermediateStages() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effects = List(9) { retainedBlurEffect() }
      setContent { SiblingRuntimeGlassGridTestContent(effects) }
      waitForIdle()
      drawFrame()
      drawFrame()

      val delegates = effects.map { effect ->
        checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      }
      delegates.forEach { delegate ->
        assertThat(delegate.fusedShader).isNotNull()
        assertThat(delegate.layers.blurPrefiltered).isNull()
        assertThat(delegate.layers.blurHorizontal).isNull()
        assertThat(delegate.layers.blurred).isNull()
        assertThat(delegate.layers.depthMixed).isNull()
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.refractionDetailCoverage).isNull()
        assertThat(delegate.layers.refractionComposite).isNull()
        assertThat(delegate.layers.source?.renderEffect).isNull()
        assertThat(delegate.layers.optical?.renderEffect).isNotNull()
      }
      val blurRecordCounts = delegates.map { it.stageRecordCounts.blur }
      val detailRecordCounts = delegates.map { it.stageRecordCounts.detail }
      drawFrame()
      delegates.zip(blurRecordCounts).forEach { (delegate, count) ->
        assertThat(delegate.stageRecordCounts.blur).isEqualTo(count)
      }
      delegates.zip(detailRecordCounts).forEach { (delegate, count) ->
        assertThat(delegate.stageRecordCounts.detail).isEqualTo(count)
      }
    }

  @Test
  fun siblingEffectsWithDifferentOptics_useIndependentFusedLayers() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effects = listOf(
        retainedBlurEffect(refractionStrength = 0.4f),
        retainedBlurEffect(refractionStrength = 0.5f),
      )
      setContent { SiblingRuntimeGlassTestContent(effects) }
      waitForIdle()
      drawFrame()
      drawFrame()

      val delegates = effects.map { effect ->
        checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      }
      delegates.forEach { delegate ->
        assertThat(delegate.fusedShader).isNotNull()
        assertThat(delegate.layers.source?.renderEffect).isNull()
        assertThat(delegate.layers.optical?.renderEffect).isNotNull()
        assertThat(delegate.layers.refractionDetail).isNull()
      }
    }

  @Test
  fun siblingAttachment_preservesFusedOutputPixels() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effects = List(2) { retainedBlurEffect() }
      val attachSecond = mutableStateOf(false)
      setContent {
        SiblingRuntimeGlassComparisonContent(
          effects = effects,
          attachSecond = attachSecond.value,
        )
      }
      waitForIdle()
      drawFrame()
      val firstDelegate =
        checkNotNull(runtime(effects.first()).delegate as? RuntimeShaderGlassDelegate)
      val sourceLayer = checkNotNull(firstDelegate.layers.source)
      val fusedLayer = checkNotNull(firstDelegate.layers.optical)
      val dedicatedPixels = captureRegionPixels(
        left = 0,
        top = 0,
        width = 160,
        height = 120,
      )

      attachSecond.value = true
      waitForIdle()
      drawFrame()
      drawFrame()
      assertThat(firstDelegate.layers.source).isSameInstanceAs(sourceLayer)
      assertThat(firstDelegate.layers.optical).isSameInstanceAs(fusedLayer)
      assertThat(firstDelegate.layers.source?.renderEffect).isNull()
      assertThat(firstDelegate.layers.optical?.renderEffect).isNotNull()
      assertThat(firstDelegate.layers.refractionDetail).isNull()
      val siblingPixels = captureRegionPixels(
        left = 0,
        top = 0,
        width = 160,
        height = 120,
      )

      assertThat(siblingPixels).containsExactly(*dedicatedPixels)

      attachSecond.value = false
      waitForIdle()
      drawFrame()
      assertThat(firstDelegate.layers.source).isSameInstanceAs(sourceLayer)
      assertThat(firstDelegate.layers.optical).isSameInstanceAs(fusedLayer)
      assertThat(firstDelegate.layers.source?.renderEffect).isNull()
      assertThat(firstDelegate.layers.optical?.renderEffect).isNotNull()
      assertThat(firstDelegate.layers.refractionDetail).isNull()
      val restoredDedicatedPixels = captureRegionPixels(
        left = 0,
        top = 0,
        width = 160,
        height = 120,
      )
      assertThat(restoredDedicatedPixels).containsExactly(*dedicatedPixels)
    }

  @Test
  fun sizeChange_reusesFusedShaderAndMatchesFreshOutput() =
    runAndroidComposeUiTest<ComponentActivity> {
      val resizedEffect = retainedBlurEffect()
      val freshEffect = retainedBlurEffect()
      val size = mutableStateOf(120.dp)
      val showFresh = mutableStateOf(false)
      setContent {
        Row {
          RuntimeGlassTestContent(resizedEffect, size = size.value)
          if (showFresh.value) {
            RuntimeGlassTestContent(freshEffect, size = 100.dp)
          }
        }
      }
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(runtime(resizedEffect).delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(delegate.fusedShader)

      size.value = 100.dp
      showFresh.value = true
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      val resizedPixels = captureRegionPixels(left = 0, top = 0, width = 100, height = 100)
      val freshPixels = captureRegionPixels(left = 100, top = 0, width = 100, height = 100)
      assertThat(resizedPixels).containsExactly(*freshPixels)
    }

  @Test
  fun detachAndReattach_releasesAndRecreatesPlatformResources() =
    runAndroidComposeUiTest<ComponentActivity> {
      val callerInteractionSource = MutableInteractionSource()
      val callerShape = RoundedCornerShape(17.dp)
      val callerPositionAnimationSpec = tween<Offset>(37)
      val callerCompositionLocalStyle = GlassStyle { tint(Color.Red) }
      val effect = animatedStageEffect().apply {
        interactionSource = callerInteractionSource
        shape = callerShape
        style = GlassStyle {
          interactionPositionAnimationSpec(callerPositionAnimationSpec)
        }
      }
      val attached = mutableStateOf(true)
      setContent {
        CompositionLocalProvider(LocalGlassStyle provides callerCompositionLocalStyle) {
          RuntimeGlassTestContent(effect, attachEffect = attached.value)
        }
      }
      waitForIdle()
      drawFrame()
      val initialRuntime = runtime(effect)
      val delegate = checkNotNull(initialRuntime.delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(delegate.fusedShader)
      val detailShader = checkNotNull(delegate.refractionDetailShader)
      val rimShader = checkNotNull(delegate.rimShader)

      attached.value = false
      waitForIdle()
      drawFrame()

      assertThat(delegate.layers.hasSource).isFalse()
      assertThat(delegate.fusedShader).isNull()
      assertThat(delegate.opticalShader).isNull()
      assertThat(delegate.refractionDetailShader).isNull()
      assertThat(delegate.rimShader).isNull()
      assertThat(initialRuntime.interactionSource).isSameInstanceAs(callerInteractionSource)
      assertThat(initialRuntime.shape).isSameInstanceAs(callerShape)
      assertThat(initialRuntime.interactionPositionAnimationSpec)
        .isSameInstanceAs(callerPositionAnimationSpec)
      assertThat(initialRuntime.compositionLocalStyle)
        .isSameInstanceAs(callerCompositionLocalStyle)
      assertThat(initialRuntime.runtimeEffectFactory)
        .isSameInstanceAs(PlatformGlassRuntimeEffectFactory)
      assertThat(delegate.runtimeEffectFactoryForTest)
        .isSameInstanceAs(PlatformGlassRuntimeEffectFactory)

      attached.value = true
      waitForIdle()
      drawFrame()

      val reattachedRuntime = runtime(effect)
      val reattachedDelegate =
        checkNotNull(reattachedRuntime.delegate as? RuntimeShaderGlassDelegate)
      assertThat(reattachedRuntime).isSameInstanceAs(initialRuntime)
      assertThat(reattachedDelegate).isSameInstanceAs(delegate)
      assertThat(reattachedDelegate.fusedShader).isNotSameInstanceAs(fusedShader)
      assertThat(reattachedDelegate.opticalShader).isNull()
      assertThat(reattachedDelegate.refractionDetailShader).isNotSameInstanceAs(detailShader)
      assertThat(reattachedDelegate.rimShader).isNotSameInstanceAs(rimShader)
      assertThat(reattachedRuntime.interactionSource).isSameInstanceAs(callerInteractionSource)
      assertThat(reattachedRuntime.shape).isSameInstanceAs(callerShape)
      assertThat(reattachedRuntime.interactionPositionAnimationSpec)
        .isSameInstanceAs(callerPositionAnimationSpec)
      assertThat(reattachedRuntime.compositionLocalStyle)
        .isSameInstanceAs(callerCompositionLocalStyle)
      assertThat(reattachedDelegate.runtimeEffectFactoryForTest)
        .isSameInstanceAs(PlatformGlassRuntimeEffectFactory)
    }

  @Test
  fun progressiveConfiguration_detachReleasesAllPlatformResources() =
    runAndroidComposeUiTest<ComponentActivity> {
      val callerBrush = Brush.linearGradient(listOf(Color.Transparent, Color.Black))
      val effect = animatedStageEffect().apply {
        optics = optics.copy(
          progressive = HazeProgressive.Brush(callerBrush),
        )
      }
      val attached = mutableStateOf(true)
      setContent { RuntimeGlassTestContent(effect, attachEffect = attached.value) }
      waitForIdle()
      drawFrame()

      val initialDelegate =
        checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(initialDelegate.fusedShader)
      val detailShader = checkNotNull(initialDelegate.refractionDetailShader)
      val rimShader = checkNotNull(initialDelegate.rimShader)

      attached.value = false
      waitForIdle()

      assertThat(initialDelegate.fusedShader).isNull()
      assertThat(initialDelegate.refractionDetailShader).isNull()
      assertThat(initialDelegate.rimShader).isNull()

      attached.value = true
      waitForIdle()
      drawFrame()

      val reattachedDelegate =
        checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      assertThat(reattachedDelegate).isSameInstanceAs(initialDelegate)
      assertThat(reattachedDelegate.fusedShader).isNotSameInstanceAs(fusedShader)
      assertThat(reattachedDelegate.refractionDetailShader).isNotSameInstanceAs(detailShader)
      assertThat(reattachedDelegate.rimShader).isNotSameInstanceAs(rimShader)
    }

  @Test
  fun runtimeEffectFactoryChangeWhileDetached_usesReplacementFactoryOnReattach() =
    runAndroidComposeUiTest<ComponentActivity> {
      val initialFactory = GlassRuntimeEffectFactory { create -> create() }
      val effect = animatedStageEffect().apply { runtimeEffectFactory = initialFactory }
      val attached = mutableStateOf(true)
      setContent { RuntimeGlassTestContent(effect, attachEffect = attached.value) }
      waitForIdle()
      drawFrame()

      val initialDelegate =
        checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      val initialFusedShader = checkNotNull(initialDelegate.fusedShader)

      attached.value = false
      waitForIdle()
      assertThat(initialDelegate.fusedShader).isNull()
      assertThat(initialDelegate.runtimeEffectFactoryForTest)
        .isSameInstanceAs(initialFactory)
      val replacementFactory = GlassRuntimeEffectFactory { create -> create() }
      effect.runtimeEffectFactory = replacementFactory
      attached.value = true
      waitForIdle()
      drawFrame()

      val reattachedDelegate =
        checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      assertThat(reattachedDelegate).isNotSameInstanceAs(initialDelegate)
      assertThat(initialDelegate.fusedShader).isNull()
      assertThat(reattachedDelegate.fusedShader).isNotSameInstanceAs(initialFusedShader)
      assertThat(reattachedDelegate.runtimeEffectFactoryForTest)
        .isSameInstanceAs(replacementFactory)
    }

  @Test
  fun runtimeEffectFactoryChange_releasesReplacedDelegateShaderHandles() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val runtime = runtime(effect)
      val originalDelegate = checkNotNull(runtime.delegate as? RuntimeShaderGlassDelegate)
      assertThat(originalDelegate.fusedShader).isNotNull()

      effect.runtimeEffectFactory = GlassRuntimeEffectFactory { create -> create() }
      waitForIdle()
      drawFrame()

      assertThat(runtime.delegate).isNotSameInstanceAs(originalDelegate)
      assertThat(originalDelegate.fusedShader).isNull()
    }

  @Test
  fun liveUniformChanges_retainShadersAndReplaceRetainedLayerRenderEffects() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = animatedStageEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate) {
        val context = runtime(effect).attachedContextForTest
        "Expected runtime delegate; budget=${runtime(effect).preparedRenderBudget}, " +
          "prepared=${runtime(effect).preparedRender}, runtimeSupported=${isRuntimeShaderGlassSupported()}, " +
          "modifierSize=${context?.modifierSize}"
      }

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)
      effect.ambientResponse = 0.6f
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.source?.renderEffect).isNull()
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.opticalShader).isNull()

      val rimShader = delegate.rimShader
      val rimEffect = delegate.rimEffect
      val rimLayerEffect = checkNotNull(delegate.layers.rim?.renderEffect)
      effect.lightPosition = exactLightAlignment(Offset(10f, 20f))
      waitForIdle()
      drawFrame()

      assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
      assertThat(delegate.rimEffect).isNotSameInstanceAs(rimEffect)
      assertThat(delegate.layers.rim?.renderEffect).isNotSameInstanceAs(rimLayerEffect)
    }

  @Test
  fun activeInteraction_liveAndBaseUniformChangesRetainFusedShaderHandles() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = interactiveEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(delegate.fusedShader)
      val detailShader = checkNotNull(delegate.refractionDetailShader)

      runtime(effect).setPressedForTest(Offset(20f, 20f))
      waitForIdle()
      drawFrame()
      runtime(effect).setPressedForTest(Offset(80f, 60f))
      waitForIdle()
      drawFrame()
      effect.ambientResponse = 0.6f
      effect.optics = effect.optics.copy(refractionDisplacement = 18.dp)
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
      assertThat(delegate.layers.interactionLighting).isNotNull()
    }

  @Test
  fun interactionLighting_usesForegroundLayerWithOpaqueContent() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = interactiveEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      runtime(effect).setPressedForTest(Offset(60f, 60f))
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      assertThat(runtime(effect).currentInteractionState.hasLighting).isTrue()
      assertThat(delegate.layers.interactionLighting).isNotNull()
      assertThat(delegate.layers.interactionLighting?.renderEffect).isNotNull()
    }

  @Test
  fun fractionalAlpha_isAppliedToBaseGroupAndForegroundLighting() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = interactiveEffect().apply { alpha = 0.5f }
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      runtime(effect).setPressedForTest(Offset(60f, 60f))
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      assertThat(checkNotNull(delegate.layers.groupAlpha.layer).alpha).isEqualTo(0.5f)
      assertThat(checkNotNull(delegate.layers.interactionLighting).alpha).isEqualTo(0.5f)
    }

  @Test
  fun activeInteractionFrames_retainFusedShaderAndBaseLayer() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = interactiveEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      runtime(effect).setPressedForTest(Offset(20f, 20f))
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      val source = checkNotNull(delegate.layers.source)
      val optical = checkNotNull(delegate.layers.optical)
      val fusedShader = checkNotNull(delegate.fusedShader)
      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionRefractionDetailCoverage).isNull()
      assertThat(delegate.layers.interactionLighting).isNotNull()

      drawFrame()

      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionRefractionDetailCoverage).isNull()
      assertThat(delegate.layers.interactionLighting).isNotNull()
    }

  @Test
  fun largePanel_interactionPatchRetainsBaseLayersAcrossFrames() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = largePanelInteractiveEffect()
      setContent { RuntimeLargeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      mainClock.autoAdvance = false

      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
      val source = checkNotNull(delegate.layers.source)
      val optical = checkNotNull(delegate.layers.optical)
      assertThat(delegate.fusedShader).isNotNull()
      assertThat(delegate.layers.refractionDetail).isNull()
      val scaleFactor = (runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor
      val plannedKinds = checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }
      val positions = listOf(Offset(200f, 150f), Offset(400f, 240f), Offset(600f, 360f))

      runtime(effect).setPressedForTest(positions.first())
      drawInteractionFrame()

      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionLighting).isNotNull()
      val fusedShader = checkNotNull(delegate.fusedShader)

      positions.forEach { position ->
        runtime(effect).setPressedForTest(position)
        drawInteractionFrame()

        assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.interactionOptical).isNull()
        assertThat(delegate.layers.interactionRefractionDetail).isNull()
        assertThat(delegate.layers.interactionLighting).isNotNull()
        assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
        assertThat(
          (runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }

      runtime(effect).setPressedForTest(positions.last(), pressed = false)
      repeat(3) {
        drawInteractionFrame()

        assertThat(runtime(effect).currentInteractionState.hasLighting).isTrue()
        assertThat(runtime(effect).currentInteractionState.hasOptics).isTrue()
        assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.interactionOptical).isNull()
        assertThat(delegate.layers.interactionRefractionDetail).isNull()
        assertThat(delegate.layers.interactionLighting).isNotNull()
        assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
        assertThat(
          (runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }
      repeat(12) {
        drawInteractionFrame()

        assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.interactionOptical).isNull()
        assertThat(delegate.layers.interactionLighting).isNotNull()
        assertThat(
          (runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }
      assertThat(runtime(effect).currentInteractionState.hasLighting).isFalse()
      assertThat(runtime(effect).currentInteractionState.hasOptics).isFalse()
      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionLighting).isNotNull()
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
      mainClock.autoAdvance = true
    }

  @Test
  fun fusedOpticalUniformChanges_retainShaderAndReplaceRenderEffect() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)

      effect.optics = effect.optics.copy(
        refractionDisplacement = 18.dp,
      )
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.source?.renderEffect).isNull()
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.layers.blurHorizontal).isNull()
      assertThat(delegate.layers.blurred).isNull()
      assertThat(delegate.layers.refractionDetail).isNull()
    }

  @Test
  fun fusedBlurChanges_reuseShaderAndReplaceDepthInputGraph() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)
      effect.optics = effect.optics.copy(blurRadius = OpticalSizeValue.Fixed(36.dp))
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.layers.blurHorizontal).isNull()
      assertThat(delegate.layers.blurred).isNull()
      assertThat(delegate.layers.refractionDetail).isNull()
    }

  @Test
  fun progressiveBlurChanges_reuseShaderAndReplaceComposedInputGraph() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect(
        progressive = HazeProgressive.verticalGradient(
          startIntensity = 0f,
          endIntensity = 1f,
        ),
      )
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)

      effect.optics = effect.optics.copy(
        blurRadius = OpticalSizeValue.Fixed(34.dp),
        progressive = HazeProgressive.verticalGradient(
          startIntensity = 0.1f,
          endIntensity = 0.9f,
        ),
      )
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.source?.renderEffect).isNull()
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.layers.blurHorizontal).isNull()
      assertThat(delegate.layers.blurred).isNull()
    }

  private fun animatedStageEffect() = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      depth = OpticalSizeValue.Fixed(0.5f),
      blurRadius = OpticalSizeValue.Fixed(14.dp),
    )
    specularIntensity = 1f
    ambientResponse = 0.5f
    lightPosition = Alignment.Center
  }

  private fun interactiveEffect() = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      blurRadius = OpticalSizeValue.Fixed(0.dp),
    )
    specularIntensity = 0f
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }

  private fun largePanelInteractiveEffect() = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = 0.5f,
      refractionDisplacement = 20.dp,
      blurRadius = OpticalSizeValue.Fixed(0.dp),
    )
    specularIntensity = 0f
    pressed {
      animate(toSpec = tween(1), fromSpec = tween(160)) {
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

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
    refractionStrength: Float = 0.5f,
  ) = GlassRuntimeEffect().apply {
    optics = GlassOptics(
      refractionStrength = refractionStrength,
      refractionDisplacement = 20.dp,
      depth = OpticalSizeValue.Fixed(0.5f),
      blurRadius = OpticalSizeValue.Fixed(38.5.dp),
      progressive = progressive,
    )
    specularIntensity = 0f
  }

  @Composable
  private fun RuntimeGlassTestContent(
    effect: GlassRuntimeEffect,
    attachEffect: Boolean = true,
    size: Dp = 120.dp,
  ) {
    Box(
      Modifier
        .size(size)
        .then(
          if (attachEffect) {
            Modifier.testGlassRuntime(effect, HazeInput.Content)
          } else {
            Modifier
          },
        ),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  @Composable
  private fun RuntimeLargeGlassTestContent(effect: GlassRuntimeEffect) {
    Box(
      Modifier
        .size(width = 800.dp, height = 480.dp)
        .testGlassRuntime(effect, HazeInput.Content),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  @Composable
  private fun SiblingRuntimeGlassTestContent(effects: List<GlassRuntimeEffect>) {
    val hazeState = rememberHazeState()
    Box(Modifier.size(width = 360.dp, height = 120.dp)) {
      Box(
        Modifier
          .fillMaxSize()
          .background(Color.Red)
          .hazeSource(hazeState),
      )
      Row(Modifier.fillMaxSize()) {
        effects.forEach { effect ->
          Box(
            Modifier
              .size(120.dp)
              .testGlassRuntime(effect, HazeInput.Sources(hazeState)),
          )
        }
      }
    }
  }

  @Composable
  private fun SiblingRuntimeGlassGridTestContent(effects: List<GlassRuntimeEffect>) {
    require(effects.size == 9)
    val hazeState = rememberHazeState()
    Box(Modifier.size(300.dp)) {
      Box(
        Modifier
          .fillMaxSize()
          .background(Color.Red)
          .hazeSource(hazeState),
      )
      Column {
        repeat(3) { row ->
          Row {
            repeat(3) { column ->
              val effect = effects[row * 3 + column]
              Box(
                Modifier
                  .size(100.dp)
                  .testGlassRuntime(effect, HazeInput.Sources(hazeState)),
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun SiblingRuntimeGlassComparisonContent(
    effects: List<GlassRuntimeEffect>,
    attachSecond: Boolean,
  ) {
    val hazeState = rememberHazeState()
    Box(Modifier.size(width = 300.dp, height = 100.dp)) {
      Row(
        Modifier
          .fillMaxSize()
          .hazeSource(hazeState),
      ) {
        listOf(
          Color.Red,
          Color.Green,
          Color.Blue,
          Color.Yellow,
          Color.Magenta,
          Color.Cyan,
        ).forEach { color ->
          Box(Modifier.size(width = 50.dp, height = 100.dp).background(color))
        }
      }
      Box(
        Modifier
          .offset(x = 50.dp)
          .size(100.dp)
          .testGlassRuntime(effects[0], HazeInput.Sources(hazeState)),
      )
      if (attachSecond) {
        Box(
          Modifier
            .offset(x = 180.dp)
            .size(100.dp)
            .testGlassRuntime(effects[1], HazeInput.Sources(hazeState)),
        )
      }
    }
  }

  @Composable
  private fun Modifier.testGlassRuntime(
    effect: GlassRuntimeEffect,
    input: HazeInput,
  ): Modifier {
    val factory = remember(effect) { FixedGlassRuntimeFactory(effect) }
    return hazeGlass(
      factory = factory,
      input = input,
      style = effect.style,
      performanceMode = HazePerformanceMode.Quality,
      expandLayerBounds = true,
      interactionSource = effect.interactionSource,
      interactionTransformTarget = effect.interactionTransformTarget,
      interactionTransformPivot = effect.interactionTransformPivot,
      interactionReducedMotionPolicy = effect.interactionReducedMotionPolicy,
    )
  }

  private fun AndroidComposeUiTest<ComponentActivity>.drawFrame() {
    captureFrame().recycle()
  }

  private fun runtime(effect: GlassRuntimeEffect): GlassRuntimeEffect = effect

  private fun assertFusedRenderer(
    effect: GlassRuntimeEffect,
    assertions: (RuntimeShaderGlassDelegate) -> Unit,
  ) = runAndroidComposeUiTest<ComponentActivity> {
    setContent { RuntimeGlassTestContent(effect) }
    waitForIdle()
    drawFrame()

    val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)
    assertThat(delegate.fusedShader).isNotNull()
    assertThat(delegate.layers.source?.renderEffect).isNull()
    assertThat(delegate.layers.optical?.renderEffect).isNotNull()
    assertions(delegate)
  }

  private fun AndroidComposeUiTest<ComponentActivity>.captureRegionPixels(
    left: Int,
    top: Int,
    width: Int,
    height: Int,
  ): IntArray {
    val bitmap = captureFrame()
    return try {
      IntArray(width * height).also { pixels ->
        bitmap.getPixels(pixels, 0, width, left, top, width, height)
      }
    } finally {
      bitmap.recycle()
    }
  }

  private fun AndroidComposeUiTest<ComponentActivity>.captureFrame(): Bitmap =
    runOnIdle {
      val view = checkNotNull(activity).window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      view.draw(Canvas(bitmap))
      bitmap
    }

  private fun AndroidComposeUiTest<ComponentActivity>.drawInteractionFrame() {
    mainClock.advanceTimeByFrame()
    mainClock.advanceTimeByFrame()
    waitForIdle()
    drawFrame()
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandle(fieldName: String): Any =
    checkNotNull(interactionField(fieldName))

  private fun RuntimeShaderGlassDelegate.interactionField(fieldName: String): Any? {
    val field = RuntimeShaderGlassDelegate::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this)
  }

  private fun RuntimeShaderGlassDelegate.recordedInteractionLayer(fieldName: String): GraphicsLayer? =
    interactionField(fieldName) as? GraphicsLayer
}

private class FixedGlassRuntimeFactory(
  private val effect: GlassRuntimeEffect,
) : HazeEffectFactory<GlassNodeConfiguration> {
  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> = effect
}
