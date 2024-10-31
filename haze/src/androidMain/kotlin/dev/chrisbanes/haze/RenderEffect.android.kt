// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.lruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import kotlin.concurrent.getOrSet
import kotlin.math.roundToInt

internal actual fun HazeChildNode.createRenderEffect(
  blurRadiusPx: Float,
  noiseFactor: Float,
  tints: List<HazeTint>,
  tintAlphaModulate: Float,
  contentSize: Size,
  contentOffset: Offset,
  layerSize: Size,
  mask: Brush?,
  progressive: Brush?,
): RenderEffect? {
  if (Build.VERSION.SDK_INT < 31) return null

  log(HazeChildNode.TAG) {
    "createRenderEffect. " +
      "blurRadiusPx=$blurRadiusPx, " +
      "noiseFactor=$noiseFactor, " +
      "tints=$tints, " +
      "contentSize=$contentSize, " +
      "contentOffset=$contentOffset, " +
      "layerSize=$layerSize"
  }

  require(blurRadiusPx >= 0f) { "blurRadius needs to be equal or greater than 0f" }

  val progressiveShader = progressive?.toShader(contentSize)
  val blur = if (Build.VERSION.SDK_INT >= 33 && progressiveShader != null) {
    // If we've been provided with a progressive/gradient blur shader, we need to use
    // our custom blur via a runtime shader
    createBlurImageFilterWithMask(
      blurRadiusPx = blurRadiusPx,
      bounds = Rect(contentOffset, contentSize),
      mask = progressiveShader,
    )
  } else {
    AndroidRenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
  }

  return blur
    .withNoise(noiseFactor)
    .withTints(tints, tintAlphaModulate, progressiveShader, contentOffset)
    .withMask(mask, contentSize, contentOffset)
    .asComposeRenderEffect()
}

internal actual fun DrawScope.useGraphicLayers(): Boolean {
  return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && drawContext.canvas.nativeCanvas.isHardwareAccelerated
}

private val noiseTextureCache = lruCache<Int, Bitmap>(3)

context(CompositionLocalConsumerModifierNode)
private fun getNoiseTexture(noiseFactor: Float): Bitmap {
  val noiseAlphaInt = (noiseFactor * 255).roundToInt().coerceIn(0, 255)
  val cached = noiseTextureCache[noiseAlphaInt]
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  // We draw the noise with the given opacity
  val resources = currentValueOf(LocalContext).resources
  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise)
    .withAlpha(noiseAlphaInt)
    .also { noiseTextureCache.put(noiseAlphaInt, it) }
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

@RequiresApi(31)
private fun AndroidRenderEffect.withMask(
  mask: Brush?,
  size: Size,
  offset: Offset,
  blendMode: BlendMode = BlendMode.DST_IN,
): AndroidRenderEffect {
  val shader = mask?.toShader(size) ?: return this
  return blendWith(AndroidRenderEffect.createShaderEffect(shader), blendMode, offset)
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTints(
  tints: List<HazeTint>,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
  maskOffset: Offset = Offset.Zero,
): AndroidRenderEffect = tints.fold(this) { acc, tint ->
  acc.withTint(tint, alphaModulate, mask, maskOffset)
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTint(
  tint: HazeTint,
  alphaModulate: Float = 1f,
  mask: Shader?,
  maskOffset: Offset,
): AndroidRenderEffect {
  if (!tint.color.isSpecified) return this
  val color = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  if (color.alpha < 0.005f) return this

  return if (mask != null) {
    blendWith(
      foreground = AndroidRenderEffect.createColorFilterEffect(
        BlendModeColorFilter(color.toArgb(), BlendMode.SRC_IN),
        AndroidRenderEffect.createShaderEffect(mask),
      ),
      blendMode = tint.blendMode.toAndroidBlendMode(),
      offset = maskOffset,
    )
  } else {
    AndroidRenderEffect.createColorFilterEffect(
      BlendModeColorFilter(color.toArgb(), tint.blendMode.toAndroidBlendMode()),
      this,
    )
  }
}

@RequiresApi(31)
private fun AndroidRenderEffect.blendWith(
  foreground: AndroidRenderEffect,
  blendMode: BlendMode,
  offset: Offset = Offset.Zero,
): AndroidRenderEffect = AndroidRenderEffect.createBlendModeEffect(
  this,
  // We need to offset the shader to the bounds
  AndroidRenderEffect.createOffsetEffect(offset.x, offset.y, foreground),
  blendMode,
)

@RequiresApi(33)
private fun createBlurImageFilterWithMask(
  blurRadiusPx: Float,
  bounds: Rect,
  mask: Shader,
): AndroidRenderEffect {
  fun shader(vertical: Boolean): AndroidRenderEffect {
    val shader = RuntimeShader(BLUR_SKSL).apply {
      setFloatUniform("blurRadius", blurRadiusPx)
      setIntUniform("direction", if (vertical) 1 else 0)
      setFloatUniform("crop", bounds.left, bounds.top, bounds.right, bounds.bottom)
      setInputShader("mask", mask)
    }
    return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
  }

  // Our blur runtime shader is separated, therefore requires two passes, one in each direction
  return shader(vertical = false).chainWith(shader(vertical = true))
}

@RequiresApi(31)
private fun AndroidRenderEffect.chainWith(imageFilter: AndroidRenderEffect): AndroidRenderEffect {
  return AndroidRenderEffect.createChainEffect(imageFilter, this)
}

/**
 * Returns a copy of the current [Bitmap], drawn with the given [alpha] value.
 *
 * There might be a better way to do this via a [BlendMode], but none of the results looked as
 * good.
 */
private fun Bitmap.withAlpha(alpha: Int): Bitmap {
  val paint = paintLocal.getOrSet { Paint() }
  paint.reset()
  paint.alpha = alpha

  return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
    android.graphics.Canvas(it).apply {
      drawBitmap(this@withAlpha, 0f, 0f, paint)
    }
  }
}

private val paintLocal = ThreadLocal<Paint>()
