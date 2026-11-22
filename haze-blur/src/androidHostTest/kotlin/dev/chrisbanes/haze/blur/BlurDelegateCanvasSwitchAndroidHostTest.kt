// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The same attached node reselects its delegate when only the canvas acceleration changes. */
@RunWith(RobolectricTestRunner::class)
class BlurDelegateCanvasSwitchAndroidHostTest {

  @Test
  @Config(sdk = [35])
  fun canvasSwitch_hardwareSoftwareHardware_reselectsAndReusesDelegate() {
    val scope = HostLifecycleScope(ApplicationProvider.getApplicationContext())
    val effect = BlurVisualEffect()
    val configuration = BlurConfiguration(HazeBlurStyle, HazePerformanceMode.Quality)
    effect.attach(scope)
    effect.update(scope, configuration, HazeSampling.FullResolution)
    val context = CanvasSwitchContext()

    context.prepare(effect, configuration, hardware = true)
    val hardwareDelegate = effect.delegate
    assertThat(hardwareDelegate).isInstanceOf<RenderEffectBlurVisualEffectDelegate>()
    context.prepare(effect, configuration, hardware = true)
    assertThat(effect.delegate).isSameInstanceAs(hardwareDelegate)

    context.prepare(effect, configuration, hardware = false)
    val softwareDelegate = effect.delegate
    // Robolectric disables RenderScript, so the software fallback is the scrim here.
    assertThat(softwareDelegate).isInstanceOf<ScrimBlurVisualEffectDelegate>()
    context.prepare(effect, configuration, hardware = false)
    assertThat(effect.delegate).isSameInstanceAs(softwareDelegate)

    context.prepare(effect, configuration, hardware = true)
    assertThat(effect.delegate).isInstanceOf<RenderEffectBlurVisualEffectDelegate>()
  }
}

private class CanvasSwitchContext(
  private val drawScope: CanvasDrawScope = CanvasDrawScope(),
) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
  override val modifierSize: Size = Size(64f, 64f)
  override val modifierBounds: Rect = Rect(Offset.Zero, modifierSize)
  override val sampling: HazeSampling = HazeSampling.FullResolution
  override val layerSize: Size = modifierSize
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = false
  override val inputSnapshot: HazeEffectInputSnapshot? = null
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  fun prepare(effect: BlurVisualEffect, configuration: BlurConfiguration, hardware: Boolean) {
    val canvas = object : android.graphics.Canvas(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)) {
      override fun isHardwareAccelerated() = hardware
    }
    drawScope.draw(
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
      canvas = androidx.compose.ui.graphics.Canvas(canvas),
      size = modifierSize,
    ) {
      with(effect) { this@CanvasSwitchContext.prepareDraw(configuration) }
    }
  }

  override fun requirePlatformContext(): PlatformContext =
    ApplicationProvider.getApplicationContext()

  override fun requireGraphicsContext() = error("Selection does not need graphics layers")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = HazeBlurStyle as T
  override fun invalidateDraw() = Unit
  override fun drawInput() = Unit
  override fun DrawScope.drawInput() = Unit
}
