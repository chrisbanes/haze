// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RuntimeShaderGlassDelegateAndroidHostTest : ContextTest() {

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
        optics = (optics as GlassOptics.Absolute).copy(
          depth = 0.5f,
          blurRadius = 38.5.dp,
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
        optics = (optics as GlassOptics.Absolute).copy(
          depth = 0f,
          blurRadius = 0.dp,
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
        checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
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
        checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
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
      val firstDelegate = checkNotNull(effects.first().delegate as? RuntimeShaderGlassDelegate)
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
  fun detachAndReattach_releasesLayersAndRetainsShaderHandles() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = animatedStageEffect()
      val attached = mutableStateOf(true)
      setContent { RuntimeGlassTestContent(effect, attachEffect = attached.value) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(delegate.fusedShader)
      val detailShader = checkNotNull(delegate.refractionDetailShader)
      val rimShader = checkNotNull(delegate.rimShader)

      attached.value = false
      waitForIdle()
      drawFrame()

      assertThat(delegate.layers.hasSource).isFalse()
      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.opticalShader).isNull()
      assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
      assertThat(delegate.rimShader).isSameInstanceAs(rimShader)

      attached.value = true
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.opticalShader).isNull()
      assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
      assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
    }

  @Test
  fun liveUniformChanges_retainShadersAndReplaceRetainedLayerRenderEffects() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = animatedStageEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate) {
        val context = effect.attachedContextForTest
        "Expected runtime delegate; budget=${effect.preparedRenderBudget}, " +
          "prepared=${effect.preparedRender}, runtimeSupported=${isRuntimeShaderGlassSupported()}, " +
          "size=${context?.size}, layerSize=${context?.layerSize}, inputScale=${context?.inputScale}"
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
      effect.lightPosition = Offset(10f, 20f)
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

      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(delegate.fusedShader)
      val detailShader = checkNotNull(delegate.refractionDetailShader)

      effect.setPressedForTest(Offset(20f, 20f))
      waitForIdle()
      drawFrame()
      effect.setPressedForTest(Offset(80f, 60f))
      waitForIdle()
      drawFrame()
      effect.ambientResponse = 0.6f
      effect.optics = (effect.optics as GlassOptics.Absolute).copy(refractionScale = 18f)
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

      effect.setPressedForTest(Offset(60f, 60f))
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      assertThat(effect.currentInteractionState.hasLighting).isTrue()
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

      effect.setPressedForTest(Offset(60f, 60f))
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
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

      effect.setPressedForTest(Offset(20f, 20f))
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
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

      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      val source = checkNotNull(delegate.layers.source)
      val optical = checkNotNull(delegate.layers.optical)
      assertThat(delegate.fusedShader).isNotNull()
      assertThat(delegate.layers.refractionDetail).isNull()
      val scaleFactor = (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor
      val plannedKinds = checkNotNull(effect.preparedRender).plan.layers.map { it.kind }
      val positions = listOf(Offset(200f, 150f), Offset(400f, 240f), Offset(600f, 360f))

      effect.setPressedForTest(positions.first())
      drawInteractionFrame()

      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionLighting).isNotNull()
      val fusedShader = checkNotNull(delegate.fusedShader)

      positions.forEach { position ->
        effect.setPressedForTest(position)
        drawInteractionFrame()

        assertThat(effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.interactionOptical).isNull()
        assertThat(delegate.layers.interactionRefractionDetail).isNull()
        assertThat(delegate.layers.interactionLighting).isNotNull()
        assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
        assertThat(
          (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }

      effect.setPressedForTest(positions.last(), pressed = false)
      repeat(3) {
        drawInteractionFrame()

        assertThat(effect.currentInteractionState.hasLighting).isTrue()
        assertThat(effect.currentInteractionState.hasOptics).isTrue()
        assertThat(effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.interactionOptical).isNull()
        assertThat(delegate.layers.interactionRefractionDetail).isNull()
        assertThat(delegate.layers.interactionLighting).isNotNull()
        assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
        assertThat(
          (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }
      repeat(12) {
        drawInteractionFrame()

        assertThat(effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isNull()
        assertThat(delegate.layers.interactionOptical).isNull()
        assertThat(delegate.layers.interactionLighting).isNotNull()
        assertThat(
          (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }
      assertThat(effect.currentInteractionState.hasLighting).isFalse()
      assertThat(effect.currentInteractionState.hasOptics).isFalse()
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
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)

      effect.optics = (effect.optics as GlassOptics.Absolute).copy(
        refractionScale = 18f,
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
  fun fusedBlurChanges_replaceDepthInputGraph() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      effect.optics = (effect.optics as GlassOptics.Absolute).copy(blurRadius = 36.dp)
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isNotSameInstanceAs(fusedShader)
      assertThat(delegate.layers.blurHorizontal).isNull()
      assertThat(delegate.layers.blurred).isNull()
      assertThat(delegate.layers.refractionDetail).isNull()
    }

  @Test
  fun progressiveBlurChanges_replaceComposedInputGraph() =
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
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)

      effect.optics = (effect.optics as GlassOptics.Absolute).copy(
        blurRadius = 34.dp,
        progressive = HazeProgressive.verticalGradient(
          startIntensity = 0.1f,
          endIntensity = 0.9f,
        ),
      )
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isNotSameInstanceAs(fusedShader)
      assertThat(delegate.layers.source?.renderEffect).isNull()
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.layers.blurHorizontal).isNull()
      assertThat(delegate.layers.blurred).isNull()
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

  private fun interactiveEffect() = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = 0.5f,
      refractionScale = 20f,
      blurRadius = 0.dp,
    )
    specularIntensity = 0f
    pressed {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      whitePointDelta(0.04f)
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }

  private fun largePanelInteractiveEffect() = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = 0.5f,
      refractionScale = 20f,
      blurRadius = 0.dp,
    )
    specularIntensity = 0f
    pressed {
      animate(toSpec = tween(1), fromSpec = tween(160)) {
        lightingIntensity(1f)
        refractionMultiplier(1.08f)
        whitePointDelta(0.04f)
      }
    }
    interactionLightRadiusFraction = 0.25f
    interactionPositionAnimationSpec = tween(1)
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  }

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
    refractionStrength: Float = 0.5f,
  ) = GlassVisualEffect().apply {
    optics = GlassOptics.Absolute(
      refractionStrength = refractionStrength,
      refractionScale = 20f,
      depth = 0.5f,
      blurRadius = 38.5.dp,
      progressive = progressive,
    )
    specularIntensity = 0f
  }

  @Composable
  private fun RuntimeGlassTestContent(
    effect: GlassVisualEffect,
    attachEffect: Boolean = true,
  ) {
    Box(
      Modifier
        .size(120.dp)
        .then(
          if (attachEffect) {
            Modifier.hazeEffect {
              inputScale = HazeInputScale.None
              visualEffect = effect
            }
          } else {
            Modifier
          },
        ),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  @Composable
  private fun RuntimeLargeGlassTestContent(effect: GlassVisualEffect) {
    Box(
      Modifier
        .size(width = 800.dp, height = 480.dp)
        .hazeEffect {
          inputScale = HazeInputScale.None
          visualEffect = effect
        },
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  @Composable
  private fun SiblingRuntimeGlassTestContent(effects: List<GlassVisualEffect>) {
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
              .hazeEffect(hazeState) {
                inputScale = HazeInputScale.None
                visualEffect = effect
              },
          )
        }
      }
    }
  }

  @Composable
  private fun SiblingRuntimeGlassGridTestContent(effects: List<GlassVisualEffect>) {
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
                  .hazeEffect(hazeState) {
                    inputScale = HazeInputScale.None
                    visualEffect = effect
                  },
              )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun SiblingRuntimeGlassComparisonContent(
    effects: List<GlassVisualEffect>,
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
          .hazeEffect(hazeState) {
            inputScale = HazeInputScale.None
            visualEffect = effects[0]
          },
      )
      if (attachSecond) {
        Box(
          Modifier
            .offset(x = 180.dp)
            .size(100.dp)
            .hazeEffect(hazeState) {
              inputScale = HazeInputScale.None
              visualEffect = effects[1]
            },
        )
      }
    }
  }

  private fun AndroidComposeUiTest<ComponentActivity>.drawFrame() {
    captureFrame().recycle()
  }

  private fun assertFusedRenderer(
    effect: GlassVisualEffect,
    assertions: (RuntimeShaderGlassDelegate) -> Unit,
  ) = runAndroidComposeUiTest<ComponentActivity> {
    setContent { RuntimeGlassTestContent(effect) }
    waitForIdle()
    drawFrame()

    val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
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
