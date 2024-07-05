// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.roundToIntSize

/**
 * A simple object path for [Path]s. They're fairly expensive so it makes sense to
 * re-use instances.
 */
private val pathPool by lazy { Pool(Path::rewind) }

internal class HazeNode(
  var state: HazeState,
  var style: HazeStyle,
) : Modifier.Node(),
  DrawModifierNode,
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  ObserverModifierNode {

  private var position by mutableStateOf(Offset.Unspecified)
  private var size by mutableStateOf(IntSize.Zero)
  private var effects: List<Effect> = emptyList()

  fun onUpdate() {
    updateAndInvalidate()
  }

  override fun onAttach() {
    onObservedReadsChanged()
  }

  override fun onObservedReadsChanged() {
    observeReads { updateAndInvalidate() }
  }

  private fun updateAndInvalidate() {
    if (update()) {
      invalidateDraw()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    position = coordinates.positionInWindow() + calculateWindowOffset()
    size = coordinates.size
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPlaced(coordinates)
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size
  }

  override fun ContentDrawScope.draw() {
    if (effects.isEmpty()) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    effects.forEach { it.update() }

    if (!usingGraphicsLayers()) {
      // If we're not using graphics layers, our code path is much simpler.
      // We just draw the content directly to the canvas, and then draw each effect over it
      drawContent()

      for (effect in effects) {
        clipShape(
          shape = effect.shape,
          bounds = effect.bounds,
          path = { effect.getUpdatedPath(layoutDirection, drawContext.density) },
          block = { drawEffect(this, effect) },
        )
      }
      return
    }

    val graphicsContext = currentValueOf(LocalGraphicsContext)
    val contentLayer = graphicsContext.createGraphicsLayer()

    // First we draw the composable content into a graphics layer
    contentLayer.record(size = size.roundToIntSize()) {
      this@draw.drawContent()
    }

    // Now we draw `contentNode` into the window canvas, clipping any effect areas
    // (they will be drawn on top)
    with(drawContext.canvas) {
      withSave {
        // We add all of the clip outs to the canvas (Canvas will combine them)
        for (effect in effects) {
          clipShape(effect.shape, effect.contentClipBounds, ClipOp.Difference) {
            effect.getUpdatedContentClipPath(layoutDirection, drawContext.density)
          }
        }
        // Then we draw the content layer
        drawLayer(contentLayer)
      }
    }

    // Now we draw each effect over the content
    for (effect in effects) {
      // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
      // The RenderEffect applied will provide the blurring effect.
      val effectLayer = graphicsContext.createGraphicsLayer()

      // We need to inflate the bounds by the blur radius, so that the effect
      // has access to the pixels it needs in the clipRect
      val inflatedBounds = effect.bounds.inflate(effect.blurRadius.toPx())
      effectLayer.record(size = inflatedBounds.size.roundToIntSize()) {
        translate(-effect.bounds.left, -effect.bounds.top) {
          // Finally draw the content into our effect layer
          drawLayer(contentLayer)
        }
      }

      // Now position the effect, and apply the render effect
      effectLayer.topLeft = effect.bounds.topLeft.round()
      effectLayer.renderEffect = effect.renderEffect
      effectLayer.colorFilter = when {
        effect.tint.alpha >= 0.005f -> ColorFilter.tint(effect.tint, BlendMode.SrcOver)
        else -> null
      }

      // We draw the 'effect' to the window canvas, drawing on top of the original content
      clipShape(
        shape = effect.shape,
        bounds = effect.bounds,
        path = { effect.getUpdatedPath(layoutDirection, drawContext.density) },
        block = { drawEffect(this, effect, effectLayer) },
      )

      graphicsContext.releaseGraphicsLayer(effectLayer)
    }

    graphicsContext.releaseGraphicsLayer(contentLayer)
  }

  private fun update(
    state: HazeState = this.state,
    defaultStyle: HazeStyle = this.style,
    position: Offset = this.position,
  ): Boolean {
    val currentEffectsIsEmpty = effects.isEmpty()
    val currentEffects = effects.associateByTo(HashMap(), Effect::area)

    // We create a RenderNode for each of the areas we need to apply our effect to
    effects = state.areas.asSequence()
      .filter { it.isValid }
      .map { area ->
        // We re-use any current effects, otherwise we need to create a new one
        currentEffects.remove(area) ?: Effect(
          area = area,
          path = pathPool.acquireOrCreate(::Path),
          contentClipPath = pathPool.acquireOrCreate(::Path),
        )
      }
      .toList()

    // Any effects left in the currentEffects are no longer used, lets recycle them
    currentEffects.forEach { (_, effect) -> effect.recycle() }
    currentEffects.clear()

    val invalidateCount = effects.count { effect ->
      val bounds = effect.area.boundsInLocal(position) ?: Rect.Zero
      val resolvedStyle = resolveStyle(defaultStyle, effect.area.style)

      effect.updateParameters(
        bounds = bounds,
        blurRadius = resolvedStyle.blurRadius,
        noiseFactor = resolvedStyle.noiseFactor,
        tint = resolvedStyle.tint,
        shape = effect.area.shape,
      )
    }

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    return invalidateCount > 0 || (effects.isEmpty() != currentEffectsIsEmpty)
  }

  private fun Effect.update() {
    if (renderEffectDirty && usingGraphicsLayers()) updateRenderEffect(this)
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }

  private fun Effect.recycle() {
    pathPool.release(path)
    pathPool.release(contentClipPath)
  }
}

internal expect fun HazeNode.updateRenderEffect(effect: Effect)
internal expect fun HazeNode.usingGraphicsLayers(): Boolean
internal expect fun HazeNode.drawEffect(drawScope: DrawScope, effect: Effect, graphicsLayer: GraphicsLayer? = null)

internal fun Effect.updateParameters(
  bounds: Rect,
  blurRadius: Dp,
  noiseFactor: Float,
  tint: Color,
  shape: Shape,
): Boolean {
  this.bounds = bounds
  this.blurRadius = blurRadius
  this.noiseFactor = noiseFactor
  this.shape = shape
  this.tint = tint
  return renderEffectDirty || layerDirty || pathsDirty
}

private fun Effect.getUpdatedPath(layoutDirection: LayoutDirection, density: Density): Path {
  if (pathsDirty) {
    updatePaths(layoutDirection, density)
  }
  return path
}

private fun Effect.getUpdatedContentClipPath(
  layoutDirection: LayoutDirection,
  density: Density,
): Path {
  if (pathsDirty) {
    updatePaths(layoutDirection, density)
  }
  return contentClipPath
}

private fun Effect.updatePaths(layoutDirection: LayoutDirection, density: Density) {
  path.rewind()
  if (!bounds.isEmpty) {
    path.addOutline(shape.createOutline(bounds.size, layoutDirection, density))
  }

  contentClipPath.rewind()
  val bounds = contentClipBounds
  if (!bounds.isEmpty) {
    contentClipPath.addOutline(
      shape.createOutline(bounds.size, layoutDirection, density),
    )
  }

  pathsDirty = false
}

internal class Effect(
  val area: HazeArea,
  val path: Path,
  val contentClipPath: Path,
  var renderEffect: RenderEffect? = null,
  var renderEffectDirty: Boolean = true,
  var pathsDirty: Boolean = true,
  var layerDirty: Boolean = true,
) {

  val contentClipBounds: Rect
    get() = when {
      bounds.isEmpty -> bounds
      // We clip the content to a slightly smaller rect than the blur bounds, to reduce the
      // chance of rounding + anti-aliasing causing visually problems
      else -> bounds.deflate(2f).takeIf { it.width >= 0 && it.height >= 0 } ?: Rect.Zero
    }

  var bounds: Rect = Rect.Zero
    set(value) {
      if (value != field) {
        layerDirty = true
        if (value.size != field.size) {
          pathsDirty = true
        }
        field = value
      }
    }

  var blurRadius: Dp = Dp.Unspecified
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  var noiseFactor: Float = 0f
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  var tint: Color = Color.Unspecified
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  var shape: Shape = RectangleShape
    set(value) {
      if (value != field) {
        pathsDirty = true
      }
      field = value
    }
}

private fun Canvas.clipShape(
  shape: Shape,
  bounds: Rect,
  clipOp: ClipOp = ClipOp.Intersect,
  path: () -> Path,
) {
  if (shape == RectangleShape) {
    clipRect(bounds, clipOp)
  } else {
    pathPool.usePath { tmpPath ->
      tmpPath.addPath(path(), bounds.topLeft)
      clipPath(tmpPath, clipOp)
    }
  }
}

private fun DrawScope.clipShape(
  shape: Shape,
  bounds: Rect,
  clipOp: ClipOp = ClipOp.Intersect,
  path: () -> Path,
  block: DrawScope.() -> Unit,
) {
  if (shape == RectangleShape) {
    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom, clipOp, block)
  } else {
    pathPool.usePath { tmpPath ->
      tmpPath.addPath(path(), bounds.topLeft)
      clipPath(tmpPath, clipOp, block)
    }
  }
}
