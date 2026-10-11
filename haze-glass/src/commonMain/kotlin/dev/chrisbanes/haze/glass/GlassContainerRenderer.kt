// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.HazeConfiguredEffectSurface
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.Poko
import kotlin.math.ceil

/** Evaluated values, never a Style block that could read the owner's context. */
@Poko
internal class GlassSurfaceSnapshot(
  val key: GlassMergeKey,
  val material: ResolvedGlassStyle,
  val field: GlassMergedField,
  val opticalSize: Size,
)

/** Retains one independent renderer per capped cluster; singleton shaders stay local. */
internal class GlassContainerRenderer(private val owner: GlassContainerNode) {
  private class Slot(val handle: HazeConfiguredEffectSurface<GlassNodeConfiguration>, val runtime: GlassRuntimeEffect)
  private val slots = mutableMapOf<List<Any>, Slot>()
  private var drawn = emptyList<Pair<Rect, GlassRuntimeEffect>>()
  internal val runtimeSurfaces: List<GlassRuntimeEffect> get() = slots.values.map { it.runtime }
  internal val surfaceCount: Int get() = drawn.size

  fun reconcile(): List<HazeEffectSurface> {
    val model = owner.model
    val spacing = owner.spacingPx
    val validKeys = model.surfaces.filter { it.members.size > 1 }.map { it.members.map { member -> member.identity } }.toSet()
    slots.keys.filter { it !in validKeys }.forEach { slots.remove(it)?.handle?.dispose() }
    val output = mutableListOf<HazeEffectSurface>()
    val runtimes = mutableListOf<Pair<Rect, GlassRuntimeEffect>>()
    for (cluster in model.surfaces) {
      val members = cluster.members
      val nodes = members.map { it.identity as GlassMemberNode }
      val inputs = nodes.mapNotNull { it.binding.surface }
      if (inputs.size != nodes.size) continue
      if (members.size == 1) {
        output += inputs.single()
        nodes.single().binding.runtime?.let { runtimes += members.single().layoutRect to it }
        continue
      }
      // Pressed members only shrink within their layout rects, so the surface keeps its bounds.
      val bounds = members.map { it.layoutRect }.reduce { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
      }.inflate(spacing)
      val opticalSize = checkNotNull(model.opticalSize)
      val field = GlassMergedField(
        members.map { it.toMergedMember(bounds.topLeft) },
        spacing,
        opticalSize,
      )
      val first = members.first()
      val configuration = GlassNodeConfiguration(
        style = GlassStyle,
        performanceMode = HazePerformanceMode.Fixed(first.key.qualityFraction),
        interactionSource = null,
        surfaceSnapshot = GlassSurfaceSnapshot(first.key, first.material, field, opticalSize),
      )
      val slot = slots.getOrPut(members.map { it.identity }) {
        val runtime = GlassRuntimeEffect().also { it.runtimeEffectFactory = checkNotNull(nodes.first().binding.runtime).runtimeEffectFactory }
        Slot(inputs.first().createConfiguredSurface(HazeEffectFactory { runtime }, configuration, bounds, inputs), runtime)
      }
      slot.handle.update(configuration, bounds, inputs)
      output += slot.handle.surface
      runtimes += bounds to slot.runtime
    }
    drawn = runtimes
    return output
  }

  /** Full-resolution transient native buffer plus actual plans of intersecting surfaces. */
  fun canComposeNativeSurfaces(bounds: Rect): Boolean {
    val size = IntSize(ceil(bounds.width).toInt(), ceil(bounds.height).toInt())
    if (!size.fitsGlassLayerBudget()) return false
    var total = size.width.toLong() * size.height.toLong()
    for ((material, runtime) in drawn) {
      val coordinates = runtime.preparedRender?.params?.coordinates ?: continue
      val capture = Rect(material.topLeft - coordinates.materialOrigin / coordinates.scaleFactor, coordinates.sampleSize / coordinates.scaleFactor)
      if (!capture.overlaps(bounds)) continue
      val pixels = (runtime.preparedRenderBudget as? GlassRenderBudgetDecision.Runtime)?.plan?.retainedPixelCountOrNull() ?: continue
      if (pixels > MAX_GLASS_RETAINED_PIXELS - total) return false
      total += pixels
    }
    return total <= MAX_GLASS_RETAINED_PIXELS
  }

  fun release() {
    slots.values.forEach { it.handle.dispose() }
    slots.clear()
    drawn = emptyList()
  }
  fun releaseMember(member: GlassMemberNode) {
    slots.keys.filter { member in it }.forEach { slots.remove(it)?.handle?.dispose() }
  }
}

private fun GlassMemberSnapshot.toMergedMember(origin: Offset): GlassMergedMember {
  val scale = minOf(rect.width / layoutRect.width, rect.height / layoutRect.height)
  return GlassMergedMember(rect.translate(-origin), material.cornerRadii * scale, material.tint, material.backgroundColor)
}
