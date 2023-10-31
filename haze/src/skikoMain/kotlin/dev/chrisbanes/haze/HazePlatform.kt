// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * Heavily influenced by
 * https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html
 */
private const val SHADER_SKSL = """
  uniform shader content;
  uniform shader blur;
  uniform shader noise;

  uniform vec4 rectangle;
  uniform vec2 topLeftCornerRadius;
  uniform vec2 topRightCornerRadius;
  uniform vec2 bottomRightCornerRadius;
  uniform vec2 bottomLeftCornerRadius;
  uniform vec4 color;
  uniform float colorShift;

  // https://www.iquilezles.org/www/articles/distfunctions2d/distfunctions2d.htm
  float boxSDF(vec2 position, vec2 box, vec2 radius) {
    float cornerRadius = max(radius.x, radius.y);
    vec2 q = abs(position) - box + vec2(cornerRadius);
    return length(max(q,0.0)) + min(max(q.x,q.y),0.0) - cornerRadius;
  }

  vec4 main(vec2 coord) {
    vec2 shiftRect = (rectangle.zw - rectangle.xy) / 2.0;
    vec2 shiftCoord = coord - rectangle.xy;
    vec2 cornerRadius;
    if (shiftCoord.x > shiftRect.x) {
      if (shiftCoord.y > shiftRect.y) {
        cornerRadius = bottomRightCornerRadius;
      } else {
        cornerRadius = topRightCornerRadius;
      }
    } else {
      if (shiftCoord.y > shiftRect.y) {
        cornerRadius = bottomLeftCornerRadius;
      } else {
        cornerRadius = topLeftCornerRadius;
      }
    }
    float distanceToClosestEdge = boxSDF(shiftCoord - shiftRect, shiftRect, cornerRadius);

    vec4 c = content.eval(coord);
    if (distanceToClosestEdge > 0.0) {
      return c;
    }

    vec4 b = blur.eval(coord);
    vec4 n = noise.eval(coord);

    // Add noise for extra texture
    float noiseLuminance = dot(n.rgb, vec3(0.2126, 0.7152, 0.0722));
    // We apply the noise, toned down to 10%
    float noiseFactor = min(1.0, noiseLuminance) * 0.1;

    // Apply the noise, and shift towards `color` by `colorShift`
    return b + noiseFactor + ((color - b) * colorShift);
  }
"""

private val RUNTIME_SHADER by lazy { RuntimeEffect.makeForShader(SHADER_SKSL) }

private val NOISE_SHADER by lazy {
  Shader.makeFractalNoise(
    baseFrequencyX = 0.45f,
    baseFrequencyY = 0.45f,
    numOctaves = 4,
    seed = 2.0f,
  )
}

internal actual fun Modifier.haze(
  areas: List<RoundRect>,
  backgroundColor: Color,
  tint: Color,
  blurRadius: Dp,
): Modifier = composed {
  val blurRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }
  val blur = remember(blurRadius) {
    val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
    ImageFilter.makeBlur(
      sigmaX = sigma,
      sigmaY = sigma,
      mode = FilterTileMode.DECAL,
    )
  }

  graphicsLayer(
    renderEffect = remember(areas, tint, blur) {
      areas.asSequence()
        .filterNot { it.isEmpty }
        .map { area ->
          val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
            uniform("rectangle", area.left, area.top, area.right, area.bottom)
            uniform("topLeftCornerRadius", area.topLeftCornerRadius.x, area.topLeftCornerRadius.y)
            uniform("topRightCornerRadius", area.topRightCornerRadius.x, area.topRightCornerRadius.y)
            uniform("bottomRightCornerRadius", area.bottomRightCornerRadius.x, area.bottomRightCornerRadius.y)
            uniform("bottomLeftCornerRadius", area.bottomLeftCornerRadius.x, area.bottomLeftCornerRadius.y)
            uniform("color", tint.red, tint.green, tint.blue, 1f)
            uniform("colorShift", tint.alpha)

            child("noise", NOISE_SHADER)
          }

          ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = compositeShaderBuilder,
            shaderNames = arrayOf("content", "blur"),
            inputs = arrayOf(null, blur),
          )
        }
        .toList()
        .flatten()?.asComposeRenderEffect()
    },
  )
}

private fun Collection<ImageFilter>.flatten(): ImageFilter? = when {
  isEmpty() -> null
  size == 1 -> first()
  else -> ImageFilter.makeMerge(toTypedArray(), null)
}
