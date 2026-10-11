// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.requireDensity
import kotlin.math.ceil
import kotlin.math.floor

/** A configured renderer which may decline a cluster and request ordinary member surfaces. */
@InternalHazeApi
public interface HazeEffectRendererSurfaceFallback {
  /** Evaluated after preparation; never paint a merged field through a single-shape fallback. */
  public val usesMemberSurfaces: Boolean
}

/** Independently owned, configured surface; member input selection/capture stays in core. */
@InternalHazeApi
public class HazeConfiguredEffectSurface<Style> internal constructor(
  anchor: HazeEffectSurface,
  factory: HazeEffectFactory<Style>,
  style: Style,
  bounds: Rect,
  members: List<HazeEffectSurface>,
) {
  private val renderer = ConfiguredSurface(anchor, factory.createRenderer(), style, bounds, members)

  /** Opaque handle submitted to the same owner draw boundary as ordinary surfaces. */
  public val surface: HazeEffectSurface = HazeEffectSurface(anchor.node, renderer).also {
    it.relocateTo(anchor.owner)
  }

  /** Replaces immutable configuration and geometry without recreating renderer resources. */
  public fun update(style: Style, bounds: Rect, members: List<HazeEffectSurface>) {
    if (renderer.update(style, bounds, members)) surface.changed()
  }

  /** Releases this surface's renderer and native resources once on split, merge or detach. */
  public fun dispose() {
    surface.relocateTo(null)
    renderer.dispose()
  }
}

internal interface HazeConfiguredSurfaceDraw {
  val bounds: Rect
  fun footprint(scope: DrawScope, owner: HazeEffectSurfaceOwner?): HazeSurfaceFootprint?
  fun draw(scope: DrawScope, owner: HazeEffectSurfaceOwner?)
  fun usePortableInput(failed: Boolean, temporary: Boolean)
  fun useNativeInput()
  fun completeDraw()
  fun onTrimMemory(level: TrimMemoryLevel)
}

internal class HazeSurfaceInputEntry(val area: HazeArea, val layer: GraphicsLayer, val transform: Matrix)

@Poko
private class SurfaceInputVersion(
  val area: HazeArea,
  val layer: GraphicsLayer,
  val version: Long,
  val transform: List<Float>,
  val size: Size,
)

@Poko
private class SurfaceInputSnapshot(val entries: List<SurfaceInputVersion>) : HazeEffectInputSnapshot

@Suppress("UNCHECKED_CAST")
private class ConfiguredSurface<Style>(
  private val anchor: HazeEffectSurface,
  private val renderer: HazeEffectRenderer<Style>,
  private var style: Style,
  override var bounds: Rect,
  private var members: List<HazeEffectSurface>,
) : HazeConfiguredSurfaceDraw {
  private val backend = HazeBackdropBackendState().also { it.attach(anchor.node.surfaceNativeEligible()) }
  private var nativeRenderer: HazeBackdropRenderer? = null
  private var preparedBackdrop: HazeEffectBackdrop? = null
  private var temporaryFallback = false
  private var disposed = false
  private var entries = emptyList<HazeSurfaceInputEntry>()
  private var snapshot: HazeEffectInputSnapshot? = null
  private var layerBounds = Rect.Zero
  private var layerSize = Size.Zero
  private var layerOffset = Offset.Zero
  private var fallbackMembers = false
  private var mixedSelection = false
  private var renderedSource = false
  private var input: HazeInput = checkNotNull(anchor.node.explicitInput)
  private val context = object : HazeEffectLifecycleScope by anchor.node.surfaceLifecycleScope() {
    override val modifierSize: Size get() = bounds.size
  }
  private val lifecycle get() = renderer as? HazeEffectRendererLifecycle<Style>
  private val hooks get() = renderer as? HazeEffectRendererDrawHooks<Style>
  private val retained get() = renderer as? HazeEffectRendererRetainedOutput

  init {
    anchor.node.registerConfiguredSurface(this)
    lifecycle?.attach(context)
    lifecycle?.update(context, style, HazeSampling.FullResolution)
  }

  fun update(style: Style, bounds: Rect, members: List<HazeEffectSurface>): Boolean {
    check(!disposed)
    val nextInput = checkNotNull(anchor.node.explicitInput)
    if (input != nextInput) {
      retained?.clearRetainedOutput()
      renderedSource = false
      entries = emptyList()
      snapshot = null
      nativeRenderer?.release()
      nativeRenderer = null
      preparedBackdrop = null
      temporaryFallback = false
      input = nextInput
      backend.attach(anchor.node.surfaceNativeEligible())
    } else if (this.style == style && this.bounds == bounds && this.members == members) return false
    if (this.bounds != bounds) retained?.invalidateRetainedOutput()
    this.style = style
    this.bounds = bounds
    this.members = members
    lifecycle?.update(context, style, HazeSampling.FullResolution)
    return true
  }

  private fun resolveEntries(owner: LayoutCoordinates?) {
    // Core members already enforce selection, demand, window boundaries and source ordering.
    // Equal Content inputs still represent distinct areas; Sources shared by members are deduped.
    val selections = members.map { it.node.surfaceInputEntries(owner) }
    // Equal Sources can still resolve differently (Behind ancestors); never draw one member's exclusions.
    mixedSelection = input !is HazeInput.Content && selections.map { it.mapTo(mutableSetOf()) { entry -> entry.area } }.distinct().size > 1
    entries = selections.flatten().distinctBy { it.area }.sortedBy { it.area.zIndex }
    snapshot = entries.takeIf { it.isNotEmpty() }?.let { input ->
      SurfaceInputSnapshot(input.map { SurfaceInputVersion(it.area, it.layer, it.area.contentVersion, it.transform.values.toList(), it.area.size) })
    }
  }

  private fun runtimeScope(drawScope: DrawScope) = object : HazeEffectRuntimeDrawScope, DrawScope by drawScope {
    override val modifierSize: Size get() = bounds.size
    override val modifierBounds: Rect get() = Rect(layerOffset, modifierSize)
    override val sampling: HazeSampling get() = HazeSampling.FullResolution
    override val layerSize: Size get() = this@ConfiguredSurface.layerSize
    override val layerOffset: Offset get() = this@ConfiguredSurface.layerOffset
    override val hasDrawableInput: Boolean get() = entries.isNotEmpty() || (input is HazeInput.Backdrop && !backend.usesFallback)
    override val inputSnapshot: HazeEffectInputSnapshot? get() = snapshot
    override val coroutineScope get() = context.coroutineScope
    override fun requirePlatformContext() = context.requirePlatformContext()
    override fun requireGraphicsContext() = context.requireGraphicsContext()
    override fun requireDensity() = context.requireDensity()
    override fun invalidateDraw() = context.invalidateDraw()
    override fun <T> currentValueOf(local: CompositionLocal<T>): T = context.currentValueOf(local)
    override fun drawInput() {
      with(this) { drawScope.drawInput() }
    }
    override fun DrawScope.drawInput() {
      translate(this@ConfiguredSurface.layerOffset.x - bounds.left, this@ConfiguredSurface.layerOffset.y - bounds.top) {
        entries.forEach { entry -> withTransform({ transform(entry.transform) }) { drawLayer(entry.layer) } }
      }
    }
  }

  private inline fun <T> inSurface(scope: DrawScope, block: DrawScope.() -> T): T {
    var result: T? = null
    scope.translate(bounds.left, bounds.top) {
      val old = drawContext.size
      drawContext.size = bounds.size
      try {
        result = block()
      } finally {
        drawContext.size = old
      }
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  override fun footprint(scope: DrawScope, owner: HazeEffectSurfaceOwner?): HazeSurfaceFootprint? {
    if (disposed || owner?.surfaceCoordinates?.isAttached != true || !bounds.size.isSpecified || bounds.size.minDimension <= 0f) return null
    if (hooks?.shouldPrepareDraw(style) == false) return null
    val layoutScope = object : HazeEffectLayoutScope, androidx.compose.ui.unit.Density by context.requireDensity() {
      override val modifierBounds: Rect get() = Rect(Offset.Zero, bounds.size)
      override fun <T> currentValueOf(local: CompositionLocal<T>): T = context.currentValueOf(local)
    }
    // Like each member's own node, never expand beyond the configured bounds unless every member allows it.
    layerBounds = if (members.all { it.node.expandLayerBounds }) {
      with(renderer) { layoutScope.calculateLayerBounds(style) }
    } else {
      layoutScope.modifierBounds
    }
    layerSize = layerBounds.size
    layerOffset = Offset.Zero - layerBounds.topLeft
    if (layerSize.minDimension <= 0f || !layerSize.width.isFinite() || !layerSize.height.isFinite()) return null
    var native = input is HazeInput.Backdrop && (!backend.usesFallback || temporaryFallback)
    if (native) {
      try {
        nativeRenderer = nativeRenderer ?: createHazeBackdropRenderer()
        if (nativeRenderer?.isSupported(scope.drawContext.canvas) != true || renderer !is HazeEffectRendererBackdrop<*>) {
          usePortableInput(false, temporary = false)
          native = false
        }
      } catch (exception: Exception) {
        HazeLogger.d("HazeEffectSurface", exception) { "Configured native support failed" }
        usePortableInput(true, temporary = false)
        native = false
      }
    } else if (input is HazeInput.Backdrop && backend.usesFallback) {
      members.forEach { it.usePortableInput(backend.selection == HazeBackdropBackendSelection.FallbackFailed, temporary = temporaryFallback) }
    }
    resolveEntries(owner.surfaceCoordinates)
    if (!mixedSelection) {
      inSurface(scope) {
        val runtime = runtimeScope(this)
        preparedBackdrop = if (native) {
          try {
            with(renderer as HazeEffectRendererBackdrop<Style>) { runtime.backdropEffect(style) }
          } catch (exception: Exception) {
            HazeLogger.d("HazeEffectSurface", exception) { "Configured native preparation failed" }
            usePortableInput(true, temporary = false)
            native = false
            null
          }
        } else {
          hooks?.let { with(it) { runtime.prepareDraw(style) } }
          null
        }
      }
    }
    // Members draw individually through their own input policy when the cluster cannot be drawn:
    // mixed selections, a renderer declining the field, or a declined native effect.
    fallbackMembers = mixedSelection ||
      (native && preparedBackdrop == null) ||
      (renderer as? HazeEffectRendererSurfaceFallback)?.usesMemberSurfaces == true
    if (fallbackMembers) {
      val footprints = members.mapNotNull { it.footprint(scope) }
      if (footprints.isEmpty()) return null
      return HazeSurfaceFootprint(
        footprints.map { it.output }.reduce(::unionSurfaceBounds),
        footprints.mapNotNull { it.nativeCapture }.takeIf { it.isNotEmpty() }?.reduce(::unionSurfaceBounds),
        footprints.size > 1 && footprints.any { it.nativeCapture != null },
      )
    }
    val local = Rect(floor(layerBounds.left), floor(layerBounds.top), ceil(layerBounds.right), ceil(layerBounds.bottom))
    val output = local.translate(bounds.topLeft)
    val capture = if (native && preparedBackdrop != null) {
      val transform = checkNotNull(preparedBackdrop).materialTransform
      val material = Rect(
        transform.pivot.x + (local.left - transform.pivot.x) * transform.scaleX,
        transform.pivot.y + (local.top - transform.pivot.y) * transform.scaleY,
        transform.pivot.x + (local.right - transform.pivot.x) * transform.scaleX,
        transform.pivot.y + (local.bottom - transform.pivot.y) * transform.scaleY,
      ).translate(bounds.topLeft)
      val matrix = owner.surfaceCoordinates!!.transformToRoot()
      val root = matrix.map(material)
      matrix.invert()
      matrix.map(Rect(floor(root.left), floor(root.top), ceil(root.right), ceil(root.bottom)))
    } else {
      null
    }
    return HazeSurfaceFootprint(if (capture != null) unionSurfaceBounds(output, capture) else output, capture)
  }

  override fun draw(scope: DrawScope, owner: HazeEffectSurfaceOwner?) {
    if (disposed) return
    if (fallbackMembers) {
      members.forEach { it.draw(scope) }
      return
    }
    val sourceInput = when (val input = input) {
      is HazeInput.Sources -> input
      is HazeInput.Backdrop -> input.fallback.takeIf { backend.usesFallback }
      HazeInput.Content -> null
    }
    if (input is HazeInput.Backdrop && backend.usesFallback && sourceInput == null) return
    val drawable = entries.isNotEmpty()
    if (sourceInput != null && !drawable) {
      if (!sourceInput.retention.keepsLastFrame()) {
        retained?.clearRetainedOutput()
        renderedSource = false
        return
      }
      if (!renderedSource || retained?.shouldDrawRetainedOutput() == false) return
    }
    if (hooks?.shouldPrepareDraw(style) == false) return
    inSurface(scope) {
      val runtime = runtimeScope(this)
      val backdrop = preparedBackdrop
      if (input is HazeInput.Backdrop && !backend.usesFallback && backdrop != null) {
        var drawn = false
        try {
          val transform = backdrop.materialTransform
          scale(transform.scaleX, transform.scaleY, transform.pivot) {
            drawn = nativeRenderer?.let { it.configure(layerBounds, null, backdrop.getPlatformEffect(), backdrop.alpha) && it.draw(drawContext.canvas) } == true
          }
        } catch (exception: Exception) {
          HazeLogger.d("HazeEffectSurface", exception) { "Configured native draw failed" }
        }
        if (drawn) {
          backend.resolve(true)
          hooks?.let { with(it) { runtime.drawForeground(style) } }
        } else {
          usePortableInput(true, temporary = false)
        }
      } else if (input !is HazeInput.Backdrop || backend.usesFallback) {
        with(renderer) { runtime.draw(style) }
        hooks?.let { with(it) { runtime.drawForeground(style) } }
        if (drawable && sourceInput != null) renderedSource = true
      }
    }
    preparedBackdrop = null
  }

  override fun usePortableInput(failed: Boolean, temporary: Boolean) {
    if (input !is HazeInput.Backdrop) return
    val recoverable = temporary && !failed && (temporaryFallback || !backend.usesFallback)
    if (failed) backend.fail() else backend.resolve(false)
    temporaryFallback = recoverable
    nativeRenderer?.release()
    nativeRenderer = null
    members.forEach { it.usePortableInput(failed, temporary = recoverable) }
    preparedBackdrop = null
  }

  override fun useNativeInput() {
    if (!temporaryFallback) return
    temporaryFallback = false
    members.forEach { it.useNativeInput() }
    backend.attach(anchor.node.surfaceNativeEligible())
  }

  fun dispose() {
    if (disposed) return
    disposed = true
    anchor.node.unregisterConfiguredSurface(this)
    lifecycle?.detach()
    renderer.dispose()
    nativeRenderer?.release()
    nativeRenderer = null
    entries = emptyList()
    snapshot = null
  }
  override fun completeDraw() {
    members.forEach { it.node.completeSurfaceDraw() }
  }
  override fun onTrimMemory(level: TrimMemoryLevel) {
    renderer.onTrimMemory(level)
  }
}

internal fun unionSurfaceBounds(a: Rect, b: Rect): Rect = Rect(
  minOf(a.left, b.left),
  minOf(a.top, b.top),
  maxOf(a.right, b.right),
  maxOf(a.bottom, b.bottom),
)
