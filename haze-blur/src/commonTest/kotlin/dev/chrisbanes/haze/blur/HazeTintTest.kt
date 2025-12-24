// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HazeColorEffectTest {
  @Test
  fun colorTint_isSpecified_whenColorSpecified() {
    val effect = HazeColorEffect.tint(Color.Red)
    assertTrue(effect.isSpecified)
  }

  @Test
  fun colorTint_isNotSpecified_whenColorUnspecified() {
    val effect = HazeColorEffect.tint(Color.Unspecified)
    assertFalse(effect.isSpecified)
  }

  @Test
  fun brushTint_isSpecified() {
    val effect = HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))
    assertTrue(effect.isSpecified)
  }

  @Test
  fun colorFilter_isSpecified() {
    val effect = HazeColorEffect.colorFilter(ColorFilter.colorMatrix(ColorMatrix()))
    assertTrue(effect.isSpecified)
  }

  @Test
  fun unspecified_isNotSpecified() {
    assertFalse(HazeColorEffect.Unspecified.isSpecified)
  }

  @Test
  fun colorTint_defaultBlendMode() {
    val effect = HazeColorEffect.tint(Color.Red)
    assertEquals(BlendMode.SrcOver, effect.blendMode)
  }

  @Test
  fun colorTint_customBlendMode() {
    val effect = HazeColorEffect.tint(Color.Red, BlendMode.Multiply)
    assertEquals(BlendMode.Multiply, effect.blendMode)
  }

  @Test
  fun colorFilter_withBlendMode() {
    val colorFilter = ColorFilter.colorMatrix(ColorMatrix())
    val effect = HazeColorEffect.colorFilter(colorFilter, BlendMode.Multiply)
    assertEquals(BlendMode.Multiply, effect.blendMode)
  }

  @Test
  fun colorTint_equality() {
    val effect1 = HazeColorEffect.tint(Color.Red, BlendMode.SrcOver)
    val effect2 = HazeColorEffect.tint(Color.Red, BlendMode.SrcOver)
    assertEquals(effect1, effect2)
  }

  @Test
  fun colorTint_inequality_differentColor() {
    val effect1 = HazeColorEffect.tint(Color.Red)
    val effect2 = HazeColorEffect.tint(Color.Blue)
    assertNotEquals(effect1, effect2)
  }

  @Test
  fun colorTint_inequality_differentBlendMode() {
    val effect1 = HazeColorEffect.tint(Color.Red, BlendMode.SrcOver)
    val effect2 = HazeColorEffect.tint(Color.Red, BlendMode.Multiply)
    assertNotEquals(effect1, effect2)
  }

  @Test
  fun colorFilter_inequality_differentFilter() {
    val cf1 = ColorFilter.tint(Color.Green)
    val cf2 = ColorFilter.lighting(Color.White, Color.Black)
    val effect1 = HazeColorEffect.colorFilter(cf1)
    val effect2 = HazeColorEffect.colorFilter(cf2)
    assertNotEquals(effect1, effect2)
  }

  @Test
  fun colorTint_hashCode() {
    val effect1 = HazeColorEffect.tint(Color.Red, BlendMode.SrcOver)
    val effect2 = HazeColorEffect.tint(Color.Red, BlendMode.SrcOver)
    assertEquals(effect1.hashCode(), effect2.hashCode())
  }

  @Test
  fun sealedInterface_typeChecking() {
    val colorTint = HazeColorEffect.tint(Color.Red)
    val brushTint = HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))
    val colorFilter = HazeColorEffect.colorFilter(ColorFilter.tint(Color.Blue))

    assertTrue(colorTint is HazeColorEffect.TintColor)
    assertTrue(brushTint is HazeColorEffect.TintBrush)
    assertTrue(colorFilter is HazeColorEffect.ColorFilter)
    assertFalse(colorTint is HazeColorEffect.TintBrush)
    assertFalse(brushTint is HazeColorEffect.TintColor)
  }

  @Test
  fun colorTint_accessColorProperty() {
    val color = Color.Red
    val effect = HazeColorEffect.tint(color) as HazeColorEffect.TintColor
    assertEquals(color, effect.color)
  }

  @Test
  fun brushTint_accessBrushProperty() {
    val brush = Brush.horizontalGradient(listOf(Color.Red, Color.Blue))
    val effect = HazeColorEffect.tint(brush) as HazeColorEffect.TintBrush
    assertEquals(brush, effect.brush)
  }

  @Test
  fun colorFilter_accessColorFilterProperty() {
    val colorFilter = ColorFilter.colorMatrix(ColorMatrix())
    val effect = HazeColorEffect.colorFilter(colorFilter) as HazeColorEffect.ColorFilter
    assertEquals(colorFilter, effect.colorFilter)
  }

  // Backward compatibility tests
  @Test
  @Suppress("DEPRECATION")
  fun backwardCompatibility_HazeTint_typeAlias() {
    val effect: HazeTint = HazeTint(Color.Red)
    assertTrue(effect is HazeColorEffect)
  }

  @Test
  @Suppress("DEPRECATION")
  fun backwardCompatibility_HazeTint_factoryFunction() {
    val colorEffect = HazeTint(Color.Red)
    val brushEffect = HazeTint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))
    
    assertTrue(colorEffect.isSpecified)
    assertTrue(brushEffect.isSpecified)
  }
}
