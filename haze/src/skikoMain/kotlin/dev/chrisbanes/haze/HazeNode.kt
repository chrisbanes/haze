// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
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
  uniform vec4 radius;
  uniform vec4 color;
  uniform float colorShift;

  // https://www.iquilezles.org/www/articles/distfunctions2d/distfunctions2d.htm
  float sdRoundedBox(vec2 position, vec2 box, vec4 radius)
  {
    radius.xy = (position.x > 0.0) ? radius.xy : radius.zw;
    radius.x = (position.y > 0.0) ? radius.x : radius.y;
    vec2 q = abs(position) - box + radius.x;
    return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - radius.x;
  }

  bool rectContains(vec4 rectangle, vec2 coord) {
      vec2 shiftRect = (rectangle.zw - rectangle.xy) / 2.0;
      vec2 shiftCoord = coord - rectangle.xy;
      return sdRoundedBox(shiftCoord - shiftRect, shiftRect, radius) <= 0.0;
  }

  vec4 main(vec2 coord) {
    vec4 c = content.eval(coord);

    if (!rectContains(rectangle, coord)) {
        // If we're not drawing in the rectangle, return transparent
        return vec4(0.0, 0.0, 0.0, 0.0);
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

internal actual class HazeNode actual constructor(
  private var areas: List<RoundRect>,
  private var backgroundColor: Color,
  private var tint: Color,
  private var blurRadius: Dp,
) : Modifier.Node(), LayoutModifierNode, CompositionLocalConsumerModifierNode {

  private var blurFilter: ImageFilter? = null

  override fun onAttach() {
    super.onAttach()
    blurFilter = createBlurImageFilter(blurRadius)
  }

  actual fun update(
    areas: List<RoundRect>,
    backgroundColor: Color,
    tint: Color,
    blurRadius: Dp,
  ) {
    this.areas = areas
    this.backgroundColor = backgroundColor
    this.tint = tint
    blurFilter = createBlurImageFilter(blurRadius)
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      placeable.placeWithLayer(x = 0, y = 0) {
        renderEffect = createBlurRenderEffect()
      }
    }
  }

  private fun createBlurImageFilter(blurRadius: Dp): ImageFilter {
    val blurRadiusPx = with(currentValueOf(LocalDensity)) {
      blurRadius.toPx()
    }
    val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
    return ImageFilter.makeBlur(
      sigmaX = sigma,
      sigmaY = sigma,
      mode = FilterTileMode.DECAL,
    )
  }

  private fun createBlurRenderEffect(): RenderEffect? {
    val rects = areas.filterNot { it.isEmpty }
    if (rects.isEmpty()) {
      return null
    }

    val filters = rects.asSequence().map { area ->
      val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
        uniform("rectangle", area.left, area.top, area.right, area.bottom)
        uniform("radius", area.bottomRightCornerRadius.x, area.topRightCornerRadius.x, area.bottomLeftCornerRadius.x, area.topLeftCornerRadius.x)
        uniform("color", tint.red, tint.green, tint.blue, 1f)
        uniform("colorShift", tint.alpha)

        child("noise", NOISE_SHADER)
      }

      ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = compositeShaderBuilder,
        shaderNames = arrayOf("content", "blur"),
        inputs = arrayOf(null, blurFilter),
      )
    }

    return ImageFilter.makeMerge(
      buildList {
        // We need null as the first item, which tells Skia to draw the content without any filter.
        // The filters then draw on top, clipped to their respective areas.
        add(null)
        addAll(filters)
      }.toTypedArray(),
      null,
    ).asComposeRenderEffect()
  }
}
