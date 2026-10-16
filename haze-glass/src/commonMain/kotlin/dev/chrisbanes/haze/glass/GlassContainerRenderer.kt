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
  val maxRefractionMultiplier: Float = 1f,
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
    val validKeys = model.surfaces.filterNot { it.drawsOwnSurface() }.map { it.slotKey() }.toSet()
    slots.keys.filter { it !in validKeys }.forEach { slots.remove(it)?.handle?.dispose() }
    val output = mutableListOf<HazeEffectSurface>()
    val runtimes = mutableListOf<Pair<Rect, GlassRuntimeEffect>>()
    for (cluster in model.surfaces) {
      val members = cluster.members
      val nodes = cluster.drawingNodes()
      val inputs = nodes.mapNotNull { it.binding.surface }
      if (inputs.size != nodes.size) continue
      if (cluster.drawsOwnSurface()) {
        output += inputs.single()
        nodes.single().binding.runtime?.let { runtimes += members.single().layoutRect to it }
        continue
      }
      // Pressed members shrink within their layout rects, keeping the surface stable; a spring
      // overshoot past scale 1 briefly grows the bounds instead of clipping the member.
      val bounds = members.flatMap { listOf(it.layoutRect, it.rect) }.reduce { a, b ->
        Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
      }.inflate(spacing)
      val opticalSize = checkNotNull(model.opticalSize)
      val field = GlassMergedField(
        members.map { it.toMergedMember(bounds.topLeft) },
        spacing,
        opticalSize,
      )
      val maxRefractionMultiplier = maxOf(
        members.maxOfOrNull { member ->
          (member.identity as? GlassMemberNode)?.binding?.maxRefractionMultiplier ?: 1f
        } ?: 1f,
        field.members.maxOf { it.refractionMultiplier },
      )
      val first = members.first()
      val configuration = GlassNodeConfiguration(
        style = GlassStyle,
        performanceMode = HazePerformanceMode.Fixed(first.key.qualityFraction),
        interactionSource = null,
        surfaceSnapshot = GlassSurfaceSnapshot(
          first.key,
          first.material,
          field,
          opticalSize,
          maxRefractionMultiplier,
        ),
      )
      val slot = slots.getOrPut(cluster.slotKey()) {
        val runtime = GlassRuntimeEffect().also {
          it.runtimeEffectFactory = checkNotNull(nodes.first().binding.runtime).runtimeEffectFactory
          it.surfaceOwner = owner
        }
        Slot(inputs.first().createConfiguredSurface(HazeEffectFactory { runtime }, configuration, bounds, inputs), runtime)
      }
      slot.handle.update(configuration, bounds, inputs)
      // The cluster draws in the surface translated by its origin, which moves with its members.
      slot.runtime.surfaceOrigin = bounds.topLeft
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

/** A lone live member draws through its own surface unless it is transitioning with another. */
private fun GlassCluster.drawsOwnSurface(): Boolean =
  members.size == 1 && members.single().identity is GlassMemberNode && members.single().link == null

/** Live members whose inputs draw the cluster; a lone fading ghost draws through its borrowed anchor. */
private fun GlassCluster.drawingNodes(): List<GlassMemberNode> =
  members.mapNotNull { it.identity as? GlassMemberNode }.ifEmpty { listOf((members.first().identity as GlassMemberGhost).anchor) }

/** A borrowed anchor is part of the key, so its departure disposes the slot. */
private fun GlassCluster.slotKey(): List<Any> = (members.map { it.identity } + drawingNodes()).distinct()

/** Maps member-local values through the member's draw-time interaction scale, as it draws alone. */
private fun GlassMemberSnapshot.toMergedMember(origin: Offset): GlassMergedMember {
  val scaleX = rect.width / layoutRect.width
  val scaleY = rect.height / layoutRect.height
  // Ghosts carry no interaction.
  val runtime = (identity as? GlassMemberNode)?.binding?.runtime
  val interaction = runtime?.let { resolveGlassInteraction(it.currentInteractionState, it.interactionLightRadiusFraction) }
  val topLeft = rect.topLeft - origin
  val fieldRect = Rect(topLeft, rect.size)
  val refractionMultiplier = interaction?.refractionMultiplier ?: 1f
  val whitePointDelta = interaction?.whitePointDelta ?: 0f
  val hasOpticalResponse = refractionMultiplier != 1f || whitePointDelta != 0f
  return GlassMergedMember(
    rect = fieldRect,
    cornerRadii = material.cornerRadii * minOf(scaleX, scaleY),
    tint = material.tint,
    backgroundColor = material.backgroundColor,
    fallbackBaseColor = material.fallbackBaseColor,
    lightPosition = interaction?.position?.takeIf { it.x.isFinite() && it.y.isFinite() }?.let { topLeft + Offset(it.x * scaleX, it.y * scaleY) } ?: fieldRect.center,
    lightRadius = (rect.minDimension * (interaction?.radiusFraction ?: 0f)).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
    lightIntensity = interaction?.lightingIntensity ?: 0f,
    opticalPosition = if (hasOpticalResponse) {
      interaction?.position?.takeIf { it.x.isFinite() && it.y.isFinite() }
        ?.let { topLeft + Offset(it.x * scaleX, it.y * scaleY) } ?: fieldRect.center
    } else {
      Offset.Zero
    },
    opticalRadius = if (hasOpticalResponse) {
      (rect.minDimension * (interaction?.radiusFraction ?: 0f)).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    } else {
      0f
    },
    refractionMultiplier = refractionMultiplier,
    whitePointDelta = whitePointDelta,
  )
}
