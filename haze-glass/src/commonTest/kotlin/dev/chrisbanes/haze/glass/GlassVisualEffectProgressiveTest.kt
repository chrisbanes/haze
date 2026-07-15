// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class GlassVisualEffectProgressiveTest {

  private val localProgressive = HazeProgressive.verticalGradient(startIntensity = 0f, endIntensity = 1f)
  private val localProgressiveB = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
  private val styleProgressive = HazeProgressive.horizontalGradient(startIntensity = 0f, endIntensity = 1f)
  private val styleProgressiveB = HazeProgressive.horizontalGradient(startIntensity = 1f, endIntensity = 0f)
  private val directProgressive = HazeProgressive.RadialGradient(centerIntensity = 1f, radiusIntensity = 0f)

  @Test
  fun progressive_resolvesDirectThenStyleThenLocal() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        optics = GlassOptics(progressive = localProgressive),
      )
    }

    assertThat(effect.progressive).isEqualTo(localProgressive)

    effect.style = GlassStyle(
      optics = GlassOptics(progressive = styleProgressive),
    )
    assertThat(effect.progressive).isEqualTo(styleProgressive)

    effect.progressive = directProgressive
    assertThat(effect.progressive).isEqualTo(directProgressive)
  }

  @Test
  fun progressive_nullDirectValueDisablesInheritedProgressive() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        optics = GlassOptics(progressive = localProgressive),
      )
    }

    effect.progressive = null

    assertThat(effect.progressive).isNull()
  }

  @Test
  fun progressive_clearDirectOverrideRestoresInheritedProgressive() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        optics = GlassOptics(progressive = localProgressive),
      )
      progressive = null
    }

    effect.clearProgressiveOverride()

    assertThat(effect.progressive).isEqualTo(localProgressive)
  }

  @Test
  fun progressive_copyConstructorPreservesLocalInheritedProgressive() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        optics = GlassOptics(progressive = localProgressive),
      )
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.progressive).isEqualTo(localProgressive)

    copy.compositionLocalStyle = GlassStyle(
      optics = GlassOptics(progressive = localProgressiveB),
    )

    assertThat(copy.progressive).isEqualTo(localProgressiveB)
  }

  @Test
  fun progressive_copyConstructorPreservesNullOverrideOverLocalInheritedProgressive() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        optics = GlassOptics(progressive = localProgressive),
      )
      progressive = null
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.progressive).isNull()

    copy.clearProgressiveOverride()

    assertThat(copy.progressive).isEqualTo(localProgressive)
  }

  @Test
  fun progressive_copyConstructorPreservesDirectProgressiveOverride() {
    val effect = GlassVisualEffect().apply {
      compositionLocalStyle = GlassStyle(
        optics = GlassOptics(progressive = localProgressive),
      )
      style = GlassStyle(
        optics = GlassOptics(progressive = styleProgressive),
      )
      progressive = directProgressive
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.progressive).isEqualTo(directProgressive)

    copy.compositionLocalStyle = GlassStyle(
      optics = GlassOptics(progressive = localProgressiveB),
    )
    copy.style = GlassStyle(
      optics = GlassOptics(progressive = styleProgressiveB),
    )

    assertThat(copy.progressive).isEqualTo(directProgressive)
  }

  @Test
  fun progressive_copyConstructorPreservesStyleInheritedProgressive() {
    val effect = GlassVisualEffect().apply {
      style = GlassStyle(
        optics = GlassOptics(progressive = styleProgressive),
      )
    }

    val copy = GlassVisualEffect(effect)

    assertThat(copy.progressive).isEqualTo(styleProgressive)

    copy.style = GlassStyle(
      optics = GlassOptics(progressive = styleProgressiveB),
    )

    assertThat(copy.progressive).isEqualTo(styleProgressiveB)
  }

  @Test
  fun progressive_marksDirtyWhenChanged() {
    val effect = GlassVisualEffect()

    effect.progressive = directProgressive

    assertThat(GlassDirtyFields.stringify(effect.dirtyTracker)).contains("Progressive")
  }
}
