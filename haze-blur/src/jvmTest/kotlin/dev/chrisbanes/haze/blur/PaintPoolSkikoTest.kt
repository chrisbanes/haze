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
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.skiaPaint
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

class PaintPoolSkikoTest {
  @Test
  fun usePaint_reusedPaintMatchesFreshPaint() {
    val pool = ArrayDeque<Paint>()
    val fresh = Paint()

    val first = pool.usePaint { paint ->
      paint.shader = LinearGradientShader(Offset.Zero, Offset(10f, 10f), listOf(Color.Red, Color.Blue))
      paint.colorFilter = ColorFilter.tint(Color.Red)
      paint.pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
      paint.blendMode = BlendMode.SrcAtop
      paint.style = PaintingStyle.Stroke
      paint.strokeCap = StrokeCap.Square
      paint.strokeJoin = StrokeJoin.Bevel
      paint.strokeMiterLimit = 10f
      paint.filterQuality = FilterQuality.None
      paint.isAntiAlias = false
      paint.alpha = 0.5f
      paint
    }
    val reused = pool.usePaint { it }

    assertThat(reused).isSameInstanceAs(first)
    assertThat(reused.shader).isNull()
    assertThat(reused.colorFilter).isNull()
    assertThat(reused.pathEffect).isNull()
    assertThat(reused.blendMode).isEqualTo(fresh.blendMode)
    assertThat(reused.style).isEqualTo(fresh.style)
    assertThat(reused.strokeCap).isEqualTo(fresh.strokeCap)
    assertThat(reused.strokeJoin).isEqualTo(fresh.strokeJoin)
    assertThat(reused.strokeMiterLimit).isEqualTo(fresh.strokeMiterLimit)
    assertThat(reused.filterQuality).isEqualTo(fresh.filterQuality)
    assertThat(reused.isAntiAlias).isEqualTo(fresh.isAntiAlias)
    assertThat(reused.alpha).isEqualTo(fresh.alpha)
    assertThat(reused.color).isEqualTo(fresh.color)
    assertThat(reused.strokeWidth).isEqualTo(fresh.strokeWidth)

    val reusedSkia = reused.skiaPaint
    val freshSkia = fresh.skiaPaint
    assertThat(reusedSkia.shader).isNull()
    assertThat(reusedSkia.colorFilter).isNull()
    assertThat(reusedSkia.pathEffect).isNull()
    assertThat(reusedSkia.strokeJoin).isEqualTo(freshSkia.strokeJoin)
    assertThat(reusedSkia.strokeMiter).isEqualTo(freshSkia.strokeMiter)
    assertThat(reusedSkia.mode).isEqualTo(freshSkia.mode)
    assertThat(reusedSkia.strokeCap).isEqualTo(freshSkia.strokeCap)
    assertThat(reusedSkia.isAntiAlias).isEqualTo(freshSkia.isAntiAlias)
  }
}
