// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.roundToIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.trace
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

@OptIn(ExperimentalHazeApi::class)
internal class FallbackGlassDelegate(
  private val effect: GlassRuntimeEffect,
) : GlassRuntimeEffect.Delegate {

  private var preparedDraw: FallbackGlassPreparedDraw? = null
  private val groupAlpha = RetainedGlassGroupAlphaLayer()
  private var graphicsContext: GraphicsContext? = null

  override val drawsContentBehind: Boolean get() = true

  override fun DrawScope.prepareDraw(context: HazeEffectRuntimeDrawScope) {
    if (effect.alpha == 0f) return

    val style = effect.resolvePreparedStyle(context)
    val interaction = resolveFallbackGlassInteraction(
      state = effect.currentInteractionState,
      radiusFraction = effect.interactionLightRadiusFraction,
      size = size,
    )
    val previous = preparedDraw
    if (previous?.size != size || previous.style != style || previous.interaction != interaction) {
      // Lights are not drawn here, so a light-only change keeps the member paths.
      val mergedPaths = style.mergedField?.withoutInteractions?.let { field ->
        previous?.mergedPaths?.takeIf { it.field == field } ?: FallbackMergedPaths(field)
      }
      val shapePath = mergedPaths?.union ?: previous
        ?.takeIf { it.size == size && it.mergedPaths == null && it.style.cornerRadii == style.cornerRadii }
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

      val edgeShadowAlpha = style.edgeShadow.alpha * style.alpha
      val edgeStrokeSoftnessPx = when {
        style.edgeSoftnessPx > 0f -> style.edgeSoftnessPx
        edgeShadowAlpha > 0f -> FALLBACK_HARD_EDGE_SOFTNESS_PX
        else -> 0f
      }
      val edgeStroke = when {
        edgeStrokeSoftnessPx <= 0f -> null
        previous?.edgeStroke != null &&
          previous.style.edgeSoftnessPx == style.edgeSoftnessPx -> previous.edgeStroke
        else -> Stroke(width = edgeStrokeSoftnessPx * 2f)
      }
      val edgeShadowBrush = when {
        edgeShadowAlpha <= 0f -> null
        previous?.edgeShadowAlpha == edgeShadowAlpha &&
          previous.style.edgeShadow == style.edgeShadow -> previous.edgeShadowBrush
        else -> SolidColor(style.edgeShadow.copy(alpha = edgeShadowAlpha))
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

      // The cluster runtime has no interaction source; each member carries its own light instead.
      val memberLightBrushes = style.mergedField?.takeIf { it.hasInteractionLighting }?.members?.map { member ->
        val alpha = 0.32f * member.lightIntensity * style.alpha
        if (!member.hasLighting || alpha <= 0f) {
          null
        } else {
          Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            center = member.lightPosition,
            radius = member.lightRadius,
          )
        }
      }

      preparedDraw = FallbackGlassPreparedDraw(
        size = size,
        style = style,
        shapePath = shapePath,
        mergedPaths = mergedPaths,
        highlightAlpha = highlightAlpha,
        highlightRadius = highlightRadius,
        highlightBrush = highlightBrush,
        edgeAlpha = edgeAlpha,
        edgeBrush = edgeBrush,
        edgeDirectAlpha = edgeDirectAlpha,
        edgeDirectBrush = edgeDirectBrush,
        edgeStroke = edgeStroke,
        edgeShadowAlpha = edgeShadowAlpha,
        edgeShadowBrush = edgeShadowBrush,
        interaction = interaction,
        interactionAlpha = interactionAlpha,
        interactionBrush = interactionBrush,
        memberLightBrushes = memberLightBrushes,
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
      val mergedPaths = prepared.mergedPaths
      val visible = effect.windowBoundsInEffect()
      val inputBounds = Rect(Offset.Zero, size).visiblePart(visible)

      if (forceInput && style.fallbackBaseColor == null && mergedPaths == null && backgroundColor.alpha <= 0f && !inputBounds.isEmpty) {
        drawInputWithAlpha(context, alpha = 1f, bounds = inputBounds)
      }

      fun DrawScope.drawFallback(alphaMultiplier: Float) {
        fun DrawScope.drawBase() {
          style.fallbackBaseColor?.let { drawRect(it.copy(alpha = it.alpha * alphaMultiplier)) }
          if (backgroundColor.alpha > 0f) {
            drawRect(
              color = backgroundColor.copy(alpha = backgroundColor.alpha * alphaMultiplier),
            )
            if (style.fallbackBaseColor == null && !inputBounds.isEmpty) {
              this@drawBase.drawInputWithAlpha(context, alphaMultiplier, inputBounds)
            }
          }
          if (tint.alpha > 0f) {
            drawRect(color = tint.copy(alpha = tint.alpha * alphaMultiplier))
          }
        }

        when {
          mergedPaths != null -> drawMergedBase(context, mergedPaths, alphaMultiplier, forceInput, visible)
          shapePath != null -> clipPath(shapePath) { drawBase() }
          else -> drawBase()
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

      val mergedPaths = prepared.mergedPaths
      if (mergedPaths != null) {
        prepared.memberLightBrushes?.let {
          drawMergedInteractionLighting(mergedPaths, it, effect.windowBoundsInEffect())
        }
      } else {
        drawInteractionLighting(
          interaction = prepared.interaction,
          brush = prepared.interactionBrush,
          shapePath = shapePath,
        )
      }

      prepared.edgeShadowBrush?.let { edgeShadowBrush ->
        drawFallbackEdge(
          brush = edgeShadowBrush,
          stroke = checkNotNull(prepared.edgeStroke),
          shapePath = shapePath,
        )
      }

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

/**
 * Draws each member's light over its shape where no earlier member covers it, like
 * [drawMergedColours]: in a layer when the visible union fits, otherwise over exclusive paths.
 */
internal fun DrawScope.drawMergedInteractionLighting(
  paths: FallbackMergedPaths,
  brushes: List<Brush?>,
  visible: Rect?,
) {
  if (paths.unionBounds.visiblePart(visible).isEmpty) return
  val layerBounds = paths.layerBounds(visible)
  if (layerBounds == null) {
    brushes.forEachIndexed { index, brush ->
      // The gradient is transparent beyond its radius, so filling the whole region is equivalent.
      if (brush != null) drawPath(paths.exclusive[index], brush)
    }
    return
  }
  withLayerPaint(alpha = 1f, blendMode = BlendMode.SrcOver, bounds = layerBounds) {
    for (index in brushes.indices.reversed()) {
      // An unlit member still clears what it covers: the earliest member owns the overlap.
      drawPath(paths.members[index], Color.Black, blendMode = BlendMode.DstOut)
      brushes[index]?.let { drawPath(paths.members[index], it, blendMode = BlendMode.Plus) }
    }
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

/** Draws a merged field's members as one surface, without a bridge. */
private fun DrawScope.drawMergedBase(
  context: HazeEffectRuntimeDrawScope,
  paths: FallbackMergedPaths,
  alphaMultiplier: Float,
  forceInput: Boolean,
  visible: Rect?,
) {
  val members = paths.field.members
  if (members.any { it.fallbackBaseColor != null }) {
    drawMergedColours(paths, alphaMultiplier, visible) { it.fallbackBaseColor ?: Color.Transparent }
  }
  if (paths.field.hasBackground) drawMergedColours(paths, alphaMultiplier, visible) { it.backgroundColor }
  // Reduced members never replay captured input, including on the downgrade frame.
  val inputPath = if (forceInput) paths.forceInputMask else paths.inputMask
  // Both masks lie within the union, so its visible bounds hold the input.
  val inputBounds = paths.unionBounds.visiblePart(visible)
  if (!inputBounds.isEmpty) {
    inputPath?.let { clipPath(it) { drawInputWithAlpha(context, alphaMultiplier, inputBounds) } }
  }
  if (members.any { it.tint.alpha > 0f }) drawMergedColours(paths, alphaMultiplier, visible) { it.tint }
}

/**
 * Draws one colour stage in a layer bounded by the union within [visible], in which each member
 * clears its own shape with [BlendMode.DstOut] and then adds its colour with [BlendMode.Plus], from
 * last to first. Where members overlap the earliest member's colour applies once, and anti-aliased
 * edges never composite twice. A null [visible] bounds the layer by the whole union.
 */
internal fun DrawScope.drawMergedColours(
  paths: FallbackMergedPaths,
  alphaMultiplier: Float,
  visible: Rect? = null,
  color: (GlassMergedMember) -> Color,
) {
  val members = paths.field.members
  if (paths.unionBounds.visiblePart(visible).isEmpty) return
  val layerBounds = paths.layerBounds(visible)
  if (layerBounds == null) {
    // ponytail: only when the visible union exceeds the layer budget (over 4096 px on a side, or
    // over 16,777,216 px on screen). Each member then fills only what earlier members leave
    // uncovered, so overlaps composite once, but curved internal edges show a faint seam.
    members.forEachIndexed { index, member ->
      val memberColor = color(member)
      drawPath(paths.exclusive[index], memberColor.copy(alpha = memberColor.alpha * alphaMultiplier))
    }
    return
  }
  withLayerPaint(alpha = 1f, blendMode = BlendMode.SrcOver, bounds = layerBounds) {
    for (index in members.indices.reversed()) {
      val memberColor = color(members[index])
      // Src with path coverage wipes the member's whole bounding box on the API 23-27 renderer;
      // DstOut then Plus is the same coverage blend without that.
      drawPath(paths.members[index], Color.Black, blendMode = BlendMode.DstOut)
      drawPath(
        path = paths.members[index],
        color = memberColor.copy(alpha = memberColor.alpha * alphaMultiplier),
        blendMode = BlendMode.Plus,
      )
    }
  }
}

/** A merged field's member paths and unions, rebuilt only when its shapes or colours change. */
internal class FallbackMergedPaths(val field: GlassMergedField) {
  val members: List<Path> = field.members.map { it.fallbackPath() }
  val union: Path = unionOf(members)

  /** Ordinary input and downgrade input both respect the winning member's fallback policy. */
  val inputMask: Path? = inputMask { it.fallbackBaseColor == null && it.backgroundColor.alpha > 0f }
  val forceInputMask: Path? = inputMask { it.fallbackBaseColor == null }

  private fun inputMask(include: (GlassMergedMember) -> Boolean): Path? = when {
    field.members.none(include) -> null
    field.members.all(include) -> union
    else -> members.indices.reversed().fold(Path()) { mask, index ->
      val operation = if (include(field.members[index])) PathOperation.Union else PathOperation.Difference
      Path().apply { op(mask, members[index], operation) }
    }
  }

  /** Each member minus every earlier member, for drawing without a layer. */
  val exclusive: List<Path> by lazy(LazyThreadSafetyMode.NONE) {
    var covered = Path()
    members.map { path ->
      Path().apply { op(path, covered, PathOperation.Difference) }
        .also { covered = Path().apply { op(covered, path, PathOperation.Union) } }
    }
  }

  /** The pixel-aligned union bounds. */
  val unionBounds: Rect = union.getBounds().let { bounds ->
    Rect(floor(bounds.left), floor(bounds.top), ceil(bounds.right), ceil(bounds.bottom))
  }

  /** The union bounds within [visible] for a layer, or null when they exceed the layer budget. */
  fun layerBounds(visible: Rect?): Rect? = unionBounds.visiblePart(visible)
    .takeIf { IntSize(it.width.toInt(), it.height.toInt()).fitsGlassLayerBudget() }
}

private fun GlassMergedMember.fallbackPath(): Path = Path().apply {
  addRoundRect(cornerRadii.toRoundRect(rect.size).translate(rect.topLeft))
}

/** The hard union of the [include]d members' rounded rects, with no bridge between them. */
internal fun GlassMergedField.fallbackUnionPath(
  include: (GlassMergedMember) -> Boolean = { true },
): Path = unionOf(members.filter(include).map { it.fallbackPath() })

private fun unionOf(paths: List<Path>): Path = paths.reduceOrNull { union, path ->
  Path().apply { op(union, path, PathOperation.Union) }
} ?: Path()

internal fun fallbackEdgeAlpha(ambientResponse: Float): Float = 0.18f * ambientResponse

private const val FALLBACK_HARD_EDGE_SOFTNESS_PX = 2f

private data class FallbackGlassPreparedDraw(
  val size: Size,
  val style: ResolvedGlassStyle,
  val shapePath: Path?,
  val mergedPaths: FallbackMergedPaths?,
  val highlightAlpha: Float,
  val highlightRadius: Float,
  val highlightBrush: Brush?,
  val edgeAlpha: Float,
  val edgeBrush: Brush?,
  val edgeDirectAlpha: Float,
  val edgeDirectBrush: Brush?,
  val edgeStroke: Stroke?,
  val edgeShadowAlpha: Float,
  val edgeShadowBrush: Brush?,
  val interaction: GlassInteractionUniforms,
  val interactionAlpha: Float,
  val interactionBrush: Brush?,
  val memberLightBrushes: List<Brush?>?,
)
