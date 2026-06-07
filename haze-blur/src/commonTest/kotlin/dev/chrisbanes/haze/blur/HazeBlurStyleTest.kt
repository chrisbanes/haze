// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import kotlin.test.Test

class HazeBlurStyleTest {

  @Test
  fun hazeBlurStyle_colorEffectsSnapshotsMutableInput() {
    val first = HazeColorEffect.tint(Color.Red)
    val second = HazeColorEffect.tint(Color.Blue)
    val input = mutableListOf(first)

    val style = HazeBlurStyle(colorEffects = input)

    input += second

    assertThat(style.colorEffects).containsExactly(first)
  }

  @Test
  fun blurVisualEffect_emptyColorEffectsViaStyleAreExplicitlySpecified() {
    val inherited = HazeBlurStyle(colorEffect = HazeColorEffect.tint(Color.Red))
    val style = HazeBlurStyle(colorEffects = emptyList())
    val effect = BlurVisualEffect().apply {
      compositionLocalStyle = inherited
      this.style = style
    }

    assertThat(effect.colorEffects.orEmpty()).isEmpty()
  }

  @Test
  fun blurVisualEffect_emptyColorEffectsClearsInheritedEffects() {
    val inherited = HazeBlurStyle(colorEffect = HazeColorEffect.tint(Color.Red))
    val effect = BlurVisualEffect().apply {
      compositionLocalStyle = inherited
      colorEffects = emptyList()
    }

    assertThat(effect.colorEffects.orEmpty()).isEmpty()
  }

  @Test
  fun blurVisualEffect_nullColorEffectsFallsBackToInheritedStyle() {
    val inherited = HazeBlurStyle(colorEffect = HazeColorEffect.tint(Color.Red))
    val effect = BlurVisualEffect().apply {
      compositionLocalStyle = inherited
      colorEffects = listOf(HazeColorEffect.tint(Color.Blue))
    }

    // Verify the direct override is active
    assertThat(effect.colorEffects.orEmpty()).containsExactly(HazeColorEffect.tint(Color.Blue))

    // Clear the direct override
    effect.colorEffects = null

    // Should fall back to inherited style
    assertThat(effect.colorEffects.orEmpty()).containsExactly(HazeColorEffect.tint(Color.Red))
  }
}
