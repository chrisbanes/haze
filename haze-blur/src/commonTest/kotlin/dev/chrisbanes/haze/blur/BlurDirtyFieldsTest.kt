// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.Bitmask
import kotlin.test.Test

class BlurDirtyFieldsTest {

  @Test
  fun invalidateFlags_shouldIncludeAllExpectedFlagsWithoutRedundantOnes() {
    val expected =
      BlurDirtyFields.RenderEffectAffectingFlags or
        BlurDirtyFields.BackgroundColor or
        BlurDirtyFields.Alpha

    assertThat(BlurDirtyFields.InvalidateFlags).isEqualTo(expected)
  }

  @Test
  fun stringify_shouldIncludeModernColorEffectsName() {
    val names = BlurDirtyFields.stringify(Bitmask(BlurDirtyFields.ColorEffects))

    assertThat(names).isEqualTo("[ColorEffects]")
  }
}
