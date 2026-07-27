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
import android.graphics.RuntimeShader
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.PlatformRenderEffect
import dev.chrisbanes.haze.createBlendRenderEffect
import dev.chrisbanes.haze.createShaderRenderEffect
import kotlin.math.abs

private var noiseTexture: Bitmap? = null

private const val COMBINED_NOISE_TINT_SKSL = """
  uniform shader content;
  uniform shader noise;
  uniform float noiseAlpha;
  layout(color) uniform half4 tintColor;

  half guardedDivide(half numerator, half denominator) {
    return denominator == 0.0 ? 0.0 : numerator / denominator;
  }

  half softLightComponent(half2 source, half2 destination) {
    if (2.0 * source.x <= source.y) {
      return guardedDivide(
        destination.x * destination.x * (source.y - 2.0 * source.x),
        destination.y
      ) + (1.0 - destination.y) * source.x
        + destination.x * (-source.y + 2.0 * source.x + 1.0);
    } else if (4.0 * destination.x <= destination.y) {
      half destinationSquared = destination.x * destination.x;
      half destinationCubed = destinationSquared * destination.x;
      half destinationAlphaSquared = destination.y * destination.y;
      half destinationAlphaCubed = destinationAlphaSquared * destination.y;
      return guardedDivide(
        destinationAlphaSquared
          * (source.x - destination.x * (3.0 * source.y - 6.0 * source.x - 1.0))
          + 12.0 * destination.y * destinationSquared * (source.y - 2.0 * source.x)
          - 16.0 * destinationCubed * (source.y - 2.0 * source.x)
          - destinationAlphaCubed * source.x,
        destinationAlphaSquared
      );
    } else {
      return destination.x * (source.y - 2.0 * source.x + 1.0)
        + source.x
        - sqrt(destination.y * destination.x) * (source.y - 2.0 * source.x)
        - destination.y * source.x;
    }
  }

  half4 softLight(half4 source, half4 destination) {
    if (destination.a == 0.0) {
      return source;
    }
    return half4(
      softLightComponent(source.ra, destination.ra),
      softLightComponent(source.ga, destination.ga),
      softLightComponent(source.ba, destination.ba),
      source.a + (1.0 - source.a) * destination.a
    );
  }

  half4 main(float2 coord) {
    half4 blurred = content.eval(coord);
    half4 noiseColor = noise.eval(coord) * noiseAlpha;
    half4 withNoise = softLight(noiseColor, blurred);
    half4 tint = tintColor.rgb1 * tintColor.a;
    return tint + withNoise * (1.0 - tintColor.a);
  }
"""

internal fun Context.getNoiseTexture(): Bitmap {
  val cached = noiseTexture
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise).also { decoded ->
    noiseTexture = decoded
  }
}

private fun Context.createNoiseShader(scale: Float): BitmapShader {
  val normalizedScale = if (scale > 0f) scale else 1f
  return BitmapShader(getNoiseTexture(), REPEAT, REPEAT).apply {
    if (abs(normalizedScale - 1f) >= 0.001f) {
      setLocalMatrix(
        Matrix().apply {
          val reciprocal = 1f / normalizedScale
          setScale(reciprocal, reciprocal)
        },
      )
    }
  }
}

@RequiresApi(31)
internal actual fun createCombinedNoiseTintRenderEffectOrNull(
  context: PlatformContext,
  input: PlatformRenderEffect,
  noiseFactor: Float,
  tintColor: Color,
  scale: Float,
): PlatformRenderEffect? {
  if (Build.VERSION.SDK_INT < 33) return null

  val shader = RuntimeShader(COMBINED_NOISE_TINT_SKSL).apply {
    setInputShader("noise", context.createNoiseShader(scale))
    setFloatUniform("noiseAlpha", noiseFactor.coerceIn(0f, 1f))
    setColorUniform("tintColor", tintColor.toArgb())
  }
  val postProcess = AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
  return AndroidRenderEffect.createChainEffect(postProcess, input)
}

@RequiresApi(31)
internal actual fun createNoiseEffect(
  context: PlatformContext,
  noiseFactor: Float,
  mask: Shader?,
  scale: Float,
): PlatformRenderEffect {
  // Apply scaling through the shader matrix so we can reuse the decoded bitmap.
  val noiseAlpha = noiseFactor.coerceIn(0f, 1f)
  val baseNoiseEffect = AndroidRenderEffect.createShaderEffect(context.createNoiseShader(scale))
  val noiseEffect = if (noiseAlpha < 1f) {
    val matrix = ColorMatrix().apply { setScale(1f, 1f, 1f, noiseAlpha) }
    AndroidRenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix), baseNoiseEffect)
  } else {
    baseNoiseEffect
  }

  return when {
    mask != null -> {
      // If we have a mask, we need to apply it to the noise bitmap shader via a blend mode
      createBlendRenderEffect(
        blendMode = BlendMode.SrcIn,
        background = createShaderRenderEffect(mask),
        foreground = noiseEffect,
      )
    }
    else -> noiseEffect
  }
}
