// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import kotlin.test.Test

/** Path geometry needs a real [androidx.compose.ui.graphics.Path], so it runs on Skiko. */
class ContinuousCornerPathTest {

  @Test
  fun createOutline_fullSmoothingIsGenericPathFillingSize() {
    val outline = ContinuousRoundedCornerShape(16.dp).createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f))

    assertThat(outline).isInstanceOf<Outline.Generic>()
      .prop(Outline::bounds)
      .isEqualTo(Rect(0f, 0f, 200f, 100f))
  }

  @Test
  fun toPath_fullSmoothingFillsSize() {
    val radii = CornerRadii(16f, 16f, 16f, 16f, 1f, 1f, 1f, 1f)

    assertThat(radii.toPath(Size(200f, 100f)).getBounds()).isEqualTo(Rect(0f, 0f, 200f, 100f))
  }
}
