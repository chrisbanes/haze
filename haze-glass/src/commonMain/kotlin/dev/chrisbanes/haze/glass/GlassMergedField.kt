// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.RuntimeShaderUniformProvider

internal const val MAX_GLASS_MERGED_MEMBERS = 4

/**
 * One rounded-rectangle member of a [GlassMergedField], in material-local pixels, with its own
 * interaction lighting and optical response.
 */
@Poko
internal class GlassMergedMember(
  val rect: Rect,
  val cornerRadii: CornerRadii,
  val tint: Color,
  val backgroundColor: Color,
  val lightPosition: Offset = Offset.Zero,
  val lightRadius: Float = 0f,
  val lightIntensity: Float = 0f,
  val opticalPosition: Offset = Offset.Zero,
  val opticalRadius: Float = 0f,
  val refractionMultiplier: Float = 1f,
  val whitePointDelta: Float = 0f,
) {
  init {
    require(
      rect.left.isFinite() && rect.top.isFinite() &&
        rect.width.isFinite() && rect.height.isFinite() &&
        rect.width > 0f && rect.height > 0f,
    ) { "rect must be finite with a positive size: $rect" }
    require(cornerRadii.isFiniteAndNonNegative()) { "cornerRadii must be finite and non-negative" }
    require(lightPosition.x.isFinite() && lightPosition.y.isFinite()) { "lightPosition must be finite" }
    require(lightRadius.isFinite() && lightRadius >= 0f) { "lightRadius must be finite and non-negative" }
    require(lightIntensity.isFinite() && lightIntensity >= 0f) { "lightIntensity must be finite and non-negative" }
    require(opticalPosition.x.isFinite() && opticalPosition.y.isFinite()) { "opticalPosition must be finite" }
    require(opticalRadius.isFinite() && opticalRadius >= 0f) { "opticalRadius must be finite and non-negative" }
    require(refractionMultiplier.isFinite() && refractionMultiplier in 0f..2f) { "refractionMultiplier must be finite and in [0, 2]" }
    require(whitePointDelta.isFinite() && whitePointDelta in -1f..1f) { "whitePointDelta must be finite and in [-1, 1]" }
  }

  val hasLighting: Boolean get() = lightIntensity > 0f && lightRadius > 0f
}

/**
 * Up to [MAX_GLASS_MERGED_MEMBERS] members drawn as one Glass surface (ADR-0013).
 *
 * The shaders join members with a polynomial smooth minimum whose radius is [spacingPx], so
 * members bridge while their gap is under half the spacing. Optics are resolved for
 * [opticalSize] rather than the layer size.
 */
@Poko
internal class GlassMergedField(
  val members: List<GlassMergedMember>,
  val spacingPx: Float,
  val opticalSize: Size,
) {
  init {
    require(members.size in 1..MAX_GLASS_MERGED_MEMBERS) {
      "A merged field needs 1 to $MAX_GLASS_MERGED_MEMBERS members, was ${members.size}"
    }
    require(spacingPx.isFinite() && spacingPx >= 0f) { "spacingPx must be finite and non-negative" }
    require(
      opticalSize.width.isFinite() && opticalSize.height.isFinite() &&
        opticalSize.width >= 0f && opticalSize.height >= 0f,
    ) { "opticalSize must be finite and non-negative" }
  }

  val isBackgroundOpaque: Boolean get() = members.all { it.backgroundColor.alpha == 1f }

  val hasBackground: Boolean get() = members.any { it.backgroundColor.alpha > 0f }

  val hasInteractionLighting: Boolean get() = members.any { it.hasLighting }

  /**
   * This field with idle member lights. Optical values remain because optical stages consume them;
   * shape/colour-only stages use [withoutInteractions].
   */
  val withoutLights: GlassMergedField by lazy(LazyThreadSafetyMode.NONE) {
    if (members.all { it.lightPosition == Offset.Zero && it.lightRadius == 0f && it.lightIntensity == 0f }) {
      this
    } else {
      GlassMergedField(
        members.map {
          GlassMergedMember(
            it.rect,
            it.cornerRadii,
            it.tint,
            it.backgroundColor,
            opticalPosition = it.opticalPosition,
            opticalRadius = it.opticalRadius,
            refractionMultiplier = it.refractionMultiplier,
            whitePointDelta = it.whitePointDelta,
          )
        },
        spacingPx,
        opticalSize,
      )
    }
  }

  /** Shape/colour projection for stages that must ignore both interaction channels. */
  val withoutInteractions: GlassMergedField by lazy(LazyThreadSafetyMode.NONE) {
    if (members.all {
        it.lightPosition == Offset.Zero && it.lightRadius == 0f && it.lightIntensity == 0f &&
          it.opticalPosition == Offset.Zero && it.opticalRadius == 0f &&
          it.refractionMultiplier == 1f && it.whitePointDelta == 0f
      }
    ) {
      this
    } else {
      GlassMergedField(members.map { GlassMergedMember(it.rect, it.cornerRadii, it.tint, it.backgroundColor) }, spacingPx, opticalSize)
    }
  }

  /**
   * The smoothing radius for each member, which the shader applies as
   * `d = smin(d, d_i, k_i)` in index order. Member 0 starts the field, so its value is unused.
   *
   * The smooth minimum lowers the field by up to `k / 4` where members are equally near, so
   * overlapping members would grow past their union. Each member keeps the full spacing while it
   * is apart from every earlier member, and fades to zero as its deepest overlap with one of them
   * reaches that pair's smaller shortest side. Fading per member keeps one overlapping pair from
   * removing an unrelated member's bridge.
   */
  fun memberSmoothingPx(): FloatArray = FloatArray(members.size) { index ->
    val rect = members[index].rect
    var overlap = 0f
    for (earlier in 0 until index) {
      val other = members[earlier].rect
      val separation = maxOf(
        maxOf(rect.left - other.right, other.left - rect.right),
        maxOf(rect.top - other.bottom, other.top - rect.bottom),
      )
      if (separation < 0f) {
        val fullOverlap = minOf(rect.minDimension, other.minDimension)
        overlap = maxOf(overlap, (-separation / fullOverlap).coerceIn(0f, 1f))
      }
    }
    spacingPx * (1f - overlap * overlap * (3f - 2f * overlap))
  }

  fun scaled(scaleFactor: Float): GlassMergedField = GlassMergedField(
    members = members.map { member ->
      GlassMergedMember(
        rect = member.rect.let {
          Rect(it.left * scaleFactor, it.top * scaleFactor, it.right * scaleFactor, it.bottom * scaleFactor)
        },
        cornerRadii = member.cornerRadii * scaleFactor,
        tint = member.tint,
        backgroundColor = member.backgroundColor,
        lightPosition = member.lightPosition * scaleFactor,
        lightRadius = member.lightRadius * scaleFactor,
        lightIntensity = member.lightIntensity,
        opticalPosition = member.opticalPosition * scaleFactor,
        opticalRadius = member.opticalRadius * scaleFactor,
        refractionMultiplier = member.refractionMultiplier,
        whitePointDelta = member.whitePointDelta,
      )
    },
    spacingPx = spacingPx * scaleFactor,
    opticalSize = opticalSize * scaleFactor,
  )
}

/** Only the merged shader variants declare these uniforms, so set them only for a merged field. */
internal fun RuntimeShaderUniformProvider.setMergedFieldUniforms(
  field: GlassMergedField,
  includeTint: Boolean = false,
  includeBackground: Boolean = false,
  includeLighting: Boolean = false,
  includeOptics: Boolean = false,
) {
  val rects = FloatArray(MAX_GLASS_MERGED_MEMBERS * 4)
  val radii = FloatArray(MAX_GLASS_MERGED_MEMBERS * 4)
  field.members.forEachIndexed { index, member ->
    val offset = index * 4
    rects[offset] = member.rect.left
    rects[offset + 1] = member.rect.top
    rects[offset + 2] = member.rect.width
    rects[offset + 3] = member.rect.height
    radii[offset] = member.cornerRadii.topLeft
    radii[offset + 1] = member.cornerRadii.topRight
    radii[offset + 2] = member.cornerRadii.bottomRight
    radii[offset + 3] = member.cornerRadii.bottomLeft
  }
  setFloatUniform("memberRects", rects)
  setFloatUniform("memberRadii", radii)
  setFloatUniform("memberSmoothing", field.memberSmoothingPx().copyOf(MAX_GLASS_MERGED_MEMBERS))
  setFloatUniform("memberCount", field.members.size.toFloat())
  if (includeLighting) {
    val positions = FloatArray(MAX_GLASS_MERGED_MEMBERS * 2)
    val lightRadii = FloatArray(MAX_GLASS_MERGED_MEMBERS)
    val intensities = FloatArray(MAX_GLASS_MERGED_MEMBERS)
    field.members.forEachIndexed { index, member ->
      positions[index * 2] = member.lightPosition.x
      positions[index * 2 + 1] = member.lightPosition.y
      lightRadii[index] = member.lightRadius
      intensities[index] = member.lightIntensity
    }
    setFloatUniform("memberLightPositions", positions)
    setFloatUniform("memberLightRadii", lightRadii)
    setFloatUniform("memberLightIntensities", intensities)
  }
  if (includeOptics) {
    val positions = FloatArray(MAX_GLASS_MERGED_MEMBERS * 2)
    val opticalRadii = FloatArray(MAX_GLASS_MERGED_MEMBERS)
    val multipliers = FloatArray(MAX_GLASS_MERGED_MEMBERS) { 1f }
    val whitePointDeltas = FloatArray(MAX_GLASS_MERGED_MEMBERS)
    field.members.forEachIndexed { index, member ->
      positions[index * 2] = member.opticalPosition.x
      positions[index * 2 + 1] = member.opticalPosition.y
      opticalRadii[index] = member.opticalRadius
      multipliers[index] = member.refractionMultiplier
      whitePointDeltas[index] = member.whitePointDelta
    }
    setFloatUniform("memberOpticalPositions", positions)
    setFloatUniform("memberOpticalRadii", opticalRadii)
    setFloatUniform("memberRefractionMultipliers", multipliers)
    setFloatUniform("memberWhitePointDeltas", whitePointDeltas)
  }
  for (index in 0 until MAX_GLASS_MERGED_MEMBERS) {
    val member = field.members.getOrNull(index)
    if (includeTint) setColorUniform("memberTint$index", member?.tint ?: Color.Transparent)
    if (includeBackground) {
      setColorUniform("memberBackground$index", member?.backgroundColor ?: Color.Transparent)
    }
  }
}
