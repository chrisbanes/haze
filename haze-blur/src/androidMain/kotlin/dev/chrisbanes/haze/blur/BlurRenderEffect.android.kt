// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("NOTHING_TO_INLINE")
@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader.TileMode.REPEAT
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toAndroidTileMode
import dev.chrisbanes.haze.HazeBlendMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createBlendImageFilter
import dev.chrisbanes.haze.createShaderImageFilter
import kotlin.math.abs

private var noiseTexture: Bitmap? = null

internal fun Context.getNoiseTexture(): Bitmap {
  val cached = noiseTexture
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise).also { decoded ->
    noiseTexture = decoded
  }
}

@RequiresApi(31)
internal actual fun createNoiseEffect(
  context: PlatformContext,
  noiseFactor: Float,
  mask: Shader?,
  scale: Float,
): PlatformRenderEffect {
  // Apply scaling through the shader matrix so we can reuse the decoded bitmap.
  val normalizedScale = if (scale > 0f) scale else 1f
  val noiseShader = BitmapShader(context.getNoiseTexture(), REPEAT, REPEAT).apply {
    if (abs(normalizedScale - 1f) >= 0.001f) {
      val matrix = Matrix().apply {
        val reciprocal = 1f / normalizedScale
        setScale(reciprocal, reciprocal)
      }
      setLocalMatrix(matrix)
    }
  }

  val noiseAlpha = noiseFactor.coerceIn(0f, 1f)
  val baseNoiseEffect = AndroidRenderEffect.createShaderEffect(noiseShader)
  val noiseEffect = if (noiseAlpha < 1f) {
    val matrix = ColorMatrix().apply { setScale(1f, 1f, 1f, noiseAlpha) }
    AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix), baseNoiseEffect)
  } else {
    baseNoiseEffect
  }

  return when {
    mask != null -> {
      // If we have a mask, we need to apply it to the noise bitmap shader via a blend mode
      createBlendImageFilter(
        blendMode = HazeBlendMode.SrcIn,
        background = createShaderImageFilter(mask),
        foreground = noiseEffect,
      )
    }
    else -> noiseEffect
  }
}

@RequiresApi(31)
internal actual fun createBlurRenderEffect(
  blurRadiusPx: Float,
  params: RenderEffectParams,
): PlatformRenderEffect {
  if (blurRadiusPx <= 0f) {
    return AndroidRenderEffect.createOffsetEffect(0f, 0f)
  }

  return try {
    // On Android we use the native blur effect directly for better performance
    AndroidRenderEffect.createBlurEffect(
      blurRadiusPx,
      blurRadiusPx,
      params.blurTileMode.toAndroidTileMode(),
    )
  } catch (e: IllegalArgumentException) {
    throw IllegalArgumentException(
      "Error whilst creating blur effect. " +
        "This is likely because this device does not support a blur radius of ${params.blurRadius}dp",
      e,
    )
  }
}
