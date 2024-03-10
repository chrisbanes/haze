// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
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
import org.jetbrains.skia.IRect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader

/**
 * Heavily influenced by
 * https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html
 */
private val RUNTIME_SHADER by lazy {
  RuntimeEffect.makeForShader(
    SHADER_SKSL,
  )
}
private val CLIPPING_SHADER by lazy {
  RuntimeEffect.makeForShader(
    CLIPPING_SHADER_SKSL,
  )
}
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
  style: HazeStyle,
): HazeNode = SkiaHazeNode(state, style)

internal actual fun CompositionLocalConsumerModifierNode.calculateWindowOffset(): Offset {
  // The Skiko-backed platforms don't use native windows for dialogs, etc
  return Offset.Zero
}

private class SkiaHazeNode(
  state: HazeState,
  style: HazeStyle,
) : HazeNode(state, style),
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
    var clippingFilter: ImageFilter? = null

    state.areas.forEach { area ->
      val areaLocalBounds =
        area.boundsInLocal(position) ?: return null
      val compositeShaderBuilder =
        RuntimeShaderBuilder(CLIPPING_SHADER).apply {
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
                shape.bottomEnd.toPx(area.size, density),
                shape.topEnd.toPx(area.size, density),
                shape.bottomStart.toPx(area.size, density),
                shape.topStart.toPx(area.size, density),
              )
            }

            else -> {
              uniform("radius", 0f, 0f, 0f, 0f)
            }
          }
        }

      clippingFilter = ImageFilter.makeRuntimeShader(
        runtimeShaderBuilder = compositeShaderBuilder,
        shaderNames = arrayOf("content"),
        inputs = arrayOf(clippingFilter),
      )
    }
    val filters = state.areas.asSequence().mapNotNull { area ->
      val areaLocalBounds =
        area.boundsInLocal(position) ?: return@mapNotNull null
      val resolvedStyle = resolveStyle(style, area.style)
      val compositeShaderBuilder =
        RuntimeShaderBuilder(RUNTIME_SHADER).apply {
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
                shape.bottomEnd.toPx(area.size, density),
                shape.topEnd.toPx(area.size, density),
                shape.bottomStart.toPx(area.size, density),
                shape.topStart.toPx(area.size, density),
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
      // For CLAMP to work, we need to provide the crop rect
      val blurFilter = createBlurImageFilter(
        blurRadiusPx = with(density) { resolvedStyle.blurRadius.toPx() },
        cropRect = areaLocalBounds,
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
        add(clippingFilter)
        addAll(filters)
      }.toTypedArray(),
      null,
    ).asComposeRenderEffect()
  }
}

private fun createBlurImageFilter(
  blurRadiusPx: Float,
  cropRect: Rect? = null,
): ImageFilter {
  val sigma = BlurEffect.convertRadiusToSigma(blurRadiusPx)
  return ImageFilter.makeBlur(
    sigmaX = sigma,
    sigmaY = sigma,
    mode = FilterTileMode.CLAMP,
    crop = cropRect?.toIRect(),
  )
}

private fun Rect.toIRect(): IRect {
  return IRect.makeLTRB(
    left.toInt(),
    top.toInt(),
    right.toInt(),
    bottom.toInt(),
  )
}
