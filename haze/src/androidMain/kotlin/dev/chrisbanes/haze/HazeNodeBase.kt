// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import kotlin.properties.Delegates.observable

/**
 * On older platforms, we draw a translucent scrim over the content
 */
internal class HazeNodeBase(
  state: HazeState,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
  noiseFactor: Float,
) : HazeNode(state, backgroundColor, tint, blurRadius, noiseFactor),
  DrawModifierNode,
  ObserverModifierNode,
  LayoutAwareModifierNode,
  CompositionLocalConsumerModifierNode {

  private var pathsDirty = true
  private var paths: List<PathHolder> = emptyList()

  private var positionInRoot by observable(Offset.Unspecified) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      invalidatePaths()
    }
  }
  private var size by observable(Size.Unspecified) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      invalidatePaths()
    }
  }

  override fun onUpdate() {
    invalidateDraw()
  }

  override fun onObservedReadsChanged() {
    invalidatePaths()
  }

  private fun invalidatePaths() {
    pathsDirty = true
    invalidateDraw()
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    positionInRoot = coordinates.positionInRoot()
    size = coordinates.size.toSize()
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size.toSize()
  }

  override fun ContentDrawScope.draw() {
    drawContent()

    if (pathsDirty) {
      observeReads {
        paths = buildPaths(layoutDirection, currentValueOf(LocalDensity))
        pathsDirty = false
      }
    }

    for (pathHolder in paths) {
      drawPath(
        path = pathHolder.path,
        color = pathHolder.tint,
      )
    }
  }

  private fun buildPaths(
    layoutDirection: LayoutDirection,
    density: Density,
  ): List<PathHolder> = state.areas.asSequence()
    .filter { it.isValid }
    .mapNotNull { area ->
      val bounds = area.boundsInLocal(positionInRoot) ?: return@mapNotNull null

      // TODO: Should try and re-use this
      val path = Path()
      area.updatePath(path, bounds, layoutDirection, density)

      PathHolder(
        path = path,
        tint = when {
          area.tint.isSpecified -> area.tint
          // We need to boost the alpha as we don't have a blur effect
          else -> defaultTint.copy(alpha = (defaultTint.alpha * 1.35f).coerceAtMost(1f))
        },
      )
    }.toList()

  private class PathHolder(
    val tint: Color,
    val path: Path = Path(),
  )
}
