// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect

internal fun Canvas.clipShape(
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

internal fun DrawScope.clipShape(
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
