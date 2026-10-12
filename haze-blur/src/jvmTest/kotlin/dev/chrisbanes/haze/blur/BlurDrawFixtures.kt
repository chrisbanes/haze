// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(
  androidx.compose.ui.InternalComposeUiApi::class,
  dev.chrisbanes.haze.InternalHazeApi::class,
)

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SkiaGraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope

/** Fakes that drive Blur delegates and renderers directly, without a Compose node. */

internal class RecordingLifecycleScope : HazeEffectLifecycleScope {
  var invalidateDrawCalls = 0
    private set

  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = error("Unused in trim-memory tests")
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in trim-memory tests")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in trim-memory tests")

  override fun invalidateDraw() {
    invalidateDrawCalls++
  }

  override fun invalidateLayerBounds() = Unit
}

internal data object BlurLifecycleScope : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size.Zero
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  override fun requirePlatformContext(): PlatformContext = PlatformContext.INSTANCE
  override fun requireDensity(): Density = Density(1f)
  override fun <T> currentValueOf(local: CompositionLocal<T>): T {
    @Suppress("UNCHECKED_CAST")
    return HazeBlurStyle as T
  }
  override fun requireGraphicsContext(): GraphicsContext = error("Unused in direct-draw tests")
  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}

@OptIn(InternalComposeUiApi::class, InternalHazeApi::class)
internal class RecordingDrawContext(
  private val drawScope: CanvasDrawScope = CanvasDrawScope(),
  private val recordedSize: Size = Size(10f, 10f),
) : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
  private val graphicsContext = RecordingGraphicsContext()

  /** The density of the next [render], mutable so a test can change it between draws. */
  var renderDensity: Density = Density(1f)

  var inputCaptureCount = 0
    private set

  override val modifierSize: Size = recordedSize
  override val modifierBounds: Rect = Rect(Offset.Zero, modifierSize)
  override var sampling: HazeSampling = HazeSampling.FullResolution
  override var layerSize: Size = modifierSize
  override var layerOffset: Offset = Offset.Zero
  override var hasDrawableInput: Boolean = true
  override var inputSnapshot: HazeEffectInputSnapshot? = CaptureSnapshot
  override val coroutineScope: CoroutineScope = object : CoroutineScope {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext
  }

  /** The frame the latest [render] drew into. */
  var output: ImageBitmap? = null
    private set

  fun render(canvasSize: Size = recordedSize, block: DrawScope.() -> Unit) {
    val bitmap = ImageBitmap(canvasSize.width.toInt(), canvasSize.height.toInt())
    drawScope.draw(
      density = renderDensity,
      layoutDirection = LayoutDirection.Ltr,
      canvas = Canvas(bitmap),
      size = canvasSize,
      block = block,
    )
    output = bitmap
  }

  fun releaseLatestLayer() {
    graphicsContext.releaseLatestLayer()
  }

  override fun requirePlatformContext(): PlatformContext = PlatformContext.INSTANCE
  override fun requireGraphicsContext(): GraphicsContext = graphicsContext
  override fun requireDensity(): Density = renderDensity
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unused in direct-draw tests")
  override fun invalidateDraw() = Unit
  override fun drawInput() = Unit

  override fun DrawScope.drawInput() {
    this@RecordingDrawContext.inputCaptureCount++
    drawRect(Color.Red)
  }
}

internal data object CaptureSnapshot : HazeEffectInputSnapshot
internal data object DifferentCaptureSnapshot : HazeEffectInputSnapshot

@OptIn(InternalComposeUiApi::class)
internal class RecordingGraphicsContext : GraphicsContext {
  private val delegate = SkiaGraphicsContext()
  private var latestLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null

  override fun createGraphicsLayer() = delegate.createGraphicsLayer().also { latestLayer = it }

  override fun releaseGraphicsLayer(layer: androidx.compose.ui.graphics.layer.GraphicsLayer) {
    delegate.releaseGraphicsLayer(layer)
  }

  fun releaseLatestLayer() {
    latestLayer?.let(::releaseGraphicsLayer)
  }
}
