// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThan
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.serialization.encodeToString

class BenchmarkAggregationTest {
  @Test
  fun matchingProtocolsProducePairedDelta() {
    val summary = aggregate(abbaFixture(baseValue = 10_000_000, headValue = 11_000_000))
      .scenarios.first { it.id == "pointer_sweep" }

    assertThat(summary.comparable).isTrue()
    assertThat(abs(checkNotNull(summary.renderPairedDeltaPercent) - 10.0)).isLessThan(0.0001)
    assertThat(abs(checkNotNull(summary.intervalPairedDeltaPercent) - 10.0)).isLessThan(0.0001)
  }

  @Test
  fun protocolMismatchSuppressesOnlyThatScenarioDelta() {
    val artifact = aggregate(
      abbaScenarioFixture(
        baseValue = 10_000_000,
        headValue = 11_000_000,
        scenarioId = "pointer_sweep",
        baseProtocol = 1,
        headProtocol = 2,
      ) + abbaScenarioFixture(10_000_000, 11_000_000, "playground_drag"),
    )

    with(artifact.scenarios.first { it.id == "pointer_sweep" }) {
      assertThat(comparable).isFalse()
      assertThat(renderPairedDeltaPercent).isNull()
      assertThat(intervalPairedDeltaPercent).isNull()
    }
    assertThat(artifact.scenarios.first { it.id == "playground_drag" }.comparable).isTrue()
  }

  @Test
  fun headOnlyBootstrapHasNoDelta() {
    val summary = aggregate(headOnlyFixture()).scenarios.first { it.id == "pointer_sweep" }

    assertThat(summary.baseRender).isNull()
    assertThat(summary.baseInterval).isNull()
    assertThat(summary.comparable).isFalse()
    assertThat(summary.renderPairedDeltaPercent).isNull()
    assertThat(summary.intervalPairedDeltaPercent).isNull()
  }

  @Test
  fun duplicateMissingAndWrongSlotsAreRejected() {
    val valid = abbaFixture(10_000_000, 11_000_000)
    val duplicate = valid + valid.first()
    val missing = valid.dropLast(1)
    val wrong = valid.dropLast(1) + valid.last().copy(order = 2)

    listOf(duplicate, missing, wrong).forEach { blocks ->
      assertFailure { aggregate(blocks) }
    }
  }

  @Test
  fun unknownScenarioIsRejected() {
    assertFailure {
      aggregate(
        abbaFixture(10_000_000, 11_000_000) +
          abbaScenarioFixture(10_000_000, 11_000_000, scenarioId = "unknown"),
      )
    }
  }

  @Test
  fun everyAllowedScenarioMustBePresent() {
    assertFailure {
      aggregate(abbaScenarioFixture(10_000_000, 11_000_000, scenarioId = "pointer_sweep"))
    }
  }

  @Test
  fun baseBlocksRequireBaseSha() {
    assertFailure {
      aggregate(abbaFixture(10_000_000, 11_000_000), baseSha = null)
    }
  }

  @Test
  fun headOnlyBootstrapAllowsPresentOrAbsentBaseSha() {
    assertThat(aggregate(headOnlyFixture(), baseSha = BASE_SHA).scenarios.size).isEqualTo(2)
    assertThat(aggregate(headOnlyFixture(), baseSha = null).scenarios.size).isEqualTo(2)
  }

  @Test
  fun invalidSchemaSuiteRevisionRoundOrderRepositoryAndShaAreRejected() {
    val block = abbaFixture(10_000_000, 11_000_000).first()
    listOf(
      block.copy(schemaVersion = 2),
      block.copy(suiteId = "other"),
      block.copy(revision = "local"),
      block.copy(round = 3),
      block.copy(order = 4),
    ).forEach { invalid ->
      assertFailure {
        aggregate(abbaFixture(10_000_000, 11_000_000).map { if (it == block) invalid else it })
      }
    }
    assertFailure {
      aggregateBenchmarkBlocks(
        suiteId = "glass",
        allowedScenarioIds = ALLOWED_SCENARIO_IDS,
        repository = "not-a-repository",
        baseSha = BASE_SHA,
        headSha = HEAD_SHA,
        blocks = abbaFixture(10_000_000, 11_000_000),
      )
    }
    assertFailure {
      aggregateBenchmarkBlocks(
        suiteId = "glass",
        allowedScenarioIds = ALLOWED_SCENARIO_IDS,
        repository = REPOSITORY,
        baseSha = BASE_SHA,
        headSha = "not-a-sha",
        blocks = abbaFixture(10_000_000, 11_000_000),
      )
    }
  }

  @Test
  fun repositoryTraversalSegmentsAreRejected() {
    assertFailure {
      aggregateBenchmarkBlocks(
        suiteId = "glass",
        allowedScenarioIds = ALLOWED_SCENARIO_IDS,
        repository = "../..",
        baseSha = BASE_SHA,
        headSha = HEAD_SHA,
        blocks = abbaFixture(10_000_000, 11_000_000),
      )
    }
  }

  @Test
  fun sampleCapIsEnforcedAcrossBlocks() {
    val oversizedSamples = List(100_001) { FrameSample(1, 1) }
    val blocks = headOnlyFixture().mapIndexed { index, block ->
      if (index == 0) block.copy(samples = oversizedSamples) else block
    }

    assertFailure { aggregate(blocks) }
  }

  @Test
  fun invalidTimingAndEmptyMetricSetsAreRejected() {
    val valid = headOnlyFixture()
    val invalidBlocks = listOf(
      valid.first().copy(workloadDurationNanos = -1),
      valid.first().copy(samples = listOf(FrameSample(-1, 1))),
      valid.first().copy(samples = listOf(FrameSample(1, -1))),
      valid.first().copy(samples = emptyList()),
      valid.first().copy(samples = listOf(FrameSample(1, null))),
      valid.first().copy(
        environment = benchmarkEnvironmentFixture().copy(contentScale = Float.NaN),
      ),
    )

    invalidBlocks.forEach { invalid ->
      assertFailure { aggregate(listOf(invalid) + valid.drop(1)) }
    }
  }

  @Test
  fun zeroBaseMedianCannotCreateNonFiniteDelta() {
    assertFailure { aggregate(abbaFixture(baseValue = 0, headValue = 1)) }
  }

  @Test
  fun nonMetalAndMixedRenderApisAreRejected() {
    val valid = headOnlyFixture()
    val openGl = benchmarkEnvironmentFixture().copy(renderApi = "OPENGL")

    assertFailure { aggregate(valid.map { it.copy(environment = openGl) }) }
    assertFailure { aggregate(listOf(valid.first().copy(environment = openGl)) + valid.drop(1)) }
  }

  @Test
  fun scenariosAndBlocksAreSortedDeterministically() {
    val blocks = abbaFixture(10_000_000, 11_000_000).reversed()

    val artifact = aggregate(blocks)

    assertThat(artifact.scenarios.map { it.id })
      .containsExactly("playground_drag", "pointer_sweep")
    artifact.scenarios.forEach { scenario ->
      assertThat(scenario.blocks.map { Triple(it.round, it.order, it.revision) })
        .isEqualTo(
          scenario.blocks.map { Triple(it.round, it.order, it.revision) }.sortedWith(
            compareBy<Triple<Int, Int, String>>({ it.first }, { it.second }, { it.third }),
          ),
        )
    }
    assertThat(artifact.scenarios.flatMap { it.blocks }.toSet()).isEqualTo(blocks.toSet())
  }

  @Test
  fun renderAndIntervalMetricsUseIndependentSamplesAndThresholds() {
    val samples = listOf(
      FrameSample(10_000_000, null),
      FrameSample(20_000_000, 10_000_000),
      FrameSample(40_000_000, 40_000_000),
    )
    val summary = aggregate(headOnlyFixture().map { it.copy(samples = samples) })
      .scenarios.first { it.id == "pointer_sweep" }

    assertThat(summary.headRender.sampleCount).isEqualTo(18)
    assertThat(summary.headRender.p50Nanos).isEqualTo(20_000_000)
    assertThat(summary.headRender.p95Nanos).isEqualTo(40_000_000)
    assertThat(summary.headRender.above16MillisCount).isEqualTo(12)
    assertThat(abs(summary.headRender.above16MillisPercent - 100.0 * 12 / 18)).isLessThan(0.0001)
    assertThat(summary.headRender.above33MillisCount).isEqualTo(6)
    assertThat(summary.headInterval.sampleCount).isEqualTo(12)
    assertThat(summary.headInterval.p50Nanos).isEqualTo(10_000_000)
    assertThat(summary.headInterval.above16MillisCount).isEqualTo(6)
    assertThat(summary.headInterval.above16MillisPercent).isEqualTo(50.0)
  }

  @Test
  fun budgetCountsUseExactNanosecondBoundaries() {
    val samples = listOf(
      FrameSample(16_666_666, null),
      FrameSample(16_666_667, 16_666_667),
      FrameSample(16_666_668, 16_666_668),
      FrameSample(33_333_332, 33_333_332),
      FrameSample(33_333_333, 33_333_333),
      FrameSample(33_333_334, 33_333_334),
    )
    val summary = aggregate(headOnlyFixture().map { it.copy(samples = samples) })
      .scenarios.first { it.id == "pointer_sweep" }

    assertThat(summary.headRender.above16MillisCount).isEqualTo(24)
    assertThat(summary.headRender.above33MillisCount).isEqualTo(6)
    assertThat(summary.headInterval.above16MillisCount).isEqualTo(24)
    assertThat(summary.headInterval.above33MillisCount).isEqualTo(6)
  }

  @Test
  fun robustBlockMedianVariationSetsNoiseFlag() {
    val blocks = headOnlyFixture().map { block ->
      val value = if (block.round == 0 || block.round == 1 && block.order == 1) {
        10_000_000L
      } else {
        20_000_000L
      }
      block.copy(samples = listOf(FrameSample(value, value)))
    }

    val summary = aggregate(blocks).scenarios.first { it.id == "pointer_sweep" }

    assertThat(abs(summary.headRender.robustVariationPercent - 100.0 / 3.0)).isLessThan(0.0001)
    assertThat(summary.headRender.noisy).isTrue()
    assertThat(summary.headInterval.noisy).isTrue()
  }

  @Test
  fun strictBlockReaderUsesOnlyImmediateJsonFiles() = withTempDirectory { directory ->
    val block = headOnlyFixture().first()
    Files.writeString(directory.resolve("one.json"), BenchmarkJson.encodeToString(block))
    Files.writeString(directory.resolve("ignored.txt"), "not json")
    Files.createDirectories(directory.resolve("nested"))
    Files.writeString(
      directory.resolve("nested/two.json"),
      BenchmarkJson.encodeToString(block.copy(order = 2)),
    )

    assertThat(readBenchmarkBlocks(directory)).containsExactly(block)
  }

  @Test
  fun strictBlockReaderRejectsMalformedAndUnknownJson() = withTempDirectory { directory ->
    Files.writeString(directory.resolve("malformed.json"), "{")
    assertFailure { readBenchmarkBlocks(directory) }

    Files.writeString(
      directory.resolve("malformed.json"),
      BenchmarkJson.encodeToString(headOnlyFixture().first()).dropLast(1) + ",\"extra\":1}",
    )
    assertFailure { readBenchmarkBlocks(directory) }
  }

  @Test
  fun environmentMetadataAccepts256Utf8Bytes() {
    val boundary = "é".repeat(128)
    val environment = metadataEnvironment(boundary)

    assertThat(
      aggregate(headOnlyFixture().map { it.copy(environment = environment) }).scenarios.size,
    ).isEqualTo(2)
  }

  @Test
  fun requiredEnvironmentMetadataRejectsBlankAndOversizedValues() {
    val environment = benchmarkEnvironmentFixture()
    listOf(" \t", "é".repeat(129)).forEach { invalid ->
      requiredMetadataVariants(environment, invalid).forEach { invalidEnvironment ->
        assertFailure {
          aggregate(headOnlyFixture().map { it.copy(environment = invalidEnvironment) })
        }
      }
    }
  }

  @Test
  fun optionalEnvironmentMetadataMustBeValidWhenPresent() {
    val environment = benchmarkEnvironmentFixture()
    listOf(
      environment.copy(runnerImage = ""),
      environment.copy(runnerImageVersion = " \t"),
      environment.copy(runnerImage = "é".repeat(129)),
      environment.copy(runnerImageVersion = "é".repeat(129)),
    ).forEach { invalidEnvironment ->
      assertFailure {
        aggregate(headOnlyFixture().map { it.copy(environment = invalidEnvironment) })
      }
    }
  }

  @Test
  fun environmentRequiresFixedFramebufferAndPositiveNumbers() {
    val environment = benchmarkEnvironmentFixture()
    listOf(
      environment.copy(memoryBytes = 0),
      environment.copy(framebufferWidth = 1279),
      environment.copy(framebufferHeight = 721),
      environment.copy(contentScale = 0f),
      environment.copy(refreshRateHz = 0),
    ).forEach { invalidEnvironment ->
      assertFailure {
        aggregate(headOnlyFixture().map { it.copy(environment = invalidEnvironment) })
      }
    }
  }

  @Test
  fun strictBlockReaderRejectsJsonSymlinksWhenSupported() = withTempDirectory { directory ->
    val target = directory.resolve("target.txt")
    Files.writeString(target, BenchmarkJson.encodeToString(headOnlyFixture().first()))
    val link = directory.resolve("link.json")
    if (runCatching { Files.createSymbolicLink(link, target.fileName) }.isFailure) {
      return@withTempDirectory
    }

    assertFailure { readBenchmarkBlocks(directory) }
  }

  @Test
  fun strictBlockReaderEnforcesCumulativeByteLimit() = withTempDirectory { directory ->
    val padding = " ".repeat(3 * 1024 * 1024)
    headOnlyFixture().take(2).forEachIndexed { index, block ->
      Files.writeString(
        directory.resolve("$index.json"),
        BenchmarkJson.encodeToString(block) + padding,
      )
    }

    assertFailure { readBenchmarkBlocks(directory) }
  }
}

private fun aggregate(
  blocks: List<BenchmarkBlockResult>,
  baseSha: String? = BASE_SHA,
): BenchmarkArtifact =
  aggregateBenchmarkBlocks(
    suiteId = "glass",
    allowedScenarioIds = ALLOWED_SCENARIO_IDS,
    repository = REPOSITORY,
    baseSha = baseSha,
    headSha = HEAD_SHA,
    blocks = blocks,
  )

private fun abbaFixture(
  baseValue: Long,
  headValue: Long,
): List<BenchmarkBlockResult> = ALLOWED_SCENARIO_IDS.flatMap { scenarioId ->
  abbaScenarioFixture(baseValue, headValue, scenarioId)
}

private fun abbaScenarioFixture(
  baseValue: Long,
  headValue: Long,
  scenarioId: String,
  baseProtocol: Int = 1,
  headProtocol: Int = 1,
): List<BenchmarkBlockResult> = buildList {
  repeat(3) { round ->
    add(blockFixture(scenarioId, baseProtocol, "base", round, 0, baseValue))
    add(blockFixture(scenarioId, headProtocol, "head", round, 1, headValue))
    add(blockFixture(scenarioId, headProtocol, "head", round, 2, headValue))
    add(blockFixture(scenarioId, baseProtocol, "base", round, 3, baseValue))
  }
}

private fun headOnlyFixture(): List<BenchmarkBlockResult> = ALLOWED_SCENARIO_IDS.flatMap { scenarioId ->
  buildList {
    repeat(3) { round ->
      add(blockFixture(scenarioId, 1, "head", round, 1, 11_000_000))
      add(blockFixture(scenarioId, 1, "head", round, 2, 11_000_000))
    }
  }
}

private fun metadataEnvironment(value: String): BenchmarkEnvironment =
  benchmarkEnvironmentFixture().copy(
    osName = value,
    osVersion = value,
    architecture = value,
    cpu = value,
    javaVendor = value,
    javaVersion = value,
    composeVersion = value,
    skikoVersion = value,
    runnerImage = value,
    runnerImageVersion = value,
  )

private fun requiredMetadataVariants(
  environment: BenchmarkEnvironment,
  value: String,
): List<BenchmarkEnvironment> = listOf(
  environment.copy(osName = value),
  environment.copy(osVersion = value),
  environment.copy(architecture = value),
  environment.copy(cpu = value),
  environment.copy(javaVendor = value),
  environment.copy(javaVersion = value),
  environment.copy(composeVersion = value),
  environment.copy(skikoVersion = value),
)

private fun blockFixture(
  scenarioId: String,
  protocolVersion: Int,
  revision: String,
  round: Int,
  order: Int,
  value: Long,
) = BenchmarkBlockResult(
  suiteId = "glass",
  scenarioId = scenarioId,
  protocolVersion = protocolVersion,
  revision = revision,
  round = round,
  order = order,
  environment = benchmarkEnvironmentFixture(),
  workloadDurationNanos = if (scenarioId == "pointer_sweep") 4_000_000_000 else 6_000_000_000,
  samples = listOf(FrameSample(value, null), FrameSample(value, value)),
)

private inline fun withTempDirectory(block: (Path) -> Unit) {
  val directory = Files.createTempDirectory("haze-benchmark-aggregation")
  try {
    block(directory)
  } finally {
    directory.toFile().deleteRecursively()
  }
}

private val ALLOWED_SCENARIO_IDS = setOf("pointer_sweep", "playground_drag")
private const val REPOSITORY = "chrisbanes/haze"
private val BASE_SHA = "a".repeat(40)
private val HEAD_SHA = "b".repeat(40)
