// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.haze(
  vararg area: Rect,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
): Modifier = haze(
  areas = area.map { RoundRect(it) },
  tint = tint,
  backgroundColor = backgroundColor,
  blurRadius = blurRadius,
)

fun Modifier.haze(
  vararg area: RoundRect,
  backgroundColor: Color,
  tint: Color = HazeDefaults.tint(backgroundColor),
  blurRadius: Dp = HazeDefaults.blurRadius,
): Modifier = haze(
  areas = area.toList(),
  tint = tint,
  backgroundColor = backgroundColor,
  blurRadius = blurRadius,
)

/**
 * Defaults for the [haze] modifiers.
 */
object HazeDefaults {
  /**
   * Default blur radius. Larger values produce a stronger blur effect.
   */
  val blurRadius: Dp = 20.dp

  /**
   * Default alpha used for the tint color. Used by the [tint] function.
   */
  val tintAlpha: Float = 0.7f

  /**
   * Default builder for the 'tint' color. Transforms the provided [color].
   */
  fun tint(color: Color): Color = color.copy(alpha = tintAlpha)
}

internal fun Modifier.haze(
  areas: List<RoundRect>,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
): Modifier {
  if (Build.VERSION.SDK_INT < 31) {
    // On older platforms we display a translucent scrim
    return drawWithContent {
      drawContent()

      val path = Path()
      for (area in areas) {
        path.addRoundRect(area)
        // We need to boost the alpha as we don't have a blur effect
//        drawRoundRect(
//          color = tint.copy(alpha = (tint.alpha * 1.35f).coerceAtMost(1f)),
//          topLeft = Offset(area.left, area.top),
//          size = Size(area.width, area.height),
//          cornerRadius = CornerRadius(
//            x = area.corn
//          ),
//        )
      }
      // We need to boost the alpha as we don't have a blur effect
      drawPath(
        path = path,
        color = tint.copy(alpha = (tint.alpha * 1.35f).coerceAtMost(1f)),
      )
    }
  }

  return drawWithCache {
    // This is our RenderEffect. It first applies a blur effect, and then a color filter effect
    // to allow content to be visible on top
    val blurRadiusPx = blurRadius.toPx()
    val effect = RenderEffect.createColorFilterEffect(
      BlendModeColorFilter(tint.toArgb(), BlendMode.SRC_OVER),
      RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.DECAL),
    )

    val contentNode = RenderNode("content").apply {
      setPosition(0, 0, size.width.toInt(), size.height.toInt())
    }

    // We create a RenderNode for each of the areas we need to apply our effect to
    val effectRenderNodes = areas.map { area ->
      // We expand the area where our effect is applied to. This is necessary so that the blur
      // effect is applied evenly to allow edges. If we don't do this, the blur effect is much less
      // visible on the edges of the area.
      val expandedRect = area.inflate(blurRadiusPx)

      val node = RenderNode("blur").apply {
        setRenderEffect(effect)
        setPosition(0, 0, expandedRect.width.toInt(), expandedRect.height.toInt())
        translationX = expandedRect.left
        translationY = expandedRect.top
      }
      RoundRect
      EffectRenderNodeHolder(renderNode = node, renderNodeDrawArea = expandedRect, area = area)
    }

    onDrawWithContent {
      // First we draw the composable content into `contentNode`
      Canvas(contentNode.beginRecording()).also { canvas ->
        draw(this, layoutDirection, canvas, size) {
          this@onDrawWithContent.drawContent()
        }
        contentNode.endRecording()
      }

      // Now we draw `contentNode` into the window canvas, so that it is displayed
      drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawRenderNode(contentNode)
      }

      // Now we need to draw `contentNode` into each of our 'effect' RenderNodes, allowing
      // their RenderEffect to be applied to the composable content.
      effectRenderNodes.forEach { effect ->
        effect.renderNode.beginRecording().also { canvas ->
          // We need to draw our background color first, as the `contentNode` may not draw
          // a background. This then makes the blur effect much less pronounced, as blurring with
          // transparent negates the effect.
          canvas.drawColor(backgroundColor.toArgb())
          canvas.translate(-effect.renderNodeDrawArea.left, -effect.renderNodeDrawArea.top)
          canvas.drawRenderNode(contentNode)
          effect.renderNode.endRecording()
        }
      }

      // Finally we draw each 'effect' RenderNode to the window canvas, drawing on top
      // of the original content
      drawIntoCanvas { canvas ->
        effectRenderNodes.forEach { effect ->
          with(effect) {
            clipRect(area.left, area.top, area.right, area.bottom) {
              canvas.nativeCanvas.drawRenderNode(renderNode)
            }
          }
        }
      }
    }
  }
}

private class EffectRenderNodeHolder(
  val renderNode: RenderNode,
  val renderNodeDrawArea: RoundRect,
  val area: RoundRect,
)
