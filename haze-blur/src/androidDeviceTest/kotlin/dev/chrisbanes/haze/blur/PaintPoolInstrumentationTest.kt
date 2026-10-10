// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.os.Build
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativePaint
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import org.junit.Assume.assumeTrue
import org.junit.Test

class PaintPoolInstrumentationTest {

  @Test
  fun usePaint_reusedPaintAppliesBlendMode() {
    assumeTrue("Framework blend mode getter requires API 29+", Build.VERSION.SDK_INT == 30)
    val pool = ArrayDeque<Paint>()

    val first = pool.usePaint { paint ->
      paint.blendMode = BlendMode.SrcAtop
      paint
    }
    val (second, frameworkBlendMode) = pool.usePaint { paint ->
      paint.blendMode = BlendMode.SrcAtop
      paint to paint.nativePaint.blendMode
    }

    assertThat(second, "reused paint").isSameInstanceAs(first)
    assertThat(frameworkBlendMode, "framework blend mode").isEqualTo(android.graphics.BlendMode.SRC_ATOP)
  }
}
