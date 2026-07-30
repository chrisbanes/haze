// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class GlassRuntimeEffectAndroidCapabilityTest {

  @Test
  @Config(sdk = [32])
  fun unsupportedRuntimeShader_skipsExactRuntimePreparation() {
    val effect = GlassRuntimeEffect()
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
    val effect = GlassRuntimeEffect()
    val context = AndroidCapabilityContext()

    effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = isRuntimeShaderGlassSupported(),
    )
    effect.delegate = effect.updateDelegate()

    assertThat(effect.preparedRender).isNotNull()
    assertThat(effect.delegate is RuntimeShaderGlassDelegate).isTrue()
  }

  @Test
  @Config(sdk = [35])
  fun runtimeEffectFactoryChange_replacesRuntimeDelegate() {
    val effect = GlassRuntimeEffect()
    val context = AndroidCapabilityContext()
    effect.prepareRenderBudget(
      context,
      runtimeShaderSupported = isRuntimeShaderGlassSupported(),
    )
    effect.delegate = effect.updateDelegate()
    val original = effect.delegate

    effect.runtimeEffectFactory = GlassRuntimeEffectFactory { create -> create() }
    effect.delegate = effect.updateDelegate()

    assertThat(effect.delegate).isNotSameInstanceAs(original)
  }
}

@OptIn(InternalComposeUiApi::class, InternalHazeApi::class)
private class AndroidCapabilityContext :
  HazeEffectRuntimeDrawScope,
  DrawScope by CanvasDrawScope() {
  override val modifierSize: Size = Size(100f, 100f)
  override val modifierBounds: Rect = Rect(Offset.Zero, modifierSize)
  override val sampling: HazeSampling = HazeSampling.FullResolution
  override val layerSize: Size = Size(100f, 100f)
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = true
  override val inputSnapshot: HazeEffectInputSnapshot = AndroidCapabilityInputSnapshot
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun requirePlatformContext(): PlatformContext = error("Unused")

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = LayoutDirection.Ltr as T

  override fun requireGraphicsContext(): GraphicsContext = error("Unused")

  override fun drawInput() = Unit

  override fun DrawScope.drawInput() = Unit

  override fun invalidateDraw() = Unit
}

@OptIn(InternalHazeApi::class)
private object AndroidCapabilityInputSnapshot : HazeEffectInputSnapshot
