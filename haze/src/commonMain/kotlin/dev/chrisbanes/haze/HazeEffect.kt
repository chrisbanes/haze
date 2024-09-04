// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.takeOrElse
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
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

  private val _effects: MutableList<HazeEffect> = mutableListOf()
  val effects: List<HazeEffect> = _effects

  override val shouldAutoInvalidate: Boolean = false

  internal var lastInvalidationTick = Int.MIN_VALUE

  open fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    update()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      updateEffects()
      observeInvalidationTick()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  protected open fun updateEffects() {
    val currentEffectsIsEmpty = effects.isEmpty()
    val currentEffects = effects.associateByTo(mutableMapOf(), HazeEffect::area)

    _effects.clear()

    val density = currentValueOf(LocalDensity)

    // We create a RenderNode for each of the areas we need to apply our effect to
    calculateHazeAreas()
      .filter { it.isValid }
      .map { area ->
        // We re-use any current effects, otherwise we need to create a new one
        currentEffects.remove(area) ?: HazeEffect(area = area)
      }
      .onEach { effect ->
        val resolvedStyle = resolveStyle(state.contentArea.style(), effect.area.style())

        val blurRadiusPx = with(density) { resolvedStyle.blurRadius.toPx() }

        effect.size = effect.area.size
        effect.layerSize = Size(
          width = effect.size.width + (blurRadiusPx * 2),
          height = effect.size.height + (blurRadiusPx * 2),
        )
        effect.positionOnScreen = effect.area.positionOnScreen

        effect.blurRadius = resolvedStyle.blurRadius
        effect.noiseFactor = resolvedStyle.noiseFactor
        effect.tints = resolvedStyle.tints
        effect.fallbackTint = resolvedStyle.fallbackTint
        effect.backgroundColor = resolvedStyle.backgroundColor

        effect.mask = effect.area.mask()
      }
      .forEach(_effects::add)

    // Any effects left in currentEffects are no longer used
    currentEffects.clear()

    val needInvalidate = effects.any { it.needInvalidation }

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    if (needInvalidate || (effects.isEmpty() != currentEffectsIsEmpty)) {
      invalidateDraw()
    }
  }

  protected fun DrawScope.drawEffectsWithGraphicsLayer(contentLayer: GraphicsLayer) {
    val graphicsContext = currentValueOf(LocalGraphicsContext)

    // Now we draw each effect over the content
    for (effect in effects) {
      // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
      // The RenderEffect applied will provide the blurring effect.

      graphicsContext.useGraphicsLayer { layer ->
        layer.renderEffect = effect.renderEffect

        // The layer size is usually than the bounds. This is so that we include enough
        // content around the edges to keep the blurring uniform. Without the extra border,
        // the blur will naturally fade out at the edges.
        val inflatedSize = effect.layerSize
        // This is the topLeft in the inflated bounds where the real are should be at [0,0]
        val inflatedOffset = effect.layerOffset

        layer.record(inflatedSize.roundToIntSize()) {
          if (effect.backgroundColor.isSpecified) {
            drawRect(effect.backgroundColor)
          } else {
            error("HazeStyle.backgroundColor not specified. Please provide a color.")
          }

          val contentArea = state.contentArea
          translate(inflatedOffset + contentArea.positionOnScreen - effect.positionOnScreen) {
            // Draw the content into our effect layer
            drawLayer(contentLayer)
          }
        }

        drawEffect(effect = effect, innerDrawOffset = -inflatedOffset, layer = layer)
      }
    }
  }

  protected fun DrawScope.drawEffectsWithScrim() {
    for (effect in effects) {
      drawEffect(effect)
    }
  }

  private fun DrawScope.drawEffect(
    effect: HazeEffect,
    innerDrawOffset: Offset = Offset.Zero,
    layer: GraphicsLayer? = null,
  ) {
    val drawOffset = (effect.positionOnScreen - positionOnScreen).takeOrElse { Offset.Zero }

    translate(drawOffset) {
      clipRect(right = effect.size.width, bottom = effect.size.height) {
        // Since we included a border around the content, we need to translate so that
        // we don't see it (but it still affects the RenderEffect)
        translate(innerDrawOffset) {
          drawEffect(this, effect, layer)
        }
      }
    }
  }

  protected open fun calculateHazeAreas(): Sequence<HazeArea> = emptySequence()

  protected fun HazeEffect.onPreDraw(density: Density) {
    if (renderEffectDirty) {
      renderEffect = createRenderEffect(this, density)
      renderEffectDirty = false
    }
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }
}

internal expect fun HazeEffectNode.drawEffect(
  drawScope: DrawScope,
  effect: HazeEffect,
  graphicsLayer: GraphicsLayer? = null,
)

internal expect fun HazeEffectNode.createRenderEffect(
  effect: HazeEffect,
  density: Density,
): RenderEffect?

internal expect fun HazeEffectNode.observeInvalidationTick()

internal class HazeEffect(val area: HazeArea) {
  var renderEffect: RenderEffect? = null
  var renderEffectDirty: Boolean = true

  var size: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        // We use the size for crop rects/brush sizing
        renderEffectDirty = true
        field = value
      }
    }

  var layerSize: Size = Size.Unspecified
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  val layerOffset: Offset
    get() {
      if (layerSize.isSpecified && size.isSpecified) {
        return Offset(
          x = (layerSize.width - size.width) / 2f,
          y = (layerSize.height - size.height) / 2f,
        )
      }
      return Offset.Zero
    }

  var positionOnScreen: Offset = Offset.Unspecified

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

  var mask: Brush? = null
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  var backgroundColor: Color = Color.Unspecified

  var tints: List<HazeTint> = emptyList()
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  var fallbackTint: HazeTint? = null
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }
}

internal val HazeEffect.blurRadiusOrZero: Dp get() = blurRadius.takeOrElse { 0.dp }

internal val HazeEffect.needInvalidation: Boolean
  get() = renderEffectDirty
