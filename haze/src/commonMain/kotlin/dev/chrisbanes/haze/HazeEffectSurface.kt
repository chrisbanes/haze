// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toIntSize

/** Built-in-only owner which relocates material output behind its recorded children. */
@InternalHazeApi
public interface HazeEffectSurfaceOwner {
  /** Current owner coordinates; relocation cannot draw until they are attached. */
  public val surfaceCoordinates: LayoutCoordinates?

  /** Called when geometry, input, or renderer state changes. */
  public fun onSurfaceChanged(surface: HazeEffectSurface)

  /** Called when the member releases this owner or detaches. */
  public fun onSurfaceReleased(surface: HazeEffectSurface)

  /** Whether a temporary native interference buffer fits this owner's rendering budget. */
  public fun canComposeNativeSurfaces(bounds: Rect): Boolean
}

/** Opaque core-owned surface. Source selection, capture, retention and native policy stay in core. */
@InternalHazeApi
public class HazeEffectSurface internal constructor(
  internal val node: HazeEffectNode,
  private val configured: HazeConfiguredSurfaceDraw? = null,
) {
  internal var owner: HazeEffectSurfaceOwner? = null
    private set

  /** Relocates only material output; replacing/removing the owner restores ordinary member draw. */
  public fun relocateTo(owner: HazeEffectSurfaceOwner?) {
    if (this.owner === owner) return
    this.owner?.onSurfaceReleased(this)
    this.owner = owner
    if (owner == null) useNativeInput()
    node.invalidateVisualEffectDraw()
    owner?.onSurfaceChanged(this)
  }

  /** Member bounds in the owner's coordinates, or null before valid placement. */
  public fun boundsInOwner(): Rect? = configured?.bounds ?: node.surfaceBounds(owner?.surfaceCoordinates)

  /**
   * The source areas this member's input currently resolves to. Equal inputs can still resolve
   * differently, for example `Behind` under different source ancestors.
   */
  public fun inputSelection(): Set<Any> = node.areas.toSet()

  /**
   * Creates an independently owned renderer over these members' core-selected input captures.
   *
   * When a Backdrop cluster declines its native frame, its members' own surfaces draw in its place.
   * Pass false for [drawsMembersOnDecline] when the owner already outputs those surfaces, so the
   * cluster draws nothing instead of drawing them a second time.
   */
  public fun <Style> createConfiguredSurface(
    factory: HazeEffectFactory<Style>,
    style: Style,
    bounds: Rect,
    members: List<HazeEffectSurface>,
    drawsMembersOnDecline: Boolean = true,
  ): HazeConfiguredEffectSurface<Style> =
    HazeConfiguredEffectSurface(this, factory, style, bounds, members, drawsMembersOnDecline)

  internal fun changed() = owner?.onSurfaceChanged(this)
  internal fun release() = relocateTo(null)
  internal fun footprint(scope: DrawScope): HazeSurfaceFootprint? =
    if (configured != null) configured.footprint(scope, owner) else node.surfaceFootprint(scope, owner?.surfaceCoordinates)
  internal fun draw(scope: DrawScope) {
    if (configured != null) configured.draw(scope, owner) else node.drawRelocatedSurface(scope, owner?.surfaceCoordinates)
  }
  internal fun completeDraw() {
    if (configured != null) configured.completeDraw() else node.completeSurfaceDraw()
  }
  internal fun usePortableInput(failed: Boolean = false, temporary: Boolean = true) {
    if (configured != null) configured.usePortableInput(failed, temporary) else node.usePortableSurfaceInput(failed, temporary)
  }
  internal fun useNativeInput() {
    if (configured != null) configured.useNativeInput() else node.restoreSurfaceInput()
  }
}

internal class HazeSurfaceFootprint(val output: Rect, val nativeCapture: Rect?, val needsIsolation: Boolean = false)

/**
 * Records children once, draws material output, then replays children once above every surface.
 * Interference components include all overlapping output and every output/native-capture overlap.
 * Only components requiring native isolation acquire a temporary, tightly bounded buffer.
 */
@InternalHazeApi
public fun ContentDrawScope.drawHazeEffectSurfaces(
  owner: HazeEffectSurfaceOwner,
  surfaces: List<HazeEffectSurface>,
  children: GraphicsLayer,
) {
  drawHazeEffectSurfaces(owner, { surfaces }, children)
}

/** Resolves surface topology after child recording has refreshed member captures and snapshots. */
@InternalHazeApi
public fun ContentDrawScope.drawHazeEffectSurfaces(
  owner: HazeEffectSurfaceOwner,
  surfaces: () -> List<HazeEffectSurface>,
  children: GraphicsLayer,
) {
  children.record(size = drawContext.size.toIntSize()) {
    this@drawHazeEffectSurfaces.drawContent()
  }
  val current = surfaces().filter { it.owner === owner }
  val footprints = current.map { it.footprint(this) }
  val remaining = current.indices.filter { footprints[it] != null }.toMutableList()
  val components = mutableListOf<List<Int>>()
  while (remaining.isNotEmpty()) {
    val component = mutableListOf(remaining.removeAt(0))
    var cursor = 0
    while (cursor < component.size) {
      val a = checkNotNull(footprints[component[cursor++]])
      val iterator = remaining.iterator()
      while (iterator.hasNext()) {
        val index = iterator.next()
        val b = checkNotNull(footprints[index])
        if (
          a.output.overlaps(b.output) ||
          a.nativeCapture?.overlaps(b.output) == true ||
          b.nativeCapture?.overlaps(a.output) == true
        ) {
          component += index
          iterator.remove()
        }
      }
    }
    components += component.sorted()
  }
  for (component in components) {
    val isolate = component.any { footprints[it]?.needsIsolation == true } ||
      (component.size > 1 && component.any { footprints[it]?.nativeCapture != null })
    val bounds = component.map { checkNotNull(footprints[it]).output }.reduce { a, b ->
      Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }
    if (isolate && !owner.canComposeNativeSurfaces(bounds)) {
      component.forEach { current[it].usePortableInput() }
      component.forEach { current[it].draw(this) }
    } else if (isolate) {
      component.forEach { current[it].useNativeInput() }
      val canvas = drawContext.canvas
      val saved = try {
        canvas.saveLayer(bounds, Paint())
        true
      } catch (exception: Exception) {
        HazeLogger.d("HazeEffectSurface", exception) { "Native surface composition failed" }
        component.forEach { current[it].usePortableInput(failed = true) }
        false
      }
      try {
        component.forEach { current[it].draw(this) }
      } finally {
        if (saved) canvas.restore()
      }
    } else {
      component.forEach { current[it].useNativeInput() }
      component.forEach { current[it].draw(this) }
    }
  }
  current.forEach { it.completeDraw() }
  drawLayer(children)
}
