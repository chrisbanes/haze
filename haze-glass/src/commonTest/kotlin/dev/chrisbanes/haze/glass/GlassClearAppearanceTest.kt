// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import kotlin.test.Test

class GlassClearAppearanceTest {
  @Test
  fun clear_legacyOverridesMatchFormerDarkResponseInBothAppearances() {
    val legacy = GlassStyle.clear.then {
      specularIntensity(0.42f)
      edgeShadow(Color.Black.copy(alpha = 0.25f))
      ambientResponse(0.22f)
      contrast(0.12f)
      whitePoint(-0.18f)
      chromaMultiplier(1f)
    }
    for (appearance in GlassSystemAppearance.entries) {
      val former = resolveGlassStyleValues(GlassStyle, GlassStyle.clearDark, appearance)
      former.isClear = true
      assertThat(resolveGlassStyleValues(GlassStyle, legacy, appearance)).isEqualTo(former)
    }
  }

  @Test
  fun clear_localAndExplicitWritesPreserveOrderAndIdentity() {
    val local = GlassStyle { whitePoint(0.3f) }
    val after = GlassStyle.clear.then { whitePoint(0.2f) }
    val before = GlassStyle { whitePoint(0.2f) }.then(GlassStyle.clear)
    for (appearance in GlassSystemAppearance.entries) {
      val expected = resolveGlassStyleValues(GlassStyle, GlassStyle.clearLight, appearance)
      val resolvedAfter = resolveGlassStyleValues(local, after, appearance)
      val resolvedBefore = resolveGlassStyleValues(local, before, appearance)
      assertThat(resolvedAfter.whitePoint).isEqualTo(0.2f)
      assertThat(resolvedBefore.whitePoint).isEqualTo(expected.whitePoint)
      assertThat(resolvedAfter.isClear).isTrue()
      assertThat(resolvedBefore.isClear).isTrue()
    }
  }

  @Test
  fun clear_reducedTransparencyUsesActualAppearanceAndExplicitFallback() {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle.clear.then {
        fallbackColor(Color.Red)
        tint(Color.Green)
        backgroundColor(Color.Yellow)
      }
      accessibilitySettings = GlassAccessibilitySettings(reduceTransparency = true)
    }
    fun base() = resolveGlassStyle(effect, Size(200f, 200f), Density(1f), LayoutDirection.Ltr).fallbackBaseColor
    effect.systemAppearance = GlassSystemAppearance.Light
    assertThat(base()).isEqualTo(Color.White)
    effect.systemAppearance = GlassSystemAppearance.Dark
    assertThat(base()).isEqualTo(Color.Black)
    effect.systemAppearance = GlassSystemAppearance.Light
    assertThat(base()).isEqualTo(Color.White)
    effect.accessibilitySettings = GlassAccessibilitySettings(reduceTransparency = true, reducedTransparencyFallbackColor = Color.Blue)
    assertThat(base()).isEqualTo(Color.Blue)
  }
}
