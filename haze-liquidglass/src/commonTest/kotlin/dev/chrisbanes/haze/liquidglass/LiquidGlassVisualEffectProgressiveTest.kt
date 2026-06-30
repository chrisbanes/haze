// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class LiquidGlassVisualEffectProgressiveTest {

  private val localProgressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
  private val localProgressiveB = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
  private val styleProgressive = HazeProgressive.horizontalGradient(startIntensity = 0f, endIntensity = 1f)
  private val styleProgressiveB = HazeProgressive.horizontalGradient(startIntensity = 1f, endIntensity = 0f)
  private val directProgressive = HazeProgressive.RadialGradient(centerIntensity = 1f, radiusIntensity = 0f)

  @Test
  fun progressive_resolvesDirectThenStyleThenLocal() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = localProgressive),
      )
    }

    assertThat(effect.progressive).isEqualTo(localProgressive)

    effect.style = LiquidGlassStyle(
      optics = LiquidGlassOptics(progressive = styleProgressive),
    )
    assertThat(effect.progressive).isEqualTo(styleProgressive)

    effect.progressive = directProgressive
    assertThat(effect.progressive).isEqualTo(directProgressive)
  }

  @Test
  fun progressive_nullDirectValueDisablesInheritedProgressive() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = localProgressive),
      )
    }

    effect.progressive = null

    assertThat(effect.progressive).isNull()
  }

  @Test
  fun progressive_clearDirectOverrideRestoresInheritedProgressive() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = localProgressive),
      )
      progressive = null
    }

    effect.clearProgressiveOverride()

    assertThat(effect.progressive).isEqualTo(localProgressive)
  }

  @Test
  fun progressive_copyConstructorPreservesLocalInheritedProgressive() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = localProgressive),
      )
    }

    val copy = LiquidGlassVisualEffect(effect)

    assertThat(copy.progressive).isEqualTo(localProgressive)

    copy.compositionLocalStyle = LiquidGlassStyle(
      optics = LiquidGlassOptics(progressive = localProgressiveB),
    )

    assertThat(copy.progressive).isEqualTo(localProgressiveB)
  }

  @Test
  fun progressive_copyConstructorPreservesNullOverrideOverLocalInheritedProgressive() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = localProgressive),
      )
      progressive = null
    }

    val copy = LiquidGlassVisualEffect(effect)

    assertThat(copy.progressive).isNull()

    copy.clearProgressiveOverride()

    assertThat(copy.progressive).isEqualTo(localProgressive)
  }

  @Test
  fun progressive_copyConstructorPreservesDirectProgressiveOverride() {
    val effect = LiquidGlassVisualEffect().apply {
      compositionLocalStyle = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = localProgressive),
      )
      style = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = styleProgressive),
      )
      progressive = directProgressive
    }

    val copy = LiquidGlassVisualEffect(effect)

    assertThat(copy.progressive).isEqualTo(directProgressive)

    copy.compositionLocalStyle = LiquidGlassStyle(
      optics = LiquidGlassOptics(progressive = localProgressiveB),
    )
    copy.style = LiquidGlassStyle(
      optics = LiquidGlassOptics(progressive = styleProgressiveB),
    )

    assertThat(copy.progressive).isEqualTo(directProgressive)
  }

  @Test
  fun progressive_copyConstructorPreservesStyleInheritedProgressive() {
    val effect = LiquidGlassVisualEffect().apply {
      style = LiquidGlassStyle(
        optics = LiquidGlassOptics(progressive = styleProgressive),
      )
    }

    val copy = LiquidGlassVisualEffect(effect)

    assertThat(copy.progressive).isEqualTo(styleProgressive)

    copy.style = LiquidGlassStyle(
      optics = LiquidGlassOptics(progressive = styleProgressiveB),
    )

    assertThat(copy.progressive).isEqualTo(styleProgressiveB)
  }

  @Test
  fun progressive_marksDirtyWhenChanged() {
    val effect = LiquidGlassVisualEffect()

    effect.progressive = directProgressive

    assertThat(LiquidGlassDirtyFields.stringify(effect.dirtyTracker)).contains("Progressive")
  }
}
