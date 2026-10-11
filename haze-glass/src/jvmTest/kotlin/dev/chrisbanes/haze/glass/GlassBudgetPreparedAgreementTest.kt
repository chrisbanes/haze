// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
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
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

/**
 * The budget plan is built before the prepared render, so the two must agree on which layers are
 * active. Compares non-fused plans, so it only holds on JVM (Android fuses detail into one layer).
 */
class GlassBudgetPreparedAgreementTest {
  @Test
  fun budgetAndPreparedPlans_agreeAtTheRefractionDetailThreshold() {
    val below = detailStyle(refractionStrength = .008f)
    val above = detailStyle(refractionStrength = .009f)

    assertThat(isDetailActive(below)).isFalse()
    assertThat(isDetailActive(above)).isTrue()
    assertThat(budgetPlan(below).hasKind(GlassRetainedLayerKind.RefractionDetail)).isFalse()
    assertThat(budgetPlan(above).hasKind(GlassRetainedLayerKind.RefractionDetail)).isTrue()
    assertThat(budgetPlan(below).layers).isEqualTo(preparedPlan(below).layers)
    assertThat(budgetPlan(above).layers).isEqualTo(preparedPlan(above).layers)
  }

  @Test
  fun budgetAndPreparedPlans_agreeWhenInputScaleIsReduced() {
    val style = detailStyle(refractionStrength = .009f)

    assertThat(budgetPlan(style, scale = 0.5f).layers)
      .isEqualTo(preparedPlan(style, scale = 0.5f).layers)
  }

  @Test
  fun budgetAndPreparedPlans_agreeForNoRimSpecularOnlyAndShadowOnly() {
    listOf(
      Triple(0f, Color.Transparent, false),
      Triple(0.5f, Color.Transparent, true),
      Triple(0f, Color.Black.copy(alpha = 0.14f), true),
    ).forEach { (specular, shadow, expectedRim) ->
      val style = detailStyle(refractionStrength = .009f) {
        specularIntensity(specular)
        edgeShadow(shadow)
      }

      assertThat(budgetPlan(style).hasKind(GlassRetainedLayerKind.Rim)).isEqualTo(expectedRim)
      assertThat(budgetPlan(style).layers).isEqualTo(preparedPlan(style).layers)
    }
  }

  private fun detailStyle(
    refractionStrength: Float,
    extra: GlassStyleScope.() -> Unit = {},
  ): GlassStyle = GlassRuntimeEffect().style.then {
    optics(
      GlassOptics(
        refractionStrength = refractionStrength,
        refractionHeightFraction = 0.2f,
        refractionDisplacement = 20.dp,
        refractionDetailIntensity = 0.76f,
        depth = OpticalSizeValue.Fixed(0f),
      ),
    )
    edgeSoftness(4.dp)
    extra()
  }

  private fun isDetailActive(style: GlassStyle): Boolean {
    val resolved = resolveGlassStyle(
      effect = effectWith(style),
      materialSizePx = MaterialSize,
      density = Density(1f),
      layoutDirection = LayoutDirection.Ltr,
    )
    val params = buildGlassRenderParams(
      resolved,
      GlassCoordinates(MaterialSize, Offset.Zero, MaterialSize, 1f),
    )
    return params.activeRefractionDetailEffectKey(params.refractionDetailIntensity) != null
  }

  /** The plan from the budget lambda, which `runtimeShaderSupported = true` would validate away. */
  private fun budgetPlan(style: GlassStyle, scale: Float = 1f): GlassRetainedLayerPlan {
    val effect = effectWith(style)
    val decision = effect.prepareRenderBudget(
      AgreementContext(),
      runtimeShaderSupported = false,
      requestedScaleOverride = scale,
    )
    assertThat(decision).isInstanceOf<GlassRenderBudgetDecision.Runtime>()
    return (decision as GlassRenderBudgetDecision.Runtime).plan
  }

  private fun preparedPlan(style: GlassStyle, scale: Float = 1f): GlassRetainedLayerPlan {
    val effect = effectWith(style)
    effect.prepareRenderBudget(
      AgreementContext(),
      runtimeShaderSupported = true,
      requestedScaleOverride = scale,
    )
    assertThat(effect.preparedRender).isNotNull()
    return checkNotNull(effect.preparedRender).plan
  }

  private fun effectWith(style: GlassStyle) = GlassRuntimeEffect().apply { this.style = style }

  private fun GlassRetainedLayerPlan.hasKind(kind: GlassRetainedLayerKind) =
    layers.any { it.kind == kind }

  private companion object {
    val MaterialSize = Size(200f, 100f)
  }
}

@OptIn(InternalComposeUiApi::class, InternalHazeApi::class)
private class AgreementContext(
  size: Size = Size(200f, 100f),
) : HazeEffectRuntimeDrawScope,
  DrawScope by CanvasDrawScope() {
  override val modifierSize: Size = size
  override val modifierBounds: Rect = Rect(Offset.Zero, size)
  override val sampling: HazeSampling = HazeSampling.Default
  override val layerSize: Size = size
  override val layerOffset: Offset = Offset.Zero
  override val hasDrawableInput: Boolean = true
  override val inputSnapshot: HazeEffectInputSnapshot = AgreementInputSnapshot
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun requirePlatformContext(): PlatformContext = error("Unused")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = LayoutDirection.Ltr as T

  override fun requireGraphicsContext(): GraphicsContext = error("Unused")

  override fun drawInput() = Unit

  override fun DrawScope.drawInput() = Unit

  override fun invalidateDraw() = Unit
}

@OptIn(InternalHazeApi::class)
private object AgreementInputSnapshot : HazeEffectInputSnapshot
