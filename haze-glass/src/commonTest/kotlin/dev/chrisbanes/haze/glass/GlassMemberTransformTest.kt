// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GlassMemberTransformTest : ContextTest() {
  @Test
  fun isTranslationOnlyTransform_acceptsSignedZeroIdentityEntries() {
    val signed = Matrix().apply {
      values[Matrix.SkewX] = -0f
      values[Matrix.Perspective0] = -0f
    }
    assertThat((-0f).toRawBits()).isNotEqualTo(0f.toRawBits())
    assertThat(signed.values[Matrix.SkewX] == 0f).isTrue()
    assertThat(signed.values.contentEquals(Matrix().values)).isFalse()
    assertThat(signed.isTranslationOnlyTransform()).isTrue()
    assertThat(Matrix().apply { translate(12f, -40f, 3f) }.isTranslationOnlyTransform()).isTrue()
  }

  @Test
  fun isTranslationOnlyTransform_rejectsRotationScaleSkewAndPerspective() {
    assertThat(Matrix().apply { rotateZ(10f) }.isTranslationOnlyTransform()).isFalse()
    assertThat(Matrix().apply { scale(1.5f, 1f) }.isTranslationOnlyTransform()).isFalse()
    assertThat(Matrix().apply { values[Matrix.SkewX] = 0.1f }.isTranslationOnlyTransform()).isFalse()
    assertThat(Matrix().apply { values[Matrix.SkewY] = 2.4492937e-16f }.isTranslationOnlyTransform()).isFalse()
    assertThat(Matrix().apply { values[Matrix.Perspective0] = 0.01f }.isTranslationOnlyTransform()).isFalse()
    assertThat(Matrix().apply { values[Matrix.ScaleX] = Float.NaN }.isTranslationOnlyTransform()).isFalse()
  }

  @Test
  fun layoutTransform_translationOnlyPathsEmitNoSignedZeros() = runComposeUiTest {
    var owner: LayoutCoordinates? = null
    val members = arrayOfNulls<LayoutCoordinates>(3)
    setContent {
      Box(Modifier.fillMaxSize().padding(100.dp).onPlaced { owner = it }) {
        Box(Modifier.offset((-40).dp, (-60).dp).size(20.dp).onPlaced { members[0] = it })
        Box(
          Modifier.offset(40.dp, 60.dp).size(20.dp).graphicsLayer {
            translationX = -12f
            rotationZ = 0f
          }.onPlaced { members[1] = it },
        )
        Box(Modifier.graphicsLayer { translationY = -30f }) {
          Box(Modifier.offset(5.dp, 7.dp).size(20.dp).graphicsLayer { scaleX = 1f }.onPlaced { members[2] = it })
        }
      }
    }
    waitForIdle()
    for (member in members) {
      val matrix = Matrix()
      checkNotNull(owner).transformFrom(checkNotNull(member), matrix)
      val negativeZeros = matrix.values.indices.filter { matrix.values[it].toRawBits() == (-0f).toRawBits() }
      assertThat(negativeZeros).isEqualTo(emptyList())
      assertThat(matrix.isTranslationOnlyTransform()).isTrue()
    }
  }
}
