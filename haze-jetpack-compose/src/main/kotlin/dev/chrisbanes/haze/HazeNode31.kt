// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.jetpackcompose.R
import kotlin.math.roundToInt

@RequiresApi(31)
internal class HazeNode31(
  private var areas: List<RoundRect>,
  private var backgroundColor: Color,
  private var tint: Color,
  private var noiseFactor: Float,
  private var blurRadius: Dp,
) : HazeNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {

  private var effects: List<EffectHolder> = emptyList()

  private var noiseTexture: Bitmap? = null
  private var noiseTextureFactor: Float = Float.MIN_VALUE

  override fun onAttach() {
    effects = buildEffects()
  }

  override fun update(
    areas: List<RoundRect>,
    backgroundColor: Color,
    tint: Color,
    blurRadius: Dp,
    noiseFactor: Float,
  ) {
    this.areas = areas
    this.backgroundColor = backgroundColor
    this.tint = tint
    this.blurRadius = blurRadius
    this.noiseFactor = noiseFactor

    if (isAttached) {
      effects = buildEffects()
    }
  }

  override fun ContentDrawScope.draw() {
    val contentDrawScope = this

    val contentNode = RenderNode("content").apply {
      setPosition(0, 0, size.width.toInt(), size.height.toInt())
    }

    // First we draw the composable content into `contentNode`
    Canvas(contentNode.beginRecording()).also { canvas ->
      draw(this, layoutDirection, canvas, size) {
        contentDrawScope.drawContent()
      }
      contentNode.endRecording()
    }

    // Now we draw `contentNode` into the window canvas, so that it is displayed
    drawIntoCanvas { canvas ->
      canvas.nativeCanvas.drawRenderNode(contentNode)
    }

    // Now we need to draw `contentNode` into each of our 'effect' RenderNodes, allowing
    // their RenderEffect to be applied to the composable content.
    effects.forEach { effect ->
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
      effects.forEach { effect ->
        with(effect) {
          clipPath(
            Path().apply { addRoundRect(area) },
          ) {
            canvas.nativeCanvas.drawRenderNode(renderNode)
          }
        }
      }
    }
  }

  private fun buildEffects(): List<EffectHolder> {
    val blurRadiusPx = with(currentValueOf(LocalDensity)) { blurRadius.toPx() }

    // This is our RenderEffect. It first applies a blur effect, and then a color filter effect
    // to allow content to be visible on top
    val effect = RenderEffect.createBlurEffect(
      blurRadiusPx,
      blurRadiusPx,
      Shader.TileMode.DECAL,
    ).let {
      RenderEffect.createBlendModeEffect(
        RenderEffect.createShaderEffect(
          BitmapShader(createNoiseTextureIfNeeded(), REPEAT, REPEAT),
        ),
        it,
        BlendMode.HARD_LIGHT,
      )
    }.let {
      RenderEffect.createColorFilterEffect(
        BlendModeColorFilter(tint.toArgb(), BlendMode.SRC_OVER),
        it,
      )
    }

    // We create a RenderNode for each of the areas we need to apply our effect to
    return areas.map { area ->
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
      EffectHolder(
        renderNode = node,
        renderNodeDrawArea = expandedRect,
        area = area,
      )
    }
  }

  @SuppressLint("SuspiciousCompositionLocalModifierRead") // LocalContext will never change
  private fun createNoiseTextureIfNeeded(noiseFactor: Float = this.noiseFactor): Bitmap {
    val current = noiseTexture
    // If the noise factor hasn't changed and we have a texture, nothing to do...
    if (current != null && noiseTextureFactor == noiseFactor) {
      return current
    }

    // We draw the noise with the given opacity
    return BitmapFactory.decodeResource(
      currentValueOf(LocalContext).resources,
      R.drawable.haze_noise,
    ).withAlpha(noiseFactor)
  }
}

private class EffectHolder(
  val renderNode: RenderNode,
  val renderNodeDrawArea: RoundRect,
  val area: RoundRect,
)

/**
 * Returns a copy of the current [Bitmap], drawn with the given [alpha] value.
 *
 * There might be a better way to do this via a [BlendMode], but none of the results looked as
 * good.
 */
private fun Bitmap.withAlpha(alpha: Float): Bitmap {
  val paint = android.graphics.Paint().apply {
    this.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
  }

  return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
    android.graphics.Canvas(it).apply {
      drawBitmap(this@withAlpha, 0f, 0f, paint)
    }
  }
}
