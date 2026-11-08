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
internal data class GlassMergedMember(
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
  val fallbackBaseColor: Color? = null,
  val fallbackColor: Color = Color.Transparent,
  /** The unpressed layout rect, in [rect]'s frame, that the member's press transform maps to [rect]. */
  val layoutRect: Rect = rect,
) {
  init {
    require(
      rect.left.isFinite() && rect.top.isFinite() &&
        rect.width.isFinite() && rect.height.isFinite() &&
        rect.width > 0f && rect.height > 0f,
    ) { "rect must be finite with a positive size: $rect" }
    require(
      layoutRect.left.isFinite() && layoutRect.top.isFinite() &&
        layoutRect.width.isFinite() && layoutRect.height.isFinite() &&
        layoutRect.width > 0f && layoutRect.height > 0f,
    ) { "layoutRect must be finite with a positive size: $layoutRect" }
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

/** Whether any member is smoothed, which selects the continuous shader variant. */
internal val GlassMergedField.isContinuous: Boolean
  get() = members.any { it.cornerRadii.isContinuous }

/**
 * Whether shaders for these corners use the continuous variant. A merged field decides alone, since
 * merged shaders draw its members, so a field whose smoothing [withExactCorners] removed keeps the
 * exact circular variant.
 */
internal fun continuousCorners(cornerRadii: CornerRadii, mergedField: GlassMergedField?): Boolean =
  mergedField?.isContinuous ?: cornerRadii.isContinuous

private const val EXTENT_ROUNDING_SLACK_PX = 0.01f

/**
 * Removes every member's smoothing when the field has a smoothed member and any member whose corner
 * boxes can overlap, in either frame the shader evaluates. The continuous shaders evaluate one
 * corner per point, which is exact only for disjoint corner boxes; the circular shaders are exact
 * everywhere.
 */
internal fun List<GlassMergedMember>.withExactCorners(): List<GlassMergedMember> {
  if (none { it.cornerRadii.isContinuous }) return this
  val disjoint = all { member ->
    // The shader evaluates corner extents, which equal the radii for circular corners. Fitted
    // extents can fill an edge exactly, so allow float rounding.
    val extents = member.cornerRadii.extents()
    val scale = minOf(member.rect.width / member.layoutRect.width, member.rect.height / member.layoutRect.height)
    extents.isRadiusDisjoint(member.rect.size, EXTENT_ROUNDING_SLACK_PX) &&
      (extents * (1f / scale)).isRadiusDisjoint(member.layoutRect.size, EXTENT_ROUNDING_SLACK_PX)
  }
  return if (disjoint) this else map { it.copy(cornerRadii = it.cornerRadii.withoutSmoothing()) }
}

/**
 * Up to [MAX_GLASS_MERGED_MEMBERS] members drawn as one Glass surface (ADR-0013).
 *
 * The shaders join members with a polynomial smooth minimum whose radius is [mergeDistancePx], so
 * members bridge while their gap is under half the merge distance. Optics are resolved for
 * [opticalSize] rather than the layer size.
 */
@Poko
internal class GlassMergedField(
  val members: List<GlassMergedMember>,
  val mergeDistancePx: Float,
  val opticalSize: Size,
) {
  init {
    require(members.size in 1..MAX_GLASS_MERGED_MEMBERS) {
      "A merged field needs 1 to $MAX_GLASS_MERGED_MEMBERS members, was ${members.size}"
    }
    requireFiniteNonNegative("mergeDistancePx", mergeDistancePx)
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
          it.copy(lightPosition = Offset.Zero, lightRadius = 0f, lightIntensity = 0f)
        },
        mergeDistancePx,
        opticalSize,
      )
    }
  }

  /** Lighting-only stages keep each member's light and geometry while ignoring optical state. */
  val withoutOptics: GlassMergedField by lazy(LazyThreadSafetyMode.NONE) {
    if (members.all {
        it.opticalPosition == Offset.Zero && it.opticalRadius == 0f &&
          it.refractionMultiplier == 1f && it.whitePointDelta == 0f
      }
    ) {
      this
    } else {
      GlassMergedField(
        members.map {
          it.copy(
            opticalPosition = Offset.Zero,
            opticalRadius = 0f,
            refractionMultiplier = 1f,
            whitePointDelta = 0f,
          )
        },
        mergeDistancePx,
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
      GlassMergedField(
        members.map {
          it.copy(
            lightPosition = Offset.Zero,
            lightRadius = 0f,
            lightIntensity = 0f,
            opticalPosition = Offset.Zero,
            opticalRadius = 0f,
            refractionMultiplier = 1f,
            whitePointDelta = 0f,
          )
        },
        mergeDistancePx,
        opticalSize,
      )
    }
  }

  /** Fallback policy is material metadata, never an input to the full renderer or its keys. */
  val withoutFallbackBase: GlassMergedField by lazy(LazyThreadSafetyMode.NONE) {
    if (members.all { it.fallbackBaseColor == null && it.fallbackColor == Color.Transparent }) {
      this
    } else {
      GlassMergedField(
        members.map {
          it.copy(fallbackBaseColor = null, fallbackColor = Color.Transparent)
        },
        mergeDistancePx,
        opticalSize,
      )
    }
  }

  /**
   * The smoothing radius for each member, which the shader applies as
   * `d = smin(d, d_i, k_i)` in index order. Member 0 starts the field, so its value is unused.
   *
   * The smooth minimum lowers the field by up to `k / 4` where members are equally near, so
   * overlapping members would grow past their union. Each member keeps the full merge distance
   * while it is apart from every earlier member, and fades to zero as its deepest overlap with one
   * of them reaches that pair's smaller shortest side. Fading per member keeps one overlapping pair
   * from removing an unrelated member's bridge.
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
    mergeDistancePx * (1f - overlap * overlap * (3f - 2f * overlap))
  }

  /** Optical blend weight for each member, based only on its nearest other member in this field. */
  fun memberStandaloneWeights(): FloatArray = FloatArray(members.size) { index ->
    if (members.size == 1) {
      1f
    } else if (mergeDistancePx == 0f) {
      0f
    } else {
      val rect = members[index].rect
      var nearestGap = Float.POSITIVE_INFINITY
      for (otherIndex in members.indices) {
        if (otherIndex != index) {
          nearestGap = minOf(nearestGap, glassMemberGap(rect, members[otherIndex].rect))
        }
      }
      val t = ((nearestGap - mergeDistancePx / 2f) / (mergeDistancePx / 2f)).coerceIn(0f, 1f)
      t * t * t * (t * (t * 6f - 15f) + 10f)
    }
  }

  fun scaled(scaleFactor: Float): GlassMergedField = GlassMergedField(
    members = members.map { member ->
      member.copy(
        rect = member.rect.scaledBy(scaleFactor),
        cornerRadii = member.cornerRadii * scaleFactor,
        lightPosition = member.lightPosition * scaleFactor,
        lightRadius = member.lightRadius * scaleFactor,
        opticalPosition = member.opticalPosition * scaleFactor,
        opticalRadius = member.opticalRadius * scaleFactor,
        layoutRect = member.layoutRect.scaledBy(scaleFactor),
      )
    },
    mergeDistancePx = mergeDistancePx * scaleFactor,
    opticalSize = opticalSize * scaleFactor,
  )
}

/**
 * Arrays that [setMergedFieldUniforms] fills and uploads, kept so that each call does not allocate
 * them again. Every [RuntimeShaderUniformProvider] copies an array when it is set, so a later call
 * can overwrite these. Hold one per renderer, since a call overwrites the previous call's values.
 */
internal class MergedFieldUniformScratch {
  private val arrays = arrayOfNulls<FloatArray>(SLOT_COUNT)

  /** The array in [slot], created at [size] on first use and reset to [initial] on every call. */
  fun floats(slot: Int, size: Int, initial: Float = 0f): FloatArray {
    val array = arrays[slot] ?: FloatArray(size).also { arrays[slot] = it }
    array.fill(initial)
    return array
  }

  /** A copy of [values] padded with zeros to [size], without allocating. */
  fun padded(slot: Int, values: FloatArray, size: Int): FloatArray =
    floats(slot, size).also { values.copyInto(it, endIndex = minOf(values.size, size)) }

  companion object {
    const val RECTS = 0
    const val LAYOUT_RECTS = 1
    const val RADII = 2
    const val SMOOTHING = 3
    const val LIGHT_POSITIONS = 4
    const val LIGHT_RADII = 5
    const val LIGHT_INTENSITIES = 6
    const val OPTICAL_POSITIONS = 7
    const val OPTICAL_RADII = 8
    const val MULTIPLIERS = 9
    const val WHITE_POINT_DELTAS = 10
    const val STANDALONE_WEIGHTS = 11
    const val CORNER_EXPONENTS = 12
    private const val SLOT_COUNT = 13
  }
}

/**
 * Only the merged shader variants declare these uniforms, so set them only for a merged field.
 *
 * [scratch] holds the arrays uploaded; pass one kept per renderer to avoid allocating them on each call.
 */
internal fun RuntimeShaderUniformProvider.setMergedFieldUniforms(
  field: GlassMergedField,
  includeTint: Boolean = false,
  includeBackground: Boolean = false,
  includeLighting: Boolean = false,
  includeOptics: Boolean = false,
  scratch: MergedFieldUniformScratch = MergedFieldUniformScratch(),
) {
  val rects = scratch.floats(MergedFieldUniformScratch.RECTS, MAX_GLASS_MERGED_MEMBERS * 4)
  val layoutRects = scratch.floats(MergedFieldUniformScratch.LAYOUT_RECTS, MAX_GLASS_MERGED_MEMBERS * 4)
  val radii = scratch.floats(MergedFieldUniformScratch.RADII, MAX_GLASS_MERGED_MEMBERS * 4)
  field.members.forEachIndexed { index, member ->
    val offset = index * 4
    rects[offset] = member.rect.left
    rects[offset + 1] = member.rect.top
    rects[offset + 2] = member.rect.width
    rects[offset + 3] = member.rect.height
    layoutRects[offset] = member.layoutRect.left
    layoutRects[offset + 1] = member.layoutRect.top
    layoutRects[offset + 2] = member.layoutRect.width
    layoutRects[offset + 3] = member.layoutRect.height
    // A circular field has no smoothing, so its extents are its radii.
    val corners = member.cornerRadii.extents()
    radii[offset] = corners.topLeft
    radii[offset + 1] = corners.topRight
    radii[offset + 2] = corners.bottomRight
    radii[offset + 3] = corners.bottomLeft
  }
  setFloatUniform("memberRects", rects)
  setFloatUniform("memberLayoutRects", layoutRects)
  setFloatUniform("memberRadii", radii)
  // Only the continuous variant declares the exponents, and Android rejects undeclared uniforms.
  if (field.isContinuous) {
    // Unused slots keep circular exponents.
    val exponents = scratch.floats(MergedFieldUniformScratch.CORNER_EXPONENTS, MAX_GLASS_MERGED_MEMBERS * 4, initial = 2f)
    field.members.forEachIndexed { index, member ->
      val offset = index * 4
      val corners = member.cornerRadii
      exponents[offset] = continuousCornerExponent(corners.topLeftSmoothing)
      exponents[offset + 1] = continuousCornerExponent(corners.topRightSmoothing)
      exponents[offset + 2] = continuousCornerExponent(corners.bottomRightSmoothing)
      exponents[offset + 3] = continuousCornerExponent(corners.bottomLeftSmoothing)
    }
    setFloatUniform("memberCornerExponents", exponents)
  }
  setFloatUniform(
    "memberSmoothing",
    scratch.padded(MergedFieldUniformScratch.SMOOTHING, field.memberSmoothingPx(), MAX_GLASS_MERGED_MEMBERS),
  )
  setFloatUniform("memberCount", field.members.size.toFloat())
  if (includeLighting) {
    val positions = scratch.floats(MergedFieldUniformScratch.LIGHT_POSITIONS, MAX_GLASS_MERGED_MEMBERS * 2)
    val lightRadii = scratch.floats(MergedFieldUniformScratch.LIGHT_RADII, MAX_GLASS_MERGED_MEMBERS)
    val intensities = scratch.floats(MergedFieldUniformScratch.LIGHT_INTENSITIES, MAX_GLASS_MERGED_MEMBERS)
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
    val positions = scratch.floats(MergedFieldUniformScratch.OPTICAL_POSITIONS, MAX_GLASS_MERGED_MEMBERS * 2)
    val opticalRadii = scratch.floats(MergedFieldUniformScratch.OPTICAL_RADII, MAX_GLASS_MERGED_MEMBERS)
    val multipliers = scratch.floats(MergedFieldUniformScratch.MULTIPLIERS, MAX_GLASS_MERGED_MEMBERS, initial = 1f)
    val whitePointDeltas = scratch.floats(MergedFieldUniformScratch.WHITE_POINT_DELTAS, MAX_GLASS_MERGED_MEMBERS)
    field.members.forEachIndexed { index, member ->
      positions[index * 2] = member.opticalPosition.x
      positions[index * 2 + 1] = member.opticalPosition.y
      opticalRadii[index] = member.opticalRadius
      multipliers[index] = member.refractionMultiplier
      whitePointDeltas[index] = member.whitePointDelta
    }
    setFloatUniform(
      "memberStandaloneWeights",
      scratch.padded(MergedFieldUniformScratch.STANDALONE_WEIGHTS, field.memberStandaloneWeights(), MAX_GLASS_MERGED_MEMBERS),
    )
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

private fun Rect.scaledBy(scale: Float) = Rect(left * scale, top * scale, right * scale, bottom * scale)
