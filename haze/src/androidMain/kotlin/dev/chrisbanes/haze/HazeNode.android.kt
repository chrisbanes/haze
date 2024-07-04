// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import androidx.annotation.RequiresApi
import androidx.collection.lruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

internal actual fun CompositionLocalConsumerModifierNode.updateRenderEffect(effect: Effect) {
  with(effect) {
    layer.renderEffect = RenderEffect
      .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
      .withNoise(noiseFactor)
      .withTint(tint)
      .asComposeRenderEffect()

    renderEffectDirty = false
  }
}

private val noiseTextureCache = lruCache<Float, Bitmap>(3)

context(CompositionLocalConsumerModifierNode)
private fun getNoiseTexture(noiseFactor: Float): Bitmap {
  val cached = noiseTextureCache[noiseFactor]
  if (cached != null) return cached

  // We draw the noise with the given opacity
  return BitmapFactory.decodeResource(
    currentValueOf(LocalContext).resources,
    R.drawable.haze_noise,
  )
    .withAlpha(noiseFactor)
    .also { noiseTextureCache.put(noiseFactor, it) }
}

context(CompositionLocalConsumerModifierNode)
@RequiresApi(31)
private fun RenderEffect.withNoise(noiseFactor: Float): RenderEffect = when {
  noiseFactor >= 0.005f -> {
    val noiseShader = BitmapShader(getNoiseTexture(noiseFactor), REPEAT, REPEAT)
    RenderEffect.createBlendModeEffect(
      RenderEffect.createShaderEffect(noiseShader), // dst
      this, // src
      BlendMode.DST_ATOP, // blendMode
    )
  }

  else -> this
}

context(CompositionLocalConsumerModifierNode)
private fun RenderEffect.withTint(tint: Color): RenderEffect = when {
  tint.alpha >= 0.005f -> {
    // If we have an tint with a non-zero alpha value, wrap the effect with a color filter
    RenderEffect.createColorFilterEffect(
      BlendModeColorFilter(tint.toArgb(), BlendMode.SRC_OVER),
      this,
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
