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

class HazeTintTest {
  @Test
  fun colorTint_isSpecified_whenColorSpecified() {
    val tint = HazeTint(Color.Red)
    assertTrue(tint.isSpecified)
  }

  @Test
  fun colorTint_isNotSpecified_whenColorUnspecified() {
    val tint = HazeTint(Color.Unspecified)
    assertFalse(tint.isSpecified)
  }

  @Test
  fun brushTint_isSpecified() {
    val tint = HazeTint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))
    assertTrue(tint.isSpecified)
  }

  @Test
  fun unspecified_isNotSpecified() {
    assertFalse(HazeTint.Unspecified.isSpecified)
  }

  @Test
  fun colorTint_defaultBlendMode() {
    val tint = HazeTint(Color.Red)
    assertEquals(BlendMode.SrcOver, tint.blendMode)
  }

  @Test
  fun colorTint_customBlendMode() {
    val tint = HazeTint(Color.Red, BlendMode.Multiply)
    assertEquals(BlendMode.Multiply, tint.blendMode)
  }

  @Test
  fun colorTint_withColorFilter() {
    val colorFilter = ColorFilter.colorMatrix(ColorMatrix())
    val tint = HazeTint(Color.Red, colorFilter = colorFilter)
    assertEquals(colorFilter, tint.colorFilter)
  }

  @Test
  fun brushTint_withColorFilter() {
    val colorFilter = ColorFilter.tint(Color.Blue, BlendMode.Overlay)
    val brush = Brush.horizontalGradient(listOf(Color.Red, Color.Yellow))
    val tint = HazeTint(brush, colorFilter = colorFilter)
    assertEquals(colorFilter, tint.colorFilter)
  }

  @Test
  fun colorTint_equality() {
    val tint1 = HazeTint(Color.Red, BlendMode.SrcOver)
    val tint2 = HazeTint(Color.Red, BlendMode.SrcOver)
    assertEquals(tint1, tint2)
  }

  @Test
  fun colorTint_inequality_differentColor() {
    val tint1 = HazeTint(Color.Red)
    val tint2 = HazeTint(Color.Blue)
    assertNotEquals(tint1, tint2)
  }

  @Test
  fun colorTint_inequality_differentBlendMode() {
    val tint1 = HazeTint(Color.Red, BlendMode.SrcOver)
    val tint2 = HazeTint(Color.Red, BlendMode.Multiply)
    assertNotEquals(tint1, tint2)
  }

  @Test
  fun colorTint_inequality_differentColorFilter() {
    val cf1 = ColorFilter.tint(Color.Green)
    val cf2 = ColorFilter.lighting(Color.White, Color.Black)
    val tint1 = HazeTint(Color.Red, colorFilter = cf1)
    val tint2 = HazeTint(Color.Red, colorFilter = cf2)
    assertNotEquals(tint1, tint2)
  }

  @Test
  fun colorTint_hashCode() {
    val tint1 = HazeTint(Color.Red, BlendMode.SrcOver)
    val tint2 = HazeTint(Color.Red, BlendMode.SrcOver)
    assertEquals(tint1.hashCode(), tint2.hashCode())
  }

  @Test
  fun sealedInterface_typeChecking() {
    val colorTint = HazeTint(Color.Red)
    val brushTint = HazeTint(Brush.verticalGradient(listOf(Color.Red, Color.Blue)))

    assertTrue(colorTint is HazeTint.Color)
    assertTrue(brushTint is HazeTint.Brush)
    assertFalse(colorTint is HazeTint.Brush)
    assertFalse(brushTint is HazeTint.Color)
  }

  @Test
  fun colorTint_accessColorProperty() {
    val color = Color.Red
    val tint = HazeTint(color) as HazeTint.Color
    assertEquals(color, tint.color)
  }

  @Test
  fun brushTint_accessBrushProperty() {
    val brush = Brush.horizontalGradient(listOf(Color.Red, Color.Blue))
    val tint = HazeTint(brush) as HazeTint.Brush
    assertEquals(brush, tint.brush)
  }
}
