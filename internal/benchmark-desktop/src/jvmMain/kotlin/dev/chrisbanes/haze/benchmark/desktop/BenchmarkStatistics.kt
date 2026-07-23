// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import kotlin.math.ceil

internal fun summarizeMetric(values: List<Long>): MetricSummary {
  require(values.isNotEmpty())
  val sorted = values.sorted()
  fun percentile(value: Double) = sorted[
    (ceil(value * sorted.size).toInt() - 1).coerceIn(sorted.indices),
  ]
  return MetricSummary(
    sampleCount = sorted.size,
    p50Nanos = percentile(0.50),
    p95Nanos = percentile(0.95),
    maxNanos = sorted.last(),
  )
}
