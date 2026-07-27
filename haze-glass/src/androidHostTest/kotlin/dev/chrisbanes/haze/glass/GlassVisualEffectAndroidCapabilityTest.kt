// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeArea
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class GlassVisualEffectAndroidCapabilityTest {

  @Test
  @Config(sdk = [32])
  fun unsupportedRuntimeShader_skipsExactRuntimePreparation() {
    val effect = GlassVisualEffect()
    val context = AndroidCapabilityContext()

    effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = isRuntimeShaderGlassSupported(),
    )
    effect.delegate = effect.updateDelegate()

    assertThat(effect.preparedRenderBudget is GlassRenderBudgetDecision.Runtime).isTrue()
    assertThat(effect.preparedRender).isNull()
    assertThat(effect.delegate is FallbackGlassDelegate).isTrue()
  }

  @Test
  @Config(sdk = [35])
  fun supportedRuntimeShader_buildsExactRuntimePreparation() {
    val effect = GlassVisualEffect()
    val context = AndroidCapabilityContext()

    effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = isRuntimeShaderGlassSupported(),
    )
    effect.delegate = effect.updateDelegate()

    assertThat(effect.preparedRender).isNotNull()
    assertThat(effect.delegate is RuntimeShaderGlassDelegate).isTrue()
  }
}

@OptIn(ExperimentalHazeApi::class)
private class AndroidCapabilityContext : VisualEffectContext {
  override val size: Size = Size(100f, 100f)
  override val position: Offset = Offset.Zero
  override val layerSize: Size = Size(100f, 100f)
  override val layerOffset: Offset = Offset.Zero
  override val rootBounds: Rect = Rect(Offset.Zero, size)
  override val inputScale: HazeInputScale = HazeInputScale.None
  override val windowId: Any? = null
  override val areas: List<HazeArea> = emptyList()
  override val state: HazeState? = null
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun positionOf(area: HazeArea): Offset = Offset.Zero

  override fun boundsOf(area: HazeArea): Rect? = null

  override fun requirePlatformContext(): PlatformContext = error("Unused")

  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = LayoutDirection.Ltr as T

  override fun requireGraphicsContext(): GraphicsContext = error("Unused")

  override fun invalidateDraw() = Unit
}
