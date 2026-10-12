// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassLayerPaddingCacheTest {
  private val rect = Rect(0f, 0f, 200f, 100f)
  private val density = Density(1f)

  private fun effect(configure: GlassRuntimeEffect.() -> Unit = {}) = GlassRuntimeEffect()
    .apply { appearanceReader = { GlassSystemAppearance.Light } }
    .apply(configure)

  // A fresh effect never has a warm cache, so its bounds are the uncached result.
  private fun uncachedBounds(
    rect: Rect = this.rect,
    density: Density = this.density,
    configure: GlassRuntimeEffect.() -> Unit = {},
  ): Rect = effect(configure).calculateLayerBounds(rect, density)

  private fun fixedOptics(blur: Int, displacement: Int) = GlassOptics(
    blurRadius = OpticalSizeValue.Fixed(blur.dp),
    refractionDisplacement = displacement.dp,
  )

  @Test
  fun calculateLayerBounds_unchangedInputs_resolvesStyleOnce() {
    val effect = effect()

    val results = List(6) { effect.calculateLayerBounds(rect, density) }

    assertThat(results.distinct()).isEqualTo(listOf(uncachedBounds()))
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(1)
  }

  @Test
  fun calculateLayerBounds_freshDensityObjectWithSameValues_resolvesStyleOnce() {
    val effect = effect()

    // Layout wraps the node's Density in a new delegating scope for every call.
    repeat(4) {
      effect.calculateLayerBounds(rect, object : Density by Density(1f, 1f) {})
    }

    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(1)
  }

  @Test
  fun calculateLayerBounds_opticsChangedTwiceWithoutDraw_recomputesEachTime() {
    val effect = effect()
    val variants = listOf(fixedOptics(8, 10), fixedOptics(20, 10), fixedOptics(32, 10))

    variants.forEachIndexed { index, optics ->
      effect.style = effect.style.then { optics(optics) }

      assertThat(effect.calculateLayerBounds(rect, density))
        .isEqualTo(uncachedBounds { style = style.then { optics(optics) } })
      assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(index + 1)
    }
  }

  @Test
  fun calculateLayerBounds_otherPaddingInputsChanged_recompute() {
    val effect = effect()
    var previous = effect.calculateLayerBounds(rect, density)
    var expectedCount = 1
    val changes = listOf<GlassRuntimeEffect.() -> Unit>(
      { style = style.then { edgeSoftness(24.dp) } },
      { style = style.then { chromaticAberrationStrength(0.8f) } },
      { style = style.then { optics(fixedOptics(40, 15)) } },
      { accessibilitySettings = GlassAccessibilitySettings(reduceTransparency = true) },
    )

    changes.forEach { change ->
      effect.change()
      val bounds = effect.calculateLayerBounds(rect, density)
      expectedCount++

      assertThat(bounds).isNotEqualTo(previous)
      assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(expectedCount)
      previous = bounds
    }
  }

  @Test
  fun calculateLayerBounds_sizeOrDensityChanged_recomputes() {
    val effect = effect()
    val large = Rect(0f, 0f, 900f, 500f)
    val first = effect.calculateLayerBounds(rect, density)

    val sized = effect.calculateLayerBounds(large, density)

    assertThat(sized).isNotEqualTo(first)
    assertThat(sized).isEqualTo(uncachedBounds(rect = large))
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(2)

    val dense = effect.calculateLayerBounds(large, Density(3f))

    assertThat(dense).isNotEqualTo(sized)
    assertThat(dense).isEqualTo(uncachedBounds(rect = large, density = Density(3f)))
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(3)
  }

  @Test
  fun calculateLayerBounds_originOnlyChange_reusesPadding() {
    val effect = effect()
    val first = effect.calculateLayerBounds(rect, density)

    val moved = effect.calculateLayerBounds(rect.translate(50f, 30f), density)

    assertThat(moved).isEqualTo(first.translate(50f, 30f))
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(1)
  }

  @Test
  fun calculateLayerBounds_interactionRefractionChanged_recomputes() {
    val base: GlassStyleScope.() -> Unit = {
      optics(
        GlassOptics(
          refractionStrength = 0.4f,
          refractionDisplacement = 40.dp,
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
    }
    val effect = effect {
      style = style.then(base)
      updateStyleInteractionSlots()
    }
    val idle = effect.calculateLayerBounds(rect, density)

    effect.style = effect.style.then { pressed { refractionMultiplier(1.8f) } }
    effect.updateStyleInteractionSlots()
    val pressed = effect.calculateLayerBounds(rect, density)

    assertThat(pressed.width).isGreaterThan(idle.width)
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(2)

    effect.style = GlassStyle.then(base)
    effect.updateStyleInteractionSlots()

    assertThat(effect.calculateLayerBounds(rect, density)).isEqualTo(idle)
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(3)
  }

  @Test
  fun calculateLayerBounds_mergedFieldChanged_recomputes() {
    val effect = effect()
    fun field(opticalSide: Float) = mergedField(
      mergedMember(0f, 0f, 40f, 40f),
      opticalSize = Size(opticalSide, opticalSide),
    )

    effect.mergedField = field(20f)
    val small = effect.calculateLayerBounds(rect, density)
    effect.mergedField = field(400f)
    val large = effect.calculateLayerBounds(rect, density)

    assertThat(large).isNotEqualTo(small)
    assertThat(large).isEqualTo(uncachedBounds { mergedField = field(400f) })
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(2)
  }

  @Test
  fun calculateLayerBounds_containerSurface_usesSnapshotMaterialAndSkipsCache() {
    val effect = effect()
    val scope = PaddingLifecycleScope(Size(rect.width, rect.height))
    effect.attach(scope)
    val source = effect()
    val field = mergedField(mergedMember(0f, 0f, rect.width, rect.height))

    fun snapshot(blur: Int): GlassSurfaceSnapshot {
      source.style = GlassStyle.then { optics(fixedOptics(blur, 15)) }
      val material = resolveGlassStyle(source, rect.size, density, LayoutDirection.Ltr)
      val key = glassMergeKey(HazeInput.Content, true, 1f, source, material, LayoutDirection.Ltr)
      return GlassSurfaceSnapshot(key, material, field, field.opticalSize)
    }

    fun expectedBounds(snapshot: GlassSurfaceSnapshot): Rect {
      val material = snapshot.material
      return rect.inflate(
        calculateGlassSamplePaddingPx(
          blurRadiusPx = if (material.resolvedOptics.depth <= 0f) 0f else material.resolvedOptics.blurRadiusPx,
          refractionScale = material.resolvedOptics.refractionScalePx,
          refractionStrength = material.resolvedOptics.refractionStrength.coerceIn(0f, 1f),
          chromaticAberrationStrength = material.chromaticAberrationStrength,
          edgeSoftnessPx = material.edgeSoftnessPx,
          foregroundOutsetPx = 0f,
        ),
      )
    }

    fun update(snapshot: GlassSurfaceSnapshot?) = effect.update(
      scope,
      GlassNodeConfiguration(style = GlassStyle, interactionSource = null, surfaceSnapshot = snapshot),
      HazeSampling.Default,
    )

    val small = snapshot(8)
    val large = snapshot(32)
    update(small)
    val smallBounds = effect.calculateLayerBounds(rect, density)
    update(large)
    val largeBounds = effect.calculateLayerBounds(rect, density)

    assertThat(smallBounds).isEqualTo(expectedBounds(small))
    assertThat(largeBounds).isEqualTo(expectedBounds(large))
    assertThat(largeBounds).isNotEqualTo(smallBounds)
    assertThat(effect.layerPaddingResolveCountForTest).isEqualTo(0)

    update(null)

    assertThat(effect.calculateLayerBounds(rect, density)).isEqualTo(uncachedBounds())
    effect.detach()
  }
}

private class PaddingLifecycleScope(
  override val modifierSize: Size,
) : HazeEffectLifecycleScope {
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun requirePlatformContext(): PlatformContext = error("Unused")

  override fun requireGraphicsContext(): GraphicsContext = error("Unused")

  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalHazePerformanceMode -> HazePerformanceMode.Default
    LocalGlassStyle -> GlassStyle
    LocalGlassAccessibilitySettings -> GlassAccessibilitySettings()
    LocalLayoutDirection -> LayoutDirection.Ltr
    else -> error("Unused composition local")
  } as T

  override fun invalidateDraw() = Unit

  override fun invalidateLayerBounds() = Unit
}
