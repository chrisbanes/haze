// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.robolectric.annotation.Config

/**
 * The RenderScript presentation block runs through [RenderScriptBlurVisualEffectDelegate.drawPresentation]
 * so it can be driven without a RenderScript surface. The capture and padded layers need the surface
 * and are covered by the device test.
 */
class RenderScriptLayerReuseAndroidHostTest : ScreenshotTest() {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  @Test
  @Config(sdk = [35])
  fun drawPresentation_repeatedDraws_reuseOnePresentationLayer() = runScreenshotTest {
    val h = Harness(PLAIN)
    setContent { h.Content() }
    frame()
    val layer = checkNotNull(h.delegate.presentationLayer.layer)

    repeat(4) { h.redraw(this) }

    assertThat(h.factory.draws).isEqualTo(5)
    assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(layer)
    assertThat(layer.isReleased).isFalse()
  }

  @Test
  @Config(sdk = [35])
  fun drawPresentation_maskAddedThenRemoved_restoresTheCompositingStrategy() = runScreenshotTest {
    val h = Harness(PLAIN)
    setContent { h.Content() }
    frame()
    val layer = checkNotNull(h.delegate.presentationLayer.layer)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Auto)
    assertThat(layer.alpha).isEqualTo(1f)

    h.update(MASKED)
    h.redraw(this)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Offscreen)
    assertThat(layer.alpha).isEqualTo(0.5f)

    h.update(PLAIN)
    h.redraw(this)
    assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(layer)
    assertThat(layer.compositingStrategy).isEqualTo(CompositingStrategy.Auto)
    assertThat(layer.alpha).isEqualTo(1f)
  }

  @Test
  @Config(sdk = [35])
  fun drawPresentation_afterStyleChanges_matchesAFreshDelegate() = runScreenshotTest {
    val h = Harness(MASKED)
    setContent { h.Content() }
    frame()
    h.update(PLAIN)
    h.redraw(this)
    h.update(MASKED)
    h.redraw(this)
    h.update(NOISY)
    val reused = h.redraw(this)

    h.attachment++
    val fresh = frame()

    assertThat(maxDifference(reused, fresh)).isLessThan(0.01f)
  }

  @Test
  @Config(sdk = [35])
  fun drawPresentation_halfAlpha_compositesTheOutputOnce() = runScreenshotTest {
    val h = Harness(HALF_ALPHA)
    setContent { h.Content() }

    // The output is opaque magenta at its left edge over a white root: half alpha leaves 0.5 green,
    // and a second composite of the same layer would leave 0.25.
    val pixels = frame()

    assertThat(pixels[4, 40].green).isCloseTo(0.5f, 0.06f)
  }

  @Test
  @Config(sdk = [35])
  fun drawPresentation_withNoise_buildsTheShaderOnce() = runScreenshotTest {
    val h = Harness(NOISY)
    setContent { h.Content() }
    frame()
    repeat(4) { h.redraw(this) }
    assertThat(h.delegate.noiseShaders.buildCount).isEqualTo(1)

    h.factory.scale = 0.5f
    h.redraw(this)
    assertThat(h.delegate.noiseShaders.buildCount).isEqualTo(2)
  }

  @Test
  @Config(sdk = [35])
  fun clearRetainedOutput_releasesThePresentationLayerAndNoiseShader() =
    assertReleased { it.clearRetainedOutput() }

  @Test
  @Config(sdk = [35])
  fun onTrimMemory_moderate_releasesThePresentationLayerAndNoiseShader() =
    assertReleased { it.onTrimMemory(HostLifecycleScope(context), TrimMemoryLevel.MODERATE) }

  @Test
  @Config(sdk = [35])
  fun detach_releasesThePresentationLayerAndNoiseShader() = assertReleased { it.detach() }

  @Test
  @Config(sdk = [35])
  fun onTrimMemory_background_keepsThePresentationLayerAndNoiseShader() = runScreenshotTest {
    val h = Harness(NOISY)
    setContent { h.Content() }
    frame()
    val layer = checkNotNull(h.delegate.presentationLayer.layer)

    h.delegate.onTrimMemory(HostLifecycleScope(context), TrimMemoryLevel.BACKGROUND)
    h.redraw(this)

    assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(layer)
    assertThat(h.delegate.noiseShaders.buildCount).isEqualTo(1)
  }

  private fun assertReleased(release: (RenderScriptBlurVisualEffectDelegate) -> Unit) = runScreenshotTest {
    val h = Harness(NOISY)
    setContent { h.Content() }
    frame()
    val layer = checkNotNull(h.delegate.presentationLayer.layer)
    assertThat(h.delegate.noiseShaders.buildCount).isEqualTo(1)

    release(h.delegate)

    assertThat(layer.isReleased).isTrue()
    assertThat(h.delegate.presentationLayer.layer).isNull()

    h.redraw(this)
    assertThat(h.delegate.presentationLayer.layer).isNotNull().isNotSameInstanceAs(layer)
    assertThat(h.delegate.noiseShaders.buildCount).isEqualTo(2)
  }

  /** Draws the presentation of one RenderScript delegate with a Blur runtime styled by [update]. */
  private inner class Harness(initial: HazeBlurStyle) {
    val effect = BlurVisualEffect()
    val factory = PresentationFactory(effect)
    private var tick by mutableStateOf(0)
    var attachment by mutableStateOf(0)

    val delegate get() = checkNotNull(factory.delegate)

    init {
      update(initial)
    }

    fun update(style: HazeBlurStyle) {
      effect.update(
        HostLifecycleScope(context),
        BlurConfiguration(style, HazePerformanceMode.Quality),
        HazeSampling.FullResolution,
      )
    }

    /** Forces the next frame to run the renderer's draw again. */
    fun redraw(test: ScreenshotUiTest) = test.run {
      tick++
      frame()
    }

    @androidx.compose.runtime.Composable
    fun Content() {
      key(attachment) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
          Box(
            Modifier.size(100.dp).hazeEffect(
              factory = factory,
              input = HazeInput.Content,
              style = tick,
            ),
          )
        }
      }
    }
  }

  private class PresentationFactory(val effect: BlurVisualEffect) : HazeEffectFactory<Int> {
    var scale = 1f
    var draws = 0
      private set

    /** The delegate of the latest renderer, built by its first draw. */
    var delegate: RenderScriptBlurVisualEffectDelegate? = null
      private set

    override fun createRenderer(): HazeEffectRenderer<Int> = object : HazeEffectRenderer<Int> {
      private var own: RenderScriptBlurVisualEffectDelegate? = null
      private var output: GraphicsLayer? = null

      override fun HazeEffectDrawScope.draw(style: Int) {
        val runtime = this as HazeEffectRuntimeDrawScope
        val graphicsContext = runtime.requireGraphicsContext()
        val current = own ?: RenderScriptBlurVisualEffectDelegate(
          effect,
          graphicsContext,
          runtime.requirePlatformContext(),
        ).also {
          own = it
          delegate = it
        }
        val layer = output ?: graphicsContext.createGraphicsLayer().also {
          it.record(IntSize(256, 256)) {
            drawRect(Brush.horizontalGradient(listOf(Color.Magenta, Color.Cyan)))
          }
          output = it
        }
        with(current) { drawPresentation(runtime, layer, Offset.Zero, scale) }
        draws++
      }
    }
  }
}

private val PLAIN = HazeBlurStyle {
  noiseFactor(0f)
}

private val HALF_ALPHA = HazeBlurStyle {
  noiseFactor(0f)
  alpha(0.5f)
}

private val MASKED = HazeBlurStyle {
  noiseFactor(0f)
  alpha(0.5f)
  mask(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)))
  colorEffects(listOf(HazeColorEffect.tint(Color.Red.copy(alpha = 0.3f))))
}

private val NOISY = HazeBlurStyle {
  noiseFactor(0.5f)
  alpha(0.75f)
  colorEffects(listOf(HazeColorEffect.tint(Color.Blue.copy(alpha = 0.2f))))
}
