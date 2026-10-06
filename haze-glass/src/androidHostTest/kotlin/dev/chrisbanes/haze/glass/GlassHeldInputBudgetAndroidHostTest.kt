// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererInteraction
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassHeldInputBudgetAndroidHostTest : ContextTest() {
  @Test fun opaqueFullScale_actualFusedPlanFitsBeforeTopologyChanges() {
    val plan = render(scale = 1f, lighting = false, alpha = 1f).plan
    assertThat(plan.layers.map { it.kind }).containsExactly(GlassRetainedLayerKind.Source, GlassRetainedLayerKind.Optical)
    assertThat(plan.retainedPixelCountOrNull()).isEqualTo(9_680_000L)
    assertThat(plan.fitsGlassRenderBudget()).isTrue()
  }

  @Test fun partialAlphaAndLighting_currentHalfScaleFitsWhileHeldFullScaleExceedsAggregate() {
    val current = render(scale = .5f, lighting = true, alpha = .5f)
    val held = render(scale = 1f, lighting = true, alpha = .5f)
    assertThat(current.plan.retainedPixelCountOrNull()).isEqualTo(8_470_000L)
    assertThat(current.plan.fitsGlassRenderBudget()).isTrue()
    assertThat(held.plan.layers.map { it.kind }).containsExactly(GlassRetainedLayerKind.Source, GlassRetainedLayerKind.Optical, GlassRetainedLayerKind.InteractionLighting, GlassRetainedLayerKind.GroupComposite)
    assertThat(held.plan.retainedPixelCountOrNull()).isEqualTo(19_360_000L)
    assertThat(held.plan.layers.all { it.size.fitsGlassLayerBudget() }).isTrue()
    assertThat(held.plan.fitsGlassRenderBudget()).isFalse()
  }

  @Test fun restoringOpaqueBase_actualHeldPlanFitsAgainWithoutRescalingInput() {
    val invalid = render(scale = 1f, lighting = true, alpha = .5f)
    val valid = render(scale = 1f, lighting = false, alpha = 1f)
    assertThat(valid.params.coordinates).isEqualTo(invalid.params.coordinates)
    assertThat(valid.plan.fitsGlassRenderBudget()).isTrue()
    assertThat(valid.plan.retainedPixelCountOrNull()).isEqualTo(9_680_000L)
  }

  @Test fun rejectedHeldGraph_allocatesOnlyFallbackGroupThenReleasesItBeforeRecovery() = runAndroidComposeUiTest<ComponentActivity> {
    val effect = GlassRuntimeEffect()
    val observer = CountingObserver(effect)
    val factory = HazeEffectFactory<GlassNodeConfiguration> { observer }
    val state = HazeState()
    val selected = mutableStateOf(true)
    val mode = mutableStateOf(HazePerformanceMode.Quality)
    val base = GlassStyle.regular.then {
      optics(GlassOptics(refractionStrength = .5f, refractionDisplacement = 0.dp, blurRadius = OpticalSizeValue.Fixed(0.dp)))
      specularIntensity(0f)
      edgeShadow(Color.Transparent)
      edgeSoftness(0.dp)
    }
    val style = mutableStateOf(base)
    val input = HazeInput.Sources(state, selection = HazeSourceSelection.All.where { selected.value }, retention = HazeSourceRetention.KeepLastFrame)
    setContent {
      val side = with(LocalDensity.current) { 2200.toDp() }
      Box {
        Box(Modifier.requiredSize(side).hazeSource(state).background(Color.Red))
        Box(Modifier.requiredSize(side).testTag("budget").hazeGlass(factory, input, style.value, mode.value, false, null, interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced))
      }
    }
    waitUntil(timeoutMillis = 10_000) {
      org.robolectric.Shadows.shadowOf(Looper.getMainLooper()).idle()
      onRoot().captureToImage()
      (effect.delegate as? RuntimeShaderGlassDelegate)?.displayedImmutableInput != null
    }
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val image = checkNotNull(delegate.displayedImmutableInput)
    val context = checkNotNull(observer.graphics)
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val allocated = context.created.size
    val captures = delegate.immutableInputCaptureCount
    runOnIdle {
      selected.value = false
      mode.value = HazePerformanceMode.Performance
      style.value = base.then {
        alpha(.5f)
        interactionLightRadiusFraction(.5f)
        pressed { lightingIntensity(.5f) }
      }
    }
    waitForIdle()
    onRoot().captureToImage()
    assertThat(context.created.size).isEqualTo(allocated + 1)
    val fallbackGroup = context.created.last()
    assertThat(fallbackGroup.isReleased).isFalse()
    assertThat(source.isReleased).isTrue()
    assertThat(optical.isReleased).isTrue()
    assertThat(delegate.layers.source).isNull()
    assertThat(delegate.layers.optical).isNull()
    assertThat(delegate.layers.interactionLighting).isNull()
    assertThat(delegate.displayedImmutableInput).isSameInstanceAs(image)
    repeat(3) { onRoot().captureToImage() }
    assertThat(context.created.size).isEqualTo(allocated + 1)
    assertThat(delegate.immutableInputCaptureCount).isEqualTo(captures)
    runOnIdle { style.value = base }
    waitForIdle()
    onRoot().captureToImage()
    assertThat(fallbackGroup.isReleased).isTrue()
    assertThat(context.created.size).isEqualTo(allocated + 3)
    assertThat(delegate.layers.source).isNotNull()
    assertThat(delegate.displayedImmutableInput).isSameInstanceAs(image)
    runOnIdle { delegate.clearRetainedOutput() }
    assertThat(delegate.immutableInputOwnerCount).isEqualTo(0)
    assertThat(context.created.all { it.isReleased }).isTrue()
  }

  private class CountingContext(private val delegate: GraphicsContext) : GraphicsContext by delegate {
    val created = mutableListOf<GraphicsLayer>()
    override fun createGraphicsLayer(): GraphicsLayer = delegate.createGraphicsLayer().also { created += it }
  }

  private class CountingObserver(private val effect: GlassRuntimeEffect) :
    HazeEffectRenderer<GlassNodeConfiguration> by effect,
    HazeEffectRendererLifecycle<GlassNodeConfiguration> by effect,
    HazeEffectRendererDrawHooks<GlassNodeConfiguration> by effect,
    HazeEffectRendererRetainedOutput by effect,
    HazeEffectRendererInteraction by effect {
    var graphics: CountingContext? = null
    override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
      val counting = graphics ?: CountingContext(requireGraphicsContext()).also { graphics = it }
      val scope = object : HazeEffectRuntimeDrawScope by this {
        override fun requireGraphicsContext(): GraphicsContext = counting
      }
      with(effect) { scope.prepareDraw(style) }
    }
    override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
      with(effect) { draw(style) }
    }
  }

  private fun render(scale: Float, lighting: Boolean, alpha: Float): GlassPreparedRender {
    val effect = GlassRuntimeEffect().apply {
      appearanceReader = { GlassSystemAppearance.Light }
      style = GlassStyle.regular.then {
        optics(GlassOptics(refractionStrength = .5f, refractionDisplacement = 0.dp, blurRadius = OpticalSizeValue.Fixed(0.dp)))
        specularIntensity(0f)
        edgeShadow(Color.Transparent)
        edgeSoftness(0.dp)
      }
    }
    val resolved = resolveGlassStyle(effect, Size(2200f, 2200f), Density(1f), LayoutDirection.Ltr)
    val coordinates = resolveGlassCoordinates(Size(2200f, 2200f), Offset.Zero, Size(2200f, 2200f), scale)
    return buildGlassPreparedRender(
      params = buildGlassRenderParams(resolved, coordinates),
      interactionUniforms = GlassInteractionUniforms(Offset(1100f * scale, 1100f * scale), 1100f * scale, if (lighting) .5f else 0f, 1f, 0f),
      interactionTopology = GlassInteractionTopology(false, lighting, 1f),
      interactionRadiusFraction = .5f,
      alpha = alpha,
      outputSize = IntSize(2200, 2200),
    )
  }
}
