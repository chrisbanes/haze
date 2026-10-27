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
import dev.chrisbanes.haze.trace
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
  private class Slot(
    val handle: HazeConfiguredEffectSurface<GlassNodeConfiguration>,
    val runtime: GlassRuntimeEffect,
    val drawsMembersOnDecline: Boolean,
    var configuration: GlassNodeConfiguration,
    var bounds: Rect,
    var inputs: List<HazeEffectSurface>,
  )
  private val slots = mutableMapOf<List<Any>, Slot>()
  private var drawn = emptyList<Pair<Rect, GlassRuntimeEffect>>()
  private var reconciledModel: GlassClusterModel? = null
  private var cachedOutput = emptyList<HazeEffectSurface>()
  internal val runtimeSurfaces: List<GlassRuntimeEffect> get() = slots.values.map { it.runtime }
  internal val surfaceCount: Int get() = drawn.size
  internal val drawsMembersOnDeclineForTest: List<Boolean> get() = slots.values.map { it.drawsMembersOnDecline }

  fun reconcile(): List<HazeEffectSurface> = trace(GlassTraceSection.ContainerReconcile) {
    val model = owner.model
    if (model === reconciledModel) return@trace cachedOutput
    if (reconciledModel != null && reconciledModel === owner.placementPreviousModel &&
      owner.mergeDistancePx.isExactPixel() && slots.values.all {
        it.bounds.isExactPixels() && it.bounds.translate(owner.placementOffset).isExactPixels()
      }
    ) {
      val offset = owner.placementOffset
      slots.values.forEach { slot ->
        slot.bounds = slot.bounds.translate(offset)
        slot.handle.update(slot.configuration, slot.bounds, slot.inputs)
        slot.runtime.surfaceOrigin = slot.bounds.topLeft
      }
      drawn = drawn.map { (bounds, runtime) -> bounds.translate(offset) to runtime }
      reconciledModel = model
      return@trace cachedOutput
    }
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
      val opticalSize = model.opticalSize ?: continue
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
        // A lone fading ghost borrows its anchor's surface, which the anchor's own cluster already outputs.
        val drawsMembersOnDecline = members.any { it.identity is GlassMemberNode }
        val handle = inputs.first().createConfiguredSurface(
          HazeEffectFactory { runtime },
          configuration,
          bounds,
          inputs,
          drawsMembersOnDecline,
        )
        Slot(handle, runtime, drawsMembersOnDecline, configuration, bounds, inputs)
      }
      slot.handle.update(configuration, bounds, inputs)
      slot.configuration = configuration
      slot.bounds = bounds
      slot.inputs = inputs
      // The cluster draws in the surface translated by its origin, which moves with its members.
      slot.runtime.surfaceOrigin = bounds.topLeft
      output += slot.handle.surface
      runtimes += bounds to slot.runtime
    }
    drawn = runtimes
    cachedOutput = output
    // Group shapes can read state at their combined size only during this draw. Preserve those
    // observed reads and rebuild their fields even when the container model itself is unchanged.
    reconciledModel = model.takeIf { it.surfaces.all { cluster -> cluster.groups.all { unit -> unit.size == 1 } } }
    output
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
    reconciledModel = null
    cachedOutput = emptyList()
  }
  fun releaseMember(member: GlassMemberNode) {
    reconciledModel = null
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

  /**
   * The density [member] lays out at, which its own `dp` shape is measured in. A ghost uses its
   * anchor's. A node that detached since the last pass has none, so the container's stands in.
   */
  private fun densityOf(member: GlassMemberSnapshot): Density {
    val node = when (val identity = member.identity) {
      is GlassMemberNode -> identity
      is GlassMemberGhost -> identity.anchor
      else -> null
    }
    return node?.takeIf { it.isAttached }?.requireDensity() ?: density
  }

  override fun toMergedMember(origin: Offset): GlassMergedMember {
    val first = unit.first()
    val radii = first.shape.resolveCornerRadiiPx(layoutRect.size, densityOf(first), first.key.layoutDirection)
    // A retracting first member's ghost stays first, so its corners follow the retract towards the
    // shape of the member that remains, as its tint already does.
    val ghost = first.identity as? GlassMemberGhost
    val remaining = ghost?.let { unit.firstOrNull { member -> member.identity !is GlassMemberGhost } }
    val groupRadii = if (ghost != null && remaining != null) {
      lerpCornerRadii(
        from = remaining.shape.resolveCornerRadiiPx(layoutRect.size, densityOf(remaining), remaining.key.layoutDirection),
        to = radii,
        fraction = ghost.progress.value.coerceIn(0f, 1f),
      )
    } else {
      radii
    }
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
      cornerRadii = groupRadii * minOf(scaleX, scaleY),
      interaction = lighting?.interaction,
      lightPosition = position,
      lightSize = lighting?.member?.rect?.minDimension ?: 0f,
    )
  }
}

private fun lerpCornerRadii(from: CornerRadii, to: CornerRadii, fraction: Float): CornerRadii {
  fun lerp(start: Float, stop: Float) = start + (stop - start) * fraction
  return CornerRadii(
    lerp(from.topLeft, to.topLeft),
    lerp(from.topRight, to.topRight),
    lerp(from.bottomRight, to.bottomRight),
    lerp(from.bottomLeft, to.bottomLeft),
  )
}
