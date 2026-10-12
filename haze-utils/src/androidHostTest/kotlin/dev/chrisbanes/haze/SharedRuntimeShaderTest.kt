// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedRuntimeShaderTest {
  @Test
  fun zeroFloats_isSharedPerSize() {
    val shader = SharedRuntimeShader("half4 main(float2 p) { return half4(0); }")

    assertThat(shader.zeroFloats(4)).isSameInstanceAs(shader.zeroFloats(4))
    assertThat(shader.zeroFloats(2)).isNotSameInstanceAs(shader.zeroFloats(4))
    assertThat(shader.zeroFloats(4).toList()).containsExactly(0f, 0f, 0f, 0f)
  }
}
