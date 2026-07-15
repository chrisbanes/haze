// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("ktlint:standard:property-naming")

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import kotlin.math.abs
import kotlin.math.ceil

private const val PixelTolerance = 1f / 255f
private const val FloatingPointEpsilon = 1e-6f
private const val MinimumChangedRatio = 0.01f
private const val EdgePositionTolerancePx = 1f
private const val MinimumDisplacementConfidence = .005f
private const val UnclippedRedCarrierContrast = .6f
private const val WeakRedCarrierDeviation = .0065f

internal data class MetricBand(val min: Float, val max: Float) {
  init {
    require(min <= max) { "MetricBand min must not exceed max" }
  }

  operator fun contains(value: Float): Boolean = value in min..max
}

internal data class GlassReferenceBands(
  val displacementPx: MetricBand,
  val blurAttenuation: MetricBand,
  val interiorLumaShift: MetricBand,
)

internal data class GlassOpticalMetrics(
  val displacementPx: Float,
  val blurAttenuation: Float,
  val interiorLumaShift: Float,
)

internal data class RefractionStrengthMetrics(
  val disabledDisplacementPx: Float,
  val enabledDisplacementPx: Float,
  val directionalDisplacementDeltaPx: Float,
  val edgeBandResidualChangedPixelRatio: Float,
)

internal enum class GlassAppearance(val fileName: String) { Light("light"), Dark("dark") }

internal enum class GlassSurface(val referenceBounds: IntRect) {
  Capsule(IntRect(180, 270, 900, 462)),
  Card(IntRect(120, 672, 960, 1200)),
  Panel(IntRect(60, 1380, 1020, 2040)),
}

internal data class GlassReferenceKey(
  val appearance: GlassAppearance,
  val surface: GlassSurface,
)

/*
 * Opaque refracted screenshots do not expose alpha coverage. Their apparent luminance edge mixes
 * source-grid phase, refraction, tone, and backend quantization, so it cannot provide an invariant
 * material-width observable. Task 7 separately enforces geometry through alpha silhouette,
 * hard-clip, corner, padding, scale, and shape invariants. These bands intentionally contain only
 * optical observables derived from paired grid/uniform captures.
 */
internal val Ios26RegularReferenceMetrics = mapOf(
  GlassReferenceKey(GlassAppearance.Light, GlassSurface.Capsule) to
    GlassOpticalMetrics(-1.6625061f, .04242347f, .05534905f),
  GlassReferenceKey(GlassAppearance.Light, GlassSurface.Card) to
    GlassOpticalMetrics(-.7538605f, 0f, .02857155f),
  GlassReferenceKey(GlassAppearance.Light, GlassSurface.Panel) to
    GlassOpticalMetrics(-.7777786f, .00241895f, .02864403f),
  GlassReferenceKey(GlassAppearance.Dark, GlassSurface.Capsule) to
    GlassOpticalMetrics(-.7862549f, .01405199f, .00119604f),
  GlassReferenceKey(GlassAppearance.Dark, GlassSurface.Card) to
    GlassOpticalMetrics(-.22807312f, .00067111f, .00567346f),
  GlassReferenceKey(GlassAppearance.Dark, GlassSurface.Panel) to
    GlassOpticalMetrics(-.13253021f, .00105703f, .01514573f),
)

/** Displacement uses ±max(10%, 1px); normalized optical metrics use ±max(10%, 1/255). */
internal val Ios26RegularReferenceBands = Ios26RegularReferenceMetrics.mapValues { (_, value) ->
  GlassReferenceBands(
    value.displacementPx.band(1f),
    value.blurAttenuation.band(PixelTolerance),
    value.interiorLumaShift.band(PixelTolerance),
  )
}

private fun Float.band(minimum: Float): MetricBand {
  val tolerance = maxOf(abs(this) * .1f, minimum)
  return MetricBand(this - tolerance, this + tolerance)
}

internal fun measureGlassOpticalMetrics(
  grid: PixelSnapshot,
  uniform: PixelSnapshot,
  surfaceBounds: IntRect,
  backgroundBounds: IntRect,
  gridSpacingPx: Int,
): GlassOpticalMetrics {
  grid.requireComparableSnapshot(uniform)
  grid.requireContains(surfaceBounds, "Surface bounds")
  grid.requireContains(backgroundBounds, "Background bounds")
  val centerY = (surfaceBounds.top + surfaceBounds.bottom) / 2
  val expected = ceil((surfaceBounds.left + gridSpacingPx / 8f) / gridSpacingPx) * gridSpacingPx
  val search = maxOf(4, gridSpacingPx * 7 / 24)
  val range = maxOf(0, expected.toInt() - search)..minOf(grid.width - 1, expected.toInt() + search)
  val interior = surfaceBounds.middleQuarter()
  val sourceEnergy = grid.highFrequencyEnergy(backgroundBounds)
  require(sourceEnergy > FloatingPointEpsilon) { "Background must contain source detail" }
  return GlassOpticalMetrics(
    pairedGridDisplacementPx(
      grid,
      uniform,
      centerY,
      range,
      expected,
      referenceY = backgroundBounds.top + gridSpacingPx / 2,
    ),
    grid.highFrequencyEnergy(interior) / sourceEnergy,
    uniform.meanLuminance(interior) - uniform.meanLuminance(backgroundBounds),
  )
}

internal fun pairedGridDisplacementPx(
  grid: PixelSnapshot,
  uniform: PixelSnapshot,
  y: Int,
  range: IntRange,
  expected: Float,
  referenceY: Int = y,
): Float {
  grid.requireComparableSnapshot(uniform)
  require(y in 0 until grid.height) { "Displacement y must be within the snapshot" }
  require(referenceY in 0 until grid.height) { "Displacement reference y must be within the snapshot" }
  require(range.first >= 0 && range.last < grid.width) { "Displacement range must be within the snapshot" }
  fun signal(channel: Int, scanY: Int) = range.map { x ->
    abs(grid[x, scanY].channel(channel) - uniform[x, scanY].channel(channel))
  }
  val maxChannelResidual = range.map { x ->
    val first = grid[x, y]
    val second = uniform[x, y]
    maxOf(
      abs(first.red - second.red),
      abs(first.green - second.green),
      abs(first.blue - second.blue),
    )
  }
  val subCodeResidual = maxChannelResidual.average() <= PixelTolerance &&
    maxChannelResidual.any { it > FloatingPointEpsilon }
  val sourceContrasts = (0..2).associateWith { signal(it, referenceY).maxOrNull() ?: Float.MAX_VALUE }
  val candidateDeviations = (0..2).associateWith { signal(it, y).standardDeviation() }
  val carrierOrder = if (sourceContrasts.getValue(0) <= UnclippedRedCarrierContrast) {
    if (candidateDeviations.getValue(0) < WeakRedCarrierDeviation) {
      listOf(1, 0, 2)
    } else {
      listOf(0, 1, 2)
    }
  } else {
    (0..2).sortedBy(sourceContrasts::getValue)
  }
  val attempts = carrierOrder.map { channel ->
    runCatching {
      pairedCarrierDisplacementPx(
        source = signal(channel, referenceY),
        candidate = signal(channel, y),
        range = range,
        expected = expected,
        subCodeResidual = subCodeResidual,
      )
    }
  }
  attempts.firstOrNull { it.isSuccess }?.let { return it.getOrThrow() }
  throw attempts.firstNotNullOf { it.exceptionOrNull() }
}

internal fun pairedCarrierDisplacementDeltaPx(
  disabledCarrier: PixelSnapshot,
  disabledUniform: PixelSnapshot,
  enabledCarrier: PixelSnapshot,
  enabledUniform: PixelSnapshot,
  scanY: Int,
  range: IntRange,
  expected: Float,
): Float {
  disabledCarrier.requireComparableSnapshot(disabledUniform)
  disabledCarrier.requireComparableSnapshot(enabledCarrier)
  disabledCarrier.requireComparableSnapshot(enabledUniform)
  require(scanY in 0 until disabledCarrier.height) { "Carrier scanline must be within the snapshot" }
  require(expected.toInt() in range) { "Expected carrier must be within the search range" }
  fun signal(carrier: PixelSnapshot, uniform: PixelSnapshot) = range.map { x ->
    val first = carrier[x, scanY]
    val second = uniform[x, scanY]
    maxOf(
      abs(first.red - second.red),
      abs(first.green - second.green),
      abs(first.blue - second.blue),
    )
  }
  val source = signal(disabledCarrier, disabledUniform)
  val candidate = signal(enabledCarrier, enabledUniform)
  val sourceContrast = source.max()
  val candidateContrast = candidate.max()
  check(sourceContrast > PixelTolerance && candidateContrast > PixelTolerance) {
    "Carrier delta requires visible disabled and enabled signals"
  }
  val relativeConfidence = minOf(sourceContrast, candidateContrast) /
    maxOf(sourceContrast, candidateContrast)
  check(relativeConfidence >= MinimumDisplacementConfidence) {
    "Carrier delta has insufficient relative confidence: $relativeConfidence"
  }
  fun centroid(values: List<Float>, label: String): Float {
    val deviation = values.standardDeviation()
    check(deviation >= PixelTolerance * .25f) {
      "$label carrier residual is diffuse: deviation=$deviation"
    }
    val peakIndex = values.indices.maxBy { values[it] }
    val exclusionRadius = maxOf(2, values.size / 3)
    val secondPeak = values.indices
      .filter { abs(it - peakIndex) > exclusionRadius }
      .maxOfOrNull { values[it] } ?: 0f
    check(secondPeak < values[peakIndex] * .85f) {
      "$label carrier residual is ambiguous: peak=${values[peakIndex]}, second=$secondPeak"
    }
    val threshold = maxOf(PixelTolerance, values.max() * .25f)
    val indices = values.indices.filter { values[it] >= threshold }
    val weight = indices.sumOf { values[it].toDouble() }.toFloat()
    check(weight > FloatingPointEpsilon) { "$label carrier has no centroid confidence" }
    return indices.sumOf { index -> ((range.first + index) * values[index]).toDouble() }.toFloat() / weight
  }
  return centroid(candidate, "Enabled") - centroid(source, "Disabled")
}

private fun pairedCarrierDisplacementPx(
  source: List<Float>,
  candidate: List<Float>,
  range: IntRange,
  expected: Float,
  subCodeResidual: Boolean,
): Float {
  check(source.maxOrNull()?.let { it > PixelTolerance } == true) {
    "Displacement reference feature is missing"
  }
  val sourcePeak = range.first + source.indices.maxBy { source[it] }
  check(abs(sourcePeak - expected) <= 2f) { "Displacement reference phase does not match expected" }
  if (candidate.average() <= PixelTolerance) {
    if (subCodeResidual) return 0f
    error("Displacement feature is missing")
  }
  val sourceContrast = source.max()
  val candidateContrast = candidate.max()
  val confidence = candidateContrast * (1f - sourceContrast) / sourceContrast
  check(confidence >= MinimumDisplacementConfidence) {
    "Displacement feature has insufficient confidence: $confidence"
  }
  val candidateDeviation = candidate.standardDeviation()
  check(candidateDeviation >= PixelTolerance * .25f) {
    "Displacement residual is diffuse rather than localized: deviation=$candidateDeviation, confidence=$confidence"
  }
  val peakIndex = candidate.indices.maxBy { candidate[it] }
  val exclusionRadius = maxOf(2, candidate.size / 3)
  val secondPeak = candidate.indices
    .filter { abs(it - peakIndex) > exclusionRadius }
    .maxOfOrNull { candidate[it] } ?: 0f
  val total = candidate.sum()
  check(
    secondPeak < candidate[peakIndex] * .85f ||
      (candidate[peakIndex] + secondPeak) / total < .5f,
  ) {
    "Displacement feature is ambiguous: peak=$peakIndex/${candidate[peakIndex]}, second=$secondPeak"
  }
  check(total > FloatingPointEpsilon) { "Displacement feature has no confidence" }
  val sourceThreshold = maxOf(PixelTolerance, source.max() * .1f)
  val candidateThreshold = maxOf(PixelTolerance, candidate.max() * .1f)
  val sourceOnset = source.indexOfFirst { it >= sourceThreshold }
  val candidateOnset = candidate.indexOfFirst { it >= candidateThreshold }
  check(sourceOnset >= 0 && candidateOnset >= 0) { "Displacement feature onset is missing" }
  val centroidThreshold = maxOf(PixelTolerance, candidate.max() * .25f)
  val centroidIndices = candidate.indices.filter { candidate[it] >= centroidThreshold }
  val centroidWeight = centroidIndices.sumOf { candidate[it].toDouble() }.toFloat()
  val actual = centroidIndices.sumOf { index ->
    ((range.first + index) * candidate[index]).toDouble()
  }.toFloat() / centroidWeight
  val residual = actual - expected
  val onsetDirection = kotlin.math.sign((candidateOnset - sourceOnset).toFloat())
  val direction = if (onsetDirection == 0f) kotlin.math.sign(residual) else onsetDirection
  return direction * abs(residual)
}

// Red is stable below clipping contrast; weak residuals use green's extra quantization headroom.
// For stronger source lines, prefer the least-clipped carrier and fall back only when invalid.
private fun Color.channel(index: Int): Float = when (index) {
  0 -> red
  1 -> green
  else -> blue
}

private fun List<Float>.standardDeviation(): Float {
  val mean = average().toFloat()
  return kotlin.math.sqrt(
    sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / size,
  )
}

internal fun signedHorizontalCorrelationDisplacementPx(
  reference: PixelSnapshot,
  candidate: PixelSnapshot,
  y: Int,
  range: IntRange,
  maxShiftPx: Int,
): Float {
  reference.requireComparableSnapshot(candidate)
  require(y in 0 until reference.height) { "Correlation y must be within the snapshot" }
  require(range.first >= 0 && range.last < reference.width) { "Correlation range must be within the snapshot" }
  require(maxShiftPx > 0) { "Maximum correlation shift must be positive" }
  val scores = (-maxShiftPx..maxShiftPx).associateWith { shift ->
    val points = range.mapNotNull { x ->
      val candidateX = x + shift
      if (candidateX in 0 until candidate.width) {
        reference[x, y].luminance() to candidate[candidateX, y].luminance()
      } else {
        null
      }
    }
    val referenceMean = points.sumOf { it.first.toDouble() }.toFloat() / points.size
    val candidateMean = points.sumOf { it.second.toDouble() }.toFloat() / points.size
    var numerator = 0f
    var referenceEnergy = 0f
    var candidateEnergy = 0f
    points.forEach { (first, second) ->
      val referenceDelta = first - referenceMean
      val candidateDelta = second - candidateMean
      numerator += referenceDelta * candidateDelta
      referenceEnergy += referenceDelta * referenceDelta
      candidateEnergy += candidateDelta * candidateDelta
    }
    if (referenceEnergy <= FloatingPointEpsilon || candidateEnergy <= FloatingPointEpsilon) {
      Float.NEGATIVE_INFINITY
    } else {
      numerator / kotlin.math.sqrt(referenceEnergy * candidateEnergy)
    }
  }
  val best = scores.maxBy { it.value }
  check(best.value >= .8f) { "Content correlation has insufficient confidence: ${best.value}" }
  val second = scores
    .filterKeys { abs(it - best.key) > 1 }
    .maxOfOrNull { it.value } ?: Float.NEGATIVE_INFINITY
  check(second < best.value - .02f) { "Content correlation is ambiguous" }
  return best.key.toFloat()
}

internal fun assertContentAlignedAcrossInputScales(
  reference: PixelSnapshot,
  candidate: PixelSnapshot,
  y: Int,
  range: IntRange,
) {
  val displacement = signedHorizontalCorrelationDisplacementPx(
    reference,
    candidate,
    y,
    range,
    maxShiftPx = 8,
  )
  assertThat(abs(displacement)).isLessThanOrEqualTo(1f)
}

internal data class PixelSnapshot(
  val width: Int,
  val height: Int,
  val colors: List<Color>,
) {
  init {
    require(width >= 0 && height >= 0) {
      "PixelSnapshot dimensions must be non-negative, but were ${width}x$height"
    }
    val expectedColorCount = width.toLong() * height
    require(expectedColorCount <= Int.MAX_VALUE && colors.size.toLong() == expectedColorCount) {
      "PixelSnapshot ${width}x$height requires $expectedColorCount colors, " +
        "but received ${colors.size}"
    }
  }

  operator fun get(x: Int, y: Int): Color = colors[y * width + x]
}

internal fun PixelMap.snapshot(): PixelSnapshot = PixelSnapshot(
  width = width,
  height = height,
  colors = buildList(width * height) {
    for (y in 0 until height) {
      for (x in 0 until width) add(this@snapshot[x, y])
    }
  },
)

internal fun PixelSnapshot.changedPixelRatio(other: PixelSnapshot): Float {
  requireComparableSnapshot(other)

  var changedPixels = 0
  for (index in colors.indices) {
    val first = colors[index]
    val second = other.colors[index]
    val maxChannelDelta = maxOf(
      abs(first.red - second.red),
      abs(first.green - second.green),
      abs(first.blue - second.blue),
      abs(first.alpha - second.alpha),
    )
    if (maxChannelDelta > PixelTolerance) changedPixels++
  }
  return changedPixels.toFloat() / colors.size
}

internal fun measureRefractionStrengthMetrics(
  disabledDisplacementPx: Float,
  enabledDisplacementPx: Float,
  disabledGrid: PixelSnapshot,
  disabledUniform: PixelSnapshot,
  enabledGrid: PixelSnapshot,
  enabledUniform: PixelSnapshot,
  edgeBand: IntRect,
): RefractionStrengthMetrics = RefractionStrengthMetrics(
  disabledDisplacementPx = disabledDisplacementPx,
  enabledDisplacementPx = enabledDisplacementPx,
  directionalDisplacementDeltaPx = enabledDisplacementPx - disabledDisplacementPx,
  edgeBandResidualChangedPixelRatio = residualChangedPixelRatio(
    disabledGrid,
    disabledUniform,
    enabledGrid,
    enabledUniform,
    edgeBand,
  ),
)

internal fun residualChangedPixelRatio(
  disabledGrid: PixelSnapshot,
  disabledUniform: PixelSnapshot,
  enabledGrid: PixelSnapshot,
  enabledUniform: PixelSnapshot,
  bounds: IntRect,
): Float {
  disabledGrid.requireComparableSnapshot(disabledUniform)
  disabledGrid.requireComparableSnapshot(enabledGrid)
  disabledGrid.requireComparableSnapshot(enabledUniform)
  require(bounds.width > 0 && bounds.height > 0) { "Residual bounds must be non-empty" }
  disabledGrid.requireContains(bounds, "Residual bounds")

  var changedPixels = 0
  for (y in bounds.top until bounds.bottom) {
    for (x in bounds.left until bounds.right) {
      val disabledGridColor = disabledGrid[x, y]
      val disabledUniformColor = disabledUniform[x, y]
      val enabledGridColor = enabledGrid[x, y]
      val enabledUniformColor = enabledUniform[x, y]
      val maxChannelDelta = maxOf(
        abs((disabledGridColor.red - disabledUniformColor.red) - (enabledGridColor.red - enabledUniformColor.red)),
        abs((disabledGridColor.green - disabledUniformColor.green) - (enabledGridColor.green - enabledUniformColor.green)),
        abs((disabledGridColor.blue - disabledUniformColor.blue) - (enabledGridColor.blue - enabledUniformColor.blue)),
        abs((disabledGridColor.alpha - disabledUniformColor.alpha) - (enabledGridColor.alpha - enabledUniformColor.alpha)),
      )
      if (maxChannelDelta > PixelTolerance) changedPixels++
    }
  }
  return changedPixels.toFloat() / (bounds.width * bounds.height)
}

internal fun PixelSnapshot.meanAbsoluteDifference(other: PixelSnapshot): Float {
  requireComparableSnapshot(other)
  var total = 0f
  for (index in colors.indices) {
    val first = colors[index]
    val second = other.colors[index]
    total += abs(first.red - second.red)
    total += abs(first.green - second.green)
    total += abs(first.blue - second.blue)
    total += abs(first.alpha - second.alpha)
  }
  return total / (colors.size * 4)
}

internal fun recoverPremultipliedSnapshot(
  overBlack: PixelSnapshot,
  overWhite: PixelSnapshot,
): PixelSnapshot {
  overBlack.requireComparableSnapshot(overWhite)
  require(overBlack.colors.all { it.alpha >= 1f - PixelTolerance }) {
    "Black-matte capture must contain only opaque pixels"
  }
  require(overWhite.colors.all { it.alpha >= 1f - PixelTolerance }) {
    "White-matte capture must contain only opaque pixels"
  }
  require(
    overBlack.colors.zip(overWhite.colors).all { (black, white) ->
      white.red + PixelTolerance >= black.red &&
        white.green + PixelTolerance >= black.green &&
        white.blue + PixelTolerance >= black.blue
    },
  ) {
    "White-matte RGB channels must not be darker than black-matte channels"
  }
  var inconsistentDelta: Pair<Int, List<Float>>? = null
  for (index in overBlack.colors.indices) {
    val black = overBlack.colors[index]
    val white = overWhite.colors[index]
    val redDelta = white.red - black.red
    val greenDelta = white.green - black.green
    val blueDelta = white.blue - black.blue
    if (
      maxOf(redDelta, greenDelta, blueDelta) - minOf(redDelta, greenDelta, blueDelta) >
      PixelTolerance + FloatingPointEpsilon
    ) {
      inconsistentDelta = index to listOf(redDelta, greenDelta, blueDelta)
      break
    }
  }
  require(inconsistentDelta == null) {
    "White-minus-black RGB channel deltas must agree within $PixelTolerance; " +
      "index=${inconsistentDelta?.first}, deltas=${inconsistentDelta?.second}"
  }
  return PixelSnapshot(
    width = overBlack.width,
    height = overBlack.height,
    colors = overBlack.colors.zip(overWhite.colors) { black, white ->
      val transparency = (
        (white.red - black.red) +
          (white.green - black.green) +
          (white.blue - black.blue)
        ) / 3f
      Color(
        red = black.red,
        green = black.green,
        blue = black.blue,
        alpha = (1f - transparency).coerceIn(0f, 1f),
      )
    },
  )
}

internal fun PixelSnapshot.scanlineDerivative(
  y: Int,
  xRange: IntRange,
): List<Float> {
  require(y in 0 until height) { "Scanline y=$y must be within 0 until $height" }
  require(xRange.first < xRange.last) {
    "Scanline xRange=$xRange must contain at least 2 pixels"
  }
  require(xRange.first >= 0 && xRange.last < width) {
    "Scanline xRange=$xRange must be within 0 until $width"
  }
  return (xRange.first until xRange.last).map { x ->
    abs(this[x + 1, y].luminance() - this[x, y].luminance())
  }
}

internal fun PixelSnapshot.horizontalEdgePosition(
  y: Int,
  xRange: IntRange,
): Float {
  val derivative = scanlineDerivative(y, xRange)
  return (xRange.first + derivative.indices.maxBy { derivative[it] }).toFloat()
}

internal fun PixelSnapshot.horizontalAlphaEdgePosition(
  y: Int,
  xRange: IntRange,
): Float {
  require(y in 0 until height) { "Scanline y=$y must be within 0 until $height" }
  require(xRange.first < xRange.last) {
    "Scanline xRange=$xRange must contain at least 2 pixels"
  }
  require(xRange.first >= 0 && xRange.last < width) {
    "Scanline xRange=$xRange must be within 0 until $width"
  }
  val derivative = (xRange.first until xRange.last).map { x ->
    abs(this[x + 1, y].alpha - this[x, y].alpha)
  }
  return (xRange.first + derivative.indices.maxBy { derivative[it] }).toFloat()
}

internal fun PixelSnapshot.alphaCoverage(bounds: IntRect): Float {
  require(bounds.width > 0 && bounds.height > 0) { "Alpha bounds must be non-empty" }
  requireContains(bounds, "Alpha bounds")
  var visiblePixels = 0
  for (y in bounds.top until bounds.bottom) {
    for (x in bounds.left until bounds.right) {
      if (this[x, y].alpha > PixelTolerance) visiblePixels++
    }
  }
  return visiblePixels.toFloat() / (bounds.width * bounds.height)
}

internal fun PixelSnapshot.horizontalAlphaSpan(y: Int): IntRange? {
  require(y in 0 until height) { "Scanline y=$y must be within 0 until $height" }
  val first = (0 until width).firstOrNull { x -> this[x, y].alpha > PixelTolerance } ?: return null
  val last = (width - 1 downTo first).first { x -> this[x, y].alpha > PixelTolerance }
  return first..last
}

internal fun PixelSnapshot.highFrequencyEnergy(bounds: IntRect): Float {
  require(bounds.width >= 2 && bounds.height >= 2) {
    "High-frequency bounds must be at least 2x2, but were ${bounds.width}x${bounds.height}"
  }
  requireContains(bounds, "High-frequency bounds")
  var total = 0f
  var samples = 0
  for (y in bounds.top until bounds.bottom - 1) {
    for (x in bounds.left until bounds.right - 1) {
      val luma = this[x, y].luminance()
      total += abs(luma - this[x + 1, y].luminance())
      total += abs(luma - this[x, y + 1].luminance())
      samples += 2
    }
  }
  return total / samples
}

internal fun PixelSnapshot.crop(bounds: IntRect): PixelSnapshot {
  require(bounds.width > 0 && bounds.height > 0) {
    "Crop bounds must be non-empty, but were ${bounds.width}x${bounds.height}"
  }
  requireContains(bounds, "Crop bounds")
  return PixelSnapshot(
    width = bounds.width,
    height = bounds.height,
    colors = buildList(bounds.width * bounds.height) {
      for (y in bounds.top until bounds.bottom) {
        for (x in bounds.left until bounds.right) add(this@crop[x, y])
      }
    },
  )
}

internal fun PixelSnapshot.assertTransparentAt(
  points: List<IntOffset>,
  tolerance: Float = PixelTolerance,
) {
  require(points.isNotEmpty()) { "Transparent points must be non-empty" }
  points.forEach { point ->
    require(point.x in 0 until width && point.y in 0 until height) {
      "Transparent point $point must be within ${width}x$height"
    }
    val color = this[point.x, point.y]
    assertThat(color.alpha).isLessThanOrEqualTo(tolerance)
    assertThat(maxOf(color.red, color.green, color.blue)).isLessThanOrEqualTo(tolerance)
  }
}

private fun PixelSnapshot.requireComparableSnapshot(other: PixelSnapshot) {
  require(colors.isNotEmpty() && other.colors.isNotEmpty()) {
    "PixelSnapshot comparisons require non-empty snapshots"
  }
  require(width == other.width && height == other.height) {
    "PixelSnapshot dimensions must match: ${width}x$height != ${other.width}x${other.height}"
  }
}

private fun PixelSnapshot.requireContains(bounds: IntRect, label: String) {
  require(
    bounds.left >= 0 &&
      bounds.top >= 0 &&
      bounds.right <= width &&
      bounds.bottom <= height,
  ) {
    "$label $bounds must be within ${width}x$height snapshot bounds"
  }
}

private fun IntRect.middleQuarter() = IntRect(
  left + width * 3 / 8,
  top + height * 3 / 8,
  right - width * 3 / 8,
  bottom - height * 3 / 8,
)

private fun PixelSnapshot.meanLuminance(bounds: IntRect): Float {
  requireContains(bounds, "Luminance bounds")
  var total = 0f
  for (y in bounds.top until bounds.bottom) {
    for (x in bounds.left until bounds.right) total += this[x, y].luminance()
  }
  return total / (bounds.width * bounds.height)
}

internal fun PixelSnapshot.assertZeroAlphaHasZeroRgb(
  tolerance: Float = PixelTolerance,
) {
  colors.filter { it.alpha <= tolerance }.forEach { color ->
    assertThat(maxOf(color.red, color.green, color.blue)).isLessThanOrEqualTo(tolerance)
  }
}

internal fun assertDepthProgression(
  depth0: PixelSnapshot,
  depth50: PixelSnapshot,
  depth100: PixelSnapshot,
) {
  val fullDistance = depth0.meanAbsoluteDifference(depth100)
  val midToSharp = depth0.meanAbsoluteDifference(depth50)
  val midToBlurred = depth50.meanAbsoluteDifference(depth100)
  assertThat(midToSharp).isGreaterThan(0.005f)
  assertThat(midToBlurred).isGreaterThan(0.005f)
  assertThat(fullDistance).isGreaterThan(0.005f)
}

internal fun assertFirstEnabledFrameStable(
  disabled: PixelSnapshot,
  firstEnabled: PixelSnapshot,
  settled: PixelSnapshot,
) {
  assertThat(disabled.changedPixelRatio(firstEnabled)).isGreaterThan(MinimumChangedRatio)
  assertThat(firstEnabled.meanAbsoluteDifference(settled)).isLessThan(PixelTolerance)
}

internal fun assertBoundaryContinuous(
  derivative: List<Float>,
  boundaryIndex: Int,
) {
  require(derivative.isNotEmpty()) { "Boundary derivative must be non-empty" }
  require(boundaryIndex in derivative.indices) {
    "boundaryIndex=$boundaryIndex must be within derivative indices ${derivative.indices}"
  }
  val start = maxOf(0, boundaryIndex - 8)
  val end = minOf(derivative.size, boundaryIndex + 9)
  val neighborhood = derivative.subList(
    start,
    end,
  ).filterIndexed { index, _ -> index != boundaryIndex - start }
  val allowed = (neighborhood.maxOrNull() ?: 0f) * 1.5f + PixelTolerance
  assertThat(derivative[boundaryIndex]).isLessThanOrEqualTo(allowed)
}

internal fun assertEquivalentAlphaEdgePosition(
  first: PixelSnapshot,
  second: PixelSnapshot,
  y: Int,
  xRange: IntRange,
) {
  val delta = abs(
    first.horizontalAlphaEdgePosition(y, xRange) -
      second.horizontalAlphaEdgePosition(y, xRange),
  )
  assertThat(delta).isLessThanOrEqualTo(EdgePositionTolerancePx)
}

internal fun assertBlurReducesHighFrequencyEnergy(
  sharp: PixelSnapshot,
  blurred: PixelSnapshot,
  bounds: IntRect,
) {
  assertThat(blurred.highFrequencyEnergy(bounds))
    .isLessThan(sharp.highFrequencyEnergy(bounds) * 0.9f)
}

internal fun assertOutsideMatchesBackground(
  rendered: PixelSnapshot,
  background: PixelSnapshot,
  outsidePoints: List<IntOffset>,
) {
  require(outsidePoints.isNotEmpty()) { "Outside points must be non-empty" }
  rendered.requireComparableSnapshot(background)
  outsidePoints.forEach { point ->
    require(point.x in 0 until rendered.width && point.y in 0 until rendered.height) {
      "Outside point $point must be within ${rendered.width}x${rendered.height}"
    }
    val first = rendered[point.x, point.y]
    val second = background[point.x, point.y]
    assertThat(abs(first.red - second.red)).isLessThanOrEqualTo(PixelTolerance)
    assertThat(abs(first.green - second.green)).isLessThanOrEqualTo(PixelTolerance)
    assertThat(abs(first.blue - second.blue)).isLessThanOrEqualTo(PixelTolerance)
    assertThat(abs(first.alpha - second.alpha)).isLessThanOrEqualTo(PixelTolerance)
  }
}
