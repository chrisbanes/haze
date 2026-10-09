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
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
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
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
  fun destinationCanvasChanges_refreshRetainedRimWithoutChangingStyle() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = animatedStageEffect()
      val factory = TestGlassRuntimeFactory(effect)
      setContent {
        Box(
          Modifier.size(120.dp).testTag("canvas-transition").hazeEffect(
            factory = factory,
            input = HazeInput.Content,
            style = GlassNodeConfiguration(
              style = effect.style,
              performanceMode = HazePerformanceMode.Quality,
              interactionSource = effect.interactionSource,
            ),
            expandLayerBounds = true,
          ),
        ) {
          Box(Modifier.fillMaxSize().background(Color.Red))
        }
      }
      waitForIdle()
      onNodeWithTag("canvas-transition").captureToImage()
      val delegate = effect.delegate as RuntimeShaderGlassDelegate
      val rim = checkNotNull(delegate.layers.rim)
      assertThat(rim.renderEffect).isNull()

      factory.renderer.foregroundCanvas = androidx.compose.ui.graphics.Canvas(ImageBitmap(400, 400))
      factory.renderer.invalidateDraw()
      waitForIdle()
      onNodeWithTag("canvas-transition").captureToImage()
      assertThat(delegate.layers.rim).isSameInstanceAs(rim)
      assertThat(rim.renderEffect).isNotNull()

      factory.renderer.foregroundCanvas = null
      factory.renderer.invalidateDraw()
      waitForIdle()
      onNodeWithTag("canvas-transition").captureToImage()
      assertThat(delegate.layers.rim).isSameInstanceAs(rim)
      assertThat(rim.renderEffect).isNull()
    }

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
        style = style.then {
          optics(
            optics.copy(
              depth = OpticalSizeValue.Fixed(0.5f),
              blurRadius = OpticalSizeValue.Fixed(38.5.dp),
            ),
          )
        }
      },
    ) { delegate ->
      assertThat(delegate.layers.hasDepthMixed).isFalse()
      assertThat(delegate.layers.hasBlurred).isFalse()
    }

  @Test
  fun singleFullChroma_usesFusedBaseRenderer() =
    assertFusedRenderer(
      retainedBlurEffect().apply {
        style = style.then { chromaticAberrationMode(ChromaticAberrationMode.Full) }
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
        style = style.then {
          optics(
            optics.copy(
              depth = OpticalSizeValue.Fixed(0f),
              blurRadius = OpticalSizeValue.Fixed(0.dp),
            ),
          )
        }
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
      fun newEffect() = retainedBlurEffect().apply {
        style = style.then { tint(Color.Blue.copy(alpha = 0.75f)) }
      }
      val displayed = mutableStateOf(newEffect())
      val size = mutableStateOf(120.dp)
      setContent { RuntimeGlassTestContent(displayed.value, size = size.value, measureStableInputReuse = true) }
      waitForIdle()
      drawFrame()
      val freshPixels = mutableMapOf<Int, IntArray>()
      for (target in listOf(180, 100)) {
        runOnIdle {
          size.value = target.dp
          displayed.value = newEffect()
        }
        waitForIdle()
        freshPixels[target] = captureRegionPixels(0, 0, target, target)
        // A missing material cannot pass simply by showing its authored red content.
        assertThat(checkNotNull(freshPixels[target])[target * (target / 2) + target / 2]).isNotEqualTo(android.graphics.Color.RED)
      }
      val resizedEffect = newEffect()
      runOnIdle {
        size.value = 120.dp
        displayed.value = resizedEffect
      }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(resizedEffect).delegate as? RuntimeShaderGlassDelegate)
      val fusedShader = checkNotNull(delegate.fusedShader)
      val active = listOfNotNull(delegate.layers.source, delegate.layers.optical, delegate.layers.rim, delegate.layers.groupAlpha.layer)
      active.forEach { it.outline.bounds }
      for (target in listOf(180, 100, 180)) {
        val before = delegate.stageRecordCounts
        runOnIdle { size.value = target.dp }
        waitForIdle()
        val resizedPixels = captureRegionPixels(0, 0, target, target)
        assertThat(resizedPixels).containsExactly(*checkNotNull(freshPixels[target]))
        assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
        val current = listOfNotNull(delegate.layers.source, delegate.layers.optical, delegate.layers.rim, delegate.layers.groupAlpha.layer)
        assertThat(current.size).isEqualTo(active.size)
        current.zip(active).forEach { (layer, original) ->
          assertThat(layer).isSameInstanceAs(original)
          assertThat(layer.isReleased).isFalse()
        }
        val recorded = delegate.stageRecordCounts
        assertThat(recorded.source).isGreaterThan(before.source)
        assertThat(recorded.optical).isGreaterThan(before.optical)
        repeat(2) {
          runOnIdle { checkNotNull(resizedEffect.attachedContextForTest).invalidateDraw() }
          waitForIdle()
          assertThat(captureRegionPixels(0, 0, target, target)).containsExactly(*checkNotNull(freshPixels[target]))
          // Content capture may supply a new input generation; stable-input reuse is
          // asserted inside the renderer callback before that snapshot can change.
        }
      }
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
        style = style.then { shape(callerShape) }
        style = style.then {
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
      val rimBrushProvider = checkNotNull(delegate.rimBrushProvider)

      attached.value = false
      waitForIdle()
      drawFrame()

      assertThat(delegate.layers.hasSource).isFalse()
      assertThat(delegate.fusedShader).isNull()
      assertThat(delegate.opticalShader).isNull()
      assertThat(delegate.refractionDetailShader).isNull()
      assertThat(delegate.rimShader).isNull()
      assertThat(delegate.rimBrushProvider).isNull()
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
      assertThat(reattachedDelegate.rimBrushProvider).isNotSameInstanceAs(rimBrushProvider)
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
        style = style.then {
          optics(
            optics.copy(
              progressive = HazeProgressive.Brush(callerBrush),
            ),
          )
        }
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
  fun liveUniformChanges_retainShadersAndRefreshRecordedOutput() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = animatedStageEffect()
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, style = style.value) }
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
      style.value = style.value.then { ambientResponse(0.6f) }
      waitForIdle()
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.source?.renderEffect).isNull()
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.opticalShader).isNull()

      val rimShader = delegate.rimShader
      val rimEffect = delegate.rimEffect
      val rimBrushProvider = checkNotNull(delegate.rimBrushProvider)
      val rimRecordCount = delegate.rimRecordCount
      // drawFrame uses a software bitmap canvas, so the rim retains its RenderEffect fallback.
      assertThat(delegate.layers.rim?.renderEffect).isNotNull()
      style.value = style.value.then { lightPosition(exactLightAlignment(Offset(10f, 20f))) }
      waitForIdle()
      drawFrame()

      assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
      assertThat(delegate.rimEffect).isNotSameInstanceAs(rimEffect)
      assertThat(delegate.rimBrushProvider).isSameInstanceAs(rimBrushProvider)
      assertThat(delegate.rimRecordCount).isGreaterThan(rimRecordCount)
      assertThat(delegate.layers.rim?.renderEffect).isNotNull()
    }

  @Test
  fun backdropResize_reusesRimAndMatchesFreshPixels() = runAndroidComposeUiTest<ComponentActivity> {
    val style = animatedStageEffect().style
    val displayed = mutableStateOf(TestGlassRuntimeFactory(GlassRuntimeEffect()))
    val size = mutableStateOf(120.dp)
    setContent {
      Box(
        Modifier.size(size.value).testTag("direct-glass").hazeEffect(
          factory = displayed.value,
          input = HazeInput.Content,
          style = GlassNodeConfiguration(style, performanceMode = HazePerformanceMode.Quality, interactionSource = null),
          expandLayerBounds = true,
        ),
      ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Red, Color.Blue))))
      }
    }
    fun capturePixels(): List<Color> {
      val pixels = onNodeWithTag("direct-glass").captureToImage().toPixelMap()
      return List(pixels.width * pixels.height) { index -> pixels[index % pixels.width, index / pixels.width] }
    }
    val fresh = mutableMapOf<Int, List<Color>>()
    for (target in listOf(160, 100)) {
      runOnIdle {
        displayed.value = TestGlassRuntimeFactory(GlassRuntimeEffect())
        size.value = target.dp
      }
      waitForIdle()
      fresh[target] = capturePixels()
    }
    val effect = GlassRuntimeEffect()
    val factory = TestGlassRuntimeFactory(effect)
    runOnIdle {
      displayed.value = factory
      size.value = 120.dp
    }
    waitForIdle()
    capturePixels()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val rim = checkNotNull(delegate.layers.rim)
    rim.outline.bounds
    for (target in listOf(160, 100, 160)) {
      val before = delegate.rimRecordCount
      runOnIdle { size.value = target.dp }
      waitForIdle()
      assertThat(capturePixels()).isEqualTo(checkNotNull(fresh[target]))
      assertThat(delegate.layers.source).isNull()
      assertThat(delegate.layers.optical).isNull()
      assertThat(delegate.layers.rim).isSameInstanceAs(rim)
      assertThat(rim.isReleased).isFalse()
      assertThat(delegate.rimRecordCount).isEqualTo(before + 1)
      repeat(2) {
        runOnIdle { factory.renderer.invalidateDraw() }
        waitForIdle()
        assertThat(capturePixels()).isEqualTo(checkNotNull(fresh[target]))
        assertThat(delegate.rimRecordCount).isEqualTo(before + 1)
      }
    }
  }

  @Test
  fun repeatedBackdropPreparation_reusesUnchangedRimRecording() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = animatedStageEffect()
      val factory = TestGlassRuntimeFactory(effect)
      var contentSize by mutableStateOf(120.dp)
      val style = mutableStateOf(effect.style)
      setContent {
        val configuration = GlassNodeConfiguration(
          style = style.value,
          performanceMode = HazePerformanceMode.Quality,
          interactionSource = effect.interactionSource,
          interactionTransformTarget = effect.interactionTransformTarget,
          interactionTransformPivot = effect.interactionTransformPivot,
          interactionReducedMotionPolicy = effect.interactionReducedMotionPolicy,
        )
        Box(
          Modifier
            .size(contentSize)
            .testTag("direct-glass")
            .hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = configuration,
              expandLayerBounds = true,
            ),
        ) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Brush.verticalGradient(listOf(Color.Red, Color.Blue))),
          )
        }
      }
      waitForIdle()

      val renderer = factory.renderer

      fun capturePixels(): FloatArray {
        val pixels = onNodeWithTag("direct-glass")
          .captureToImage()
          .toPixelMap()
        val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
        assertThat(delegate.layers.source).isNull()
        assertThat(delegate.layers.optical).isNull()
        return FloatArray(pixels.width * pixels.height) { index ->
          pixels[index % pixels.width, index / pixels.width].red
        }
      }

      val initialPixels = capturePixels()
      val delegate = checkNotNull(effect.delegate as? RuntimeShaderGlassDelegate)
      val firstCount = delegate.rimRecordCount
      val firstRimLayer = checkNotNull(delegate.layers.rim)
      assertThat(firstCount).isGreaterThan(0)

      renderer.invalidateDraw()
      waitForIdle()
      val repeatedPixels = capturePixels()
      assertThat(delegate.rimRecordCount).isEqualTo(firstCount)
      assertThat(delegate.layers.rim).isSameInstanceAs(firstRimLayer)
      assertThat(repeatedPixels.size).isEqualTo(initialPixels.size)

      style.value = style.value.then { ambientResponse(0.6f) }
      renderer.invalidateDraw()
      waitForIdle()
      capturePixels()
      assertThat(delegate.rimRecordCount).isEqualTo(firstCount)
      assertThat(delegate.layers.rim).isSameInstanceAs(firstRimLayer)

      style.value = style.value.then { alpha(0.75f) }
      renderer.invalidateDraw()
      waitForIdle()
      capturePixels()
      assertThat(delegate.rimRecordCount).isEqualTo(firstCount)
      assertThat(delegate.layers.rim).isSameInstanceAs(firstRimLayer)

      var resizedPixels = initialPixels
      for (target in listOf(160.dp, 100.dp, 160.dp)) {
        val beforeResize = delegate.rimRecordCount
        contentSize = target
        waitForIdle()
        resizedPixels = capturePixels()
        assertThat(delegate.rimRecordCount).isEqualTo(beforeResize + 1)
        assertThat(delegate.layers.rim).isSameInstanceAs(firstRimLayer)
        assertThat(firstRimLayer.isReleased).isFalse()
        renderer.invalidateDraw()
        waitForIdle()
        capturePixels()
        assertThat(delegate.rimRecordCount).isEqualTo(beforeResize + 1)
      }
      val resizedRimLayer = checkNotNull(delegate.layers.rim)

      val countBeforeLight = delegate.rimRecordCount
      val pixelsBeforeLight = resizedPixels
      style.value = style.value.then { lightPosition(exactLightAlignment(Offset(10f, 20f))) }
      renderer.invalidateDraw()
      waitForIdle()
      val pixelsAfterLight = capturePixels()
      assertThat(delegate.rimRecordCount).isEqualTo(countBeforeLight + 1)
      assertThat(delegate.layers.rim).isSameInstanceAs(resizedRimLayer)
      val maximumPixelDelta = pixelsBeforeLight.indices.maxOf { index ->
        kotlin.math.abs(pixelsBeforeLight[index] - pixelsAfterLight[index])
      }
      assertThat(maximumPixelDelta).isGreaterThan(0.01f)

      val countBeforeDisable = delegate.rimRecordCount
      style.value = style.value.then {
        specularIntensity(0f)
        edgeShadow(Color.Transparent)
      }
      renderer.invalidateDraw()
      waitForIdle()
      capturePixels()
      assertThat(delegate.layers.rim).isNull()

      style.value = style.value.then {
        specularIntensity(1f)
        edgeShadow(Color.Black.copy(alpha = 0.2f))
      }
      renderer.invalidateDraw()
      waitForIdle()
      capturePixels()
      val recreatedCount = delegate.rimRecordCount
      val recreatedRimLayer = checkNotNull(delegate.layers.rim)
      assertThat(recreatedCount).isEqualTo(countBeforeDisable + 1)
      assertThat(recreatedRimLayer).isNotSameInstanceAs(resizedRimLayer)

      effect.onTrimMemory(TrimMemoryLevel.MODERATE)
      renderer.invalidateDraw()
      waitForIdle()
      capturePixels()
      assertThat(delegate.rimRecordCount).isEqualTo(recreatedCount + 1)
      assertThat(delegate.layers.rim).isNotSameInstanceAs(recreatedRimLayer)
    }

  @Test
  fun activeInteraction_liveAndBaseUniformChangesRetainFusedShaderHandles() =
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = interactiveEffect()
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, style = style.value) }
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
      style.value = style.value.then {
        ambientResponse(0.6f)
        optics(effect.optics.copy(refractionDisplacement = 18.dp))
      }
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
      val effect = interactiveEffect().apply { style = style.then { alpha(0.5f) } }
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
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, style = style.value) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)

      style.value = style.value.then {
        optics(
          effect.optics.copy(
            refractionDisplacement = 18.dp,
          ),
        )
      }
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
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, style = style.value) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)
      style.value = style.value.then { optics(effect.optics.copy(blurRadius = OpticalSizeValue.Fixed(36.dp))) }
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
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, style = style.value) }
      waitForIdle()
      drawFrame()
      val delegate = checkNotNull(runtime(effect).delegate as? RuntimeShaderGlassDelegate)

      val fusedShader = checkNotNull(delegate.fusedShader)
      val fusedEffect = checkNotNull(delegate.layers.optical?.renderEffect)

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
      drawFrame()

      assertThat(delegate.fusedShader).isSameInstanceAs(fusedShader)
      assertThat(delegate.layers.source?.renderEffect).isNull()
      assertThat(delegate.layers.optical?.renderEffect).isNotSameInstanceAs(fusedEffect)
      assertThat(delegate.layers.blurHorizontal).isNull()
      assertThat(delegate.layers.blurred).isNull()
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

  private fun interactiveEffect() = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
      specularIntensity(0f)
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.08f)
        whitePointDelta(0.04f)
      }
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
  }

  private fun largePanelInteractiveEffect() = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
      specularIntensity(0f)
      pressed {
        animate(toSpec = tween(1), fromSpec = tween(160)) {
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

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
    refractionStrength: Float = 0.5f,
  ) = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = refractionStrength,
          refractionDisplacement = 20.dp,
          depth = OpticalSizeValue.Fixed(0.5f),
          blurRadius = OpticalSizeValue.Fixed(38.5.dp),
          progressive = progressive,
        ),
      )
      specularIntensity(0f)
    }
  }

  @Composable
  private fun RuntimeGlassTestContent(
    effect: GlassRuntimeEffect,
    attachEffect: Boolean = true,
    size: Dp = 120.dp,
    style: GlassStyle = effect.style,
    measureStableInputReuse: Boolean = false,
  ) {
    Box(
      Modifier
        .size(size)
        .then(
          if (attachEffect) {
            Modifier.testGlassRuntime(effect, HazeInput.Content, style, measureStableInputReuse)
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
    style: GlassStyle = effect.style,
    measureStableInputReuse: Boolean = false,
  ): Modifier {
    val factory = remember(effect, measureStableInputReuse) { FixedGlassRuntimeFactory(effect, measureStableInputReuse) }
    return hazeGlass(
      factory = factory,
      input = input,
      style = style,
      performanceMode = HazePerformanceMode.Quality,
      expandLayerBounds = true,
      interactionSource = effect.interactionSource,
      interactionTransformTarget = effect.interactionTransformTarget,
      interactionTransformPivot = effect.interactionTransformPivot,
      interactionReducedMotionPolicy = effect.interactionReducedMotionPolicy,
    )
  }

  @Test
  fun removeWhitePoint_preservesFusedOpticsUntilExitCompletes() = assertFusedRemoval(FusedRemoval.WhitePoint)

  @Test
  fun removeRefraction_preservesFusedDetailUntilExitCompletes() = assertFusedRemoval(FusedRemoval.Refraction)

  @Test
  fun removeLighting_preservesForegroundUntilExitCompletes() = assertFusedRemoval(FusedRemoval.Lighting)

  @Test
  fun removeAllResponses_preservesFusedGraphAndCleansController() = assertFusedRemoval(FusedRemoval.All)

  @Test
  fun rapidRemovalAndReintroduction_preservesFusedGraphThroughFinalExit() = assertFusedRemoval(FusedRemoval.All, rapid = true)

  @Test
  fun reducedMotion_removalCleansFusedInteractionStagesImmediately() = assertFusedRemoval(FusedRemoval.All, reduced = true)

  private enum class FusedRemoval { WhitePoint, Refraction, Lighting, All }

  private fun fusedRemovalBase(removal: FusedRemoval) = GlassStyle.regular.then {
    optics(
      GlassOptics(
        refractionStrength = if (removal == FusedRemoval.WhitePoint) 0f else 0.5f,
        refractionDetailIntensity = if (removal == FusedRemoval.WhitePoint) 0f else 0.75f,
        refractionDisplacement = 20.dp,
        blurRadius = OpticalSizeValue.Fixed(0.dp),
      ),
    )
    tint(Color.Blue.copy(alpha = 0.5f))
  }

  private fun fusedRemovalStyle(removal: FusedRemoval) = fusedRemovalBase(removal).then {
    pressed {
      animate(tween(1), tween(500)) {
        if (removal != FusedRemoval.Refraction) whitePointDelta(0.2f)
        if (removal == FusedRemoval.Refraction || removal == FusedRemoval.All) refractionMultiplier(1.8f)
        if (removal == FusedRemoval.Lighting || removal == FusedRemoval.All) lightingIntensity(0.5f)
      }
    }
  }

  private fun assertFusedRemoval(removal: FusedRemoval, rapid: Boolean = false, reduced: Boolean = false) = runAndroidComposeUiTest<ComponentActivity> {
    val source = MutableInteractionSource()
    val effect = GlassRuntimeEffect().apply {
      interactionSource = source
      interactionReducedMotionPolicy = if (reduced) GlassReducedMotionPolicy.Reduced else GlassReducedMotionPolicy.Full
    }
    val style = mutableStateOf(fusedRemovalStyle(removal))
    val replacement = fusedRemovalBase(removal).then {
      if (removal == FusedRemoval.Lighting) pressed { animate(tween(1), tween(500)) { whitePointDelta(0.2f) } }
    }
    val state = dev.chrisbanes.haze.HazeState()
    setContent {
      Box(Modifier.size(384.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(Modifier.align(Alignment.Center).size(120.dp).testGlassRuntime(effect, HazeInput.Sources(state), style.value))
      }
    }
    waitForIdle()
    val scope = TestScope()
    scope.launch { source.emit(PressInteraction.Press(Offset(40f, 40f))) }
    scope.testScheduler.runCurrent()
    waitForIdle()
    drawFrame()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    fun assertGraph() {
      assertThat(delegate.layers.source).isNotNull()
      assertThat(delegate.layers.optical).isNotNull()
      assertThat(delegate.lastSuccessfulSourceSnapshot).isNotNull()
      assertThat(delegate.fusedShader).isNotNull()
      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionRefractionDetailCoverage).isNull()
      assertThat(delegate.layers.interactionRefractionComposite).isNull()
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
    }
    fun assertOutgoingUniforms(active: Boolean) {
      val uniforms = checkNotNull(runtime(effect).preparedRender).interactionUniforms
      when (removal) {
        FusedRemoval.WhitePoint -> {
          if (active) {
            assertThat(uniforms.whitePointDelta).isGreaterThan(0f)
          } else {
            assertThat(uniforms.whitePointDelta).isEqualTo(0f)
          }
        }
        FusedRemoval.Refraction -> {
          if (active) {
            assertThat(uniforms.refractionMultiplier).isGreaterThan(1f)
          } else {
            assertThat(uniforms.refractionMultiplier).isEqualTo(1f)
          }
        }
        FusedRemoval.Lighting -> {
          if (active) {
            assertThat(uniforms.lightingIntensity).isGreaterThan(0f)
          } else {
            assertThat(uniforms.lightingIntensity).isEqualTo(0f)
          }
        }
        FusedRemoval.All -> {
          if (active) {
            assertThat(uniforms.whitePointDelta).isGreaterThan(0f)
            assertThat(uniforms.refractionMultiplier).isGreaterThan(1f)
            assertThat(uniforms.lightingIntensity).isGreaterThan(0f)
          } else {
            assertThat(uniforms.whitePointDelta).isEqualTo(0f)
            assertThat(uniforms.refractionMultiplier).isEqualTo(1f)
            assertThat(uniforms.lightingIntensity).isEqualTo(0f)
          }
        }
      }
    }
    assertGraph()
    assertOutgoingUniforms(active = true)
    assertThat(effect.currentInteractionState.hasOptics).isTrue()
    val lighting = delegate.layers.interactionLighting
    if (removal == FusedRemoval.Lighting || removal == FusedRemoval.All) assertThat(lighting).isNotNull()
    mainClock.autoAdvance = false
    fun replace(next: GlassStyle) {
      runOnIdle { style.value = next }
      repeat(2) {
        mainClock.advanceTimeByFrame()
        waitForIdle()
      }
    }
    replace(replacement)
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    drawFrame()
    assertGraph()
    if (!reduced) {
      assertOutgoingUniforms(active = true)
      assertThat(effect.currentInteractionState.hasOptics).isTrue()
      lighting?.let {
        assertThat(delegate.layers.interactionLighting).isSameInstanceAs(it)
        assertThat(it.isReleased).isFalse()
      }
    } else {
      assertOutgoingUniforms(active = false)
    }
    if (rapid) {
      replace(fusedRemovalStyle(removal))
      mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
      waitForIdle()
      drawFrame()
      assertGraph()
      assertOutgoingUniforms(active = true)
      replace(replacement)
      mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
      waitForIdle()
      drawFrame()
      assertGraph()
      assertOutgoingUniforms(active = true)
    }
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    drawFrame()
    assertGraph()
    assertOutgoingUniforms(active = false)
    assertThat(effect.currentInteractionState.hasOptics).isEqualTo(removal == FusedRemoval.Lighting)
    assertThat(effect.currentInteractionState.hasLighting).isFalse()
    if (removal != FusedRemoval.Lighting) assertThat(effect.interactionControllerForTest).isNull()
    lighting?.let { assertThat(it.isReleased).isTrue() }
    assertThat(delegate.layers.interactionLighting).isNull()
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
  private val measureStableInputReuse: Boolean = false,
) : HazeEffectFactory<GlassNodeConfiguration> {
  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> =
    if (measureStableInputReuse) StableContentGlassRuntimeRenderer(effect) else effect
}

@OptIn(InternalHazeApi::class)
private class StableContentGlassRuntimeRenderer(
  private val effect: GlassRuntimeEffect,
) : HazeEffectRenderer<GlassNodeConfiguration> by effect,
  HazeEffectRendererLifecycle<GlassNodeConfiguration> by effect,
  HazeEffectRendererDrawHooks<GlassNodeConfiguration> by effect,
  HazeEffectRendererRetainedOutput by effect {
  override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
    with(effect) { draw(style) }
    val context = this as HazeEffectRuntimeDrawScope
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val input = checkNotNull(context.inputSnapshot)
    val recorded = delegate.stageRecordCounts
    repeat(2) {
      with(delegate) { draw(context) }
      assertThat(context.inputSnapshot).isSameInstanceAs(input)
      assertThat(delegate.stageRecordCounts).isEqualTo(recorded)
    }
  }
}

@OptIn(InternalHazeApi::class)
private class TestGlassRuntimeFactory(
  private val effect: GlassRuntimeEffect,
) : HazeEffectFactory<GlassNodeConfiguration> {
  lateinit var renderer: TestGlassRuntimeRenderer

  override fun createRenderer(): HazeEffectRenderer<GlassNodeConfiguration> =
    TestGlassRuntimeRenderer(effect).also { renderer = it }
}

@OptIn(InternalHazeApi::class)
private class TestGlassRuntimeRenderer(
  private val effect: GlassRuntimeEffect,
) :
  HazeEffectRenderer<GlassNodeConfiguration>,
  HazeEffectRendererLifecycle<GlassNodeConfiguration>,
  HazeEffectRendererDrawHooks<GlassNodeConfiguration> {
  private var lifecycleScope: HazeEffectLifecycleScope? = null
  var foregroundCanvas: androidx.compose.ui.graphics.Canvas? = null

  override fun attach(scope: HazeEffectLifecycleScope) {
    lifecycleScope = scope
    effect.attach(scope)
  }

  override fun update(
    scope: HazeEffectLifecycleScope,
    style: GlassNodeConfiguration,
    sampling: HazeSampling,
  ) {
    effect.update(scope, style, sampling)
  }

  override fun detach() {
    lifecycleScope = null
    effect.detach()
  }

  override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
    drawInput()
  }

  override fun HazeEffectLayoutScope.calculateLayerBounds(
    style: GlassNodeConfiguration,
  ): Rect = with(effect) { calculateLayerBounds(style) }

  override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
    checkNotNull(with(effect) { backdropEffect(style) })
  }

  override fun HazeEffectRuntimeDrawScope.drawForeground(style: GlassNodeConfiguration) {
    val canvas = foregroundCanvas
    if (canvas == null) {
      with(effect) { drawForeground(style) }
    } else {
      val context = this
      CanvasDrawScope().draw(this, layoutDirection, canvas, size) {
        with(effect.delegate) { drawForeground(context) }
      }
    }
  }

  override fun shouldDrawContentBehind(): Boolean = effect.shouldDrawContentBehind()

  override fun shouldClipToNodeBounds(): Boolean = effect.shouldClipToNodeBounds()

  override fun shouldPreferClipToInputBounds(): Boolean = effect.shouldPreferClipToInputBounds()

  override fun onTrimMemory(level: TrimMemoryLevel) {
    effect.onTrimMemory(level)
  }

  fun invalidateDraw() {
    lifecycleScope?.invalidateDraw()
  }
}
