// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SurfaceGeometryTest {
  @Test
  fun scaledByContentTransform_scalesAboutPivotInRectSpace() {
    val rect = Rect(10f, 20f, 30f, 60f)
    val transform = HazeEffectContentTransform(scaleX = 2f, scaleY = 0.5f, pivot = Offset(10f, 20f))

    // A pivot relative to the rect's top-left would give left == 0f.
    assertThat(rect.scaledByContentTransform(transform)).isEqualTo(Rect(10f, 20f, 50f, 40f))
    assertThat(rect.scaledByContentTransform(HazeEffectContentTransform.Identity)).isEqualTo(rect)
  }
}
