// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.isSpecified
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.math.max

@OptIn(ExperimentalHazeApi::class)
internal class FallbackLiquidGlassDelegate(
  private val effect: LiquidGlassVisualEffect,
) : LiquidGlassVisualEffect.Delegate {
  override fun DrawScope.draw(context: VisualEffectContext) {
    val tint = effect.tint
    if (!tint.isSpecified) return

    val edgeSoftnessPx = with(context.requireDensity()) { effect.edgeSoftness.toPx() }
    val highlightCenter = effect.lightPosition.takeUnless { it == Offset.Unspecified }
      ?: Offset(size.width / 2f, size.height / 3f)

    withAlpha(alpha = effect.alpha, context = context) {
      drawRect(color = tint)

      // Specular-ish radial highlight
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
          center = highlightCenter,
          radius = max(size.minDimension / 2f, edgeSoftnessPx * 4f),
        ),
      )

      // Edge falloff
      if (edgeSoftnessPx > 0f) {
        val softness = edgeSoftnessPx
        val stroke = Stroke(width = softness * 2f)
        drawRect(
          brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension,
          ),
          style = stroke,
        )
      }
    }
  }
}
