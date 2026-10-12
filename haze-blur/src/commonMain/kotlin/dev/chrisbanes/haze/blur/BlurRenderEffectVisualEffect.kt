// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeProgressive as RootHazeProgressive
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.math.ceil

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
internal class RenderEffectBlurVisualEffectDelegate(
  val blurVisualEffect: BlurVisualEffect,
) : BlurVisualEffect.Delegate {
  internal val effectSlot: BlurRenderEffectSlot = blurVisualEffect.renderEffectSlotFactory()
  private val bands = mutableListOf<Band>()
  internal val bandMasks = BandMasks()
  internal var scaledContentLayer: GraphicsLayer? = null
    private set
  private var lastScaledLayerSize: Size? = null
  private var graphicsContext: GraphicsContext? = null
  private var retainedOutputAvailable: Boolean = false
  private var inputCaptureKey: InputCaptureKey? = null

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
    if (blurVisualEffect.alpha == 0f) return

    // Calculate scaled layer size to detect size changes (needs re-allocation)
    val scaleFactor = blurVisualEffect.resolveInputScaleFactor()
    val currentScaledSize = (context.layerSize * scaleFactor).roundToIntSize().let {
      Size(it.width.toFloat(), it.height.toFloat())
    }
    val hasDrawableSourceLayers = context.hasDrawableInput

    if (!hasDrawableSourceLayers) {
      val retainedLayer = scaledContentLayer
        ?.takeUnless {
          if (it.isReleased) {
            inputCaptureKey = null
            retainedOutputAvailable = false
            true
          } else {
            false
          }
        }
        ?.takeIf { retainedOutputAvailable }
        ?: return

      if (lastScaledLayerSize != currentScaledSize) {
        retainedOutputAvailable = false
        return
      }

      drawRetainedLayer(retainedLayer, context, scaleFactor)
      return
    }

    val currentCaptureKey = InputCaptureKey(
      inputSnapshot = context.inputSnapshot,
      scaleFactor = scaleFactor,
      scaledLayerSize = currentScaledSize,
      layerOffset = context.layerOffset,
      backgroundColor = blurVisualEffect.backgroundColor,
    )

    // A new capture geometry re-records the existing layer at its new size rather than replacing
    // it. Replacing it showed an effect that samples this one -- a glass panel whose content is
    // this blur -- an empty input for a frame, and the geometry changes on every frame this effect
    // moves against a source outside it. The layer is released only by [clearRetainedOutput],
    // [detach] and memory pressure.
    if (scaledContentLayer == null || scaledContentLayer!!.isReleased) {
      graphicsContext = context.requireGraphicsContext()
      scaledContentLayer = graphicsContext!!.createGraphicsLayer()
      lastScaledLayerSize = currentScaledSize
      retainedOutputAvailable = false
      inputCaptureKey = null
    } else if (lastScaledLayerSize != currentScaledSize) {
      lastScaledLayerSize = currentScaledSize
      retainedOutputAvailable = false
      inputCaptureKey = null
    }

    val layer = scaledContentLayer!!

    val resultLayer = if (inputCaptureKey == currentCaptureKey) {
      layer
    } else {
      blurVisualEffect.invalidateRenderEffectOnInputChange()
      // Returns null when the scaled size is 0 (e.g., window minimized).
      createScaledContentLayer(
        context = context,
        scaleFactor = scaleFactor,
        layerSize = context.layerSize,
        backgroundColor = blurVisualEffect.backgroundColor,
        existingLayer = layer,
      )?.also {
        inputCaptureKey = currentCaptureKey
      } ?: return
    }

    retainedOutputAvailable = true
    drawRetainedLayer(resultLayer, context, scaleFactor)
  }

  private fun DrawScope.drawRetainedLayer(
    layer: GraphicsLayer,
    context: HazeEffectRuntimeDrawScope,
    scaleFactor: Float,
  ) {
    val clip = blurVisualEffect.shouldClipToNodeBounds()
    layer.clip = clip
    drawScaledContent(
      offset = -context.layerOffset,
      scaledSize = size * scaleFactor,
      clip = clip,
    ) {
      val p = blurVisualEffect.progressive
      if (p != null) {
        drawProgressiveEffect(
          drawScope = this,
          progressive = p,
          contentLayer = layer,
          context = context,
          inputScale = scaleFactor,
        )
      } else {
        trimBandSlots(0)
        // Always resolve the current RenderEffect, so changes coming from either the effect itself
        // or the hosting node (e.g., size, layer offset, input scale) are reflected without relying
        // on local dirty flags. The slot makes the unchanged case cheap.
        effectSlot.apply(
          layer,
          blurVisualEffect.getOrCreateRenderEffect(context, inputScale = scaleFactor, slot = effectSlot),
        )
        layer.alpha = blurVisualEffect.alpha
        drawLayer(layer)
      }
    }
  }

  override val supportsBackdropRenderEffect: Boolean get() = true

  override fun canDrawRetainedOutput(): Boolean {
    return retainedOutputAvailable && scaledContentLayer?.isReleased == false
  }

  override fun clearRetainedOutput() {
    releaseRetainedResources()
  }

  override fun invalidateRetainedOutput() {
    // Keeps the layer for the next draw to record into, for the same reason a new capture
    // geometry does in [draw].
    retainedOutputAvailable = false
    inputCaptureKey = null
  }

  override fun detach() {
    releaseRetainedResources()
  }

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    if (level.severity >= TrimMemoryLevel.MODERATE.severity) {
      releaseRetainedResources()
      context.invalidateDraw()
    }
  }

  private fun releaseRetainedResources() {
    scaledContentLayer?.let { layer ->
      graphicsContext?.releaseGraphicsLayer(layer)
    }
    scaledContentLayer = null
    lastScaledLayerSize = null
    effectSlot.reset()
    trimBandSlots(0)
    graphicsContext = null
    retainedOutputAvailable = false
    inputCaptureKey = null
  }

  internal val bandSlotCount: Int get() = bands.size

  /** The slot for the [index]th band of a layered progressive draw, in draw order. */
  internal fun bandSlot(index: Int): BlurRenderEffectSlot = band(index).slot

  /** The retained layer for the [index]th band, recorded again on every layered draw. */
  internal fun bandLayer(index: Int): RetainedGraphicsLayer = band(index).layer

  private fun band(index: Int): Band {
    while (bands.size <= index) bands += Band(blurVisualEffect.renderEffectSlotFactory())
    return bands[index]
  }

  /** Drops the slots and layers of bands the latest layered draw no longer has, and the masks with the last. */
  internal fun trimBandSlots(count: Int) {
    while (bands.size > count) {
      bands.removeAt(bands.lastIndex).let {
        it.slot.reset()
        it.layer.release()
      }
    }
    if (count == 0) bandMasks.clear()
  }

  private class Band(val slot: BlurRenderEffectSlot) {
    val layer = RetainedGraphicsLayer()
  }

  @Poko
  private class InputCaptureKey(
    val inputSnapshot: HazeEffectInputSnapshot?,
    val scaleFactor: Float,
    val scaledLayerSize: Size,
    val layerOffset: Offset,
    val backgroundColor: Color,
  )

  companion object {
    const val TAG = "RenderEffectBlurVisualEffectDelegate"
  }
}

internal expect fun RenderEffectBlurVisualEffectDelegate.drawProgressiveEffect(
  drawScope: DrawScope,
  progressive: RootHazeProgressive,
  contentLayer: GraphicsLayer,
  context: HazeEffectRuntimeDrawScope,
  inputScale: Float,
)

internal expect fun BlurVisualEffect.invalidateRenderEffectOnInputChange()

internal fun shouldDrawProgressiveWithLayers(
  progressive: RootHazeProgressive,
  inputScale: Float,
): Boolean = progressive is RootHazeProgressive.LinearGradient &&
  inputScale == BLUR_FULL_RESOLUTION_SCALE

internal fun DrawScope.drawProgressiveWithMultipleLayers(
  progressive: RootHazeProgressive.LinearGradient,
  stepHeight: Dp = 64.dp,
  masks: BandMasks? = null,
  block: (mask: Brush, intensity: Float) -> Unit,
) {
  // Use 64dp per step to balance quality and drawing work.
  val stepHeightPx = with(drawContext.density) { stepHeight.toPx() }
  val length = calculateLength(progressive.start, progressive.end, size)
  // Reject invalid arithmetic before drawing, leaving room for the inclusive final layer.
  require(length.isFinite() && length >= 0f) { "Gradient length must be finite and nonnegative" }
  require(stepHeightPx.isFinite() && stepHeightPx > 0f) { "Step height must be finite and positive" }
  val derivedSteps = ceil(length / stepHeightPx)
  require(derivedSteps.isFinite() && derivedSteps.toDouble() <= Int.MAX_VALUE.toDouble() - 1) {
    "Gradient layer count exceeds the supported integer range"
  }
  val steps = derivedSteps.toInt().coerceAtLeast(2)

  val seq = when {
    progressive.endIntensity >= progressive.startIntensity -> 0..steps
    else -> steps downTo 0
  }

  for (i in seq) {
    val fraction = i / steps.toFloat()

    val intensity = lerp(
      progressive.startIntensity,
      progressive.endIntensity,
      progressive.easing.transform(fraction),
    )

    val mask = masks?.maskFor(progressive, steps, i) ?: bandMask(progressive, steps, i)

    block(mask, intensity)
  }
}
