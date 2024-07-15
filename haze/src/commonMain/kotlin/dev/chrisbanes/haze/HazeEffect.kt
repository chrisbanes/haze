// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse

internal abstract class HazeEffectNode :
  Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode {

  abstract var state: HazeState

  protected var positionOnScreen by mutableStateOf(Offset.Unspecified)
    private set

  var effects: List<HazeEffect> = emptyList()
    private set

  open fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    update()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      if (updateEffects()) {
        invalidateDraw()
      }
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  protected open fun updateEffects(): Boolean {
    val currentEffectsIsEmpty = effects.isEmpty()
    val currentEffects = effects.associateByTo(mutableMapOf(), HazeEffect::area)

    // We create a RenderNode for each of the areas we need to apply our effect to
    effects = calculateHazeAreas()
      .asSequence()
      .filter { it.isValid }
      .map { area ->
        // We re-use any current effects, otherwise we need to create a new one
        currentEffects.remove(area) ?: HazeEffect(area = area)
      }
      .onEach { effect ->
        val resolvedStyle = resolveStyle(state.content.style, effect.area.style)

        effect.size = effect.area.size
        effect.positionOnScreen = effect.area.positionOnScreen
        effect.blurRadius = resolvedStyle.blurRadius
        effect.noiseFactor = resolvedStyle.noiseFactor
        effect.tint = resolvedStyle.tint
        effect.shape = effect.area.shape
      }
      .toList()

    // Any effects left in currentEffects are no longer used, so recycle them
    currentEffects.forEach { (_, effect) -> effect.recycle() }
    currentEffects.clear()

    val needInvalidate = effects.any { it.needInvalidation }

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    return needInvalidate || (effects.isEmpty() != currentEffectsIsEmpty)
  }

  protected fun DrawScope.drawEffectsWithGraphicsLayer(contentLayer: GraphicsLayer) {
    val graphicsContext = currentValueOf(LocalGraphicsContext)
    val effectLayer = graphicsContext.createGraphicsLayer()

    // Now we draw each effect over the content
    for (effect in effects) {
      // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
      // The RenderEffect applied will provide the blurring effect.
      val contentArea = state.content
      val boundsInContent = effect.calculateBounds(-contentArea.positionOnScreen)

      effectLayer.colorFilter = when {
        effect.tint.alpha >= 0.005f -> ColorFilter.tint(effect.tint, BlendMode.SrcOver)
        else -> null
      }
      effectLayer.clip = true
      effectLayer.setOutline(effect.outline ?: Outline.Rectangle(effect.size.toRect()))
      effectLayer.renderEffect = effect.renderEffect

      effectLayer.record {
        drawRect(effect.tint.copy(alpha = 1f))

        translate(-boundsInContent.left, -boundsInContent.top) {
          // Finally draw the content into our effect layer
          drawLayer(contentLayer)
        }
      }

      // Draw the effect's graphic layer, translated to the correct position
      val effectOffset = effect.positionOnScreen - positionOnScreen
      translate(effectOffset.x, effectOffset.y) {
        drawEffect(this, effect, effectLayer)
      }
    }

    graphicsContext.releaseGraphicsLayer(effectLayer)
  }

  protected fun DrawScope.drawEffectsWithScrim() {
    for (effect in effects) {
      clipShape(
        shape = effect.shape,
        bounds = effect.calculateBounds(-positionOnScreen),
        path = { effect.getUpdatedPath(layoutDirection, drawContext.density) },
        block = { drawEffect(this, effect) },
      )
    }
  }

  override fun onDetach() {
    effects.forEach { it.recycle() }
    effects = emptyList()
  }

  protected open fun calculateHazeAreas(): List<HazeArea> = emptyList()

  private fun HazeEffect.recycle() {
    pathPool.release(path)
    pathPool.release(contentClipPath)
  }

  protected fun HazeEffect.onPreDraw(
    layoutDirection: LayoutDirection,
    density: Density,
  ) {
    if (renderEffectDirty) {
      renderEffect = createRenderEffect(this, density)
      renderEffectDirty = false
    }
    if (outlineDirty) {
      outline = shape.createOutline(size, layoutDirection, density)
      outlineDirty = false
    }
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }
}

internal class HazeEffect(val area: HazeArea) {
  val path by lazy { pathPool.acquireOrCreate { Path() } }
  val contentClipPath by lazy { pathPool.acquireOrCreate { Path() } }
  var pathsDirty: Boolean = true

  var renderEffect: RenderEffect? = null
  var renderEffectDirty: Boolean = true

  var outline: Outline? = null
  var outlineDirty: Boolean = true

  val contentClipBounds: Rect
    get() = when {
      size.isEmpty() -> Rect.Zero
      else -> {
        // We clip the content to a slightly smaller rect than the blur bounds, to reduce the
        // chance of rounding + anti-aliasing causing visually problems
        size.toRect()
          .translate(positionOnScreen)
          .deflate(2f)
          .takeIf { it.width >= 0 && it.height >= 0 } ?: Rect.Zero
      }
    }

  fun calculateBounds(localPositionOnScreen: Offset = Offset.Zero): Rect = when {
    positionOnScreen.isSpecified && size.isSpecified -> {
      Rect(offset = positionOnScreen - localPositionOnScreen, size = size)
    }
    else -> Rect.Zero
  }

  var size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        pathsDirty = true
        outlineDirty = true
        field = value
      }
    }

  var positionOnScreen: Offset = Offset.Unspecified
    set(value) {
      if (value != field) {
        pathsDirty = true
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

  var shape: Shape = RectangleShape
    set(value) {
      if (value != field) {
        pathsDirty = true
        outlineDirty = true
      }
      field = value
    }
}

internal val HazeEffect.blurRadiusOrZero: Dp get() = blurRadius.takeOrElse { 0.dp }

internal val HazeEffect.needInvalidation: Boolean
  get() = renderEffectDirty || outlineDirty || pathsDirty

internal fun HazeEffect.getUpdatedPath(
  layoutDirection: LayoutDirection,
  density: Density,
): Path {
  if (pathsDirty) updatePaths(layoutDirection, density)
  return path
}

internal fun HazeEffect.getUpdatedContentClipPath(
  layoutDirection: LayoutDirection,
  density: Density,
): Path {
  if (pathsDirty) updatePaths(layoutDirection, density)
  return contentClipPath
}

private fun HazeEffect.updatePaths(layoutDirection: LayoutDirection, density: Density) {
  path.rewind()
  if (!size.isEmpty()) {
    path.addOutline(shape.createOutline(size, layoutDirection, density))
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
