// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.roundToIntSize
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
  GlobalPositionAwareModifierNode,
  ObserverModifierNode {

  private var position by mutableStateOf(Offset.Unspecified)
  private var size by mutableStateOf(Size.Unspecified)
  private var isCanvasHardwareAccelerated by mutableStateOf(false)

  private var effects: List<Effect> = emptyList()

  private val noiseTextureCache = lruCache<Float, Bitmap>(3)

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
    val changed = update(
      state = state,
      defaultStyle = style,
      position = position,
      density = currentValueOf(LocalDensity),
    )

    if (changed) {
      invalidateDraw()
    }
  }

  override fun onPlaced(coordinates: LayoutCoordinates) {
    position = coordinates.positionInWindow() + calculateWindowOffset()
    size = coordinates.size.toSize()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    onPlaced(coordinates)
  }

  override fun onRemeasured(size: IntSize) {
    this.size = size.toSize()
  }

  override fun ContentDrawScope.draw() {
    if (effects.isEmpty()) {
      // If we don't have any effects, just call drawContent and return early
      drawContent()
      return
    }

    val contentLayer = currentValueOf(LocalGraphicsContext).createGraphicsLayer()

    // First we draw the composable content into `contentNode`
    contentLayer.record(size = size.roundToIntSize()) {
      this@draw.drawContent()
    }

    for (effect in effects) {
      // First we need to make sure that the effects are updated (if necessary)
      effect.update()
    }

    // Now we draw `contentNode` into the window canvas, clipping any effect areas which
    // will be drawn below
    with(drawContext.canvas) {
      withSave {
        // We add all of the clip outs to the canvas (to combine them)
        for (effect in effects) {
          clipShape(effect.shape, effect.contentClipBounds, ClipOp.Difference) {
            effect.getUpdatedContentClipPath(layoutDirection, drawContext.density)
          }
        }
        // Then we draw the content layer
        drawLayer(contentLayer)
      }
    }

    // Now we need to draw `contentNode` into each of our 'effect' RenderNodes, allowing
    // their RenderEffect to be applied to the composable content.
    for (effect in effects) {
      effect.layer.record {
        translate(-effect.bounds.left, -effect.bounds.top) {
          // We need to inflate the bounds by the blur radius, so that the effect
          // has access to the pixels it needs in the clipRect
          val (l, t, r, b) = effect.bounds.inflate(effect.blurRadiusPx)
          clipRect(l, t, r, b) {
            // Finally draw the content into our effect RN
            drawLayer(contentLayer)
          }
        }
      }

      // Finally we draw the 'effect' RenderNode to the window canvas, drawing on top
      // of the original content
      with(drawContext.canvas) {
        withSave {
          clipShape(effect.shape, effect.bounds) {
            effect.getUpdatedPath(layoutDirection, drawContext.density)
          }
          drawLayer(effect.layer)
        }
      }
    }

    currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(contentLayer)
  }

  private fun update(
    state: HazeState,
    defaultStyle: HazeStyle,
    position: Offset,
    density: Density,
  ): Boolean {
    val currentEffectsIsEmpty = effects.isEmpty()
    val currentEffects = effects.associateByTo(HashMap(), Effect::area)

    // We create a RenderNode for each of the areas we need to apply our effect to
    effects = state.areas.asSequence()
      .filter { it.isValid }
      .map { area ->
        currentEffects.remove(area)
          ?: Effect(
            area = area,
            path = pathPool.acquireOrCreate(),
            layer = currentValueOf(LocalGraphicsContext).createGraphicsLayer(),
            contentClipPath = pathPool.acquireOrCreate(),
          )
      }
      .toList()

    // Any effects left in the currentEffects are no longer used, lets recycle them
    currentEffects.forEach { (_, effect) -> effect.recycle() }
    currentEffects.clear()

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
    return invalidateCount > 0 || (effects.isEmpty() != currentEffectsIsEmpty)
  }

  private fun getNoiseTexture(noiseFactor: Float): Bitmap {
    val cached = noiseTextureCache[noiseFactor]
    if (cached != null) return cached

    // We draw the noise with the given opacity
    return BitmapFactory.decodeResource(
      currentValueOf(LocalContext).resources,
      R.drawable.haze_noise
    )
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
    if (!layerDirty) {
      layerDirty = this.bounds != bounds || layer.isReleased
    }
    if (!pathsDirty) {
      pathsDirty = this.bounds.size != bounds.size || this.shape != shape || path.isEmpty
    }

    // Finally update all of the properties
    this.bounds = bounds
    this.contentClipBounds = when {
      bounds.isEmpty -> bounds
      // We clip the content to a slightly smaller rect than the blur bounds, to reduce the
      // chance of rounding + anti-aliasing causing visually problems
      else -> bounds.deflate(2f).takeIf { it.width >= 0 && it.height >= 0 } ?: Rect.Zero
    }
    this.blurRadiusPx = blurRadiusPx
    this.noiseFactor = noiseFactor
    this.shape = shape
    this.tint = tint

    return renderEffectDirty || layerDirty || pathsDirty
  }

  private fun Effect.update() {
    if (layerDirty) updateRenderNodePosition()
    if (renderEffectDirty) updateRenderEffect()
    // We don't update the path here as we may not need it. Let draw request it
    // via getUpdatedPath if it needs it
  }

  private fun Effect.updateRenderEffect() {
    layer.renderEffect = RenderEffect
      .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
      .withNoise(noiseFactor)
      .withTint(tint)
      .asComposeRenderEffect()

    renderEffectDirty = false
  }

  private fun Effect.updateRenderNodePosition() {
    layer.topLeft = bounds.topLeft.round()
    // TODO: need to update size?
    layerDirty = false
  }

  private fun Effect.getUpdatedPath(layoutDirection: LayoutDirection, density: Density): Path {
    if (pathsDirty) updatePaths(layoutDirection, density)
    return path
  }

  private fun Effect.getUpdatedContentClipPath(
    layoutDirection: LayoutDirection,
    density: Density,
  ): Path {
    if (pathsDirty) updatePaths(layoutDirection, density)
    return contentClipPath
  }

  private fun Effect.updatePaths(layoutDirection: LayoutDirection, density: Density) {
    path.rewind()
    if (!bounds.isEmpty) {
      path.addOutline(shape.createOutline(bounds.size, layoutDirection, density))
    }

    contentClipPath.rewind()
    if (!contentClipBounds.isEmpty) {
      contentClipPath.addOutline(
        shape.createOutline(
          size = contentClipBounds.size,
          layoutDirection = layoutDirection,
          density = density
        )
      )
    }

    pathsDirty = false
  }

  private fun Effect.recycle() {
    pathPool.releasePath(path)
    pathPool.releasePath(contentClipPath)
    currentValueOf(LocalGraphicsContext).releaseGraphicsLayer(layer)
  }

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

private class Effect(
  val area: HazeArea,
  val path: Path,
  val contentClipPath: Path,
  val layer: GraphicsLayer,
  var bounds: Rect = Rect.Zero,
  var contentClipBounds: Rect = Rect.Zero,
  var blurRadiusPx: Float = 0f,
  var noiseFactor: Float = 0f,
  var tint: Color = Color.Unspecified,
  var shape: Shape = RectangleShape,
  var renderEffectDirty: Boolean = true,
  var pathsDirty: Boolean = true,
  var layerDirty: Boolean = true,
)

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
