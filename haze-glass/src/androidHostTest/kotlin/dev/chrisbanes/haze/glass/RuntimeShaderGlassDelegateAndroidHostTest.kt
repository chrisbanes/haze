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
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RuntimeShaderGlassDelegateAndroidHostTest : ContextTest() {

  @Test
  fun standardBlur_avoidsDedicatedDepthLayer() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      assertFalse(delegate.layers.hasDepthMixed)
      assertTrue(delegate.layers.hasBlurPrefiltered)
      assertTrue(delegate.layers.hasBlurHorizontal)
      assertTrue(delegate.layers.hasBlurred)
      assertTrue(delegate.layers.hasOptical)
    }

  @Test
  fun progressiveBlur_preservesDedicatedDepthLayer() =
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
      assertTrue(delegate.layers.hasDepthMixed)
      assertTrue(delegate.layers.hasBlurHorizontal)
      assertTrue(delegate.layers.hasBlurred)
      assertTrue(delegate.layers.hasOptical)
    }

  @Test
  fun compatibleSiblingEffects_shareSourceBlur() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effects = List(9) { retainedBlurEffect() }
      setContent { SharedRuntimeGlassGridTestContent(effects) }
      waitForIdle()
      drawFrame()
      drawFrame()

      val delegates = effects.map { effect ->
        checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      }
      assertTrue(delegates.all(RuntimeShaderGlassDelegate::usesSharedBlurForTest))
      assertTrue(delegates.all { !it.layers.hasBlurPrefiltered })
      assertTrue(delegates.all { !it.layers.hasBlurHorizontal })
      assertTrue(delegates.all { it.layers.hasBlurred })
      val sharedBlurred = checkNotNull(delegates.first().sharedBlurredLayerForTest)
      delegates.drop(1).forEach { delegate ->
        assertSame(sharedBlurred, delegate.sharedBlurredLayerForTest)
      }
      val sharedOptical = checkNotNull(delegates.first().sharedOpticalLayerForTest)
      assertTrue(delegates.all { it.layers.optical?.renderEffect == null })
      delegates.drop(1).forEach { delegate ->
        assertSame(sharedOptical, delegate.sharedOpticalLayerForTest)
      }
      val sharedRefractionDetail = checkNotNull(
        delegates.first().sharedRefractionDetailLayerForTest,
      ) {
        "Expected shared detail atlas; keys=${effects.map { it.preparedRender?.refractionDetailKey }}"
      }
      assertTrue(delegates.all { it.layers.refractionDetail?.renderEffect == null })
      delegates.drop(1).forEach { delegate ->
        assertSame(sharedRefractionDetail, delegate.sharedRefractionDetailLayerForTest)
      }

      val blurRecordCounts = delegates.map { it.stageRecordCounts.blur }
      val detailRecordCounts = delegates.map { it.stageRecordCounts.detail }
      drawFrame()
      assertTrue(
        delegates.zip(blurRecordCounts).all { (delegate, count) ->
          delegate.stageRecordCounts.blur == count
        },
      )
      assertTrue(
        delegates.zip(detailRecordCounts).all { (delegate, count) ->
          delegate.stageRecordCounts.detail == count
        },
      )
    }

  @Test
  fun incompatibleSiblingEffects_keepDedicatedRefractionDetailPasses() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effects = listOf(
        retainedBlurEffect(refractionStrength = 0.4f),
        retainedBlurEffect(refractionStrength = 0.5f),
      )
      setContent { SharedRuntimeGlassTestContent(effects) }
      waitForIdle()
      drawFrame()
      drawFrame()

      val delegates = effects.map { effect ->
        checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      }
      assertTrue(delegates.all(RuntimeShaderGlassDelegate::usesSharedBlurForTest))
      assertTrue(delegates.all { it.sharedOpticalLayerForTest == null })
      assertTrue(delegates.all { it.layers.optical?.renderEffect != null })
      assertTrue(delegates.all { it.sharedRefractionDetailLayerForTest == null })
      assertTrue(delegates.all { it.layers.refractionDetail?.renderEffect != null })
    }

  @Test
  fun sharedRefractionDetail_preservesDedicatedPassPixels() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effects = List(2) { retainedBlurEffect() }
      val attachSecond = mutableStateOf(false)
      setContent {
        SharedRuntimeGlassComparisonContent(
          effects = effects,
          attachSecond = attachSecond.value,
        )
      }
      waitForIdle()
      drawFrame()
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
      val firstDelegate = checkNotNull(effects.first().delegate as? RuntimeShaderGlassDelegate)
      assertTrue(firstDelegate.usesSharedBlurForTest)
      assertTrue(firstDelegate.layers.optical?.renderEffect == null)
      assertTrue(firstDelegate.layers.refractionDetail?.renderEffect == null)
      val sharedPixels = captureRegionPixels(
        left = 0,
        top = 0,
        width = 160,
        height = 120,
      )

      assertContentEquals(dedicatedPixels, sharedPixels)

      attachSecond.value = false
      waitForIdle()
      drawFrame()
      assertFalse(firstDelegate.usesSharedBlurForTest)
      assertTrue(firstDelegate.layers.optical?.renderEffect != null)
      assertTrue(firstDelegate.layers.refractionDetail?.renderEffect != null)
      val restoredDedicatedPixels = captureRegionPixels(
        left = 0,
        top = 0,
        width = 160,
        height = 120,
      )
      assertContentEquals(dedicatedPixels, restoredDedicatedPixels)
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
      val opticalShader = checkNotNull(delegate.opticalShader)
      val detailShader = checkNotNull(delegate.refractionDetailShader)
      val rimShader = checkNotNull(delegate.rimShader)

      attached.value = false
      waitForIdle()
      drawFrame()

      assertFalse(delegate.layers.hasSource)
      assertSame(opticalShader, delegate.opticalShader)
      assertSame(detailShader, delegate.refractionDetailShader)
      assertSame(rimShader, delegate.rimShader)

      attached.value = true
      waitForIdle()
      drawFrame()

      assertSame(opticalShader, delegate.opticalShader)
      assertSame(detailShader, delegate.refractionDetailShader)
      assertSame(rimShader, delegate.rimShader)
    }

  @Test
  fun liveUniformChanges_retainShadersAndReplaceOnlyAffectedRenderEffectWrappers() =
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

      val opticalShader = delegate.opticalShader
      val opticalEffect = delegate.opticalEffect
      effect.ambientResponse = 0.6f
      waitForIdle()
      drawFrame()

      assertThat(delegate.opticalShader).isSameInstanceAs(opticalShader)
      assertThat(delegate.opticalEffect).isNotSameInstanceAs(opticalEffect)

      val rimShader = delegate.rimShader
      val rimEffect = delegate.rimEffect
      effect.lightPosition = Offset(10f, 20f)
      waitForIdle()
      drawFrame()

      assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
      assertThat(delegate.rimEffect).isNotSameInstanceAs(rimEffect)
    }

  @Test
  fun activeInteraction_liveAndBaseUniformChangesRetainInteractionShaderHandles() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = interactiveEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()

      effect.setPressedForTest(Offset(20f, 20f))
      waitForIdle()
      drawFrame()

      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      val opticalEffect = delegate.interactionShaderHandle("interactionOpticalEffect")
      val detailEffect = delegate.interactionShaderHandle("interactionDetailEffect")
      val lightingEffect = delegate.interactionShaderHandle("interactionLightingEffect")

      effect.setPressedForTest(Offset(80f, 60f))
      waitForIdle()
      drawFrame()
      effect.ambientResponse = 0.6f
      effect.optics = (effect.optics as GlassOptics.Absolute).copy(refractionScale = 18f)
      waitForIdle()
      drawFrame()

      assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
        .isSameInstanceAs(opticalEffect)
      assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
        .isSameInstanceAs(detailEffect)
      assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
        .isSameInstanceAs(lightingEffect)
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
      val detail = checkNotNull(delegate.layers.refractionDetail)
      val scaleFactor = (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor
      val plannedKinds = checkNotNull(effect.preparedRender).plan.layers.map { it.kind }
      val positions = listOf(Offset(200f, 150f), Offset(400f, 240f), Offset(600f, 360f))

      effect.setPressedForTest(positions.first())
      drawInteractionFrame()

      val interactionOptical = checkNotNull(delegate.layers.interactionOptical)
      val interactionDetail = checkNotNull(delegate.layers.interactionRefractionDetail)
      val interactionLighting = checkNotNull(delegate.layers.interactionLighting)
      val interactionOpticalShader = delegate.interactionShaderHandle("interactionOpticalEffect")
      val interactionDetailShader = delegate.interactionShaderHandle("interactionDetailEffect")
      val interactionLightingShader = delegate.interactionShaderHandle("interactionLightingEffect")

      positions.forEach { position ->
        effect.setPressedForTest(position)
        drawInteractionFrame()

        assertThat(effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
        assertThat(delegate.layers.interactionOptical).isSameInstanceAs(interactionOptical)
        assertThat(delegate.layers.interactionRefractionDetail).isSameInstanceAs(interactionDetail)
        assertThat(delegate.layers.interactionLighting).isSameInstanceAs(interactionLighting)
        assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
          .isSameInstanceAs(interactionOpticalShader)
        assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
          .isSameInstanceAs(interactionDetailShader)
        assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
          .isSameInstanceAs(interactionLightingShader)
        assertThat(
          (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
        listOf(interactionOptical, interactionDetail, interactionLighting).forEach { layer ->
          assertThat(layer.size.width).isLessThan(source.size.width)
          assertThat(layer.size.height).isLessThan(source.size.height)
        }
      }

      effect.setPressedForTest(positions.last(), pressed = false)
      repeat(3) {
        drawInteractionFrame()

        assertThat(effect.currentInteractionState.hasLighting).isTrue()
        assertThat(effect.currentInteractionState.hasOptics).isTrue()
        assertThat(effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
        assertThat(delegate.layers.interactionOptical).isSameInstanceAs(interactionOptical)
        assertThat(delegate.layers.interactionRefractionDetail).isSameInstanceAs(interactionDetail)
        assertThat(delegate.layers.interactionLighting).isSameInstanceAs(interactionLighting)
        assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
          .isSameInstanceAs(interactionOpticalShader)
        assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
          .isSameInstanceAs(interactionDetailShader)
        assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
          .isSameInstanceAs(interactionLightingShader)
        assertThat(
          (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
        listOf(interactionOptical, interactionDetail, interactionLighting).forEach { layer ->
          assertThat(layer.size.width).isLessThan(source.size.width)
          assertThat(layer.size.height).isLessThan(source.size.height)
        }
      }
      repeat(12) {
        drawInteractionFrame()

        assertThat(effect.delegate).isSameInstanceAs(delegate)
        assertThat(delegate.layers.source).isSameInstanceAs(source)
        assertThat(delegate.layers.optical).isSameInstanceAs(optical)
        assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
        assertThat(
          (effect.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
        ).isEqualTo(scaleFactor)
        assertThat(checkNotNull(effect.preparedRender).plan.layers.map { it.kind })
          .isEqualTo(plannedKinds)
      }
      assertThat(effect.currentInteractionState.hasLighting).isFalse()
      assertThat(effect.currentInteractionState.hasOptics).isFalse()
      assertThat(delegate.layers.interactionOptical?.isReleased != false).isTrue()
      assertThat(delegate.layers.interactionRefractionDetail?.isReleased != false).isTrue()
      assertThat(delegate.layers.interactionLighting?.isReleased != false).isTrue()
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
      mainClock.autoAdvance = true
    }

  @Test
  fun uniformBlurAndDetailChanges_retainShadersAndReplaceRenderEffects() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = retainedBlurEffect()
      setContent { RuntimeGlassTestContent(effect) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)

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
      drawFrame()

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
  fun progressiveBlurChanges_retainShadersAndReplaceRenderEffects() =
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
      drawFrame()

      assertThat(delegate.progressiveBlurHorizontalShader).isSameInstanceAs(horizontalShader)
      assertThat(delegate.progressiveBlurVerticalShader).isSameInstanceAs(verticalShader)
      assertThat(delegate.layers.blurHorizontal?.renderEffect).isNotSameInstanceAs(horizontalEffect)
      assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(verticalEffect)
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
  private fun SharedRuntimeGlassTestContent(effects: List<GlassVisualEffect>) {
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
  private fun SharedRuntimeGlassGridTestContent(effects: List<GlassVisualEffect>) {
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
  private fun SharedRuntimeGlassComparisonContent(
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

  private fun RuntimeShaderGlassDelegate.interactionShaderHandle(fieldName: String): Any {
    val field = RuntimeShaderGlassDelegate::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return checkNotNull(field.get(this))
  }
}
