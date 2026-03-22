// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.liquidglass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
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

    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val edgeSoftnessPx = with(context.requireDensity()) { effect.edgeSoftness.toPx() }
    val highlightCenter = effect.lightPosition.takeUnless { it == Offset.Unspecified }
      ?: Offset(size.width / 2f, size.height / 3f)

    val radii = effect.shape.toCornerRadiiPx(layerSize = size, density = density, layoutDirection = layoutDirection)
    val roundRect = radii.takeUnless { it.isZero() }?.toRoundRect(size)
    val shapePath = roundRect?.let { Path().apply { addRoundRect(it) } }

    withAlpha(alpha = effect.alpha, context = context) {
      if (shapePath != null) {
        clipPath(shapePath) {
          drawRect(color = tint)
        }
      } else {
        drawRect(color = tint)
      }

      // Specular-ish radial highlight
      val highlightBrush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
        center = highlightCenter,
        radius = max(size.minDimension / 2f, edgeSoftnessPx * 4f),
      )
      if (shapePath != null) {
        clipPath(shapePath) {
          drawCircle(brush = highlightBrush, radius = max(size.minDimension / 2f, edgeSoftnessPx * 4f), center = highlightCenter)
        }
      } else {
        drawCircle(
          brush = highlightBrush,
          center = highlightCenter,
          radius = max(size.minDimension / 2f, edgeSoftnessPx * 4f),
        )
      }

      // Edge falloff
      if (edgeSoftnessPx > 0f) {
        val softness = edgeSoftnessPx
        val stroke = Stroke(width = softness * 2f)
        val edgeBrush = Brush.radialGradient(
          colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
          center = Offset(size.width / 2f, size.height / 2f),
          radius = size.maxDimension,
        )
        if (shapePath != null) {
          drawPath(path = shapePath, brush = edgeBrush, style = stroke)
        } else {
          drawRect(brush = edgeBrush, style = stroke)
        }
      }
    }
  }
}
