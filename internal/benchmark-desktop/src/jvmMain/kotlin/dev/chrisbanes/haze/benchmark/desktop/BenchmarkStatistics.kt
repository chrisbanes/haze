// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import kotlin.math.abs
import kotlin.math.ceil

internal fun nearestRank(values: List<Long>, percentile: Double): Long {
  require(values.isNotEmpty() && percentile in 0.0..1.0)
  val sorted = values.sorted()
  val rank = ceil(percentile * sorted.size).toInt().coerceIn(1, sorted.size)
  return sorted[rank - 1]
}

internal fun robustRelativeVariationPercent(values: List<Double>): Double {
  require(values.isNotEmpty())
  val center = values.median()
  if (center == 0.0) return if (values.all { it == 0.0 }) 0.0 else 100.0
  return values.map { abs(it - center) }.median() / abs(center) * 100.0
}

internal fun isNoisy(blockMedians: List<Double>): Boolean =
  robustRelativeVariationPercent(blockMedians) > 10.0

internal fun pairedDeltaPercent(base: List<Double>, head: List<Double>): Double {
  require(base.size == 6 && head.size == 6)
  val roundDeltas = (0 until 3).map { round ->
    val baseMedian = base.subList(round * 2, round * 2 + 2).median()
    val headMedian = head.subList(round * 2, round * 2 + 2).median()
    (headMedian / baseMedian - 1.0) * 100.0
  }
  return roundDeltas.median()
}

private fun List<Double>.median(): Double {
  require(isNotEmpty())
  val sorted = sorted()
  val middle = sorted.size / 2
  return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
}
