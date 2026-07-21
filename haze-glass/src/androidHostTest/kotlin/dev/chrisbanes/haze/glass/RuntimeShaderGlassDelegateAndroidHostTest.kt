// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RuntimeShaderGlassDelegateAndroidHostTest : ContextTest() {

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

      assertSame(opticalShader, delegate.opticalShader)
      assertNotSame(opticalEffect, delegate.opticalEffect)

      val rimShader = delegate.rimShader
      val rimEffect = delegate.rimEffect
      effect.lightPosition = Offset(10f, 20f)
      waitForIdle()
      drawFrame()

      assertSame(rimShader, delegate.rimShader)
      assertNotSame(rimEffect, delegate.rimEffect)
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

      assertSame(opticalEffect, delegate.interactionShaderHandle("interactionOpticalEffect"))
      assertSame(detailEffect, delegate.interactionShaderHandle("interactionDetailEffect"))
      assertSame(lightingEffect, delegate.interactionShaderHandle("interactionLightingEffect"))
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

      assertSame(horizontalShader, delegate.blurHorizontalShader)
      assertSame(verticalShader, delegate.blurVerticalShader)
      assertSame(prefilterShader, delegate.blurPrefilterShader)
      assertSame(detailShader, delegate.refractionDetailShader)
      assertNotSame(horizontalEffect, delegate.layers.blurHorizontal?.renderEffect)
      assertNotSame(verticalEffect, delegate.layers.blurred?.renderEffect)
      assertNotSame(prefilterEffect, delegate.layers.blurPrefiltered?.renderEffect)
      assertNotSame(detailEffect, delegate.layers.refractionDetail?.renderEffect)
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

      assertSame(horizontalShader, delegate.progressiveBlurHorizontalShader)
      assertSame(verticalShader, delegate.progressiveBlurVerticalShader)
      assertNotSame(horizontalEffect, delegate.layers.blurHorizontal?.renderEffect)
      assertNotSame(verticalEffect, delegate.layers.blurred?.renderEffect)
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
  private fun RuntimeGlassTestContent(effect: GlassVisualEffect) {
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

  private fun AndroidComposeUiTest<ComponentActivity>.drawFrame() {
    runOnIdle {
      val view = checkNotNull(activity).window.decorView
      val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
      try {
        view.draw(Canvas(bitmap))
      } finally {
        bitmap.recycle()
      }
    }
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandle(fieldName: String): Any {
    val field = RuntimeShaderGlassDelegate::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return checkNotNull(field.get(this))
  }
}
