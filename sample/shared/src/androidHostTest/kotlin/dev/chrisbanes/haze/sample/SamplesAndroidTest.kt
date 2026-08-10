// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

class SamplesAndroidTest : ContextTest() {
  @Test
  fun exoPlayer_exposesBothBuiltInEffects() {
    assertThat(AndroidExoPlayer.effects).isEqualTo(
      listOf(SampleEffect.Blur, SampleEffect.Glass),
    )
  }
}
