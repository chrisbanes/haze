// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

internal class HazeEffect(
  val area: HazeArea,
  val layer: GraphicsLayer?,
) {
  val path by lazy { pathPool.acquireOrCreate { Path() } }
  val contentClipPath by lazy { pathPool.acquireOrCreate { Path() } }

  var pathsDirty: Boolean = true
  var layerDirty: Boolean = true

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
        layerDirty = true
        field = value
      }
    }

  var noiseFactor: Float = 0f
    set(value) {
      if (value != field) {
        layerDirty = true
        field = value
      }
    }

  var tint: Color = Color.Unspecified
    set(value) {
      if (value != field) {
        layerDirty = true
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

internal val HazeEffect.needInvalidation: Boolean
  get() = layerDirty || layerDirty || pathsDirty

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
