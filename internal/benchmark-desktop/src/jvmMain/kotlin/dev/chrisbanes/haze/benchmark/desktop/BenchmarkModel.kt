// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
public data class FrameSample(
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
public data class BenchmarkBlockResult(
  val schemaVersion: Int = 1,
  val suiteId: String,
  val scenarioId: String,
  val protocolVersion: Int,
  val revision: String,
  val round: Int,
  val order: Int,
  val environment: BenchmarkEnvironment,
  val workloadDurationNanos: Long,
  val samples: List<FrameSample>,
)

@Serializable
public data class MetricSummary(
  val sampleCount: Int,
  val p50Nanos: Long,
  val p95Nanos: Long,
  val p99Nanos: Long,
  val above16MillisCount: Int,
  val above16MillisPercent: Double,
  val above33MillisCount: Int,
  val above33MillisPercent: Double,
  val robustVariationPercent: Double,
  val noisy: Boolean,
)

@Serializable
public data class ScenarioSummary(
  val id: String,
  val baseProtocolVersion: Int?,
  val headProtocolVersion: Int,
  val comparable: Boolean,
  val baseRender: MetricSummary?,
  val headRender: MetricSummary,
  val baseInterval: MetricSummary?,
  val headInterval: MetricSummary,
  val renderPairedDeltaPercent: Double?,
  val intervalPairedDeltaPercent: Double?,
  val blocks: List<BenchmarkBlockResult>,
)

@Serializable
public data class BenchmarkArtifact(
  val schemaVersion: Int = 1,
  val suiteId: String,
  val repository: String,
  val baseSha: String?,
  val headSha: String,
  val scenarios: List<ScenarioSummary>,
  val status: String = "complete",
  val diagnostic: String? = null,
)

public val BenchmarkJson: Json = Json {
  encodeDefaults = true
  explicitNulls = true
  ignoreUnknownKeys = false
}

public fun encodeArtifact(value: BenchmarkArtifact): String {
  require(value.diagnostic == null || value.diagnostic.encodeToByteArray().size <= 2048)
  val encoded = BenchmarkJson.encodeToString(value)
  require(encoded.encodeToByteArray().size <= 5 * 1024 * 1024)
  return encoded
}

public fun boundedDiagnostic(value: String): String = buildString {
  for (character in value) {
    val candidate = toString() + character
    if (candidate.encodeToByteArray().size > 2048) break
    append(character)
  }
}
