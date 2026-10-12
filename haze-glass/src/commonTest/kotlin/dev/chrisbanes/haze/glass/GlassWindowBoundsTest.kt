// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.HazeEffectContentTransform
import kotlin.test.Test

class GlassWindowBoundsTest {
  @Test
  fun unmap_pressedScale_growsWindowAroundPivot() {
    // At scale 0.5 around (100, 100), effect point (-100, -100) draws at window point (0, 0).
    val transform = HazeEffectContentTransform(0.5f, 0.5f, Offset(100f, 100f))
    assertThat(transform.unmap(Rect(0f, 0f, 200f, 200f))).isEqualTo(Rect(-100f, -100f, 300f, 300f))
  }

  @Test
  fun unmap_overshootScaleAtPointer_shrinksWindowTowardPivot() {
    val transform = HazeEffectContentTransform(1.25f, 2f, Offset(40f, 20f))
    assertThat(transform.unmap(Rect(0f, 0f, 240f, 220f))).isEqualTo(Rect(8f, 10f, 200f, 120f))
  }

  @Test
  fun unmap_identity_returnsRect() {
    val rect = Rect(-3f, 4f, 50f, 60f)
    assertThat(HazeEffectContentTransform.Identity.unmap(rect)).isEqualTo(rect)
  }

  @Test
  fun visiblePart_partialOverlap_roundsOutIntersection() {
    assertThat(Rect(10f, 10f, 500f, 80f).visiblePart(Rect(-5.5f, 20.25f, 300.5f, 900f))).isEqualTo(Rect(10f, 20f, 301f, 80f))
  }

  @Test
  fun visiblePart_noOverlap_returnsZero() {
    assertThat(Rect(10f, 10f, 50f, 50f).visiblePart(Rect(60.2f, 0f, 60.4f, 100f))).isEqualTo(Rect.Zero)
  }
}
