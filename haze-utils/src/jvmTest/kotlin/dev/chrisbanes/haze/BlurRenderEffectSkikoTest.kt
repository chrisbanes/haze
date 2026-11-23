// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.TileMode
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlin.test.Test
import org.jetbrains.skia.Color
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

class BlurRenderEffectSkikoTest {

  @Test
  fun createBlurRenderEffect_rejectsNonFiniteRadii() {
    val nonFinitePairs = listOf(
      Float.NaN to 0f,
      0f to Float.NaN,
      Float.NaN to 8f,
      8f to Float.NaN,
      Float.NaN to Float.NaN,
      Float.POSITIVE_INFINITY to 0f,
      0f to Float.POSITIVE_INFINITY,
      Float.POSITIVE_INFINITY to 8f,
      8f to Float.POSITIVE_INFINITY,
      Float.POSITIVE_INFINITY to Float.POSITIVE_INFINITY,
      Float.NEGATIVE_INFINITY to 0f,
      0f to Float.NEGATIVE_INFINITY,
      Float.NEGATIVE_INFINITY to 8f,
      8f to Float.NEGATIVE_INFINITY,
      Float.NEGATIVE_INFINITY to Float.NEGATIVE_INFINITY,
    )

    for ((radiusX, radiusY) in nonFinitePairs) {
      assertFailure {
        createBlurRenderEffect(radiusX, radiusY, TileMode.Clamp)
      }.isInstanceOf<IllegalArgumentException>()
    }
  }

  @Test
  fun createBlurRenderEffect_returnsNullWhenBothRadiiAreNonPositive() {
    val noOpPairs = listOf(
      0f to 0f,
      -1f to 0f,
      0f to -1f,
      -4f to -4f,
    )

    for ((radiusX, radiusY) in noOpPairs) {
      assertThat(createBlurRenderEffect(radiusX, radiusY, TileMode.Clamp)).isNull()
    }
  }

  @Test
  fun createBlurRenderEffect_preservesZeroYAxisUnfiltered() {
    val effect = createBlurRenderEffect(radiusX = 8f, radiusY = 0f, tileMode = TileMode.Clamp)
    assertThat(effect).isNotNull()

    val horizontalUnfiltered = renderStripe(horizontal = true, imageFilter = null)
    val horizontalFiltered = renderStripe(horizontal = true, imageFilter = effect)
    assertThat(horizontalFiltered).isEqualTo(horizontalUnfiltered)

    val verticalUnfiltered = renderStripe(horizontal = false, imageFilter = null)
    val verticalFiltered = renderStripe(horizontal = false, imageFilter = effect)
    assertThat(verticalFiltered).isNotEqualTo(verticalUnfiltered)
  }

  @Test
  fun createBlurRenderEffect_preservesZeroXAxisUnfiltered() {
    val effect = createBlurRenderEffect(radiusX = 0f, radiusY = 8f, tileMode = TileMode.Clamp)
    assertThat(effect).isNotNull()

    val verticalUnfiltered = renderStripe(horizontal = false, imageFilter = null)
    val verticalFiltered = renderStripe(horizontal = false, imageFilter = effect)
    assertThat(verticalFiltered).isEqualTo(verticalUnfiltered)

    val horizontalUnfiltered = renderStripe(horizontal = true, imageFilter = null)
    val horizontalFiltered = renderStripe(horizontal = true, imageFilter = effect)
    assertThat(horizontalFiltered).isNotEqualTo(horizontalUnfiltered)
  }

  private fun renderStripe(horizontal: Boolean, imageFilter: ImageFilter?): List<Byte> {
    Surface.makeRasterN32Premul(32, 32).use { source ->
      source.canvas.clear(Color.BLACK)
      Paint().use { fill ->
        fill.color = Color.WHITE
        val stripe = if (horizontal) {
          Rect.makeLTRB(0f, 12f, 32f, 20f)
        } else {
          Rect.makeLTRB(12f, 0f, 20f, 32f)
        }
        source.canvas.drawRect(stripe, fill)
      }
      source.makeImageSnapshot().use { input ->
        Surface.makeRasterN32Premul(32, 32).use { dest ->
          Paint().use { paint ->
            paint.imageFilter = imageFilter
            dest.canvas.drawImage(input, 0f, 0f, paint)
          }
          dest.makeImageSnapshot().use { output ->
            output.encodeToData()!!.use { data -> return data.bytes.toList() }
          }
        }
      }
    }
  }
}
