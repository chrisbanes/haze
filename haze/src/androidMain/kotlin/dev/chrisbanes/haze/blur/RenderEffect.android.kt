// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")

package dev.chrisbanes.haze.blur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.toAndroidTileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.packFloats
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withScale
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.PaintPool
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.R
import dev.chrisbanes.haze.SimpleLruCache
import dev.chrisbanes.haze.asBrush
import dev.chrisbanes.haze.ceil
import dev.chrisbanes.haze.round
import dev.chrisbanes.haze.toAndroidBlendMode
import dev.chrisbanes.haze.unsynchronizedLazy
import dev.chrisbanes.haze.usePaint

internal actual fun createBlurRenderEffect(
  context: PlatformContext,
  density: Density,
  params: RenderEffectParams,
): RenderEffect? {
  if (Build.VERSION.SDK_INT < 31) return null

  val blurRadius = params.blurRadius * params.scale
  require(blurRadius >= 0.dp) { "blurRadius needs to be equal or greater than 0.dp" }
  val size = ceil(params.contentSize * params.scale)
  val offset = (params.contentOffset * params.scale).round()

  val progressiveShader = params.progressive?.asBrush()?.toShader(size)

  val blur = when {
    blurRadius <= 0.dp -> AndroidRenderEffect.createOffsetEffect(0f, 0f)

    Build.VERSION.SDK_INT >= 33 && progressiveShader != null -> {
      // If we've been provided with a progressive/gradient blur shader, we need to use
      // our custom blur via a runtime shader
      createBlurImageFilterWithMask(
        blurRadiusPx = with(density) { blurRadius.toPx() },
        size = size,
        offset = offset,
        mask = progressiveShader,
      )
    }

    else -> {
      try {
        val blurRadiusPx = with(density) { blurRadius.toPx() }
        AndroidRenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, params.blurTileMode.toAndroidTileMode())
      } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(
          "Error whilst calling RenderEffect.createBlurEffect. " +
            "This is likely because this device does not support a blur radius of ${blurRadius}dp",
          e,
        )
      }
    }
  }

  return blur
    .withNoise(context, params.noiseFactor, progressiveShader)
    .withTints(params.tints, size, offset, params.tintAlphaModulate, progressiveShader)
    .withMask(params.mask, size, offset)
    .asComposeRenderEffect()
}

private val noiseTextureCache by unsynchronizedLazy { SimpleLruCache<Long, Bitmap>(3) }

internal fun Context.getNoiseTexture(noiseFactor: Float, scale: Float = 1f): Bitmap {
  val key = packFloats(noiseFactor, scale)
  val cached = noiseTextureCache[key]
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  // We draw the noise with the given opacity
  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise)
    .transform(alpha = noiseFactor, scale = scale)
    .also { noiseTextureCache[key] = it }
}

@RequiresApi(31)
private fun AndroidRenderEffect.withNoise(
  context: Context,
  noiseFactor: Float,
  mask: Shader? = null,
): AndroidRenderEffect = when {
  noiseFactor >= 0.005f -> {
    // Ideally we would scale the noise texture to match the input scale, but scaling it
    // looks terrible.
    val noiseShader = BitmapShader(context.getNoiseTexture(noiseFactor), REPEAT, REPEAT)
    val dst = when {
      mask != null -> {
        // If we have a mask, we need to apply it to the noise bitmap shader via a
        // blend mode
        AndroidRenderEffect.createBlendModeEffect(
          AndroidRenderEffect.createShaderEffect(mask), // dst
          AndroidRenderEffect.createShaderEffect(noiseShader), // src
          BlendMode.SRC_IN, // blendMode
        )
      }
      else -> AndroidRenderEffect.createShaderEffect(noiseShader)
    }

    AndroidRenderEffect.createBlendModeEffect(
      dst, // dst
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
  return blendWith(
    foreground = AndroidRenderEffect.createShaderEffect(shader),
    blendMode = blendMode,
    offset = offset,
  )
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTints(
  tints: List<HazeTint>,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): AndroidRenderEffect = tints.fold(this) { acc, tint ->
  acc.withTint(tint, size, offset, alphaModulate, mask)
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTint(
  tint: HazeTint,
  size: Size,
  offset: Offset,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): AndroidRenderEffect {
  if (!tint.isSpecified) return this

  val tintBrush = tint.brush?.toShader(size)
  if (tintBrush != null) {
    val brushEffect = when {
      alphaModulate >= 1f -> {
        AndroidRenderEffect.createShaderEffect(tintBrush)
      }

      else -> {
        // If we need to modulate the alpha, we'll need to wrap it in a ColorFilter
        AndroidRenderEffect.createColorFilterEffect(
          BlendModeColorFilter(Color.Blue.copy(alpha = alphaModulate).toArgb(), BlendMode.SRC_IN),
          AndroidRenderEffect.createShaderEffect(tintBrush),
        )
      }
    }

    return if (mask != null) {
      blendWith(
        foreground = AndroidRenderEffect.createBlendModeEffect(
          AndroidRenderEffect.createShaderEffect(mask),
          brushEffect,
          BlendMode.SRC_IN,
        ),
        blendMode = tint.blendMode.toAndroidBlendMode(),
        offset = offset,
      )
    } else {
      blendWith(
        foreground = brushEffect,
        blendMode = tint.blendMode.toAndroidBlendMode(),
        offset = offset,
      )
    }
  }

  val tintColor = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  if (tintColor.alpha >= 0.005f) {
    return if (mask != null) {
      return blendWith(
        foreground = AndroidRenderEffect.createColorFilterEffect(
          BlendModeColorFilter(tintColor.toArgb(), BlendMode.SRC_IN),
          AndroidRenderEffect.createShaderEffect(mask),
        ),
        blendMode = tint.blendMode.toAndroidBlendMode(),
        offset = offset,
      )
    } else {
      AndroidRenderEffect.createColorFilterEffect(
        BlendModeColorFilter(tintColor.toArgb(), tint.blendMode.toAndroidBlendMode()),
        this,
      )
    }
  }

  return this
}

@RequiresApi(31)
private fun AndroidRenderEffect.blendWith(
  foreground: AndroidRenderEffect,
  blendMode: BlendMode,
  offset: Offset,
): AndroidRenderEffect = AndroidRenderEffect.createBlendModeEffect(
  /* dst */
  this,
  /* src */
  when {
    offset.isUnspecified -> foreground
    offset == Offset.Zero -> foreground
    // We need to offset the shader to the bounds
    else -> AndroidRenderEffect.createOffsetEffect(offset.x, offset.y, foreground)
  },
  /* blendMode */
  blendMode,
)

@RequiresApi(33)
private fun createBlurImageFilterWithMask(
  blurRadiusPx: Float,
  size: Size,
  offset: Offset,
  mask: Shader,
): AndroidRenderEffect {
  fun shader(vertical: Boolean): AndroidRenderEffect {
    val shader = RuntimeShader(if (vertical) VERTICAL_BLUR_SKSL else HORIZONTAL_BLUR_SKSL).apply {
      setFloatUniform("blurRadius", blurRadiusPx)
      setFloatUniform("crop", offset.x, offset.y, offset.x + size.width, offset.y + size.height)
      setInputShader("mask", mask)
    }
    return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
  }

  // Our blur runtime shader is separated, therefore requires two passes, one in each direction
  return shader(vertical = false).chainWith(shader(vertical = true))
}

@RequiresApi(31)
private inline fun AndroidRenderEffect.chainWith(imageFilter: AndroidRenderEffect): AndroidRenderEffect {
  return AndroidRenderEffect.createChainEffect(imageFilter, this)
}

/**
 * Returns a copy of the current [Bitmap], drawn with the given [alpha] value.
 *
 * There might be a better way to do this via a [BlendMode], but none of the results looked as
 * good.
 */
private fun Bitmap.transform(alpha: Float, scale: Float = 1f): Bitmap = PaintPool.usePaint { paint ->
  paint.alpha = alpha
  paint.isAntiAlias = true
  paint.filterQuality = FilterQuality.High

  val scaledWidth = (width * scale).toInt()
  val scaledHeight = (height * scale).toInt()

  return createBitmap(scaledWidth, scaledHeight).applyCanvas {
    withScale(scale, scale) {
      drawBitmap(this@transform, 0f, 0f, paint.asFrameworkPaint())
    }
  }
}
