// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativePaint
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PaintPoolAndroidHostTest : ContextTest() {
  @Test
  fun usePaint_reusedPaintMatchesFreshPaint() {
    val pool = ArrayDeque<Paint>()
    val fresh = Paint()

    val first = pool.usePaint { paint ->
      paint.shader = LinearGradientShader(Offset.Zero, Offset(10f, 10f), listOf(Color.Red, Color.Blue))
      paint.colorFilter = ColorFilter.tint(Color.Red)
      paint.pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
      paint.blendMode = BlendMode.SrcAtop
      paint.isAntiAlias = false
      paint.alpha = 0.5f
      paint.filterQuality = FilterQuality.None
      paint
    }
    val reused = pool.usePaint { it }

    assertThat(reused).isSameInstanceAs(first)
    assertThat(reused.shader).isNull()
    assertThat(reused.colorFilter).isNull()
    assertThat(reused.pathEffect).isNull()
    assertThat(reused.blendMode).isEqualTo(fresh.blendMode)
    assertThat(reused.isAntiAlias).isEqualTo(fresh.isAntiAlias)
    assertThat(reused.alpha).isEqualTo(fresh.alpha)
    assertThat(reused.filterQuality).isEqualTo(fresh.filterQuality)
    assertThat(reused.color).isEqualTo(fresh.color)
    assertThat(reused.style).isEqualTo(fresh.style)
    assertThat(reused.nativePaint.flags).isEqualTo(fresh.nativePaint.flags)
    assertThat(reused.nativePaint.shader).isNull()
    assertThat(reused.nativePaint.colorFilter).isNull()
    assertThat(reused.nativePaint.pathEffect).isNull()
  }

  @Test
  fun usePaint_reusedPaintAppliesBlendModeToNativePaint() {
    val pool = ArrayDeque<Paint>()

    pool.usePaint { it.blendMode = BlendMode.SrcAtop }
    val nativeBlendMode = pool.usePaint { paint ->
      paint.blendMode = BlendMode.SrcAtop
      paint.nativePaint.blendMode
    }

    assertThat(nativeBlendMode).isEqualTo(android.graphics.BlendMode.SRC_ATOP)
  }
}
