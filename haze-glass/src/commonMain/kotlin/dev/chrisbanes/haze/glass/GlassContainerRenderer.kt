// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.HazeConfiguredEffectSurface
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectSurface
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.Poko
import kotlin.math.abs
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
    val mergeDistance = owner.mergeDistancePx
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
      // A Glass group draws as one member over its own bounds, which a press can grow past its members'.
      val draws = cluster.groups.map { unit ->
        if (unit.size > 1) GlassGroupDraw(unit, owner.requireDensity()) else GlassMemberDraw(unit.single())
      }
      val bounds = (members.flatMap { listOf(it.sampleLayoutRect, it.rect) } + draws.map { it.rect }).union().inflate(mergeDistance)
      val opticalSize = checkNotNull(model.opticalSize)
      val field = GlassMergedField(draws.map { it.toMergedMember(bounds.topLeft) }, mergeDistance, opticalSize)
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

/** One drawn unit of a cluster's field: a lone member, or a Glass group. */
private interface GlassFieldDraw {
  /** The unit's drawn rect, press transform included. */
  val rect: Rect
  fun toMergedMember(origin: Offset): GlassMergedMember
}

/** Maps member-local values through the member's draw-time interaction scale, as it draws alone. */
private class GlassMemberDraw(private val member: GlassMemberSnapshot) : GlassFieldDraw {
  override val rect get() = member.rect

  override fun toMergedMember(origin: Offset): GlassMergedMember = with(member) {
    val scaleX = rect.width / layoutRect.width
    val scaleY = rect.height / layoutRect.height
    // Ghosts carry no interaction.
    val runtime = (identity as? GlassMemberNode)?.binding?.runtime
    val interaction = runtime?.let { resolveGlassInteraction(it.currentInteractionState, it.interactionLightRadiusFraction) }
    val topLeft = rect.topLeft - origin
    glassMergedMember(
      origin = origin,
      rect = rect,
      sampleLayoutRect = sampleLayoutRect,
      material = material,
      cornerRadii = material.cornerRadii * minOf(scaleX, scaleY),
      interaction = interaction,
      lightPosition = interaction?.position?.takeIf { it.x.isFinite() && it.y.isFinite() }?.let { topLeft + Offset(it.x * scaleX, it.y * scaleY) },
      lightSize = rect.minDimension,
    )
  }
}

/** [lightPosition] is already in the field's frame; the optical response only applies when [interaction] has one. */
private fun glassMergedMember(
  origin: Offset,
  rect: Rect,
  sampleLayoutRect: Rect,
  material: ResolvedGlassStyle,
  cornerRadii: CornerRadii,
  interaction: ResolvedGlassInteraction?,
  lightPosition: Offset?,
  lightSize: Float,
): GlassMergedMember {
  val fieldRect = Rect(rect.topLeft - origin, rect.size)
  val refractionMultiplier = interaction?.refractionMultiplier ?: 1f
  val whitePointDelta = interaction?.whitePointDelta ?: 0f
  val hasOpticalResponse = refractionMultiplier != 1f || whitePointDelta != 0f
  val position = lightPosition ?: fieldRect.center
  val radius = (lightSize * (interaction?.radiusFraction ?: 0f)).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
  return GlassMergedMember(
    rect = fieldRect,
    layoutRect = sampleLayoutRect.translate(-origin),
    cornerRadii = cornerRadii,
    tint = material.tint,
    backgroundColor = material.backgroundColor,
    fallbackBaseColor = material.fallbackBaseColor,
    lightPosition = position,
    lightRadius = radius,
    lightIntensity = interaction?.lightingIntensity ?: 0f,
    opticalPosition = if (hasOpticalResponse) position else Offset.Zero,
    opticalRadius = if (hasOpticalResponse) radius else 0f,
    refractionMultiplier = refractionMultiplier,
    whitePointDelta = whitePointDelta,
  )
}

/**
 * A Glass group drawn as one member. It takes the first member's shape and appearance. The group
 * scales with the member whose transform deviates most from identity, so a member still settling
 * after a release never hides a newer press, about that member's pivot mapped into the group's
 * frame. Its light and optics come from that member too, else from the first member that has any.
 */
private class GlassGroupDraw(private val unit: List<GlassMemberSnapshot>, private val density: Density) : GlassFieldDraw {
  /** A live member's interaction, with its transform pivot mapped into the group's frame. */
  private class Response(val member: GlassMemberSnapshot, val interaction: ResolvedGlassInteraction, val transform: HazeEffectContentTransform?) {
    val deviation = transform?.let { maxOf(abs(1f - it.scaleX), abs(1f - it.scaleY)) } ?: 0f
    val hasLight = interaction.lightingIntensity != 0f || interaction.refractionMultiplier != 1f || interaction.whitePointDelta != 0f
  }

  private val layoutRect = unit.map { it.layoutRect }.union()
  private val sampleLayoutRect = unit.map { it.sampleLayoutRect }.union()
  private val responses = unit.mapNotNull { member ->
    val runtime = (member.identity as? GlassMemberNode)?.binding?.runtime ?: return@mapNotNull null
    val transform = runtime.currentInteractionTransform(member.layoutRect.size).takeUnless { it == HazeEffectContentTransform.Identity }
    val grouped = transform?.let {
      val pivot = if (runtime.interactionTransformPivot == GlassTransformPivot.Center) {
        Offset(layoutRect.width / 2f, layoutRect.height / 2f)
      } else {
        member.layoutRect.topLeft - layoutRect.topLeft + it.pivot
      }
      HazeEffectContentTransform(it.scaleX, it.scaleY, pivot)
    }
    Response(member, resolveGlassInteraction(runtime.currentInteractionState, runtime.interactionLightRadiusFraction), grouped)
  }
  private val scaling = responses.filter { it.transform != null }.maxByOrNull { it.deviation }
  private val lighting = scaling?.takeIf { it.hasLight } ?: responses.firstOrNull { it.hasLight }

  // Without a transform every member draws at its layout rect, so the union of those is the group.
  override val rect = scaling?.transform?.let { layoutRect.transformedBy(it) } ?: layoutRect

  override fun toMergedMember(origin: Offset): GlassMergedMember {
    val first = unit.first()
    val scaleX = rect.width / layoutRect.width
    val scaleY = rect.height / layoutRect.height
    val topLeft = rect.topLeft - origin
    // The lit member's position maps into the group frame, then through the group's scale.
    val position = lighting?.interaction?.position?.takeIf { it.x.isFinite() && it.y.isFinite() }?.let {
      val offset = checkNotNull(lighting).member.layoutRect.topLeft - layoutRect.topLeft
      topLeft + Offset((offset.x + it.x) * scaleX, (offset.y + it.y) * scaleY)
    }
    return glassMergedMember(
      origin = origin,
      rect = rect,
      sampleLayoutRect = sampleLayoutRect,
      material = first.material,
      cornerRadii = first.shape.resolveCornerRadiiPx(layoutRect.size, density, first.key.layoutDirection) * minOf(scaleX, scaleY),
      interaction = lighting?.interaction,
      lightPosition = position,
      lightSize = lighting?.member?.rect?.minDimension ?: 0f,
    )
  }
}
