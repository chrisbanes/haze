// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RecordingCanvas
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSave
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
import androidx.core.util.Pools
import kotlin.math.roundToInt

/**
 * A simple object path for [Path]s. They're fairly expensive so it makes sense to
 * re-use instances.
 */
private val pathPool by lazy { Pools.SimplePool<Path>(10) }

internal class AndroidHazeNode(
  state: HazeState,
  style: HazeStyle,
) : HazeNode(state = state, style = style),
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
      draw()
    }
  }

  private fun updateImplIfRequired(forceScrim: Boolean): Boolean {
    // We can't currently use RenderNode impl on API 31 due to
    // https://github.com/chrisbanes/haze/issues/77
    if (Build.VERSION.SDK_INT >= 31) {
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
    fun ContentDrawScope.draw()
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

private class ScrimImpl : AndroidHazeNode.Impl {
  private var effects: List<Effect> = emptyList()

  override fun ContentDrawScope.draw() {
    drawContent()

    for (effect in effects) {
      val offset = effect.bounds.topLeft
      translate(offset.x, offset.y) {
        drawPath(path = effect.path, color = effect.tint)
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
    // Release all of the paths back into the pool
    effects.asSequence()
      .map(Effect::path)
      .forEach { pathPool.releasePath(it) }

    effects = state.areas.asSequence()
      .filter { it.isValid }
      .mapNotNull { area ->
        val bounds = area.boundsInLocal(position) ?: return@mapNotNull null

        val resolvedStyle = resolveStyle(defaultStyle, area.style)

        val path = pathPool.acquireOrCreate().apply {
          addOutline(area.shape.createOutline(bounds.size, layoutDirection, density))
        }

        Effect(
          path = path,
          tint = resolvedStyle.tint.boostAlphaForBlurRadius(resolvedStyle.blurRadius),
          bounds = bounds,
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
    val bounds: Rect,
    val path: Path,
  )
}

@RequiresApi(31)
private class RenderNodeImpl(private val context: Context) : AndroidHazeNode.Impl {
  private var effects: List<Effect> = emptyList()

  val contentNode = RenderNode("content")

  val noiseTextureCache = lruCache<Float, Bitmap>(3)

  override fun ContentDrawScope.draw() {
    // First we draw the composable content into `contentNode`
    contentNode.setPosition(0, 0, size.width.toInt(), size.height.toInt())

    Canvas(contentNode.beginRecording()).also { canvas ->
      val contentDrawScope = this
      draw(this, layoutDirection, canvas, size) {
        contentDrawScope.drawContent()
      }
      contentNode.endRecording()
    }

    for (effect in effects) {
      // First we need to make sure that the effects are updated (if necessary)
      effect.update()
    }

    // Now we draw `contentNode` into the window canvas, clipping any effect areas which
    // will be drawn below
    with(drawContext.canvas) {
      withSave {
        for (effect in effects) {
          clipShape(effect.shape, effect.bounds, ClipOp.Difference) {
            effect.getUpdatedPath(layoutDirection, drawContext.density)
          }
        }
        nativeCanvas.drawRenderNode(contentNode)
      }
    }

    // Now we need to draw `contentNode` into each of our 'effect' RenderNodes, allowing
    // their RenderEffect to be applied to the composable content.
    for (effect in effects) {
      effect.renderNode.record {
        // We need to inflate the bounds by the blur radius, so that the effect
        // has access to the pixels it needs in the clipRect
        val (l, t, r, b) = effect.bounds
        val inflate = effect.blurRadiusPx
        clipRect(l - inflate, t - inflate, r + inflate, b + inflate)
        // Finally draw the contentNode
        drawRenderNode(contentNode)
      }

      // Finally we draw the 'effect' RenderNode to the window canvas, drawing on top
      // of the original content
      with(drawContext.canvas) {
        withSave {
          clipShape(effect.shape, effect.bounds) {
            effect.getUpdatedPath(layoutDirection, drawContext.density)
          }
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
    val prevEffects = effects
    // We create a RenderNode for each of the areas we need to apply our effect to
    effects = state.areas.asSequence()
      .map { area -> prevEffects.firstOrNull { it.area == area } ?: Effect(area) }
      .toList()

    val invalidateCount = effects.count { effect ->
      val bounds = effect.area.boundsInLocal(position) ?: Rect.Zero
      val resolvedStyle = resolveStyle(defaultStyle, effect.area.style)

      effect.updateParameters(
        bounds = bounds,
        blurRadiusPx = with(density) { resolvedStyle.blurRadius.toPx() },
        noiseFactor = resolvedStyle.noiseFactor,
        tint = resolvedStyle.tint,
        shape = effect.area.shape,
      )
    }

    // Invalidate if any of the effects triggered an invalidation, or we now have zero
    // effects but were previously showing some
    return invalidateCount > 0 || (effects.isEmpty() != prevEffects.isEmpty())
  }

  private fun getNoiseTexture(noiseFactor: Float): Bitmap {
    val cached = noiseTextureCache[noiseFactor]
    if (cached != null) return cached

    // We draw the noise with the given opacity
    return BitmapFactory.decodeResource(context.resources, R.drawable.haze_noise)
      .withAlpha(noiseFactor)
      .also { noiseTextureCache.put(noiseFactor, it) }
  }

  private fun Effect.updateParameters(
    bounds: Rect,
    blurRadiusPx: Float,
    noiseFactor: Float,
    tint: Color,
    shape: Shape,
  ): Boolean {
    if (!renderEffectDirty) {
      renderEffectDirty = this.blurRadiusPx != blurRadiusPx ||
        this.tint != tint ||
        this.noiseFactor != noiseFactor
    }
    if (!renderNodeDirty) {
      renderNodeDirty = this.bounds != bounds || !renderNode.hasDisplayList()
    }
    if (!pathDirty) {
      pathDirty = this.bounds.size != bounds.size || this.shape != shape || path.isEmpty
    }

    // Finally update all of the properties
    this.bounds = bounds
    this.blurRadiusPx = blurRadiusPx
    this.noiseFactor = noiseFactor
    this.shape = shape
    this.tint = tint

    return renderEffectDirty || renderNodeDirty || pathDirty
  }

  private fun Effect.update() {
    if (renderNodeDirty) updateRenderNodePosition()
    if (renderEffectDirty) updateRenderEffect()
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }

  private fun Effect.updateRenderEffect() {
    renderNode.setRenderEffect(
      RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
        .withNoise(noiseFactor)
        .withTint(tint),
    )
    renderEffectDirty = false
  }

  private fun Effect.updateRenderNodePosition() {
    renderNode.apply {
      renderNode.setPosition(-(bounds.left.toInt()), -(bounds.top.toInt()), bounds.width.toInt(), bounds.height.toInt())
      renderNode.translationX = bounds.left
      renderNode.translationY = bounds.top
    }
    renderNodeDirty = false
  }

  private fun Effect.getUpdatedPath(layoutDirection: LayoutDirection, density: Density): Path {
    if (pathDirty) updatePath(layoutDirection, density)
    return path
  }

  private fun Effect.updatePath(layoutDirection: LayoutDirection, density: Density) {
    path.rewind()
    path.addOutline(shape.createOutline(bounds.size, layoutDirection, density))
    pathDirty = false
  }

  private data class Effect(
    val area: HazeArea,
    val path: Path = Path(),
    val renderNode: RenderNode = RenderNode(null),
    var bounds: Rect = Rect.Zero,
    var blurRadiusPx: Float = 0f,
    var noiseFactor: Float = 0f,
    var tint: Color = Color.Unspecified,
    var shape: Shape = RectangleShape,
    var renderEffectDirty: Boolean = true,
    var pathDirty: Boolean = true,
    var renderNodeDirty: Boolean = true,
  )

  private fun RenderEffect.withNoise(noiseFactor: Float): RenderEffect = when {
    noiseFactor >= 0.005f -> {
      val noiseShader = BitmapShader(getNoiseTexture(noiseFactor), REPEAT, REPEAT)
      RenderEffect.createBlendModeEffect(
        RenderEffect.createShaderEffect(noiseShader), // dst
        this, // src
        BlendMode.DST_ATOP, // blendMode
      )
    }

    else -> this
  }

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
}

@RequiresApi(31)
private inline fun RenderNode.record(block: RecordingCanvas.() -> Unit) {
  try {
    beginRecording().apply(block)
  } finally {
    endRecording()
  }
}

private fun Canvas.clipShape(
  shape: Shape,
  bounds: Rect,
  clipOp: ClipOp = ClipOp.Intersect,
  path: () -> Path,
) {
  if (shape == RectangleShape) {
    clipRect(bounds, clipOp)
  } else {
    pathPool.usePath { tmpPath ->
      tmpPath.asAndroidPath().apply {
        set(path().asAndroidPath())
        offset(bounds.left, bounds.top)
      }
      clipPath(tmpPath, clipOp)
    }
  }
}
