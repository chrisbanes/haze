// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")
@file:OptIn(InternalHazeApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.renderscript.RenderScript
import android.view.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.unit.toIntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.createScaledContentLayer
import dev.chrisbanes.haze.drawScaledContent
import dev.chrisbanes.haze.trace
import dev.chrisbanes.haze.traceAsync
import dev.chrisbanes.haze.translate
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class RenderScriptBlurVisualEffectDelegate(
  private val blurVisualEffect: BlurVisualEffect,
  private val graphicsContext: GraphicsContext,
  private val platformContext: PlatformContext,
) : BlurVisualEffect.Delegate {

  @Volatile
  private var renderScript = RenderScript.create(platformContext.applicationContext)

  @Volatile
  internal var renderScriptContext: RenderScriptContext? = null
    private set
  private val drawScope = CanvasDrawScope()

  // Test seam: host tests have no input Surface, so they replace the write to it.
  internal var writeToSurface: (RenderScriptContext, GraphicsLayer, Density) -> Unit =
    { rs, layer, density -> rs.inputSurface.drawGraphicsLayer(layer, density, drawScope) }

  // Internal so host tests can wait for the update to finish.
  internal var currentJob: Job? = null
    private set
  private var drawSkipped: Boolean = false
  private var completedCaptureKey: InputCaptureKey? = null
  private val retainedOutputState = BlurRetainedOutputState()

  @Volatile
  private var trimGeneration = 0

  private var contentLayer: GraphicsLayer? = null

  // Layers kept across draws and updates. [draw] records the capture layer; a job records, re-keys
  // or releases the padded layer only before its first suspension, while [currentJob] gates new
  // updates. [invalidateRetainedOutput] keeps them; [releaseRetainedOutput] releases all of them.
  internal val presentationLayer = RetainedGraphicsLayer()
  internal val captureLayer = RetainedGraphicsLayer()
  internal val paddedLayer = RetainedGraphicsLayer()
  internal val noiseShaders = NoiseShaderCache()

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) {
    if (blurVisualEffect.alpha == 0f) return

    val density = context.requireDensity()
    val offset = context.layerOffset
    var scaleFactor = blurVisualEffect.resolveInputScaleFactor()

    var blurRadiusPx = scaleFactor * with(density) {
      blurVisualEffect.blurRadius.takeOrElse { 0.dp }.toPx()
    }
    if (blurRadiusPx > MAX_BLUR_RADIUS) {
      // RenderScript has a max blur radius (25px), so to create an equivalent visual effect
      // we need to increase the scale factor
      scaleFactor *= (MAX_BLUR_RADIUS / blurRadiusPx)
      blurRadiusPx = MAX_BLUR_RADIUS
    }

    HazeLogger.d(TAG) { "drawEffect. blurRadius=${blurRadiusPx}px. scaleFactor=$scaleFactor" }

    val hasDrawableSourceLayers = context.hasDrawableInput
    val captureKey = InputCaptureKey(
      inputSnapshot = context.inputSnapshot,
      scaleFactor = scaleFactor,
      layerSize = context.layerSize,
      layerOffset = offset,
      blurRadiusPx = blurRadiusPx,
      backgroundColor = blurVisualEffect.backgroundColor,
    )

    if (hasDrawableSourceLayers && shouldUpdateLayer(captureKey)) {
      drawSkipped = false

      createScaledContentLayer(
        context = context,
        scaleFactor = scaleFactor,
        layerSize = context.layerSize,
        backgroundColor = blurVisualEffect.backgroundColor,
        // The currentJob gate allows one update at a time, so the one capture layer is re-recorded
        // only while no job reads it.
        existingLayer = captureLayer.acquire(graphicsContext),
      )?.let { layer ->
        layer.clip = blurVisualEffect.shouldClipToNodeBounds()
        if (contentLayer == null || contentLayer!!.isReleased) {
          contentLayer = graphicsContext.createGraphicsLayer()
        }
        val retainedOutputGenerationAtStart = retainedOutputState.beginOutputUpdate()

        currentJob = context.coroutineScope.launch(Dispatchers.Main.immediate) {
          val outputUpdated = try {
            updateSurface(
              content = layer,
              blurRadius = blurRadiusPx,
              context = context,
              density = density,
              retainedOutputGenerationAtStart = retainedOutputGenerationAtStart,
            )
          } finally {
            retainedOutputState.finishOutputUpdate(retainedOutputGenerationAtStart)
          }

          if (outputUpdated) completedCaptureKey = captureKey
          if (outputUpdated || drawSkipped) {
            // Present the completed output, or catch up with input changed during the update.
            context.invalidateDraw()
          }
        }
      } ?: run {
        // An empty scaled size leaves nothing to capture, so the kept layers would only linger.
        captureLayer.release()
        paddedLayer.release()
      }
    } else if (hasDrawableSourceLayers) {
      // Mark this draw as skipped
      drawSkipped = true
    } else if (!canDrawRetainedOutput()) {
      if (retainedOutputState.isPending) {
        drawSkipped = true
      }
      // Nothing to present, so this draw does not use the presentation layer.
      presentationLayer.release()
      return
    }

    drawPresentation(context, contentLayer, offset, scaleFactor)
  }

  internal fun DrawScope.drawPresentation(
    context: HazeEffectRuntimeDrawScope,
    output: GraphicsLayer?,
    offset: Offset,
    scaleFactor: Float,
  ) {
    val layer = presentationLayer.acquire(graphicsContext)
    // The layer keeps what the previous draw set, so set every property on every draw.
    layer.alpha = blurVisualEffect.alpha
    layer.clip = blurVisualEffect.shouldClipToNodeBounds()

    val progressiveMask = blurVisualEffect.progressiveBrush
    val userMask = blurVisualEffect.mask
    // If we have a mask, this needs to be drawn offscreen
    layer.compositingStrategy = if (progressiveMask != null || userMask != null) {
      CompositingStrategy.Offscreen
    } else {
      CompositingStrategy.Auto
    }

    layer.record(size = size.toIntSize()) {
      val expandedSize = size.expand(
        expansionWidth = max(offset.x, 0f) * 2,
        expansionHeight = max(offset.y, 0f) * 2,
      )
      val colorEffects = blurVisualEffect.colorEffects.orEmpty()

      // The blurred output, then the noise on top...
      fun DrawScope.drawOutputAndNoise() {
        drawScaledContent(
          offset = -offset,
          scaledSize = size * scaleFactor,
          clip = blurVisualEffect.shouldClipToNodeBounds(),
        ) {
          output?.let(::drawLayer)
        }

        val noiseFactor = blurVisualEffect.noiseFactor
        if (noiseFactor.hasVisibleNoise()) {
          translate(offset = -offset) {
            PaintPool.usePaint { paint ->
              paint.isAntiAlias = true
              paint.alpha = noiseFactor.coerceIn(0f, 1f)
              paint.shader = noiseShaders.get(platformContext, scaleFactor)
              paint.blendMode = BlendMode.SrcAtop
              drawContext.canvas.drawRect(expandedSize.toRect(), paint)
            }
          }
        }
      }

      // ...then the colour effects, in order. A colour filter filters everything drawn below it, as
      // the RenderEffect path does, so the stack is drawn again inside a layer carrying the filter
      // and blend mode.
      // shortcut: replays the stack per colour filter (2^n draws); record into a retained layer if
      // styles stack several filters.
      fun DrawScope.drawStack(count: Int) {
        if (count == 0) {
          drawOutputAndNoise()
          return
        }
        drawStack(count - 1)
        val colorEffect = colorEffects[count - 1]
        if (colorEffect is ColorFilterHazeColorEffect) {
          PaintPool.usePaint { paint ->
            paint.colorFilter = colorEffect.colorFilter
            paint.blendMode = colorEffect.blendMode
            val canvas = drawContext.canvas
            canvas.saveLayer(Rect(-offset, expandedSize), paint)
            drawStack(count - 1)
            canvas.restore()
          }
        } else {
          translate(offset = -offset) {
            drawScrim(
              colorEffect = colorEffect,
              context = context,
              offset = offset,
              expandedSize = expandedSize,
            )
          }
        }
      }
      drawStack(colorEffects.size)

      // Progressive intensity first, then the user's mask fades the complete output.
      if (progressiveMask != null) {
        HazeLogger.d(TAG) { "Drawing progressive mask, canvas size=$size" }
        drawRect(brush = progressiveMask, size = size, blendMode = BlendMode.DstIn)
      }
      if (userMask != null) {
        HazeLogger.d(TAG) { "Drawing mask, canvas size=$size" }
        drawRect(brush = userMask, size = size, blendMode = BlendMode.DstIn)
      }
    }

    drawLayer(layer)
  }

  private fun shouldUpdateLayer(captureKey: InputCaptureKey): Boolean = when {
    // Wait for the in-flight output, then compare its key on the presentation draw.
    currentJob?.isActive == true -> false
    // We don't have a layer yet...
    contentLayer == null || contentLayer!!.isReleased || contentLayer!!.size == IntSize.Zero -> true
    // A redraw requested by a completed output must not start the same update again.
    else -> completedCaptureKey != captureKey
  }

  override fun onTrimMemory(context: HazeEffectLifecycleScope, level: TrimMemoryLevel) {
    if (level.severity >= TrimMemoryLevel.MODERATE.severity) {
      currentJob?.cancel()
      releaseRenderScriptContext()
      // Create the new RenderScript first, then destroy the old instance.
      // This avoids leaving renderScript in a destroyed state if creation fails.
      runCatching { RenderScript.create(platformContext.applicationContext) }
        .onSuccess { newRs ->
          val oldRs = renderScript
          renderScript = newRs
          runCatching { oldRs.destroy() }
        }
        .onFailure { HazeLogger.d(TAG) { "Failed to recreate RenderScript after trim" } }
      // Bump generation so in-flight coroutines know their context is stale
      trimGeneration++
      releaseRetainedOutput()
      // Force the next draw to recreate the layer from scratch
      drawSkipped = true
      context.invalidateDraw()
    }
  }

  private suspend fun updateSurface(
    content: GraphicsLayer,
    blurRadius: Float,
    context: HazeEffectRuntimeDrawScope,
    density: Density,
    retainedOutputGenerationAtStart: Int,
  ): Boolean {
    val generationAtStart = trimGeneration
    var outputUpdated = false

    // Pad the content layer to ensure dimensions are multiples of 4, so the RenderScript
    // Allocation surface is fully covered with content (no transparent/black edges).
    val paddedContent = padToMultipleOf4(content, density, context)

    traceAsync("Haze-RenderScriptBlurEffect-updateSurface", 0) {
      // If a trim happened before we even started, bail out
      if (!isUpdateCurrent(generationAtStart, retainedOutputGenerationAtStart)) return@traceAsync

      val rs = getRenderScriptContext(paddedContent.size)

      // If the retained output was cleared while we were getting the context, bail out
      if (!isUpdateCurrent(generationAtStart, retainedOutputGenerationAtStart)) {
        releaseRenderScriptContext()
        return@traceAsync
      }

      // The trace lambda is crossinline, so it reports failure rather than returning from this block.
      val surfaceWritten = traceAsync("Haze-RenderScriptBlurEffect-updateSurface-drawLayerToSurface", 0) {
        try {
          // Draw the layer (this is async)
          writeToSurface(rs, paddedContent, density)
        } catch (e: IllegalStateException) {
          HazeLogger.d(TAG) { "Surface draw failed, likely destroyed. Releasing context." }
          releaseRenderScriptContext()
          return@traceAsync false
        }
        // Wait for the layer to be written to the Surface
        rs.awaitSurfaceWritten()
        true
      }
      // Without output, the capture key stays uncommitted and the next draw retries.
      if (!surfaceWritten) return@traceAsync

      // If the retained output was cleared during surface operations, bail out before using stale data
      if (!isUpdateCurrent(generationAtStart, retainedOutputGenerationAtStart)) return@traceAsync

      if (blurRadius > 0f) {
        // Now apply the blur on a background thread
        traceAsync("Haze-RenderScriptBlurEffect-updateSurface-applyBlur", 0) {
          withContext(Dispatchers.Default) {
            rs.applyBlur(blurRadius)
          }
        }

        trace("Haze-RenderScriptBlurEffect-updateSurface-drawToContentLayer") {
          if (!isUpdateCurrent(generationAtStart, retainedOutputGenerationAtStart)) return@trace

          // Finally draw the updated bitmap to our drawing graphics layer
          val output = rs.outputBitmap

          val outputLayer = contentLayer ?: return@trace
          outputLayer.record(
            density = density,
            layoutDirection = context.currentValueOf(LocalLayoutDirection),
            size = IntSize(output.width, output.height),
          ) {
            drawImage(output.asImageBitmap())
          }
          outputUpdated = retainedOutputState.completeOutputUpdate(retainedOutputGenerationAtStart)
        }
      } else {
        if (!isUpdateCurrent(generationAtStart, retainedOutputGenerationAtStart)) return@traceAsync

        // If the blur radius is 0, we just copy the input content into our contentLayer
        val outputLayer = contentLayer ?: return@traceAsync
        outputLayer.record(
          density = density,
          layoutDirection = context.currentValueOf(LocalLayoutDirection),
          size = paddedContent.size,
        ) {
          drawLayer(paddedContent)
        }
        outputUpdated = retainedOutputState.completeOutputUpdate(retainedOutputGenerationAtStart)
      }

      HazeLogger.d(TAG) { "Output updated in layer" }
    }
    return outputUpdated
  }

  private fun isUpdateCurrent(
    trimGenerationAtStart: Int,
    retainedOutputGenerationAtStart: Int,
  ): Boolean {
    return trimGeneration == trimGenerationAtStart &&
      retainedOutputState.generation == retainedOutputGenerationAtStart
  }

  /**
   * Returns [layer] as-is if both dimensions are already multiples of 4.
   * Otherwise records [layer] into the retained [paddedLayer], at a size rounded up to the next
   * multiple of 4, after filling the extra area with [BlurVisualEffect.backgroundColor]. This
   * ensures the RenderScript Allocation surface has no transparent/black fringe regions for the
   * blur kernel to sample. The padded layer is keyed by its size and released when none is needed.
   */
  private fun padToMultipleOf4(
    layer: GraphicsLayer,
    density: Density,
    context: HazeEffectRuntimeDrawScope,
  ): GraphicsLayer {
    val size = layer.size
    val paddedWidth = ((size.width + 3) / 4) * 4
    val paddedHeight = ((size.height + 3) / 4) * 4

    if (paddedWidth == size.width && paddedHeight == size.height) {
      paddedLayer.release()
      return layer
    }

    val padded = paddedLayer.acquire(
      graphicsContext,
      key = (paddedWidth.toLong() shl 32) or paddedHeight.toLong(),
    )
    val bg = blurVisualEffect.backgroundColor
    padded.record(
      density = density,
      layoutDirection = context.currentValueOf(LocalLayoutDirection),
      size = IntSize(paddedWidth, paddedHeight),
    ) {
      if (bg.isSpecified) {
        drawRect(bg)
      }
      drawLayer(layer)
    }
    return padded
  }

  internal fun getRenderScriptContext(size: IntSize): RenderScriptContext {
    val rs = renderScriptContext
    if (rs != null && rs.size == size) return rs

    // Release any existing context
    releaseRenderScriptContext()
    // Return a new context and store it
    return RenderScriptContext(rs = renderScript, size = size)
      .also { renderScriptContext = it }
  }

  override fun canDrawRetainedOutput(): Boolean {
    return retainedOutputState.isAvailable &&
      contentLayer?.isReleased == false &&
      contentLayer?.size != IntSize.Zero
  }

  override fun shouldDrawRetainedOutput(): Boolean {
    return canDrawRetainedOutput() || retainedOutputState.isPending
  }

  override fun clearRetainedOutput() {
    releaseRetainedOutput()
  }

  override fun detach() {
    releaseRetainedOutput()
    runCatching { renderScript.destroy() }
  }

  // Drops the output and what produces it, but keeps the capture, padded and presentation layers
  // and the noise shaders: none is tied to the old size, and the next draw and update re-record or
  // re-key them. The context goes too, because a cancelled frame's buffer-available callback can
  // still signal its channel, which would make the next job blur before its own input arrives.
  override fun invalidateRetainedOutput() {
    currentJob?.cancel()
    currentJob = null
    retainedOutputState.clear()
    completedCaptureKey = null
    contentLayer?.let(graphicsContext::releaseGraphicsLayer)
    contentLayer = null
    releaseRenderScriptContext()
  }

  private fun releaseRetainedOutput() {
    invalidateRetainedOutput()
    captureLayer.release()
    paddedLayer.release()
    presentationLayer.release()
    noiseShaders.clear()
  }

  private fun releaseRenderScriptContext() {
    renderScriptContext?.release()
    renderScriptContext = null
  }

  internal companion object {
    const val TAG = "RenderScriptBlurVisualEffectDelegate"

    // Robolectric 4.17 can create RenderScript, but its Allocation has no input Surface.
    // Keep host tests on the scrim fallback rather than failing later during the draw.
    private var isEnabled: Boolean = Build.FINGERPRINT != "robolectric"

    fun createOrNull(
      effect: BlurVisualEffect,
      context: HazeEffectRuntimeDrawScope,
    ): RenderScriptBlurVisualEffectDelegate? {
      if (isEnabled) {
        return runCatching {
          RenderScriptBlurVisualEffectDelegate(
            blurVisualEffect = effect,
            graphicsContext = context.requireGraphicsContext(),
            platformContext = context.requirePlatformContext(),
          )
        }
          .onFailure { isEnabled = false }
          .getOrNull()
      }
      return null
    }
  }

  @Poko
  private class InputCaptureKey(
    val inputSnapshot: HazeEffectInputSnapshot?,
    val scaleFactor: Float,
    val layerSize: Size,
    val layerOffset: Offset,
    val blurRadiusPx: Float,
    val backgroundColor: ComposeColor,
  )
}

private const val MAX_BLUR_RADIUS = 25f

private fun Surface.drawGraphicsLayer(
  layer: GraphicsLayer,
  density: Density,
  drawScope: CanvasDrawScope,
) {
  withSurfaceCanvas {
    drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

    with(drawScope) {
      draw(
        density = density,
        layoutDirection = layoutDirection,
        canvas = Canvas(this@withSurfaceCanvas),
        size = Size(width.toFloat(), height.toFloat()),
      ) {
        drawLayer(layer)
      }
    }
  }
}

private inline fun Surface.withSurfaceCanvas(block: android.graphics.Canvas.() -> Unit) {
  val canvas = lockHardwareCanvas()
  try {
    block(canvas)
  } finally {
    unlockCanvasAndPost(canvas)
  }
}
