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
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.trace
import kotlin.math.max

@OptIn(ExperimentalHazeApi::class)
internal class FallbackGlassDelegate(
  private val effect: GlassRuntimeEffect,
) : GlassRuntimeEffect.Delegate {

  private var preparedDraw: FallbackGlassPreparedDraw? = null
  private val groupAlpha = RetainedGlassGroupAlphaLayer()
  private var graphicsContext: GraphicsContext? = null

  override fun DrawScope.prepareDraw(context: HazeEffectRuntimeDrawScope) {
    if (effect.alpha == 0f) return

    val density = context.requireDensity()
    val layoutDirection = context.currentValueOf(LocalLayoutDirection)
    val style = resolveGlassStyle(effect, size, density, layoutDirection)
    val interaction = resolveFallbackGlassInteraction(
      state = effect.currentInteractionState,
      radiusFraction = effect.interactionLightRadiusFraction,
      size = size,
    )
    val previous = preparedDraw
    if (previous?.size != size || previous.style != style || previous.interaction != interaction) {
      val shapePath = previous
        ?.takeIf { it.size == size && it.style.cornerRadii == style.cornerRadii }
        ?.shapePath
        ?: style.cornerRadii.takeUnless { it.isZero() }
          ?.toRoundRect(size)
          ?.let { Path().apply { addRoundRect(it) } }

      val highlightAlpha = 0.25f * style.specularIntensity * style.alpha
      val highlightRadius = max(size.minDimension / 2f, style.edgeSoftnessPx * 4f)
      val highlightBrush = when {
        highlightAlpha <= 0f || highlightRadius <= 0f -> null
        previous?.highlightAlpha == highlightAlpha &&
          previous.style.lightPosition == style.lightPosition &&
          previous.highlightRadius == highlightRadius -> previous.highlightBrush
        else -> Brush.radialGradient(
          colors = listOf(
            Color.White.copy(alpha = highlightAlpha),
            Color.Transparent,
          ),
          center = style.lightPosition,
          radius = highlightRadius,
        )
      }

      val edgeAlpha = fallbackEdgeAlpha(style.ambientResponse)
      val edgeBrush = when {
        style.edgeSoftnessPx <= 0f || edgeAlpha <= 0f || style.alpha <= 0f -> null
        previous?.edgeBrush != null &&
          previous.edgeAlpha == edgeAlpha &&
          previous.size == size -> previous.edgeBrush
        else -> Brush.linearGradient(
          colors = listOf(
            Color.White.copy(alpha = edgeAlpha),
            Color.Transparent,
          ),
          start = Offset.Zero,
          end = Offset(size.width, size.height),
        )
      }
      val edgeDirectAlpha = edgeAlpha * style.alpha
      val edgeDirectBrush = when {
        style.edgeSoftnessPx <= 0f || edgeDirectAlpha <= 0f -> null
        previous?.edgeDirectBrush != null &&
          previous.edgeDirectAlpha == edgeDirectAlpha &&
          previous.size == size -> {
          previous.edgeDirectBrush
        }
        else -> Brush.linearGradient(
          colors = listOf(
            Color.White.copy(alpha = edgeDirectAlpha),
            Color.Transparent,
          ),
          start = Offset.Zero,
          end = Offset(size.width, size.height),
        )
      }

      val edgeStroke = when {
        style.edgeSoftnessPx <= 0f -> null
        previous?.style?.edgeSoftnessPx == style.edgeSoftnessPx -> previous.edgeStroke
        else -> Stroke(width = style.edgeSoftnessPx * 2f)
      }

      val interactionAlpha = 0.32f * interaction.lightingIntensity * style.alpha
      val interactionBrush = when {
        !interaction.hasLighting || interactionAlpha <= 0f -> null
        previous?.interactionAlpha == interactionAlpha &&
          previous.interaction.position == interaction.position &&
          previous.interaction.radiusPx == interaction.radiusPx -> previous.interactionBrush
        else -> Brush.radialGradient(
          colors = listOf(
            Color.White.copy(alpha = interactionAlpha),
            Color.Transparent,
          ),
          center = interaction.position,
          radius = interaction.radiusPx,
        )
      }

      preparedDraw = FallbackGlassPreparedDraw(
        size = size,
        style = style,
        shapePath = shapePath,
        highlightAlpha = highlightAlpha,
        highlightRadius = highlightRadius,
        highlightBrush = highlightBrush,
        edgeAlpha = edgeAlpha,
        edgeBrush = edgeBrush,
        edgeDirectAlpha = edgeDirectAlpha,
        edgeDirectBrush = edgeDirectBrush,
        edgeStroke = edgeStroke,
        interaction = interaction,
        interactionAlpha = interactionAlpha,
        interactionBrush = interactionBrush,
      )
    }

    val groupSize = size.roundToIntSize()
    val currentGraphicsContext = context.requireGraphicsContext()
    graphicsContext = currentGraphicsContext
    groupAlpha.prepare(
      required = requiresGlassGroupAlpha(style.alpha) && groupSize.fitsGlassLayerBudget(),
      graphicsContext = currentGraphicsContext,
    )
  }

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
    draw(context, forceInput = false)
  }

  internal fun DrawScope.draw(
    context: HazeEffectRuntimeDrawScope,
    forceInput: Boolean,
  ) {
    val prepared = preparedDraw ?: return
    val style = prepared.style
    val backgroundColor = style.backgroundColor
    val tint = style.tint
    if (!backgroundColor.isSpecified || !tint.isSpecified) return
    if (style.alpha <= 0f) return
    trace(GlassTraceSection.FallbackDraw) {
      val shapePath = prepared.shapePath

      fun DrawScope.drawFallback(alphaMultiplier: Float) {
        fun DrawScope.drawBase() {
          if (backgroundColor.alpha > 0f) {
            drawRect(
              color = backgroundColor.copy(alpha = backgroundColor.alpha * alphaMultiplier),
            )
          }
          if (backgroundColor.alpha > 0f || forceInput) {
            this@drawBase.drawInputWithAlpha(context, alphaMultiplier)
          }
          if (tint.alpha > 0f) {
            drawRect(color = tint.copy(alpha = tint.alpha * alphaMultiplier))
          }
        }

        if (shapePath != null) {
          clipPath(shapePath) { drawBase() }
        } else {
          drawBase()
        }

        // The edge falloff is part of the base material and remains behind child content.
        val edgeBrush = if (alphaMultiplier >= 1f) {
          prepared.edgeBrush
        } else {
          prepared.edgeDirectBrush
        }
        edgeBrush?.let {
          drawFallbackEdge(
            brush = it,
            stroke = checkNotNull(prepared.edgeStroke),
            shapePath = shapePath,
          )
        }
      }

      when {
        style.alpha >= 1f -> drawFallback(alphaMultiplier = 1f)
        groupAlpha.isAvailable -> recordAndDrawGlassGroupAlpha(
          layer = checkNotNull(groupAlpha.layer),
          alpha = style.alpha,
          size = size.roundToIntSize(),
        ) { drawFallback(alphaMultiplier = 1f) }
        else -> drawFallback(alphaMultiplier = style.alpha)
      }
    }
  }

  override fun DrawScope.drawForeground(context: HazeEffectRuntimeDrawScope) {
    val prepared = preparedDraw ?: return
    val style = prepared.style
    val tint = style.tint
    if (!tint.isSpecified) return
    if (style.alpha <= 0f) return

    trace(GlassTraceSection.FallbackForeground) {
      val shapePath = prepared.shapePath

      drawInteractionLighting(
        interaction = prepared.interaction,
        brush = prepared.interactionBrush,
        shapePath = shapePath,
      )

      // Draw the fallback rim approximation above child content, matching the runtime path.
      prepared.highlightBrush?.let { highlightBrush ->
        if (shapePath != null) {
          clipPath(shapePath) {
            drawCircle(
              brush = highlightBrush,
              radius = prepared.highlightRadius,
              center = style.lightPosition,
            )
          }
        } else {
          drawCircle(
            brush = highlightBrush,
            radius = prepared.highlightRadius,
            center = style.lightPosition,
          )
        }
      }
    }
  }

  override fun detach() {
    groupAlpha.release(graphicsContext)
    graphicsContext = null
    preparedDraw = null
  }

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    if (shouldReleaseRetainedGlass(level)) {
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
  brush: Brush?,
  shapePath: Path?,
) {
  if (brush == null) return
  val drawHighlight: DrawScope.() -> Unit = {
    drawCircle(brush = brush, center = interaction.position, radius = interaction.radiusPx)
  }
  if (shapePath != null) {
    clipPath(shapePath, block = drawHighlight)
  } else {
    drawHighlight()
  }
}

private fun DrawScope.drawFallbackEdge(
  brush: Brush,
  stroke: Stroke,
  shapePath: Path?,
) {
  if (shapePath != null) {
    clipPath(shapePath) {
      drawPath(path = shapePath, brush = brush, style = stroke)
    }
  } else {
    drawRect(brush = brush, style = stroke)
  }
}

internal fun fallbackEdgeAlpha(ambientResponse: Float): Float = 0.18f * ambientResponse

private data class FallbackGlassPreparedDraw(
  val size: Size,
  val style: ResolvedGlassStyle,
  val shapePath: Path?,
  val highlightAlpha: Float,
  val highlightRadius: Float,
  val highlightBrush: Brush?,
  val edgeAlpha: Float,
  val edgeBrush: Brush?,
  val edgeDirectAlpha: Float,
  val edgeDirectBrush: Brush?,
  val edgeStroke: Stroke?,
  val interaction: GlassInteractionUniforms,
  val interactionAlpha: Float,
  val interactionBrush: Brush?,
)
