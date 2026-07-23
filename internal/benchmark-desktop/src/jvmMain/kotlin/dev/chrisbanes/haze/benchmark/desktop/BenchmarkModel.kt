// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class FrameSample(
  val renderDurationNanos: Long,
  val callbackIntervalNanos: Long? = null,
)

@Serializable
public data class BenchmarkEnvironment(
  val osName: String,
  val osVersion: String,
  val architecture: String,
  val cpu: String,
  val memoryBytes: Long,
  val javaVendor: String,
  val javaVersion: String,
  val composeVersion: String,
  val skikoVersion: String,
  val renderApi: String,
  val framebufferWidth: Int,
  val framebufferHeight: Int,
  val contentScale: Float,
  val refreshRateHz: Int,
  val runnerImage: String?,
  val runnerImageVersion: String?,
)

@Serializable
public data class MetricSummary(
  val sampleCount: Int,
  val p50Nanos: Long,
  val p95Nanos: Long,
  val maxNanos: Long,
)

@Serializable
public data class BenchmarkScenarioResult(
  val id: String,
  val environment: BenchmarkEnvironment,
  val renderDuration: MetricSummary,
  val callbackInterval: MetricSummary,
)

@Serializable
public data class BenchmarkReport(
  val schemaVersion: Int = 1,
  val suiteId: String,
  val commitSha: String,
  val scenarios: List<BenchmarkScenarioResult>,
)

public val BenchmarkJson: Json = Json {
  encodeDefaults = true
  ignoreUnknownKeys = false
}
