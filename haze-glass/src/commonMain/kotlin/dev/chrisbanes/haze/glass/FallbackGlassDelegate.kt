// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.VisualEffectContext
import kotlin.math.max

@OptIn(ExperimentalHazeApi::class)
internal class FallbackGlassDelegate(
  private val effect: GlassVisualEffect,
) : GlassVisualEffect.Delegate {

  private var cachedShapePath: Path? = null
  private var cachedSize: Size = Size.Zero
  private var cachedRadii: CornerRadii = CornerRadii.zero
  private val groupAlpha = RetainedGlassGroupAlphaLayer()
  private var graphicsContext: GraphicsContext? = null

  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val style = resolveGlassStyle(effect, size, density, layoutDirection)
    val groupSize = size.roundToIntSize()
    val groupPlan = GlassRetainedLayerPlan(
      layers = listOf(GlassRetainedLayer(GlassRetainedLayerKind.GroupComposite, groupSize)),
    )
    val currentGraphicsContext = context.requireGraphicsContext()
    graphicsContext = currentGraphicsContext
    groupAlpha.prepare(
      required = requiresGlassGroupAlpha(style.alpha) && groupPlan.fitsGlassRenderBudget(),
      graphicsContext = currentGraphicsContext,
    )
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val style = resolveGlassStyle(effect, size, density, layoutDirection)
    val tint = style.tint
    if (!tint.isSpecified) return

    val edgeSoftnessPx = style.edgeSoftnessPx
    val edgeAlpha = fallbackEdgeAlpha(style.ambientResponse)
    val highlightCenter = style.lightPosition
    val highlightAlpha = 0.25f * style.specularIntensity
    val highlightRadius = max(size.minDimension / 2f, edgeSoftnessPx * 4f)

    val radii = style.cornerRadii

    if (size != cachedSize || radii != cachedRadii) {
      cachedSize = size
      cachedRadii = radii
      cachedShapePath = if (!radii.isZero()) {
        radii.toRoundRect(size).let { Path().apply { addRoundRect(it) } }
      } else {
        null
      }
    }
    val shapePath = cachedShapePath

    fun DrawScope.drawFallback(alphaMultiplier: Float) {
      if (shapePath != null) {
        clipPath(shapePath) {
          drawRect(color = tint.copy(alpha = tint.alpha * alphaMultiplier))
        }
      } else {
        drawRect(color = tint.copy(alpha = tint.alpha * alphaMultiplier))
      }

      // Specular-ish radial highlight
      if (highlightAlpha > 0f) {
        val highlightBrush = Brush.radialGradient(
          colors = listOf(
            Color.White.copy(alpha = highlightAlpha * alphaMultiplier),
            Color.Transparent,
          ),
          center = highlightCenter,
          radius = highlightRadius,
        )
        if (shapePath != null) {
          clipPath(shapePath) {
            drawCircle(brush = highlightBrush, radius = highlightRadius, center = highlightCenter)
          }
        } else {
          drawCircle(
            brush = highlightBrush,
            center = highlightCenter,
            radius = highlightRadius,
          )
        }
      }

      // Edge falloff
      if (edgeSoftnessPx > 0f) {
        val softness = edgeSoftnessPx
        val stroke = Stroke(width = softness * 2f)
        val edgeBrush = Brush.linearGradient(
          colors = listOf(
            Color.White.copy(alpha = edgeAlpha * alphaMultiplier),
            Color.Transparent,
          ),
          start = Offset.Zero,
          end = Offset(size.width, size.height),
        )
        if (shapePath != null) {
          clipPath(shapePath) {
            drawPath(path = shapePath, brush = edgeBrush, style = stroke)
          }
        } else {
          drawRect(brush = edgeBrush, style = stroke)
        }
      }

      drawInteractionLighting(
        interaction = resolveFallbackGlassInteraction(
          state = effect.currentInteractionState,
          radiusFraction = effect.interactionLightRadiusFraction,
          size = size,
        ),
        shapePath = shapePath,
        alphaMultiplier = alphaMultiplier,
      )
    }

    when {
      style.alpha <= 0f -> return
      style.alpha >= 1f -> drawFallback(alphaMultiplier = 1f)
      groupAlpha.isAvailable -> recordAndDrawGlassGroupAlpha(
        layer = checkNotNull(groupAlpha.layer),
        alpha = style.alpha,
        size = size.roundToIntSize(),
      ) { drawFallback(alphaMultiplier = 1f) }
      else -> drawFallback(alphaMultiplier = style.alpha)
    }
  }

  override fun detach() {
    groupAlpha.release(graphicsContext)
    graphicsContext = null
  }

  override fun onTrimMemory(context: VisualEffectContext, level: TrimMemoryLevel) {
    if (level == TrimMemoryLevel.UI_HIDDEN || level.severity >= TrimMemoryLevel.MODERATE.severity) {
      groupAlpha.release(graphicsContext ?: context.requireGraphicsContext())
      graphicsContext = null
      context.invalidateDraw()
    }
  }
}

internal fun resolveFallbackGlassInteraction(
  state: GlassInteractionRenderState,
  radiusFraction: Float,
  size: Size,
): GlassInteractionUniforms = resolveGlassInteraction(state, radiusFraction).uniforms(
  GlassCoordinates(
    sampleSize = size,
    materialOrigin = Offset.Zero,
    materialSize = size,
    scaleFactor = 1f,
  ),
)

private fun DrawScope.drawInteractionLighting(
  interaction: GlassInteractionUniforms,
  shapePath: Path?,
  alphaMultiplier: Float,
) {
  if (!interaction.hasLighting) return
  val brush = Brush.radialGradient(
    colors = listOf(
      Color.White.copy(alpha = 0.32f * interaction.lightingIntensity * alphaMultiplier),
      Color.Transparent,
    ),
    center = interaction.position,
    radius = interaction.radiusPx,
  )
  val drawHighlight: DrawScope.() -> Unit = {
    drawCircle(brush = brush, center = interaction.position, radius = interaction.radiusPx)
  }
  if (shapePath != null) {
    clipPath(shapePath, block = drawHighlight)
  } else {
    drawHighlight()
  }
}

internal fun fallbackEdgeAlpha(ambientResponse: Float): Float = 0.18f * ambientResponse.coerceIn(0f, 1f)
