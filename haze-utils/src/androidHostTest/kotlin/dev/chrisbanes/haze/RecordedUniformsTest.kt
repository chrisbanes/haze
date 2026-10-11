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
class RecordedUniformsTest {
  @Test
  fun scalarSetters_reuseArrayForSameSize() {
    val uniforms = RecordedUniforms()
    uniforms.setFloatUniform("a", 1f)
    uniforms.setFloatUniform("b", 1f, 2f)
    uniforms.setFloatUniform("c", 1f, 2f, 3f, 4f)
    val a = uniforms.floats.getValue("a")
    val b = uniforms.floats.getValue("b")
    val c = uniforms.floats.getValue("c")

    uniforms.setFloatUniform("a", 5f)
    uniforms.setFloatUniform("b", 6f, 7f)
    uniforms.setFloatUniform("c", 8f, 9f, 10f, 11f)

    assertThat(uniforms.floats.getValue("a")).isSameInstanceAs(a)
    assertThat(uniforms.floats.getValue("b")).isSameInstanceAs(b)
    assertThat(uniforms.floats.getValue("c")).isSameInstanceAs(c)
    assertThat(a.toList()).containsExactly(5f)
    assertThat(b.toList()).containsExactly(6f, 7f)
    assertThat(c.toList()).containsExactly(8f, 9f, 10f, 11f)
  }

  @Test
  fun arraySetter_reusesArrayAndDoesNotAliasCaller() {
    val uniforms = RecordedUniforms()
    val caller = floatArrayOf(1f, 2f, 3f)
    uniforms.setFloatUniform("v", caller)
    val stored = uniforms.floats.getValue("v")
    assertThat(stored).isNotSameInstanceAs(caller)

    caller[0] = 9f
    uniforms.setFloatUniform("v", floatArrayOf(4f, 5f, 6f))

    assertThat(uniforms.floats.getValue("v")).isSameInstanceAs(stored)
    assertThat(stored.toList()).containsExactly(4f, 5f, 6f)
  }

  @Test
  fun sizeChange_storesCorrectlySizedValues() {
    val uniforms = RecordedUniforms()
    uniforms.setFloatUniform("v", floatArrayOf(1f, 2f))
    uniforms.setFloatUniform("v", floatArrayOf(1f, 2f, 3f, 4f))
    assertThat(uniforms.floats.getValue("v").toList()).containsExactly(1f, 2f, 3f, 4f)

    uniforms.setFloatUniform("v", 7f)
    assertThat(uniforms.floats.getValue("v").toList()).containsExactly(7f)
  }
}
