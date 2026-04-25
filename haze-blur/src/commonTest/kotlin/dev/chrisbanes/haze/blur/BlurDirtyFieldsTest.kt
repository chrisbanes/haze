// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import dev.chrisbanes.haze.Bitmask
import kotlin.test.Test
import kotlin.test.assertEquals

class BlurDirtyFieldsTest {

  @Test
  fun invalidateFlags_shouldIncludeAllExpectedFlagsWithoutRedundantOnes() {
    val expected =
      BlurDirtyFields.RenderEffectAffectingFlags or
        BlurDirtyFields.BackgroundColor or
        BlurDirtyFields.Alpha

    assertEquals(expected, BlurDirtyFields.InvalidateFlags)
  }

  @Test
  fun stringify_shouldIncludeModernColorEffectsName() {
    val names = BlurDirtyFields.stringify(Bitmask(BlurDirtyFields.ColorEffects))

    assertEquals("[ColorEffects]", names)
  }
}
