// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Matrix
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class MatrixTranslationTest {
  @Test
  fun isTranslationOnly_acceptsOnlyXyTranslations() {
    assertThat(Matrix().isTranslationOnly()).isTrue()
    val translated = Matrix().apply { translate(12f, -40f) }
    assertThat(translated.isTranslationOnly()).isTrue()
    assertThat(translated.values[Matrix.TranslateX]).isEqualTo(12f)
    assertThat(translated.values[Matrix.TranslateY]).isEqualTo(-40f)
    assertThat(Matrix().apply { values[Matrix.SkewX] = -0f }.isTranslationOnly()).isTrue()

    assertThat(Matrix().apply { translate(12f, -40f, 3f) }.isTranslationOnly()).isFalse()
    assertThat(Matrix().apply { scale(1.5f, 1f) }.isTranslationOnly()).isFalse()
    assertThat(Matrix().apply { rotateZ(10f) }.isTranslationOnly()).isFalse()
    assertThat(Matrix().apply { values[Matrix.Perspective0] = 0.01f }.isTranslationOnly()).isFalse()
    assertThat(Matrix().apply { values[Matrix.ScaleX] = Float.NaN }.isTranslationOnly()).isFalse()
  }
}
