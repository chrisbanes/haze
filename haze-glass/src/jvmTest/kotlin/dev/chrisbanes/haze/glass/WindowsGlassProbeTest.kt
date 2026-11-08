// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class WindowsGlassProbeTest {
  @Test
  fun sourceFor_rejectsTruncatedVariant() {
    assertFailure { WindowsGlassProbe.sourceFor("g", continuous = true) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun sourceFor_originalIsProductionSource() {
    listOf(false, true).forEach { continuous ->
      assertThat(WindowsGlassProbe.sourceFor("original", continuous))
        .isEqualTo(GlassShaders.buildOptical(merged = true, continuous = continuous))
    }
  }

  @Test
  fun sourceFor_guardedTransformsEveryMemberLoopAndCompiles() {
    listOf(false, true).forEach { continuous ->
      val source = WindowsGlassProbe.sourceFor("guarded", continuous)
      assertThat(source).doesNotContain("if (float(i) >= memberCount) break;")
      RuntimeEffect.makeForShader(source).close()
    }
  }
}
