// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidatePlacement
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
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
  uniform float noiseFactor;

  // https://www.iquilezles.org/www/articles/distfunctions2d/distfunctions2d.htm
  float sdRoundedBox(vec2 position, vec2 box, vec4 radius) {
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
    float noiseLuma = dot(n.rgb, vec3(0.2126, 0.7152, 0.0722));

    // Calculate our overlay (tint + noise)
    float overlay = min(1.0, colorShift + (noiseLuma * noiseFactor));

    // Apply the overlay (noise + tint)
    return b + ((color - b) * overlay);
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

internal actual fun createHazeNode(
  state: HazeState,
  backgroundColor: Color,
  style: HazeStyle,
): HazeNode = SkiaHazeNode(state, backgroundColor, style)

internal actual fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset {
  // The Skiko-backed platforms don't use native windows for dialogs, etc
  return Offset.Zero
}

private class SkiaHazeNode(
  state: HazeState,
  backgroundColor: Color,
  style: HazeStyle,
) : HazeNode(state, backgroundColor, style),
  LayoutModifierNode,
  CompositionLocalConsumerModifierNode,
  ObserverModifierNode {

  private var renderEffectDirty = true
  private var hazeRenderEffect: RenderEffect? = null

  override fun onUpdate() {
    renderEffectDirty = true
    invalidatePlacement()
  }

  override fun onObservedReadsChanged() {
    renderEffectDirty = true
    invalidatePlacement()
  }

  override fun MeasureScope.measure(
    measurable: Measurable,
    constraints: Constraints,
  ): MeasureResult {
    val placeable = measurable.measure(constraints)
    return layout(placeable.width, placeable.height) {
      placeable.placeWithLayer(x = 0, y = 0) {
        val position = coordinates?.let { it.positionInWindow() + calculateWindowOffset() }
          ?: Offset.Zero
        renderEffect = getOrCreateRenderEffect(position)
      }
    }
  }

  private fun getOrCreateRenderEffect(position: Offset): RenderEffect? {
    if (renderEffectDirty) {
      observeReads {
        hazeRenderEffect = createHazeRenderEffect(position)
      }
      renderEffectDirty = false
    }
    return hazeRenderEffect
  }

  private fun createHazeRenderEffect(position: Offset): RenderEffect? {
    if (state.areas.isEmpty()) {
      return null
    }

    val density = currentValueOf(LocalDensity)

    val filters = state.areas.asSequence().mapNotNull { area ->
      val areaLocalBounds = area.boundsInLocal(position) ?: return@mapNotNull null

      val resolvedStyle = resolveStyle(style, area.style)

      val compositeShaderBuilder = RuntimeShaderBuilder(RUNTIME_SHADER).apply {
        uniform(
          "rectangle",
          areaLocalBounds.left,
          areaLocalBounds.top,
          areaLocalBounds.right,
          areaLocalBounds.bottom,
        )

        when (val shape = area.shape) {
          is CornerBasedShape -> {
            uniform(
              "radius",
              shape.topStart.toPx(area.size, density),
              shape.topEnd.toPx(area.size, density),
              shape.bottomStart.toPx(area.size, density),
              shape.bottomEnd.toPx(area.size, density),
            )
          }

          else -> {
            uniform("radius", 0f, 0f, 0f, 0f)
          }
        }

        val tint = resolvedStyle.tint
        uniform("color", tint.red, tint.green, tint.blue, 1f)
        uniform("colorShift", tint.alpha)

        uniform("noiseFactor", resolvedStyle.noiseFactor)

        child("noise", NOISE_SHADER)
      }

      val blurFilter = createBlurImageFilter(
        with(density) { resolvedStyle.blurRadius.toPx() },
      )

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

private fun createBlurImageFilter(blurRadiusPx: Float): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.DECAL,
  )
}
