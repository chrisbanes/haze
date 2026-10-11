// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import dev.chrisbanes.haze.RuntimeShaderUniformProvider

/**
 * A `float4[4]` uniform read in a constant-bound loop guarded by a member count, the shape the
 * merged Glass field uses. It renders green when the weighted sum of the active members matches
 * `expected`, and red otherwise. It never samples a child.
 */
internal object UniformArrayProbe {
  const val SOURCE = """
    uniform shader content;
    uniform float4 values[4];
    uniform float count;
    uniform float expected;
    half4 main(float2 p) {
      float sum = 0.0;
      for (int i = 0; i < 4; i++) {
        if (float(i) >= count) break;
        sum += dot(values[i], float4(1.0, 10.0, 100.0, 1000.0));
      }
      return abs(sum - expected) < 0.01 ? half4(0.0, 1.0, 0.0, 1.0) : half4(1.0, 0.0, 0.0, 1.0);
    }
  """

  // Every slot is distinct, so reading the wrong index or ignoring the count changes the sum.
  val VALUES = floatArrayOf(
    1f, 2f, 3f, 4f,
    0.5f, 0.25f, 0.125f, 2f,
    7f, 0f, 1f, 0.5f,
    0.75f, 3f, 0f, 9f,
  )

  fun expected(count: Int): Float = (0 until count).sumOf { i ->
    val o = i * 4
    (VALUES[o] + VALUES[o + 1] * 10f + VALUES[o + 2] * 100f + VALUES[o + 3] * 1000f).toDouble()
  }.toFloat()

  fun set(provider: RuntimeShaderUniformProvider, count: Int) {
    provider.setFloatUniform("values", VALUES)
    // Robolectric SDK 33 drops values set through the fixed-arity float setters, so every
    // uniform here goes through the array setter.
    provider.setFloatUniform("count", floatArrayOf(count.toFloat()))
    provider.setFloatUniform("expected", floatArrayOf(expected(count)))
  }
}
