// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SamplesJvmTest {
  @Test
  fun kamera_isRegisteredAndExposesBothBuiltInEffects() {
    assertThat(Samples).contains(Kamera)
    assertThat(Kamera.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
  }
}
