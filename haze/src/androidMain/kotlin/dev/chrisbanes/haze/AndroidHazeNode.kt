// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.lruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt

internal class AndroidHazeNode(
  state: HazeState,
  backgroundColor: Color,
  style: HazeStyle,
) : HazeNode(
  state = state,
  backgroundColor = backgroundColor,
  style = style,
),
  DrawModifierNode,
  CompositionLocalConsumerModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode {

  private var impl: Impl = ScrimImpl()

  private var position by mutableStateOf(Offset.Unspecified)
  private var size by mutableStateOf(Size.Unspecified)
  private var isCanvasHardwareAccelerated by mutableStateOf(false)

  override fun onUpdate() {
    updateImpl()
  }

  override fun onAttach() {
    onObservedReadsChanged()
  }

  override fun onObservedReadsChanged() {
    observeReads { updateImpl() }
  }

  private fun updateImpl() {
    // If LocalInspectionMode is true, we're likely running in a preview/screenshot test
    // and therefore don't have full access to Android drawing APIs. To avoid crashing we
    // force the the scrim impl
    var changed = updateImplIfRequired(
      forceScrim = !isCanvasHardwareAccelerated || currentValueOf(LocalInspectionMode),
    )

    changed = impl.update(
      state = state,
      defaultStyle = style,
      position = position,
      density = currentValueOf(LocalDensity),
      layoutDirection = currentValueOf(LocalLayoutDirection),
    ) || changed

    if (changed) {
      invalidateDraw()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    position = coordinates.positionInWindow() + calculateWindowOffset()
    size = coordinates.size.toSize()
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size.toSize()
  }

  override fun ContentDrawScope.draw() {
    // Similar to above, drawRenderNode is only available on hw-accelerated canvases.
    // To avoid crashing we just draw the content and return early.
    val canvasHardwareAccelerated = drawContext.canvas.nativeCanvas.isHardwareAccelerated
    if (canvasHardwareAccelerated != isCanvasHardwareAccelerated) {
      isCanvasHardwareAccelerated = canvasHardwareAccelerated
      updateImpl()
    }

    with(impl) {
      draw(backgroundColor)
    }
  }

  private fun updateImplIfRequired(forceScrim: Boolean): Boolean {
    // We can't currently use RenderNode impl on API 31 due to
    // https://github.com/chrisbanes/haze/issues/77
    if (Build.VERSION.SDK_INT >= 32) {
      if (forceScrim && impl !is ScrimImpl) {
        impl = ScrimImpl()
        return true
      } else if (!forceScrim && impl is ScrimImpl) {
        impl = RenderNodeImpl(currentValueOf(LocalContext))
        return true
      }
    } else {
      // This shouldn't happen, but adding it for completeness
      if (impl !is ScrimImpl) {
        impl = ScrimImpl()
        return true
      }
    }
    return false
  }

  internal interface Impl {
    fun ContentDrawScope.draw(backgroundColor: Color)
    fun update(
      state: HazeState,
      defaultStyle: HazeStyle,
      position: Offset,
      density: Density,
      layoutDirection: LayoutDirection,
    ): Boolean
  }
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

@RequiresApi(31)
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

private class ScrimImpl : AndroidHazeNode.Impl {
  private var effects: List<Effect> = emptyList()

  override fun ContentDrawScope.draw(backgroundColor: Color) {
    drawContent()

    for (effect in effects) {
      drawPath(path = effect.path, color = effect.tint)
    }
  }

  override fun update(
    state: HazeState,
    defaultStyle: HazeStyle,
    position: Offset,
    density: Density,
    layoutDirection: LayoutDirection,
  ): Boolean {
    effects = state.areas.asSequence()
      .filter { it.isValid }
      .mapNotNull { area ->
        val bounds = area.boundsInLocal(position) ?: return@mapNotNull null

        val resolvedStyle = resolveStyle(defaultStyle, area.style)

        // TODO: Should try and re-use this Path instance
        val path = Path().apply {
          updateFromHaze(bounds, area.shape, layoutDirection, density)
        }

        Effect(
          path = path,
          tint = resolvedStyle.tint.boostAlphaForBlurRadius(resolvedStyle.blurRadius),
        )
      }.toList()

    return true
  }

  /**
   * In this implementation, the only tool we have is translucency.
   */
  private fun Color.boostAlphaForBlurRadius(blurRadius: Dp): Color {
    // We treat a blur radius of 72.dp as near 'opaque', and linearly boost using that
    val factor = 1 + (blurRadius.value / 72f)
    return copy(alpha = (alpha * factor).coerceAtMost(1f))
  }

  private data class Effect(
    val tint: Color,
    val path: Path,
  )
}

@RequiresApi(31)
private class RenderNodeImpl(private val context: Context) : AndroidHazeNode.Impl {
  private var effects: List<Effect> = emptyList()

  val contentNode = RenderNode("content")

  val noiseTextureCache = lruCache<Float, Bitmap>(3)

  override fun ContentDrawScope.draw(backgroundColor: Color) {
    // First we draw the composable content into `contentNode`
    contentNode.setPosition(0, 0, size.width.toInt(), size.height.toInt())

    Canvas(contentNode.beginRecording()).also { canvas ->
      val contentDrawScope = this
      draw(this, layoutDirection, canvas, size) {
        contentDrawScope.drawContent()
      }
      contentNode.endRecording()
    }

    // Now we draw `contentNode` into the window canvas, so that it is displayed
    drawContext.canvas.nativeCanvas.drawRenderNode(contentNode)

    // Now we need to draw `contentNode` into each of our 'effect' RenderNodes, allowing
    // their RenderEffect to be applied to the composable content.
    effects.forEach { effect ->
      effect.renderNode.beginRecording().apply {
        // We need to draw our background color first, as the `contentNode` may not draw
        // a background. This then makes the blur effect much less pronounced, as blurring with
        // transparent negates the effect.
        drawColor(backgroundColor.toArgb())
        translate(-effect.renderNodeDrawArea.left, -effect.renderNodeDrawArea.top)
        drawRenderNode(contentNode)
      }
      effect.renderNode.endRecording()

      // Finally we draw the 'effect' RenderNode to the window canvas, drawing on top
      // of the original content
      with(drawContext.canvas) {
        clipPath(effect.path) {
          nativeCanvas.drawRenderNode(effect.renderNode)
        }
      }
    }
  }

  override fun update(
    state: HazeState,
    defaultStyle: HazeStyle,
    position: Offset,
    density: Density,
    layoutDirection: LayoutDirection,
  ): Boolean {
    // We create a RenderNode for each of the areas we need to apply our effect to
    effects = state.areas.asSequence().mapNotNull { area ->
      val bounds = area.boundsInLocal(position) ?: return@mapNotNull null

      val resolvedStyle = resolveStyle(defaultStyle, area.style)

      val blurRadiusPx = with(density) { resolvedStyle.blurRadius.toPx() }

      // We expand the area where our effect is applied to. This is necessary so that the blur
      // effect is applied evenly to all edges. If we don't do this, the blur effect is much less
      // visible on the edges of the area.
      val expandedRect = bounds.inflate(blurRadiusPx)

      val node = RenderNode("blur").apply {
        setRenderEffect(
          RenderEffect.createBlurEffect(
            blurRadiusPx,
            blurRadiusPx,
            Shader.TileMode.DECAL,
          ).letIf(defaultStyle.noiseFactor >= 0.0005f) {
            val noiseShader = BitmapShader(getNoiseTexture(defaultStyle.noiseFactor), REPEAT, REPEAT)
            RenderEffect.createBlendModeEffect(
              RenderEffect.createShaderEffect(noiseShader),
              it,
              BlendMode.HARD_LIGHT,
            )
          }.withTint(resolvedStyle.tint),
        )
        setPosition(0, 0, expandedRect.width.toInt(), expandedRect.height.toInt())
        translationX = expandedRect.left
        translationY = expandedRect.top
      }

      // TODO: Should try and re-use this Path
      val path = Path().apply {
        updateFromHaze(bounds, area.shape, layoutDirection, density)
      }

      Effect(
        path = path,
        renderNode = node,
        renderNodeDrawArea = expandedRect,
      )
    }.toList()

    return true
  }

  private fun getNoiseTexture(noiseFactor: Float): Bitmap {
    val cached = noiseTextureCache[noiseFactor]
    if (cached != null) return cached

    // We draw the noise with the given opacity
    return BitmapFactory.decodeResource(context.resources, R.drawable.haze_noise)
      .withAlpha(noiseFactor)
      .also { noiseTextureCache.put(noiseFactor, it) }
  }

  private data class Effect(
    val path: Path,
    val renderNode: RenderNode,
    val renderNodeDrawArea: Rect,
  )
}

private fun Path.updateFromHaze(
  bounds: Rect,
  shape: Shape,
  layoutDirection: LayoutDirection,
  density: Density,
) {
  reset()
  addOutline(
    outline = shape.createOutline(bounds.size, layoutDirection, density),
    offset = bounds.topLeft,
  )
}

private inline fun <T> T.letIf(condition: Boolean, block: (T) -> T): T = when {
  condition -> block(this)
  else -> this
}
