// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize

internal class AndroidHazeNode(
  state: HazeState,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
) : HazeNode(
  state = state,
  backgroundColor = backgroundColor,
  defaultTint = tint,
  blurRadius = blurRadius,
  noiseFactor = noiseFactor,
),
  DrawModifierNode,
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode {

  private val impl: Impl = ScrimImpl()

  private var position by mutableStateOf(Offset.Unspecified)
  private var size by mutableStateOf(Size.Unspecified)
  private var isCanvasHardwareAccelerated by mutableStateOf(false)

  override fun onUpdate() {
    updateImpl()
  }

  override fun onAttach() {
    onObservedReadsChanged()
  }

  override fun onObservedReadsChanged() {
    observeReads { updateImpl() }
  }

  private fun updateImpl() {
    val changed = impl.update(
      state = state,
      blurRadius = blurRadius,
      defaultTint = defaultTint,
      position = position,
      density = currentValueOf(LocalDensity),
      layoutDirection = currentValueOf(LocalLayoutDirection),
      noiseFactor = noiseFactor,
    )

    if (changed) {
      invalidateDraw()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    position = coordinates.positionInWindow() + calculateWindowOffset()
    size = coordinates.size.toSize()
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size.toSize()
  }

  override fun ContentDrawScope.draw() {
    // Similar to above, drawRenderNode is only available on hw-accelerated canvases.
    // To avoid crashing we just draw the content and return early.
    val canvasHardwareAccelerated = drawContext.canvas.nativeCanvas.isHardwareAccelerated
    if (canvasHardwareAccelerated != isCanvasHardwareAccelerated) {
      isCanvasHardwareAccelerated = canvasHardwareAccelerated
      updateImpl()
    }

    with(impl) {
      draw(backgroundColor)
    }
  }

  internal interface Impl {
    fun ContentDrawScope.draw(backgroundColor: Color)
    fun update(
      state: HazeState,
      blurRadius: Dp,
      defaultTint: Color,
      position: Offset,
      density: Density,
      layoutDirection: LayoutDirection,
      noiseFactor: Float,
    ): Boolean
  }
}

private class ScrimImpl : AndroidHazeNode.Impl {
  private var effects: List<Effect> = emptyList()

  override fun ContentDrawScope.draw(backgroundColor: Color) {
    drawContent()

    for (effect in effects) {
      drawPath(path = effect.path, color = effect.tint)
    }
  }

  override fun update(
    state: HazeState,
    blurRadius: Dp,
    defaultTint: Color,
    position: Offset,
    density: Density,
    layoutDirection: LayoutDirection,
    noiseFactor: Float,
  ): Boolean {
    effects = state.areas.asSequence()
      .filter { it.isValid }
      .mapNotNull { area ->
        val bounds = area.boundsInLocal(position) ?: return@mapNotNull null

        // TODO: Should try and re-use this
        val path = Path()
        area.updatePath(path, bounds, layoutDirection, density)

        Effect(
          path = path,
          tint = when {
            area.tint.isSpecified -> area.tint
            // We need to boost the alpha as we don't have a blur effect
            else -> defaultTint.copy(alpha = (defaultTint.alpha * 1.35f).coerceAtMost(1f))
          },
        )
      }.toList()

    return true
  }

  private data class Effect(
    val tint: Color,
    val path: Path,
  )
}
