// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(InternalHazeApi::class)
class GlassCapturedInputRenderTest {
  @Test fun reducedScale_rebuildsHeldFullScaleParamsAndActiveUniforms() = checkHeldRender(1f, .5f)

  @Test fun increasedScale_rebuildsHeldHalfScaleParamsAndActiveUniforms() = checkHeldRender(.5f, 1f)

  @Test fun resizedCrop_resolvesResponsiveOpticsCornersAndLightAtHeldSize() = checkHeldRender(1f, .5f, resized = true)

  private fun checkHeldRender(heldScale: Float, currentScale: Float, resized: Boolean = false) {
    val context = RenderContext(if (resized) Size(200f, 160f) else Size(100f, 100f))
    val effect = GlassRuntimeEffect().apply { appearanceReader = { GlassSystemAppearance.Light } }
    val heldSize = Size(100f, 100f)
    val held = resolveGlassCoordinates(Size(140f, 140f), Offset(20f, 20f), heldSize, heldScale)
    val optics = GlassOptics(
      refractionDisplacement = 12.dp,
      refractionHeightFraction = .3f,
      blurRadius = OpticalSizeValue.Responsive(OpticalSizePoint(100.dp, 10.dp), OpticalSizePoint(200.dp, 30.dp)),
    )
    val authored = GlassStyle.regular.then {
      optics(optics)
      shape(RoundedCornerShape(25))
      lightPosition(Alignment.BottomEnd)
      interactionLightRadiusFraction(.4f)
      pressed {
        lightingIntensity(.7f)
        refractionMultiplier(1.4f)
        whitePointDelta(.2f)
      }
    }
    effect.update(context, GlassNodeConfiguration(authored, performanceMode = dev.chrisbanes.haze.HazePerformanceMode.Quality, interactionSource = null), HazeSampling.FullResolution)
    effect.attach(context)
    try {
      checkNotNull(effect.interactionControllerForTest).setRawPressedForTest(true, Offset(12f, 14f), context.modifierSize)
      effect.prepareRenderBudget(context, runtimeShaderSupported = true, requestedScaleOverride = currentScale)
      val current = checkNotNull(effect.preparedRender)
      val render = effect.prepareCapturedInputRender(context, held, Color.Red, current)
      val expectedStyle = resolveGlassStyle(effect, heldSize, Density(1f), LayoutDirection.Ltr)
      val expectedParams = buildGlassRenderParams(expectedStyle, held).copy(backgroundColor = Color.Red)
      assertThat(render.params).isEqualTo(expectedParams)
      assertThat(render.params.blurRadiusPx).isEqualTo(10f * heldScale)
      assertThat(render.params.sampleStepPx).isEqualTo(2f * heldScale)
      assertThat(render.interactionUniforms.position).isEqualTo(Offset(12f, 14f) * heldScale + held.materialOrigin)
      assertThat(render.interactionUniforms.radiusPx).isEqualTo(40f * heldScale)
      assertThat(render.interactionUniforms.lightingIntensity).isEqualTo(.7f)
      assertThat(render.interactionUniforms.refractionMultiplier).isEqualTo(1.4f)
      assertThat(render.interactionUniforms.whitePointDelta).isEqualTo(.2f)
      val expected = buildGlassPreparedRender(
        expectedParams,
        render.interactionUniforms,
        render.interactionTopology,
        interactionRadiusFraction = .4f,
        alpha = expectedStyle.alpha,
        outputSize = heldSize.roundToIntSize(),
      )
      assertThat(render).isEqualTo(expected)

      // Current tint/alpha and removed response channels must still update while geometry is held.
      effect.style = authored.then {
        tint(Color.Green)
        alpha(.5f)
        pressed {}
      }
      effect.update(context, GlassNodeConfiguration(effect.style, performanceMode = dev.chrisbanes.haze.HazePerformanceMode.Quality, interactionSource = null), HazeSampling.FullResolution)
      val updated = effect.prepareCapturedInputRender(context, held, Color.Red, current)
      assertThat(updated.params.tint).isEqualTo(Color.Green)
      assertThat(updated.alpha).isEqualTo(.5f)
      assertThat(updated.params.coordinates).isEqualTo(held)
      assertThat(updated.params.blurRadiusPx).isEqualTo(10f * heldScale)
      assertThat(updated.interactionUniforms.lightingIntensity).isEqualTo(0f)
      assertThat(updated.interactionUniforms.refractionMultiplier).isEqualTo(1f)
      assertThat(updated.interactionUniforms.whitePointDelta).isEqualTo(0f)
    } finally {
      effect.detach()
      context.coroutineScope.cancel()
    }
  }

  private class RenderContext(override val modifierSize: Size) :
    HazeEffectRuntimeDrawScope,
    HazeEffectLifecycleScope,
    DrawScope by CanvasDrawScope() {
    override val size get() = modifierSize
    override val modifierBounds get() = Rect(Offset.Zero, modifierSize)
    override val layerSize = Size(modifierSize.width + 80f, modifierSize.height + 80f)
    override val layerOffset = Offset(40f, 40f)
    override val sampling = HazeSampling.FullResolution
    override val hasDrawableInput = true
    override val inputSnapshot: HazeEffectInputSnapshot? = null
    override val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    override fun requireDensity() = Density(1f)
    override fun requireGraphicsContext(): GraphicsContext = error("Unexpected graphics access")
    override fun requirePlatformContext(): PlatformContext = error("Unexpected platform access")
    override fun drawInput() = Unit
    override fun DrawScope.drawInput() = Unit
    override fun invalidateDraw() = Unit
    override fun invalidateLayerBounds() = Unit
    override fun <T> currentValueOf(local: CompositionLocal<T>): T {
      @Suppress("UNCHECKED_CAST")
      return when (local) {
        androidx.compose.ui.platform.LocalLayoutDirection -> LayoutDirection.Ltr
        LocalGlassStyle -> GlassStyle {}
        LocalGlassAccessibilitySettings -> GlassAccessibilitySettings()
        else -> error("Unexpected composition local")
      } as T
    }
  }
}
