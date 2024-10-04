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
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
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
import androidx.compose.ui.unit.toSize

internal class HazeChildNode(
  var state: HazeState,
  var block: HazeChildScope.() -> Unit,
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  GlobalPositionAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode {

  private var positionOnScreen by mutableStateOf(Offset.Unspecified)

  private val effect by lazy(::ReusableHazeEffect)

  override val shouldAutoInvalidate: Boolean = false

  fun update() {
    onObservedReadsChanged()
  }

  override fun onAttach() {
    update()
  }

  override fun onObservedReadsChanged() {
    observeReads {
      updateEffect()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    effect.positionOnScreen = coordinates.positionInWindow() + calculateWindowOffset()
    effect.size = coordinates.size.toSize()

    val blurRadiusPx = with(currentValueOf(LocalDensity)) { effect.blurRadius.toPx() }
    effect.layerSize = effect.size.expand(blurRadiusPx * 2)

    updateEffect()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) = onPlaced(coordinates)

  override fun ContentDrawScope.draw() {
    log(TAG) { "-> HazeChild. start draw()" }

    if (!effect.isValid) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      log(TAG) { "-> HazeChild. end draw()" }
      return
    }

    // First we need to make sure that the effects are updated (if necessary)
    effect.onPreDraw(drawContext.density)

    if (USE_GRAPHICS_LAYERS) {
      val contentLayer = state.contentLayer
      if (contentLayer != null) {
        drawEffectsWithGraphicsLayer(contentLayer)
      }
    } else {
      drawEffectsWithScrim()
    }

    // Finally we draw the content
    drawContent()

    effect.onPostDraw()

    log(TAG) { "-> HazeChild. end draw()" }
  }

  private fun updateEffect() {
    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    block(effect)

    if (effect.needInvalidation) {
      invalidateDraw()
    }
  }

  private fun DrawScope.drawEffectsWithGraphicsLayer(contentLayer: GraphicsLayer) {
    // Now we need to draw `contentNode` into each of an 'effect' graphic layers.
    // The RenderEffect applied will provide the blurring effect.
    currentValueOf(LocalGraphicsContext).useGraphicsLayer { layer ->
      layer.renderEffect = effect.renderEffect
      layer.alpha = effect.alpha

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

        translate(inflatedOffset + state.positionOnScreen - effect.positionOnScreen) {
          // Draw the content into our effect layer
          drawLayer(contentLayer)
        }
      }

      drawEffect(effect = effect, innerDrawOffset = -inflatedOffset, layer = layer)
    }
  }

  private fun DrawScope.drawEffectsWithScrim() {
    drawEffect(effect)
  }

  private fun DrawScope.drawEffect(
    effect: ReusableHazeEffect,
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

  private fun ReusableHazeEffect.onPreDraw(density: Density) {
    if (renderEffectDirty) {
      renderEffect = createRenderEffect(this, density)
      renderEffectDirty = false
    }
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }

  private fun ReusableHazeEffect.onPostDraw() {
    drawParametersDirty = false
  }

  private companion object {
    const val TAG = "HazeChild"
  }
}

internal expect fun HazeChildNode.drawEffect(
  drawScope: DrawScope,
  effect: ReusableHazeEffect,
  graphicsLayer: GraphicsLayer? = null,
)

internal expect fun HazeChildNode.createRenderEffect(
  effect: ReusableHazeEffect,
  density: Density,
): RenderEffect?

internal class ReusableHazeEffect : HazeChildScope {
  var renderEffect: RenderEffect? = null
  var renderEffectDirty: Boolean = true
  var drawParametersDirty: Boolean = true

  var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)

  val isValid: Boolean
    get() = size.isSpecified && layerSize.isSpecified

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
    get() = when {
      isValid -> {
        Offset(
          x = (layerSize.width - size.width) / 2f,
          y = (layerSize.height - size.height) / 2f,
        )
      }

      else -> Offset.Zero
    }

  override var blurRadius: Dp = HazeDefaults.blurRadius
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  override var noiseFactor: Float = HazeDefaults.noiseFactor
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  override var mask: Brush? = null
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  override var backgroundColor: Color = Color.Unspecified

  override var tints: List<HazeTint> = emptyList()
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  override var fallbackTint: HazeTint? = null
    set(value) {
      if (value != field) {
        renderEffectDirty = true
        field = value
      }
    }

  override var alpha: Float = 1f
    set(value) {
      if (value != field) {
        drawParametersDirty = true
        field = value
      }
    }

  override fun applyStyle(style: HazeStyle) {
    noiseFactor = style.noiseFactor
    blurRadius = style.blurRadius
    tints = style.tints
    fallbackTint = style.fallbackTint
    backgroundColor = style.backgroundColor
  }
}

internal val ReusableHazeEffect.blurRadiusOrZero: Dp
  get() = blurRadius.takeOrElse { 0.dp }

internal val ReusableHazeEffect.needInvalidation: Boolean
  get() = renderEffectDirty || drawParametersDirty

private fun Size.expand(expansion: Float): Size {
  return Size(width = width + expansion, height = height + expansion)
}
