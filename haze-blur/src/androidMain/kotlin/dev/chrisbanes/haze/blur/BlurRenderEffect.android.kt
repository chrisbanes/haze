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
import dev.chrisbanes.haze.HazeBlendMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createOffsetRenderEffect
import dev.chrisbanes.haze.createShaderRenderEffect
import haze_root.haze_blur.generated.resources.Res
import kotlin.math.abs

@Volatile
private var noiseTexture: Bitmap? = null

internal suspend fun preloadNoiseTexture() {
  if (noiseTexture != null) return
  val bytes = Res.readBytes("drawable/haze_noise.webp")
  noiseTexture = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

internal fun Context.getNoiseTexture(): Bitmap? = noiseTexture

@RequiresApi(31)
internal actual fun createNoiseEffect(
  context: PlatformContext,
  noiseFactor: Float,
  mask: Shader?,
  scale: Float,
): PlatformRenderEffect {
  val noiseBitmap = context.getNoiseTexture()

  if (noiseBitmap == null) {
    // Texture not loaded yet, return no-op effect.
    // Will be replaced on next frame after preloadNoiseTexture() completes.
    return createOffsetRenderEffect(0f, 0f)
  }

  val normalizedScale = if (scale > 0f) scale else 1f
  val noiseShader = BitmapShader(noiseBitmap, REPEAT, REPEAT).apply {
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
      createBlendRenderEffect(
        blendMode = HazeBlendMode.SrcIn,
        background = createShaderRenderEffect(mask),
        foreground = noiseEffect,
      )
    }
    else -> noiseEffect
  }
}
