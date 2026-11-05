// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import assertk.assertions.prop
import kotlin.test.Test

class ContinuousRoundedCornerShapeTest {

  @Test
  fun factories_resolveCornerSizesWithFullSmoothingByDefault() {
    val all = CornerSize(16.dp)
    assertThat(ContinuousRoundedCornerShape(16.dp)).isEqualTo(ContinuousRoundedCornerShape(all, all, all, all, 1f))
    assertThat(ContinuousRoundedCornerShape(all)).isEqualTo(ContinuousRoundedCornerShape(all, all, all, all, 1f))
    assertThat(ContinuousRoundedCornerShape(16f).topStart).isEqualTo(CornerSize(16f))
    assertThat(ContinuousRoundedCornerShape(percent = 50).topStart).isEqualTo(CornerSize(50))
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = 0.6f).smoothing).isEqualTo(0.6f)

    val dp = ContinuousRoundedCornerShape(topStart = 1.dp, topEnd = 2.dp, bottomEnd = 3.dp, bottomStart = 4.dp)
    assertThat(dp).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(1.dp), CornerSize(2.dp), CornerSize(3.dp), CornerSize(4.dp)),
    )
    val px = ContinuousRoundedCornerShape(topStart = 1f, topEnd = 2f, bottomEnd = 3f, bottomStart = 4f, smoothing = 0.5f)
    assertThat(px).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(1f), CornerSize(2f), CornerSize(3f), CornerSize(4f), 0.5f),
    )
    val percent = ContinuousRoundedCornerShape(topStartPercent = 1, topEndPercent = 2, bottomEndPercent = 3, bottomStartPercent = 4)
    assertThat(percent).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(1), CornerSize(2), CornerSize(3), CornerSize(4)),
    )
  }

  @Test
  fun copy_preservesOtherValues() {
    val shape = ContinuousRoundedCornerShape(16.dp, smoothing = 0.6f)

    assertThat(shape.copy()).isEqualTo(shape)
    assertThat(shape.copy(topStart = CornerSize(4.dp))).isEqualTo(
      ContinuousRoundedCornerShape(CornerSize(4.dp), CornerSize(16.dp), CornerSize(16.dp), CornerSize(16.dp), 0.6f),
    )
    val all: CornerBasedShape = shape.copy(CornerSize(8.dp))
    assertThat(all).isEqualTo(ContinuousRoundedCornerShape(8.dp, smoothing = 0.6f))
    assertThat(shape.copy(smoothing = 0.2f)).isEqualTo(ContinuousRoundedCornerShape(16.dp, smoothing = 0.2f))
  }

  @Test
  fun smoothing_rejectsNonFiniteAndCoercesIntoRange() {
    assertFailure { ContinuousRoundedCornerShape(16.dp, smoothing = Float.NaN) }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { ContinuousRoundedCornerShape(16.dp, smoothing = Float.POSITIVE_INFINITY) }
      .isInstanceOf<IllegalArgumentException>()
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = 1.5f).smoothing).isEqualTo(1f)
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = -0.5f).smoothing).isEqualTo(0f)
  }

  @Test
  fun equality_coversSmoothingAndShapeType() {
    val shape = ContinuousRoundedCornerShape(16.dp)
    assertThat(shape).isEqualTo(ContinuousRoundedCornerShape(16.dp))
    assertThat(shape.hashCode()).isEqualTo(ContinuousRoundedCornerShape(16.dp).hashCode())
    assertThat(shape).isNotEqualTo(ContinuousRoundedCornerShape(16.dp, smoothing = 0.5f))
    assertThat(shape.hashCode()).isNotEqualTo(ContinuousRoundedCornerShape(16.dp, smoothing = 0.5f).hashCode())
    assertThat<Any>(ContinuousRoundedCornerShape(16.dp, smoothing = 0f)).isNotEqualTo(RoundedCornerShape(16.dp))
  }

  @Test
  fun createOutline_zeroSmoothingMatchesRoundedCornerShape() {
    val size = Size(200f, 100f)
    val density = Density(1f)
    assertThat(ContinuousRoundedCornerShape(16.dp, smoothing = 0f).createOutline(size, LayoutDirection.Ltr, density))
      .isEqualTo(RoundedCornerShape(16.dp).createOutline(size, LayoutDirection.Ltr, density))
  }

  @Test
  fun createOutline_fullSmoothingIsGenericPathFillingSize() {
    val outline = ContinuousRoundedCornerShape(16.dp).createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f))

    assertThat(outline).isInstanceOf<Outline.Generic>()
      .prop(Outline::bounds)
      .isEqualTo(Rect(0f, 0f, 200f, 100f))
  }

  @Test
  fun createOutline_capsuleIsRounded() {
    val outline = ContinuousRoundedCornerShape(percent = 50).createOutline(Size(200f, 80f), LayoutDirection.Ltr, Density(1f))

    assertThat(outline).isInstanceOf<Outline.Rounded>()
  }
}
