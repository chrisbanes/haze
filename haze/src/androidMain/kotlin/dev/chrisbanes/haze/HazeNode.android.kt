// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.lruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

internal actual fun HazeNode.createRenderEffect(effect: Effect, density: Density): RenderEffect? =
  with(effect) {
    if (Build.VERSION.SDK_INT >= 32) {
      val blurRadiusPx = with(density) { blurRadius.toPx() }
      return AndroidRenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
        .withNoise(noiseFactor)
        .asComposeRenderEffect()
    }
    return null
  }

internal actual fun HazeNode.useGraphicsLayers(): Boolean = Build.VERSION.SDK_INT >= 32

internal actual fun HazeNode.drawEffect(
  drawScope: DrawScope,
  effect: Effect,
  graphicsLayer: GraphicsLayer?,
) = with(drawScope) {
  if (graphicsLayer != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
    drawLayer(graphicsLayer)
  } else {
    drawRect(effect.tint.boostAlphaForBlurRadius(effect.blurRadius))
  }
}

private val noiseTextureCache = lruCache<Int, Bitmap>(3)

context(CompositionLocalConsumerModifierNode)
private fun getNoiseTexture(noiseFactor: Float): Bitmap {
  val cacheKey = (noiseFactor * 255).roundToInt()
  val cached = noiseTextureCache[cacheKey]
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  // We draw the noise with the given opacity
  val resources = currentValueOf(LocalContext).resources
  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise)
    .withAlpha(noiseFactor)
    .also { noiseTextureCache.put(cacheKey, it) }
}

/**
 * In this implementation, the only tool we have is translucency.
 */
private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
  // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
  val factor = 1 + (blurRadius.value / 72f)
  return copy(alpha = (alpha * factor).coerceAtMost(1f))
}

context(CompositionLocalConsumerModifierNode)
@RequiresApi(31)
private fun AndroidRenderEffect.withNoise(noiseFactor: Float): AndroidRenderEffect = when {
  noiseFactor >= 0.005f -> {
    val noiseShader = BitmapShader(getNoiseTexture(noiseFactor), REPEAT, REPEAT)
    AndroidRenderEffect.createBlendModeEffect(
      AndroidRenderEffect.createShaderEffect(noiseShader), // dst
      this, // src
      BlendMode.DST_ATOP, // blendMode
    )
  }

  else -> this
}

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
