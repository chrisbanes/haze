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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntSize

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
  private var effects: List<HazeEffect> = emptyList()

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
    for (effect in effects) {
      effect.update(layoutDirection, drawContext.density)
    }

    if (!useGraphicsLayers()) {
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
        // We add all the clip outs to the canvas (Canvas will combine them)
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
      val effectLayer = requireNotNull(effect.layer)

      // We need to inflate the bounds by the blur radius, so that the effect
      // has access to the pixels it needs in the clipRect
      val inflatedBounds = effect.bounds.inflate(effect.blurRadius.toPx())
      effectLayer.record(size = inflatedBounds.size.roundToIntSize()) {
        translate(-effect.bounds.left, -effect.bounds.top) {
          // Finally draw the content into our effect layer
          drawLayer(contentLayer)
        }
      }

      // Draw the effect's graphic layer, translated to the correct position
      translate(effect.bounds.left, effect.bounds.top) {
        drawEffect(this, effect, effectLayer)
      }
    }

    graphicsContext.releaseGraphicsLayer(contentLayer)
  }

  override fun onDetach() {
    val graphicsContext = currentValueOf(LocalGraphicsContext)
    effects.asSequence()
      .mapNotNull { it.layer }
      .forEach { graphicsContext.releaseGraphicsLayer(it) }
  }

  private fun update(
    state: HazeState = this.state,
    defaultStyle: HazeStyle = this.style,
    position: Offset = this.position,
  ): Boolean {
    val currentEffectsIsEmpty = effects.isEmpty()
    val currentEffects = effects.associateByTo(HashMap(), HazeEffect::area)

    // We create a RenderNode for each of the areas we need to apply our effect to
    effects = state.areas.asSequence()
      .filter { it.isValid }
      .map { area ->
        // We re-use any current effects, otherwise we need to create a new one
        currentEffects.remove(area) ?: HazeEffect(
          area = area,
          layer = when {
            useGraphicsLayers() -> currentValueOf(LocalGraphicsContext).createGraphicsLayer()
            else -> null
          },
        )
      }
      .toList()

    // Any effects left in the currentEffects are no longer used, lets recycle them
    currentEffects.forEach { (_, effect) -> effect.recycle() }
    currentEffects.clear()

    effects.forEach { effect ->
      val resolvedStyle = resolveStyle(defaultStyle, effect.area.style)

      effect.bounds = effect.area.boundsInLocal(position) ?: Rect.Zero
      effect.blurRadius = resolvedStyle.blurRadius
      effect.noiseFactor = resolvedStyle.noiseFactor
      effect.tint = resolvedStyle.tint
      effect.shape = effect.area.shape
    }

    val needInvalidate = effects.any { it.needInvalidation }

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    return needInvalidate || (effects.isEmpty() != currentEffectsIsEmpty)
  }

  private fun HazeEffect.updateLayer(
    layoutDirection: LayoutDirection,
    density: Density,
  ) {
    layer?.apply {
      colorFilter = when {
        tint.alpha >= 0.005f -> ColorFilter.tint(tint, BlendMode.SrcOver)
        else -> null
      }
      clip = true
      setOutline(shape.createOutline(bounds.size, layoutDirection, density))
      renderEffect = createRenderEffect(this@updateLayer, density)
    }
    layerDirty = false
  }

  private fun HazeEffect.update(layoutDirection: LayoutDirection, density: Density) {
    if (layerDirty) updateLayer(layoutDirection, density)

    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }

  private fun HazeEffect.recycle() {
    pathPool.release(path)
    pathPool.release(contentClipPath)
    layer?.let { currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(it) }
  }
}

internal expect fun HazeNode.createRenderEffect(effect: HazeEffect, density: Density): RenderEffect?

internal expect fun HazeNode.useGraphicsLayers(): Boolean

internal expect fun HazeNode.drawEffect(
  drawScope: DrawScope,
  effect: HazeEffect,
  graphicsLayer: GraphicsLayer? = null,
)
