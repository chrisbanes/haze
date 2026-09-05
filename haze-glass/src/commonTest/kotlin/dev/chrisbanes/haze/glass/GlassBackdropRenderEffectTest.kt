// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertThat
import assertk.assertions.isInstanceOf
import dev.chrisbanes.haze.HazeEffectRendererBackdrop
import kotlin.test.Test

class GlassBackdropRenderEffectTest {

  @Test
  fun glassRenderer_exposesBuiltInBackdropCapability() {
    assertThat(GlassHazeEffectFactory.createRenderer())
      .isInstanceOf<HazeEffectRendererBackdrop<*>>()
  }
}
