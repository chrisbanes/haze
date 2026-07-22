// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes

public fun aggregateBenchmarkBlocks(
  suiteId: String,
  allowedScenarioIds: Set<String>,
  repository: String,
  baseSha: String?,
  headSha: String,
  blocks: List<BenchmarkBlockResult>,
): BenchmarkArtifact {
  validateAggregateIdentity(suiteId, allowedScenarioIds, repository, baseSha, headSha)
  require(blocks.isNotEmpty()) { "No benchmark blocks were provided" }
  require(blocks.sumOf { it.samples.size.toLong() } <= MAX_SAMPLE_COUNT) {
    "Benchmark blocks exceed the $MAX_SAMPLE_COUNT sample limit"
  }
  blocks.forEach { block -> validateBlock(block, suiteId, allowedScenarioIds) }
  require(baseSha != null || blocks.none { it.revision == BASE_REVISION }) {
    "Base benchmark blocks require a base SHA"
  }

  val groupedScenarios = blocks.groupBy(BenchmarkBlockResult::scenarioId)
  require(groupedScenarios.keys == allowedScenarioIds) {
    "Benchmark scenario ids must exactly match the allowed scenario ids"
  }
  val scenarios = groupedScenarios
    .toSortedMap()
    .map { (scenarioId, scenarioBlocks) -> summarizeScenario(scenarioId, scenarioBlocks) }

  return BenchmarkArtifact(
    suiteId = suiteId,
    repository = repository,
    baseSha = baseSha,
    headSha = headSha,
    scenarios = scenarios,
  )
}

internal fun readBenchmarkBlocks(input: Path): List<BenchmarkBlockResult> {
  val absoluteInput = input.toAbsolutePath().normalize()
  val inputAttributes = try {
    Files.readAttributes(absoluteInput, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
  } catch (failure: IOException) {
    throw IllegalArgumentException("Unable to inspect benchmark input directory: $input", failure)
  }
  require(inputAttributes.isDirectory) { "Benchmark input is not a directory: $input" }
  val files = try {
    Files.newDirectoryStream(absoluteInput).use { entries ->
      entries
        .filter { it.fileName.toString().endsWith(".json") }
        .sortedBy { it.fileName.toString() }
    }
  } catch (failure: IOException) {
    throw IllegalArgumentException("Unable to list benchmark input directory: $input", failure)
  }
  require(files.isNotEmpty()) { "No JSON benchmark blocks found in: $input" }
  var totalBytes = 0L
  return files.map { file ->
    try {
      val attributes = Files.readAttributes(
        file,
        BasicFileAttributes::class.java,
        NOFOLLOW_LINKS,
      )
      require(attributes.isRegularFile) {
        "Benchmark JSON entry is not a regular file: $file"
      }
      val bytes = readJsonEntry(file) { count ->
        totalBytes += count
        require(totalBytes <= MAX_ARTIFACT_BYTES) {
          "Raw benchmark JSON exceeds the $MAX_ARTIFACT_BYTES byte limit"
        }
      }
      BenchmarkJson.decodeFromString<BenchmarkBlockResult>(
        bytes.decodeToString(throwOnInvalidSequence = true),
      )
    } catch (failure: IOException) {
      throw IllegalArgumentException("Unable to read benchmark JSON entry: $file", failure)
    }
  }
}

private fun readJsonEntry(
  file: Path,
  onBytesRead: (Int) -> Unit,
): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteBuffer.allocate(BYTE_BUFFER_SIZE)
  Files.newByteChannel(file, setOf<OpenOption>(READ, NOFOLLOW_LINKS)).use { channel ->
    while (true) {
      val count = channel.read(buffer)
      if (count < 0) break
      if (count == 0) continue
      onBytesRead(count)
      buffer.flip()
      val chunk = ByteArray(count)
      buffer.get(chunk)
      output.write(chunk)
      buffer.clear()
    }
  }
  return output.toByteArray()
}

internal fun validateAggregateIdentity(
  suiteId: String,
  allowedScenarioIds: Set<String>,
  repository: String,
  baseSha: String?,
  headSha: String,
) {
  require(suiteId.matches(IdentifierRegex)) { "Invalid suite id: $suiteId" }
  require(allowedScenarioIds.isNotEmpty()) { "At least one scenario id is required" }
  require(allowedScenarioIds.all { it.matches(IdentifierRegex) }) {
    "Invalid allowed scenario id"
  }
  val repositorySegments = repository.split('/')
  require(
    repositorySegments.size == 2 && repositorySegments.all { segment ->
      segment.matches(RepositorySegmentRegex) && segment != "." && segment != ".."
    },
  ) { "Invalid repository: $repository" }
  require(baseSha == null || baseSha.matches(ShaRegex)) { "Invalid base SHA: $baseSha" }
  require(headSha.matches(ShaRegex)) { "Invalid head SHA: $headSha" }
}

private fun validateBlock(
  block: BenchmarkBlockResult,
  suiteId: String,
  allowedScenarioIds: Set<String>,
) {
  require(block.schemaVersion == RUNNER_SCHEMA_VERSION) {
    "Unsupported block schema version: ${block.schemaVersion}"
  }
  require(block.suiteId == suiteId) {
    "Block suite ${block.suiteId} does not match $suiteId"
  }
  require(block.scenarioId in allowedScenarioIds) {
    "Unknown scenario id: ${block.scenarioId}"
  }
  require(block.protocolVersion > 0) { "Invalid protocol version: ${block.protocolVersion}" }
  require(block.revision == BASE_REVISION || block.revision == HEAD_REVISION) {
    "Invalid revision: ${block.revision}"
  }
  require(block.round in 0..2) { "Invalid round: ${block.round}" }
  require(block.order in 0..3) { "Invalid order: ${block.order}" }
  require(block.workloadDurationNanos >= 0) { "Workload duration must be nonnegative" }
  require(block.samples.isNotEmpty()) { "Measured render samples must not be empty" }
  require(block.samples.any { it.callbackIntervalNanos != null }) {
    "Measured callback interval samples must not be empty"
  }
  block.samples.forEach { sample ->
    require(sample.renderDurationNanos >= 0) { "Render durations must be nonnegative" }
    require(sample.renderDurationNanos.toDouble().isFinite()) {
      "Render duration is not finite-compatible"
    }
    sample.callbackIntervalNanos?.let { interval ->
      require(interval >= 0) { "Callback intervals must be nonnegative" }
      require(interval.toDouble().isFinite()) { "Callback interval is not finite-compatible" }
    }
  }
  validateEnvironment(block.environment)
}

private fun validateEnvironment(environment: BenchmarkEnvironment) {
  listOf(
    "osName" to environment.osName,
    "osVersion" to environment.osVersion,
    "architecture" to environment.architecture,
    "cpu" to environment.cpu,
    "javaVendor" to environment.javaVendor,
    "javaVersion" to environment.javaVersion,
    "composeVersion" to environment.composeVersion,
    "skikoVersion" to environment.skikoVersion,
    "renderApi" to environment.renderApi,
  ).forEach { (name, value) -> validateRequiredMetadata(name, value) }
  environment.runnerImage?.let { validateRequiredMetadata("runnerImage", it) }
  environment.runnerImageVersion?.let { validateRequiredMetadata("runnerImageVersion", it) }
  require(environment.renderApi == METAL_RENDER_API) {
    "Desktop benchmark requires METAL but found ${environment.renderApi}"
  }
  require(environment.memoryBytes > 0) { "Memory size must be positive" }
  require(
    environment.framebufferWidth == TARGET_FRAMEBUFFER_WIDTH &&
      environment.framebufferHeight == TARGET_FRAMEBUFFER_HEIGHT,
  ) {
    "Framebuffer dimensions must be ${TARGET_FRAMEBUFFER_WIDTH}x$TARGET_FRAMEBUFFER_HEIGHT"
  }
  require(environment.contentScale.isFinite() && environment.contentScale > 0f) {
    "Content scale must be finite and positive"
  }
  require(environment.refreshRateHz > 0) { "Refresh rate must be positive" }
}

private fun validateRequiredMetadata(name: String, value: String) {
  require(value.isNotBlank()) { "$name must not be blank" }
  require(value.encodeToByteArray().size <= MAX_METADATA_BYTES) {
    "$name exceeds the $MAX_METADATA_BYTES byte limit"
  }
}

private fun summarizeScenario(
  scenarioId: String,
  blocks: List<BenchmarkBlockResult>,
): ScenarioSummary {
  val sortedBlocks = blocks.sortedWith(
    compareBy(BenchmarkBlockResult::round, BenchmarkBlockResult::order, BenchmarkBlockResult::revision),
  )
  val slots = sortedBlocks.map { BlockSlot(it.revision, it.round, it.order) }
  require(slots.distinct().size == slots.size) {
    "Scenario $scenarioId contains duplicate revision/round/order blocks"
  }
  val slotSet = slots.toSet()
  require(slotSet == ExpectedAbbaSlots || slotSet == ExpectedHeadOnlySlots) {
    "Scenario $scenarioId does not contain the required ABBA or head-only slots"
  }

  val baseBlocks = sortedBlocks.filter { it.revision == BASE_REVISION }
  val headBlocks = sortedBlocks.filter { it.revision == HEAD_REVISION }
  val baseProtocols = baseBlocks.map(BenchmarkBlockResult::protocolVersion).distinct()
  val headProtocols = headBlocks.map(BenchmarkBlockResult::protocolVersion).distinct()
  require(baseProtocols.size <= 1) { "Scenario $scenarioId mixes base protocol versions" }
  require(headProtocols.size == 1) { "Scenario $scenarioId mixes head protocol versions" }

  val baseProtocol = baseProtocols.singleOrNull()
  val headProtocol = headProtocols.single()
  val comparable = baseBlocks.isNotEmpty() && baseProtocol == headProtocol
  val baseRender = baseBlocks.takeIf(List<*>::isNotEmpty)?.let { summarizeMetric(it, ::renderValues) }
  val headRender = summarizeMetric(headBlocks, ::renderValues)
  val baseInterval = baseBlocks.takeIf(List<*>::isNotEmpty)?.let { summarizeMetric(it, ::intervalValues) }
  val headInterval = summarizeMetric(headBlocks, ::intervalValues)

  return ScenarioSummary(
    id = scenarioId,
    baseProtocolVersion = baseProtocol,
    headProtocolVersion = headProtocol,
    comparable = comparable,
    baseRender = baseRender,
    headRender = headRender,
    baseInterval = baseInterval,
    headInterval = headInterval,
    renderPairedDeltaPercent = if (comparable) {
      pairedMetricDelta(baseBlocks, headBlocks, ::renderValues)
    } else {
      null
    },
    intervalPairedDeltaPercent = if (comparable) {
      pairedMetricDelta(baseBlocks, headBlocks, ::intervalValues)
    } else {
      null
    },
    blocks = sortedBlocks,
  )
}

private fun summarizeMetric(
  blocks: List<BenchmarkBlockResult>,
  values: (BenchmarkBlockResult) -> List<Long>,
): MetricSummary {
  val allValues = blocks.flatMap(values)
  require(allValues.isNotEmpty()) { "Metric sample set must not be empty" }
  val blockMedians = blocks.map { block -> values(block).median() }
  val variation = robustRelativeVariationPercent(blockMedians)
  require(variation.isFinite()) { "Metric variation is not finite" }
  val above16MillisCount = allValues.count { it > SIXTEEN_MILLIS_NANOS }
  val above33MillisCount = allValues.count { it > THIRTY_THREE_MILLIS_NANOS }
  return MetricSummary(
    sampleCount = allValues.size,
    p50Nanos = nearestRank(allValues, 0.50),
    p95Nanos = nearestRank(allValues, 0.95),
    p99Nanos = nearestRank(allValues, 0.99),
    above16MillisCount = above16MillisCount,
    above16MillisPercent = percent(above16MillisCount, allValues.size),
    above33MillisCount = above33MillisCount,
    above33MillisPercent = percent(above33MillisCount, allValues.size),
    robustVariationPercent = variation,
    noisy = variation > 10.0,
  )
}

private fun pairedMetricDelta(
  baseBlocks: List<BenchmarkBlockResult>,
  headBlocks: List<BenchmarkBlockResult>,
  values: (BenchmarkBlockResult) -> List<Long>,
): Double {
  val baseMedians = baseBlocks.map { values(it).median() }
  require(baseMedians.all { it > 0.0 }) {
    "Paired delta requires positive base block medians"
  }
  val delta = pairedDeltaPercent(
    base = baseMedians,
    head = headBlocks.map { values(it).median() },
  )
  require(delta.isFinite()) { "Paired delta is not finite" }
  return delta
}

private fun renderValues(block: BenchmarkBlockResult): List<Long> =
  block.samples.map(FrameSample::renderDurationNanos)

private fun intervalValues(block: BenchmarkBlockResult): List<Long> =
  block.samples.mapNotNull(FrameSample::callbackIntervalNanos)

private fun List<Long>.median(): Double {
  require(isNotEmpty()) { "Block metric sample set must not be empty" }
  val sorted = sorted()
  val middle = size / 2
  return if (size % 2 == 1) {
    sorted[middle].toDouble()
  } else {
    sorted[middle - 1] / 2.0 + sorted[middle] / 2.0
  }
}

private fun percent(count: Int, total: Int): Double {
  require(total > 0)
  return count.toDouble() / total * 100.0
}

private data class BlockSlot(val revision: String, val round: Int, val order: Int)

private val ExpectedAbbaSlots = buildSet {
  repeat(3) { round ->
    add(BlockSlot(BASE_REVISION, round, 0))
    add(BlockSlot(HEAD_REVISION, round, 1))
    add(BlockSlot(HEAD_REVISION, round, 2))
    add(BlockSlot(BASE_REVISION, round, 3))
  }
}

private val ExpectedHeadOnlySlots = buildSet {
  repeat(3) { round ->
    add(BlockSlot(HEAD_REVISION, round, 1))
    add(BlockSlot(HEAD_REVISION, round, 2))
  }
}

private val IdentifierRegex = Regex("[a-z][a-z0-9_]{0,63}")
private val RepositorySegmentRegex = Regex("[A-Za-z0-9_.-]{1,100}")
private val ShaRegex = Regex("[0-9a-fA-F]{40}")

private const val RUNNER_SCHEMA_VERSION = 1
private const val BASE_REVISION = "base"
private const val HEAD_REVISION = "head"
private const val METAL_RENDER_API = "METAL"
private const val MAX_SAMPLE_COUNT = 100_000L
private const val MAX_ARTIFACT_BYTES = 5L * 1024 * 1024
private const val MAX_METADATA_BYTES = 256
private const val TARGET_FRAMEBUFFER_WIDTH = 1280
private const val TARGET_FRAMEBUFFER_HEIGHT = 720
private const val BYTE_BUFFER_SIZE = 8192
private const val SIXTEEN_MILLIS_NANOS = 16_666_667L
private const val THIRTY_THREE_MILLIS_NANOS = 33_333_333L
