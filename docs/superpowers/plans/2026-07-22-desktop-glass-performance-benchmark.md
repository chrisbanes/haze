# Desktop Glass Performance Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an observational Glass benchmark that runs the real JVM Desktop Skiko/Metal path on
a pinned macOS GitHub runner, compares a pull request with its base on the same host, uploads raw
measurements, and updates one informational pull-request comment.

**Architecture:** A JVM-only `:internal:benchmark-desktop` library owns the window, input replay,
Skiko callbacks, statistics, result schema, and command handling. A separate
`:haze-benchmarks:glass:desktop` executable owns the isolated pointer and Playground scenarios.
An unprivileged workflow executes pull-request code and uploads JSON; a trusted `workflow_run`
workflow validates that JSON and posts the comment.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, Skiko 0.144.6, kotlinx.serialization
1.11.0, JDK 21, Kotlin test/assertk, Node.js built-in test runner, GitHub Actions `macos-26`.

## Global Constraints

- Benchmark JVM Desktop on macOS only; do not add Android, iOS, or multiplatform runner code.
- Leave the existing Android `:internal:benchmark` module unchanged.
- The shared runner must not depend on Haze, Glass, haze-blur, or sample modules.
- Glass scenarios, tests, entry point, and distribution live only in
  `:haze-benchmarks:glass:desktop`.
- Reserve `:haze-benchmarks:glass:common` for content shared by two or more platform suites and
  `:haze-benchmarks:glass:android` for a future Android instrumentation runner; do not create either
  module in this Desktop-only phase.
- Require `GraphicsApi.METAL`; never silently fall back to software rendering.
- Use a 1280 by 720 physical-pixel backing surface.
- Launch measured processes with JDK 21, `-Xms512m`, and `-Xmx512m`.
- Measure `SkiaLayerAnalytics` callbacks; do not label them GPU time, presentation latency, or
  dropped frames.
- Use one 4-second, 120 Hz isolated pointer sweep and one 6-second, 60 Hz Playground interaction.
- Compare base/head in `base -> head -> head -> base`, repeated three times per scenario.
- Mark a metric noisy above 10 percent robust relative variation; never fail CI for a performance
  value.
- Limit aggregate JSON to 5 MiB and diagnostics to 2,048 UTF-8 bytes.
- Keep the timed Glass benchmark and Metal smoke task detached from root `check`.
- Use `macos-26`, not `macos-latest`.
- The trusted reporter must never execute pull-request code or trust artifact-provided PR identity.

---

## File Structure

### Shared runner module

- `internal/benchmark-desktop/build.gradle.kts`: JVM/Compose/serialization dependencies.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/DesktopBenchmarkScenario.kt`:
  scenario and normalized input-event contract.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkModel.kt`:
  serializable block, environment, aggregate, and summary models.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkStatistics.kt`:
  percentile, budget, MAD, noise, and paired-delta calculations.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/FrameRecorder.kt`:
  thread-safe `SkiaLayerAnalytics` callback recorder.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/DesktopInputReplayer.kt`:
  normalized AWT mouse-event replay on the Skia layer.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/ComposeDesktopBenchmarkHost.kt`:
  fixed-pixel Compose window lifecycle and Metal verification.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkEnvironment.kt`:
  runner, JVM, Skiko, framebuffer, display, CPU, and memory metadata.
- `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkCommand.kt`:
  `probe`, `run`, and `aggregate` parsing and execution.
- Matching focused tests under
  `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/`.

### Glass suite module

- `haze-benchmarks/glass/desktop/build.gradle.kts`: Glass/sample dependencies and Desktop
  application packaging.
- `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/Main.kt`:
  Glass suite registry and process entry point.
- `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/IsolatedGlassScenario.kt`:
  focused pointer-highlight scene and 120 Hz path.
- `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/PlaygroundGlassScenario.kt`:
  fixed Playground scene and 60 Hz hover/press/drag path.
- Matching scenario tests under `haze-benchmarks/glass/desktop/src/jvmTest/kotlin/`.

### CI reporting

- `.github/workflows/desktop-glass-benchmark.yml`: unprivileged macOS measurement workflow.
- `.github/workflows/desktop-glass-benchmark-report.yml`: trusted comment workflow.
- `.github/scripts/desktop-glass-benchmark-report.mjs`: strict artifact validation and Markdown
  rendering.
- `.github/scripts/desktop-glass-benchmark-report.test.mjs`: fixture-driven Node tests.
- `.github/workflows/build.yml`: execute only the fast Node reporter tests on normal CI.

---

### Task 1: Create the Shared Scenario Contract

**Files:**

- Modify: `settings.gradle.kts`
- Create: `internal/benchmark-desktop/build.gradle.kts`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/DesktopBenchmarkScenario.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/DesktopBenchmarkScenarioTest.kt`

**Interfaces:**

- Produces: `DesktopBenchmarkScenario`, `DesktopInputEvent`, `DesktopInputEventType`,
  `NormalizedPoint`, and `validateScenario()`.
- The declarations are `public` because Glass consumes them across a Gradle-module boundary, but
  the module is internal and unpublished.

- [ ] **Step 1: Register the shared module and add its test dependencies**

Add `":internal:benchmark-desktop"` to `settings.gradle.kts`. The Glass suite is registered in
Task 3 when its build file is created. Create the shared build file:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
  jvm()

  sourceSets {
    jvmMain {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    jvmTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)
      }
    }
  }
}
```

- [ ] **Step 2: Write validation tests for the cross-module scenario contract**

```kotlin
class DesktopBenchmarkScenarioTest {
  @Test fun validScenario_isAccepted() {
    assertThat(validateScenario(FakeScenario("pointer_sweep", 1, validEvents()))).isSameInstanceAs(Unit)
  }

  @Test fun identifiers_areRestricted() {
    assertFailure { validateScenario(FakeScenario("Pointer Sweep!", 1, validEvents())) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test fun events_areOrderedAndNormalized() {
    assertFailure {
      validateScenario(FakeScenario("pointer_sweep", 1, listOf(
        DesktopInputEvent(10, DesktopInputEventType.Move, NormalizedPoint(0.5f, 0.5f)),
        DesktopInputEvent(5, DesktopInputEventType.Exit, null),
      )))
    }.isInstanceOf<IllegalArgumentException>()
  }
}

private class FakeScenario(
  override val id: String,
  override val protocolVersion: Int,
  override val events: List<DesktopInputEvent>,
) : DesktopBenchmarkScenario {
  @Composable override fun Content() = Unit
  override suspend fun reset() = Unit
}

private fun validEvents() = listOf(
  DesktopInputEvent(0, DesktopInputEventType.Move, NormalizedPoint(0.5f, 0.5f)),
  DesktopInputEvent(1, DesktopInputEventType.Exit, null),
)
```

- [ ] **Step 3: Run the focused test and verify failure**

Run:

```shell
./gradlew :internal:benchmark-desktop:jvmTest \
  --tests '*DesktopBenchmarkScenarioTest'
```

Expected: compilation fails because the contract types do not exist.

- [ ] **Step 4: Implement the minimal contract and validation**

```kotlin
package dev.chrisbanes.haze.benchmark.desktop

import androidx.compose.runtime.Composable

public data class NormalizedPoint(val x: Float, val y: Float) {
  init {
    require(x.isFinite() && x in 0f..1f)
    require(y.isFinite() && y in 0f..1f)
  }
}

public enum class DesktopInputEventType { Move, Press, Drag, Release, Exit }

public data class DesktopInputEvent(
  val offsetNanos: Long,
  val type: DesktopInputEventType,
  val position: NormalizedPoint?,
)

public interface DesktopBenchmarkScenario {
  public val id: String
  public val protocolVersion: Int
  public val events: List<DesktopInputEvent>

  @Composable public fun Content()

  public suspend fun reset()

  public suspend fun verifyCompleted() = Unit
}

public fun validateScenario(scenario: DesktopBenchmarkScenario) {
  require(scenario.id.matches(Regex("[a-z][a-z0-9_]{0,63}")))
  require(scenario.protocolVersion > 0)
  require(scenario.events.isNotEmpty())
  require(scenario.events.zipWithNext().all { (a, b) -> a.offsetNanos <= b.offsetNanos })
  scenario.events.forEach { event ->
    require(event.offsetNanos >= 0)
    require((event.type == DesktopInputEventType.Exit) == (event.position == null))
  }
}
```

- [ ] **Step 5: Run tests and formatting**

Run:

```shell
./gradlew :internal:benchmark-desktop:jvmTest :internal:benchmark-desktop:spotlessCheck
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```shell
git add settings.gradle.kts internal/benchmark-desktop
git commit -m "Add shared Desktop benchmark contract"
```

---

### Task 2: Add Results, Statistics, and JSON

**Files:**

- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkModel.kt`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkStatistics.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkStatisticsTest.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkJsonTest.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkTestFixtures.kt`

**Interfaces:**

- Produces: `FrameSample`, `BenchmarkEnvironment`, `BenchmarkBlockResult`, `BenchmarkArtifact`,
  `ScenarioSummary`, `MetricSummary`, `nearestRank()`, `robustRelativeVariationPercent()`,
  `isNoisy()`, `pairedDeltaPercent()`, `encodeArtifact()`, and `boundedDiagnostic()`.
- Consumes: scenario identifiers and protocol versions from Task 1.

- [ ] **Step 1: Write failing statistical behavior tests**

```kotlin
class BenchmarkStatisticsTest {
  @Test fun nearestRankPercentiles_areStable() {
    val values = listOf(10L, 20L, 30L, 40L, 50L)
    assertThat(nearestRank(values, 0.50)).isEqualTo(30L)
    assertThat(nearestRank(values, 0.95)).isEqualTo(50L)
  }

  @Test fun variationAboveTenPercent_isNoisy() {
    assertThat(robustRelativeVariationPercent(listOf(10.0, 10.0, 14.0, 14.0)))
      .isGreaterThan(10.0)
    assertThat(isNoisy(listOf(10.0, 10.0, 14.0, 14.0))).isTrue()
  }

  @Test fun pairedDelta_usesThreeAbbaRounds() {
    val base = listOf(10.0, 10.0, 10.0, 10.0, 10.0, 10.0)
    val head = listOf(11.0, 11.0, 11.0, 11.0, 11.0, 11.0)
    assertThat(abs(pairedDeltaPercent(base, head) - 10.0)).isLessThan(0.0001)
  }
}
```

- [ ] **Step 2: Write failing JSON round-trip and size tests**

```kotlin
class BenchmarkJsonTest {
  @Test fun blockRoundTrips() {
    val block = benchmarkBlockFixture()
    assertThat(BenchmarkJson.decodeFromString<BenchmarkBlockResult>(
      BenchmarkJson.encodeToString(block),
    )).isEqualTo(block)
  }

  @Test fun artifactOverFiveMiB_isRejected() {
    val oversized = artifactFixture(diagnostic = "x".repeat(5 * 1024 * 1024))
    assertFailure { encodeArtifact(oversized) }.isInstanceOf<IllegalArgumentException>()
  }

  @Test fun diagnosticOver2048Bytes_isTruncatedAtUtf8Boundary() {
    assertThat(boundedDiagnostic("é".repeat(2048)).encodeToByteArray().size)
      .isLessThanOrEqualTo(2048)
  }
}

private fun benchmarkBlockFixture() = BenchmarkBlockResult(
  suiteId = "glass",
  scenarioId = "pointer_sweep",
  protocolVersion = 1,
  revision = "head",
  round = 0,
  order = 1,
  environment = benchmarkEnvironmentFixture(),
  workloadDurationNanos = 4_000_000_000,
  samples = listOf(FrameSample(10_000_000, null), FrameSample(11_000_000, 16_000_000)),
)

internal fun benchmarkEnvironmentFixture() = BenchmarkEnvironment(
  osName = "Mac OS X",
  osVersion = "26.0",
  architecture = "aarch64",
  cpu = "Apple M1",
  memoryBytes = 7_000_000_000,
  javaVendor = "Azul Systems, Inc.",
  javaVersion = "21",
  composeVersion = "1.11.1",
  skikoVersion = "0.144.6",
  renderApi = "METAL",
  framebufferWidth = 1280,
  framebufferHeight = 720,
  contentScale = 2f,
  refreshRateHz = 60,
  runnerImage = "macos-26",
  runnerImageVersion = "test",
)

private fun artifactFixture(diagnostic: String) = BenchmarkArtifact(
  suiteId = "glass",
  repository = "chrisbanes/haze",
  baseSha = null,
  headSha = "b".repeat(40),
  scenarios = emptyList(),
  diagnostic = diagnostic,
)
```

- [ ] **Step 3: Run tests and verify failure**

Run:

```shell
./gradlew :internal:benchmark-desktop:jvmTest \
  --tests '*BenchmarkStatisticsTest' \
  --tests '*BenchmarkJsonTest'
```

Expected: compilation fails because the result and statistics functions do not exist.

- [ ] **Step 4: Implement the serializable result model**

Use integer nanoseconds in JSON to avoid non-finite floating-point samples:

```kotlin
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
```

- [ ] **Step 5: Implement the exact statistical rules**

```kotlin
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
```

Use `16_666_667L` and `33_333_333L` as reference budgets when Task 6 builds metric summaries.

- [ ] **Step 6: Implement bounded JSON helpers**

```kotlin
public val BenchmarkJson: Json = Json {
  encodeDefaults = true
  explicitNulls = true
  ignoreUnknownKeys = false
}

public fun encodeArtifact(value: BenchmarkArtifact): String {
  val encoded = BenchmarkJson.encodeToString(value)
  require(encoded.encodeToByteArray().size <= 5 * 1024 * 1024)
  return encoded
}

public fun boundedDiagnostic(value: String): String = buildString {
  for (character in value) {
    val candidate = this + character
    if (candidate.encodeToByteArray().size > 2048) break
    append(character)
  }
}
```

- [ ] **Step 7: Run tests and commit**

```shell
./gradlew :internal:benchmark-desktop:jvmTest :internal:benchmark-desktop:spotlessCheck
git add internal/benchmark-desktop
git commit -m "Add Desktop benchmark result model"
```

Expected: tests pass and the commit contains no Glass dependency.

---

### Task 3: Build the Compose/Skiko Host and Prove Metal Availability

**Files:**

- Modify: `settings.gradle.kts`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/FrameRecorder.kt`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/DesktopInputReplayer.kt`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkEnvironment.kt`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/ComposeDesktopBenchmarkHost.kt`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkCommand.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/FrameRecorderTest.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkCommandTest.kt`
- Create: `haze-benchmarks/glass/desktop/build.gradle.kts`
- Create: `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/Main.kt`
- Create: `.github/workflows/desktop-glass-benchmark.yml`

**Interfaces:**

- Consumes: scenario contract and block models from Tasks 1-2.
- Produces: `FrameRecorder`, `ComposeDesktopBenchmarkHost.runBlock()`,
  `runDesktopBenchmarkSuite()`, and `probe`/`run` commands.

- [ ] **Step 1: Write callback-recorder tests**

```kotlin
class FrameRecorderTest {
  @Test fun warmupFrames_areExcluded() {
    val clock = FakeNanoClock(100, 110, 120, 135, 150)
    val recorder = FrameRecorder(clock::next)
    recorder.beforeFrameRender()
    recorder.afterFrameRender()
    recorder.startMeasurement()
    recorder.beforeFrameRender()
    recorder.afterFrameRender()
    assertThat(recorder.stopMeasurement()).containsExactly(
      FrameSample(renderDurationNanos = 15, callbackIntervalNanos = null),
    )
  }

  @Test fun intervalStartsWithSecondMeasuredFrame() {
    val recorder = recorderForTwoMeasuredFrames()
    assertThat(recorder.stopMeasurement().map { it.callbackIntervalNanos })
      .containsExactly(null, 20L)
  }
}

private class FakeNanoClock(vararg values: Long) {
  private val iterator = values.iterator()
  fun next(): Long = iterator.nextLong()
}

private fun recorderForTwoMeasuredFrames(): FrameRecorder {
  val clock = FakeNanoClock(100, 110, 115, 130)
  return FrameRecorder(clock::next).apply {
    startMeasurement()
    beforeFrameRender()
    afterFrameRender()
    beforeFrameRender()
    afterFrameRender()
  }
}
```

- [ ] **Step 2: Write command-validation tests**

```kotlin
class BenchmarkCommandTest {
  @Test fun runRequiresKnownScenarioAndOutput() {
    assertFailure {
      parseBenchmarkCommand(arrayOf("run", "--scenario", "missing"), setOf("pointer_sweep"))
    }.isInstanceOf<IllegalArgumentException>()
  }

  @Test fun probeDoesNotRequireScenarios() {
    assertThat(parseBenchmarkCommand(arrayOf("probe"), emptySet()))
      .isEqualTo(BenchmarkCommand.Probe)
  }
}
```

- [ ] **Step 3: Run focused tests and verify failure**

```shell
./gradlew :internal:benchmark-desktop:jvmTest \
  --tests '*FrameRecorderTest' \
  --tests '*BenchmarkCommandTest'
```

Expected: compilation fails because the recorder and commands do not exist.

- [ ] **Step 4: Implement the Skiko analytics recorder**

`FrameRecorder` implements `SkiaLayerAnalytics` and returns one `DeviceAnalytics` instance from
`device(name: String, os: OS, api: GraphicsApi, device: String)`. Route both first-frame and normal
frame callbacks through the same private methods:

```kotlin
internal class FrameRecorder(
  private val nanoTime: () -> Long = System::nanoTime,
) : SkiaLayerAnalytics {
  private val lock = Any()
  private var measuring = false
  private var frameStart = 0L
  private var previousFrameEnd: Long? = null
  private val samples = mutableListOf<FrameSample>()

  private val deviceAnalytics = object : SkiaLayerAnalytics.DeviceAnalytics {
    override fun beforeFirstFrameRender() = this@FrameRecorder.beforeFrameRender()
    override fun afterFirstFrameRender() = this@FrameRecorder.afterFrameRender()
    override fun beforeFrameRender() = this@FrameRecorder.beforeFrameRender()
    override fun afterFrameRender() = this@FrameRecorder.afterFrameRender()
  }

  override fun device(name: String, os: OS, api: GraphicsApi, device: String) = deviceAnalytics

  internal fun startMeasurement() = synchronized(lock) {
    samples.clear()
    previousFrameEnd = null
    measuring = true
  }

  internal fun stopMeasurement(): List<FrameSample> = synchronized(lock) {
    measuring = false
    samples.toList()
  }

  internal fun beforeFrameRender() = synchronized(lock) {
    frameStart = nanoTime()
  }

  internal fun afterFrameRender() = synchronized(lock) {
    val end = nanoTime()
    if (!measuring) return@synchronized
    require(end >= frameStart)
    samples += FrameSample(
      renderDurationNanos = end - frameStart,
      callbackIntervalNanos = previousFrameEnd?.let { end - it },
    )
    previousFrameEnd = end
  }
}
```

In `afterFrameRender`, sample only while `measuring`, reject a timestamp before `frameStart`, and
calculate the interval from the prior measured frame end.

- [ ] **Step 5: Implement normalized input replay on the Skia component**

Find exactly one descendant `SkiaLayer` below `ComposeWindow.contentPane`. Dispatch every event on
the Swing EDT to `SkiaLayer.canvas`, because Skiko installs Compose's mouse listeners on that
backed `HardwareLayer`, not on the outer `SkiaLayer`. Derive coordinates from the canvas's current
logical width and height:

```kotlin
private fun DesktopInputEvent.toAwtEvent(target: Component): MouseEvent {
  val point = position
  val x = point?.let { (it.x * target.width).roundToInt() } ?: 0
  val y = point?.let { (it.y * target.height).roundToInt() } ?: 0
  val (id, button, modifiers) = when (type) {
    DesktopInputEventType.Move -> Triple(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, 0)
    DesktopInputEventType.Press -> Triple(
      MouseEvent.MOUSE_PRESSED,
      MouseEvent.BUTTON1,
      InputEvent.BUTTON1_DOWN_MASK,
    )
    DesktopInputEventType.Drag -> Triple(
      MouseEvent.MOUSE_DRAGGED,
      MouseEvent.NOBUTTON,
      InputEvent.BUTTON1_DOWN_MASK,
    )
    DesktopInputEventType.Release -> Triple(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 0)
    DesktopInputEventType.Exit -> Triple(MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON, 0)
  }
  return MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button)
}
```

Replay against monotonic target offsets rather than calling a fixed `delay` after each event, so
scheduler lateness does not lengthen the workload.

- [ ] **Step 6: Implement the fixed-pixel Compose window and Metal check**

Implement this host boundary:

```kotlin
internal class ComposeDesktopBenchmarkHost {
  suspend fun runBlock(
    command: BenchmarkCommand.Run,
    scenarioFactory: () -> DesktopBenchmarkScenario,
  ): BenchmarkBlockResult

  suspend fun probe(): BenchmarkEnvironment
}
```

Create `ComposeWindow(skiaLayerAnalytics = recorder)` on the Swing EDT, make it undecorated, set
content, show it, read the Skia layer's `contentScale`, resize the logical content to
`round(1280 / contentScale)` by `round(720 / contentScale)`, and verify the resulting backing size
within one pixel. Fail unless:

```kotlin
check(window.renderApi == GraphicsApi.METAL) {
  "Desktop benchmark requires METAL but Skiko selected ${window.renderApi}"
}
```

Warm up by resetting and replaying the scenario once with recording disabled, then call
`verifyCompleted()`. Reset again, wait 500 ms for interaction release, start the recorder, replay
once, call `verifyCompleted()`, request one final render, wait for the callback, then stop. Always
dispose the window on the EDT in `finally`.

- [ ] **Step 7: Collect bounded environment metadata**

Use Java properties for OS/JVM, environment variables `ImageOS` and `ImageVersion`, package
implementation versions from `ComposeWindow` and `SkiaLayer`, display mode refresh rate, and these
macOS commands with 2-second timeouts:

```kotlin
private fun sysctl(name: String): String = ProcessBuilder("/usr/sbin/sysctl", "-n", name)
  .redirectErrorStream(true)
  .start()
  .also { check(it.waitFor(2, TimeUnit.SECONDS)) }
  .inputStream.bufferedReader().readText().trim()
```

Read `machdep.cpu.brand_string` and `hw.memsize`; bound every metadata string to 256 UTF-8 bytes.

- [ ] **Step 8: Add the Glass executable shell and probe-only workflow**

Add `":haze-benchmarks:glass:desktop"` to `settings.gradle.kts`, then create the Glass build file:

```kotlin
plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  jvm()
  compilerOptions { optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi") }
  sourceSets {
    jvmMain.dependencies {
      implementation(projects.internal.benchmarkDesktop)
      implementation(projects.hazeGlass)
      implementation(projects.sample.shared)
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.assertk)
    }
  }
}

compose.desktop.application {
  mainClass = "dev.chrisbanes.haze.benchmark.desktop.glass.MainKt"
}
```

The initial main passes an empty registry, sufficient for `probe`:

```kotlin
fun main(args: Array<String>) {
  exitProcess(runDesktopBenchmarkSuite(args, suiteId = "glass", scenarioFactories = emptyList()))
}
```

Define the shared command boundary as:

```kotlin
public fun runDesktopBenchmarkSuite(
  args: Array<String>,
  suiteId: String,
  scenarioFactories: List<() -> DesktopBenchmarkScenario>,
): Int

internal sealed interface BenchmarkCommand {
  data object Probe : BenchmarkCommand
  data class Run(
    val scenarioId: String,
    val revision: String,
    val round: Int,
    val order: Int,
    val output: Path,
    val smoke: Boolean,
  ) : BenchmarkCommand
  data class Aggregate(
    val input: Path,
    val repository: String,
    val baseSha: String?,
    val headSha: String,
    val output: Path,
  ) : BenchmarkCommand
}
```

Create a probe-only `pull_request` workflow on `macos-26`, scoped to the two benchmark modules and
its own workflow file, that packages the Uber JAR and runs:

```shell
SKIKO_RENDER_API=METAL java -Xms512m -Xmx512m \
  -jar haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar \
  probe
```

- [ ] **Step 9: Run local verification and commit**

```shell
./gradlew \
  :internal:benchmark-desktop:jvmTest \
  :haze-benchmarks:glass:desktop:packageUberJarForCurrentOS \
  :internal:benchmark-desktop:spotlessCheck \
  :haze-benchmarks:glass:desktop:spotlessCheck
SKIKO_RENDER_API=METAL java -Xms512m -Xmx512m \
  -jar haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar \
  probe
git add internal/benchmark-desktop haze-benchmarks/glass/desktop \
  .github/workflows/desktop-glass-benchmark.yml
git commit -m "Add Desktop benchmark Metal host"
```

Expected: the local probe prints structured Metal environment JSON and exits zero.

- [ ] **Step 10: Verify the hosted runner before continuing**

After the implementation branch has a pull request:

```shell
gh pr checks "$(gh pr view --json number --jq .number)" --watch
gh run view "$(gh run list --workflow 'Desktop Glass Benchmark' --limit 1 --json databaseId --jq '.[0].databaseId')" --log
```

Expected: `GraphicsApi.METAL` and a 1280 by 720 backing surface. If the hosted runner selects any
other API, stop here and revise the approved design; do not implement or publish software-renderer
timings.

---

### Task 4: Implement the Isolated Pointer-Highlight Scenario

**Files:**

- Create: `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/IsolatedGlassScenario.kt`
- Create: `haze-benchmarks/glass/desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/IsolatedGlassScenarioTest.kt`
- Modify: `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/Main.kt`

**Interfaces:**

- Consumes: `DesktopBenchmarkScenario` and normalized events from Task 1.
- Produces: `IsolatedGlassScenario`, identifier `pointer_sweep`, protocol version `1`, and
  `isolatedPointerEvents()`.

- [ ] **Step 1: Write the deterministic-path tests**

```kotlin
class IsolatedGlassScenarioTest {
  @Test fun pointerPath_isFourSecondsAt120Hz() {
    val events = isolatedPointerEvents()
    assertThat(events.count { it.type == DesktopInputEventType.Move }).isEqualTo(480)
    assertThat(events.last()).isEqualTo(
      DesktopInputEvent(4_000_000_000L, DesktopInputEventType.Exit, null),
    )
  }

  @Test fun pointerPath_staysInsideGlassSurface() {
    isolatedPointerEvents().mapNotNull { it.position }.forEach { point ->
      assertThat(point.x).isBetween(0.25f, 0.75f)
      assertThat(point.y).isBetween(0.30f, 0.70f)
    }
  }
}
```

- [ ] **Step 2: Run the focused test and verify failure**

```shell
./gradlew :haze-benchmarks:glass:desktop:jvmTest \
  --tests '*IsolatedGlassScenarioTest'
```

Expected: compilation fails because the scenario does not exist.

- [ ] **Step 3: Implement the fixed 120 Hz path**

```kotlin
internal fun isolatedPointerEvents(): List<DesktopInputEvent> = buildList {
  repeat(480) { index ->
    val progress = index / 479f
    add(DesktopInputEvent(
      offsetNanos = index * 1_000_000_000L / 120L,
      type = DesktopInputEventType.Move,
      position = NormalizedPoint(
        x = 0.25f + progress * 0.5f,
        y = 0.5f + sin(progress * 4f * PI.toFloat()) * 0.18f,
      ),
    ))
  }
  add(DesktopInputEvent(4_000_000_000L, DesktopInputEventType.Exit, null))
}
```

- [ ] **Step 4: Implement the isolated scene**

Adapt the existing deterministic interaction scene from
`haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassInteractionScreenshotTest.kt`.
Use one remembered `GlassVisualEffect` with `hovered()`, forced full-motion interaction, a 20 dp
rounded shape, and a centered Glass surface over the striped background. Size the surface to
`0.5625` of the viewport width and `5 / 9` of its height. This is 360 by 200 logical units on the
target 640 by 360 logical viewport, while keeping the surface and normalized pointer path aligned
at any content scale. Do not copy test input code or use `Robot`:

```kotlin
internal class IsolatedGlassScenario : DesktopBenchmarkScenario {
  override val id = "pointer_sweep"
  override val protocolVersion = 1
  override val events = isolatedPointerEvents()

  @Composable override fun Content() {
    val hazeState = remember { HazeState() }
    val effect = remember {
      GlassVisualEffect().apply {
        hovered()
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
        shape = RoundedCornerShape(20.dp)
      }
    }
    IsolatedGlassBenchmarkScene(hazeState, effect)
  }

  override suspend fun reset() = Unit
}

@Composable
private fun IsolatedGlassBenchmarkScene(
  hazeState: HazeState,
  effect: GlassVisualEffect,
) {
  Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      drawRect(Color(0xFF10233E))
      rotate(-25f) {
        repeat(18) { index ->
          drawRect(
            color = if (index % 2 == 0) Color(0xFF2CE1C2) else Color(0xFFF15B8A),
            topLeft = Offset(index * 56f - 300f, -200f),
            size = size.copy(width = 22f),
            alpha = 0.72f,
          )
        }
      }
      drawCircle(Color(0xFFFFD166), radius = 96f, center = center * 1.35f)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Box(
        Modifier
          .fillMaxWidth(0.5625f)
          .fillMaxHeight(5f / 9f)
          .hazeEffect(hazeState) { visualEffect = effect },
        contentAlignment = Alignment.Center,
      ) {
        Text("POINTER SWEEP", color = Color.White)
      }
    }
  }
}
```

- [ ] **Step 5: Register, test, and commit**

Change `Main.kt` to pass `listOf(::IsolatedGlassScenario)` and run:

```shell
./gradlew \
  :haze-benchmarks:glass:desktop:jvmTest \
  :haze-benchmarks:glass:desktop:spotlessCheck
git add haze-benchmarks/glass/desktop
git commit -m "Add isolated Glass pointer benchmark"
```

Expected: deterministic path tests pass.

---

### Task 5: Implement the Glass Playground Scenario

**Files:**

- Create: `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/PlaygroundGlassScenario.kt`
- Create: `haze-benchmarks/glass/desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/PlaygroundGlassScenarioTest.kt`
- Modify: `haze-benchmarks/glass/desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/glass/Main.kt`
- Modify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt`

**Interfaces:**

- Produces: `PlaygroundGlassScenario`, identifier `playground_drag`, protocol version `1`, and
  `playgroundEvents()`.
- Reuses `GlassPlaygroundSampleContent`; do not fork the sample UI.

- [ ] **Step 1: Write deterministic path and reset tests**

```kotlin
class PlaygroundGlassScenarioTest {
  @Test fun path_isSixSecondsWithPressDragReleaseAndExit() {
    val events = playgroundEvents()
    assertThat(events.first().type).isEqualTo(DesktopInputEventType.Move)
    assertThat(events.count { it.type == DesktopInputEventType.Drag }).isEqualTo(300)
    assertThat(events.map { it.type }).contains(DesktopInputEventType.Press)
    assertThat(events.map { it.type }).contains(DesktopInputEventType.Release)
    assertThat(events.last().offsetNanos).isEqualTo(6_000_000_000L)
    assertThat(events.last().type).isEqualTo(DesktopInputEventType.Exit)
  }

  @Test fun resetClearsDragOffset() = runBlocking {
    val scenario = PlaygroundGlassScenario()
    scenario.applyDragForTest(Offset(80f, 40f))
    scenario.reset()
    assertThat(scenario.dragOffsetForTest()).isEqualTo(Offset.Zero)
  }
}
```

- [ ] **Step 2: Run the test and verify failure**

```shell
./gradlew :haze-benchmarks:glass:desktop:jvmTest \
  --tests '*PlaygroundGlassScenarioTest'
```

Expected: compilation fails because the Playground scenario does not exist.

- [ ] **Step 3: Implement the six-second 60 Hz sequence**

Use the Prism center at fixed Playground progress `0.5f` (`x=0.5`, `y=0.52`). Move there at zero,
press at 500 ms, emit 300 drag points from 500 ms through 5.5 seconds, release at 5.5 seconds, and
exit at 6 seconds:

```kotlin
internal fun playgroundEvents(): List<DesktopInputEvent> = buildList {
  val start = NormalizedPoint(0.5f, 0.52f)
  add(DesktopInputEvent(0, DesktopInputEventType.Move, start))
  add(DesktopInputEvent(500_000_000L, DesktopInputEventType.Press, start))
  repeat(300) { index ->
    val progress = index / 299f
    add(DesktopInputEvent(
      500_000_000L + index * 5_000_000_000L / 299L,
      DesktopInputEventType.Drag,
      NormalizedPoint(
        x = 0.5f + 0.20f * progress,
        y = 0.52f + 0.10f * sin(progress * PI.toFloat()),
      ),
    ))
  }
  add(DesktopInputEvent(5_500_000_000L, DesktopInputEventType.Release, NormalizedPoint(0.7f, 0.52f)))
  add(DesktopInputEvent(6_000_000_000L, DesktopInputEventType.Exit, null))
}
```

- [ ] **Step 4: Render the existing Playground with fixed state**

Hold `dragOffset` as scenario-owned Compose state, use `progressProvider = { 0.5f }`,
`isPlaying = false`, and `recordingMode = true`. Update only the Prism offset from existing drag
callbacks. Add a `interactionReducedMotionPolicy: GlassReducedMotionPolicy =
GlassReducedMotionPolicy.System` parameter to `GlassPlaygroundSampleContent`, thread it through
`PlaygroundSurfaceScene` and `PlaygroundSurface`, and pass it to
`configurePlaygroundInteraction(source, policy)`. Existing callers keep the same behavior; the
benchmark passes `Full`:

```kotlin
@Composable override fun Content() {
  SamplesTheme(useDarkColors = true) {
    GlassPlaygroundSampleContent(
      progressProvider = { 0.5f },
      dragOffsetProvider = { id ->
        if (id == GlassPlaygroundSurfaceId.Prism) dragOffset else Offset.Zero
      },
      isPlaying = false,
      recordingMode = true,
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
      onPlayPause = {},
      onReset = {},
      onRecordingModeChanged = {},
      onBack = {},
      onDragStart = {},
      onDrag = { id, delta -> if (id == GlassPlaygroundSurfaceId.Prism) dragOffset += delta },
      onDragEnd = {},
    )
  }
}
```

`reset()` sets `dragOffset = Offset.Zero` on the Swing/Compose UI context before replay starts.
Keep the state and test accessors in the scenario:

```kotlin
private var dragOffset by mutableStateOf(Offset.Zero)

override suspend fun reset() = withContext(Dispatchers.Swing) {
  dragOffset = Offset.Zero
}

internal fun applyDragForTest(delta: Offset) {
  dragOffset += delta
}

internal fun dragOffsetForTest(): Offset = dragOffset
```

Track Prism drag starts and callbacks in the same Swing-confined scenario state. Override
`verifyCompleted()` to require a Prism drag start, at least 160 delivered drag callbacks, and a
final offset consistent with the normalized replay displacement. Reset these counters with the
offset. This is an input-path invariant, not a render-performance threshold; it prevents a valid
AWT stream that never enters Compose's drag gesture from producing a benchmark artifact.

- [ ] **Step 5: Register both scenarios and add the Metal smoke task**

```kotlin
private val GlassScenarios = listOf(
  ::IsolatedGlassScenario,
  ::PlaygroundGlassScenario,
)

fun main(args: Array<String>) {
  exitProcess(runDesktopBenchmarkSuite(args, suiteId = "glass", scenarioFactories = GlassScenarios))
}
```

Register two private process tasks and a `desktopBenchmarkSmoke` lifecycle task in the Glass build
file. The lifecycle task must launch both scenarios and must not be attached to `check`:

```kotlin
val benchmarkJavaLauncher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(21))
}

fun registerScenarioSmoke(name: String, scenarioId: String) = tasks.register<Exec>(name) {
  dependsOn("packageUberJarForCurrentOS")
  environment("SKIKO_RENDER_API", "METAL")
  doFirst {
    val output = layout.buildDirectory.file("benchmark-smoke/$scenarioId.json").get().asFile
    output.parentFile.mkdirs()
    commandLine(
      benchmarkJavaLauncher.get().executablePath.asFile.absolutePath,
      "-Xms512m",
      "-Xmx512m",
      "-jar",
      layout.buildDirectory.file(
        "compose/jars/desktop-macos-arm64-1.0.0.jar",
      ).get().asFile.absolutePath,
      "run",
      "--scenario", scenarioId,
      "--revision", "smoke",
      "--round", "0",
      "--order", "0",
      "--output", output.absolutePath,
      "--smoke",
    )
  }
}

val pointerSmoke = registerScenarioSmoke("desktopPointerBenchmarkSmoke", "pointer_sweep")
val playgroundSmoke = registerScenarioSmoke("desktopPlaygroundBenchmarkSmoke", "playground_drag")
playgroundSmoke.configure { mustRunAfter(pointerSmoke) }

tasks.register("desktopBenchmarkSmoke") {
  dependsOn(pointerSmoke, playgroundSmoke)
}
```

- [ ] **Step 6: Run tests, a short local scenario, and commit**

```shell
./gradlew \
  :haze-benchmarks:glass:desktop:jvmTest \
  :haze-benchmarks:glass:desktop:packageUberJarForCurrentOS \
  :haze-benchmarks:glass:desktop:spotlessCheck \
  :haze-benchmarks:glass:desktop:desktopBenchmarkSmoke
git add haze-benchmarks/glass/desktop
git add sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt
git commit -m "Add Glass Playground benchmark"
```

Expected: both smoke JSON files contain finite samples and report `METAL`.

---

### Task 6: Add ABBA Aggregation and Command Integration

**Files:**

- Modify: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkCommand.kt`
- Create: `internal/benchmark-desktop/src/jvmMain/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkAggregation.kt`
- Create: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkAggregationTest.kt`
- Modify: `internal/benchmark-desktop/src/jvmTest/kotlin/dev/chrisbanes/haze/benchmark/desktop/BenchmarkCommandTest.kt`

**Interfaces:**

- Produces: `aggregateBenchmarkBlocks()` and CLI command
  `aggregate --input /tmp/haze-desktop-benchmark/raw --repository chrisbanes/haze --base-sha
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa --head-sha bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
  --output /tmp/haze-desktop-benchmark/benchmark.json`.
- Consumes: twelve blocks per scenario (six base, six head), or six head-only blocks during
  bootstrap.

- [ ] **Step 1: Write aggregation tests for ABBA, mismatch, and bootstrap**

```kotlin
class BenchmarkAggregationTest {
  @Test fun matchingProtocolsProducePairedDelta() {
    val artifact = aggregate(abbaFixture(baseValue = 10_000_000, headValue = 11_000_000))
    assertThat(artifact.scenarios.single().comparable).isTrue()
    assertThat(abs(checkNotNull(artifact.scenarios.single().renderPairedDeltaPercent) - 10.0))
      .isLessThan(0.0001)
  }

  @Test fun protocolMismatchSuppressesOnlyThatScenarioDelta() {
    val artifact = aggregate(mixedProtocolFixture())
    assertThat(artifact.scenarios.first { it.id == "pointer_sweep" }.comparable).isFalse()
    assertThat(artifact.scenarios.first { it.id == "playground_drag" }.comparable).isTrue()
  }

  @Test fun headOnlyBootstrapHasNoDelta() {
    val summary = aggregate(headOnlyFixture())
      .scenarios.single()
    assertThat(summary.baseRender).isNull()
    assertThat(summary.renderPairedDeltaPercent).isNull()
  }
}

private fun aggregate(blocks: List<BenchmarkBlockResult>) = aggregateBenchmarkBlocks(
  suiteId = "glass",
  allowedScenarioIds = setOf("pointer_sweep", "playground_drag"),
  repository = "chrisbanes/haze",
  baseSha = "a".repeat(40),
  headSha = "b".repeat(40),
  blocks = blocks,
)

private fun abbaFixture(
  baseValue: Long,
  headValue: Long,
  scenarioId: String = "pointer_sweep",
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

private fun mixedProtocolFixture(): List<BenchmarkBlockResult> =
  abbaFixture(10_000_000, 11_000_000, "pointer_sweep", baseProtocol = 1, headProtocol = 2) +
    abbaFixture(10_000_000, 11_000_000, "playground_drag")

private fun headOnlyFixture(): List<BenchmarkBlockResult> = buildList {
  repeat(3) { round ->
    add(blockFixture("pointer_sweep", 1, "head", round, 1, 11_000_000))
    add(blockFixture("pointer_sweep", 1, "head", round, 2, 11_000_000))
  }
}

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
```

- [ ] **Step 2: Run tests and verify failure**

```shell
./gradlew :internal:benchmark-desktop:jvmTest \
  --tests '*BenchmarkAggregationTest'
```

Expected: compilation fails because aggregation does not exist.

- [ ] **Step 3: Implement strict aggregation**

Decode only `*.json` block files from the explicit input directory. Pass the current `suiteId` and
the scenario identifiers from the suite registry into `aggregateBenchmarkBlocks()`; the shared
runner must not hard-code Glass. Require the supplied suite, schema version `1`, identifiers from
that allow-list, revisions `base`/`head`, rounds `0..2`, orders `0..3`, SHA-shaped identities, and
at most 100,000 total samples. Validate these ABBA slots in every comparable scenario:

```kotlin
private val ExpectedSlots = setOf(
  Triple("base", 0, 0), Triple("head", 0, 1),
  Triple("head", 0, 2), Triple("base", 0, 3),
  Triple("base", 1, 0), Triple("head", 1, 1),
  Triple("head", 1, 2), Triple("base", 1, 3),
  Triple("base", 2, 0), Triple("head", 2, 1),
  Triple("head", 2, 2), Triple("base", 2, 3),
)

private val ExpectedHeadOnlySlots = setOf(
  Triple("head", 0, 1), Triple("head", 0, 2),
  Triple("head", 1, 1), Triple("head", 1, 2),
  Triple("head", 2, 1), Triple("head", 2, 2),
)
```

Summarize render durations and non-null callback intervals separately. Preserve every validated
block in the artifact. Reject empty measured sample sets, negative durations, duplicate
`(revision, round, order)` blocks, and mixed environment render APIs. Set `comparable=false` and
paired deltas to `null` when protocol versions differ or base blocks are absent. Sort scenarios and
blocks deterministically before encoding.

- [ ] **Step 4: Wire the aggregate command and smoke flag**

`--smoke` skips only the 500 ms post-warm-up wait, does not alter the event protocol, and is
forbidden when `CI=true`. Truncating the event list would omit Playground release/exit and bypass
its completion invariant. `aggregate` never creates a window.

Return exit code `2` for invalid arguments or block data. A `run` measurement failure returns exit
code `1` without inventing repository/SHA fields that are not present in that command. For
`aggregate`, once repository identities and the output path are validated, catch invalid block data
or unexpected aggregation failures and write a `BenchmarkArtifact(status = "failed", scenarios =
emptyList(), diagnostic = boundedDiagnostic(message))` before returning `2` or `1` respectively.
This preserves a trustworthy failure artifact without fabricating identity metadata.

- [ ] **Step 5: Run tests and commit**

```shell
./gradlew :internal:benchmark-desktop:jvmTest :internal:benchmark-desktop:spotlessCheck
git add internal/benchmark-desktop
git commit -m "Aggregate paired Desktop benchmark results"
```

Expected: all aggregate and command tests pass.

---

### Task 7: Run the Glass Comparison in Unprivileged CI

**Files:**

- Modify: `.github/workflows/desktop-glass-benchmark.yml`

**Interfaces:**

- Consumes: the Glass Uber JAR commands from Tasks 3-6.
- Produces: artifact `desktop-glass-benchmark` containing `benchmark.json` and `raw/*.json`.

- [ ] **Step 1: Replace the probe-only workflow with PR and manual triggers**

Use this trigger and permission boundary:

```yaml
name: Desktop Glass Benchmark

on:
  workflow_dispatch:
  pull_request:
    paths:
      - 'haze/**'
      - 'haze-utils/**'
      - 'haze-glass/**'
      - 'haze-materials/**'
      - 'sample/shared/**'
      - 'internal/benchmark-desktop/**'
      - 'haze-benchmarks/glass/**'
      - 'gradle/**'
      - 'build.gradle.kts'
      - 'settings.gradle.kts'
      - 'gradle.properties'
      - '.github/workflows/desktop-glass-benchmark*.yml'
      - '.github/scripts/desktop-glass-benchmark-*'

permissions:
  contents: read
```

The single job uses `runs-on: macos-26`, `timeout-minutes: 45`, JDK 21, and no secrets or remote
build-cache credentials.

- [ ] **Step 2: Check out head and base explicitly**

```yaml
- uses: actions/checkout@v7
  with:
    repository: ${{ github.event.pull_request.head.repo.full_name || github.repository }}
    ref: ${{ github.event.pull_request.head.sha || github.sha }}
    path: head
    persist-credentials: false

- if: github.event_name == 'pull_request'
  uses: actions/checkout@v7
  with:
    ref: ${{ github.event.pull_request.base.sha }}
    path: base
    persist-credentials: false
```

Set up Gradle separately for the two checkouts only for compilation; do not measure through Gradle.
For the comparison shell step, derive identities from the event rather than either checkout:

```shell
head_sha="${{ github.event.pull_request.head.sha || github.sha }}"
base_sha="${{ github.event.pull_request.base.sha }}"
```

- [ ] **Step 3: Package both revisions before measurement**

```shell
cd head
./gradlew :haze-benchmarks:glass:desktop:packageUberJarForCurrentOS --no-configuration-cache
./gradlew --stop
cd ..
if test -d base/haze-benchmarks/glass/desktop; then
  cd base
  ./gradlew :haze-benchmarks:glass:desktop:packageUberJarForCurrentOS --no-configuration-cache
  ./gradlew --stop
  cd ..
fi
```

Copy the resulting JARs into `$RUNNER_TEMP/haze-desktop-benchmark/` before launching them. Set
`SKIKO_RENDER_API=METAL` on every Java process. Use these exact variables:

```shell
benchmark_dir="$RUNNER_TEMP/haze-desktop-benchmark"
mkdir -p "$benchmark_dir/raw"
head_jar="$benchmark_dir/head.jar"
base_jar="$benchmark_dir/base.jar"
cp head/haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar "$head_jar"
if test -f base/haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar; then
  cp base/haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar "$base_jar"
fi
export SKIKO_RENDER_API=METAL
```

- [ ] **Step 4: Probe and execute ABBA blocks**

For each `pointer_sweep` and `playground_drag` scenario and rounds `0`, `1`, and `2`, invoke the
exact order below:

```shell
run_block base "$scenario" "$round" 0
run_block head "$scenario" "$round" 1
run_block head "$scenario" "$round" 2
run_block base "$scenario" "$round" 3
```

`run_block` executes:

```shell
run_block() {
  revision="$1"
  scenario="$2"
  round="$3"
  order="$4"
  case "$revision" in
    base) benchmark_jar="$base_jar" ;;
    head) benchmark_jar="$head_jar" ;;
    *) exit 2 ;;
  esac
  java -Xms512m -Xmx512m -jar "$benchmark_jar" run \
    --scenario "$scenario" \
    --revision "$revision" \
    --round "$round" \
    --order "$order" \
    --output "$benchmark_dir/raw/$scenario-$revision-$round-$order.json"
}
```

When the base JAR does not exist, skip both base invocations but retain head orders `1` and `2` for
each round. This produces `ExpectedHeadOnlySlots`; aggregation reports the bootstrap case without a
delta. Do not compare a missing base.

- [ ] **Step 5: Aggregate and upload raw data**

```shell
aggregate_args=(
  aggregate
  --input "$benchmark_dir/raw"
  --repository "$GITHUB_REPOSITORY"
  --head-sha "$head_sha"
  --output "$benchmark_dir/benchmark.json"
)
if test -n "$base_sha"; then
  aggregate_args+=(--base-sha "$base_sha")
fi
java -Xms512m -Xmx512m -jar "$head_jar" "${aggregate_args[@]}"
```

Upload with `actions/upload-artifact@v7`, `if: always()`, name `desktop-glass-benchmark`, retention
30 days, and `if-no-files-found: error`. Performance deltas do not appear in any shell condition and
cannot fail the job; missing/invalid measurement output does.

- [ ] **Step 6: Validate the workflow and commit**

```shell
./gradlew :haze-benchmarks:glass:desktop:packageUberJarForCurrentOS
git diff --check
git add .github/workflows/desktop-glass-benchmark.yml
git commit -m "Run Desktop Glass benchmark on pull requests"
```

Expected: YAML uses only read permission and the timed task remains absent from `build.yml` and root
`check`.

---

### Task 8: Validate Results and Maintain the PR Comment

**Files:**

- Create: `.github/scripts/desktop-glass-benchmark-report.mjs`
- Create: `.github/scripts/desktop-glass-benchmark-report.test.mjs`
- Create: `.github/workflows/desktop-glass-benchmark-report.yml`
- Modify: `.github/workflows/build.yml`

**Interfaces:**

- Produces: `parseArtifactBuffer(buffer, expected)`, `validateArtifact(value, expected)`,
  `renderComment(artifact, runUrl)`, marker `<!-- desktop-glass-benchmark -->`.
- Consumes: aggregate schema version `1` and trusted PR/base/head identities.

- [ ] **Step 1: Write fixture-driven reporter tests**

Use `node:test` and programmatic fixtures. Cover valid output, a noisy label, protocol mismatch,
wrong SHA, unknown keys, non-finite numbers, more than 100,000 samples, a diagnostic over 2,048
bytes, and an input buffer over 5 MiB:

```javascript
test('renders one observational table', () => {
  const artifact = validArtifact()
  const body = renderComment(validateArtifact(artifact, expectedIdentity), runUrl)
  assert.match(body, /<!-- desktop-glass-benchmark -->/)
  assert.match(body, /pointer_sweep/)
  assert.match(body, /observational/i)
})

test('rejects artifact identity instead of trusting it', () => {
  const artifact = validArtifact({ headSha: '0'.repeat(40) })
  assert.throws(() => validateArtifact(artifact, expectedIdentity), /head SHA/)
})

test('labels noisy and protocol-mismatched scenarios', () => {
  const artifact = validArtifact()
  artifact.scenarios[0].headRender.noisy = true
  artifact.scenarios[0].comparable = false
  const body = renderComment(validateArtifact(artifact, expectedIdentity), runUrl)
  assert.match(body, /noisy/)
  assert.match(body, /not comparable/)
})

test('rejects unknown keys and non-finite values', () => {
  const unknown = validArtifact()
  unknown.untrusted = 'text'
  assert.throws(() => validateArtifact(unknown, expectedIdentity), /unexpected keys/)
  const nonFinite = validArtifact()
  nonFinite.scenarios[0].headRender.p95Nanos = Number.NaN
  assert.throws(() => validateArtifact(nonFinite, expectedIdentity))
})

test('rejects sample and diagnostic limits', () => {
  const tooMany = validArtifact()
  tooMany.scenarios[0].blocks[0].samples = Array.from(
    { length: 100_001 },
    () => ({ renderDurationNanos: 1, callbackIntervalNanos: 1 }),
  )
  assert.throws(() => validateArtifact(tooMany, expectedIdentity), /too many samples/)
  const diagnostic = validArtifact({ diagnostic: 'é'.repeat(1_025) })
  assert.throws(() => validateArtifact(diagnostic, expectedIdentity))
})

test('rejects an input buffer over five MiB', () => {
  assert.throws(
    () => parseArtifactBuffer(Buffer.alloc(5 * 1024 * 1024 + 1), expectedIdentity),
    /five MiB/,
  )
})

const expectedIdentity = {
  repository: 'chrisbanes/haze',
  baseSha: 'a'.repeat(40),
  headSha: 'b'.repeat(40),
}
const runUrl = 'https://github.com/chrisbanes/haze/actions/runs/123456789'

function validArtifact(overrides = {}) {
  const metric = () => ({
    sampleCount: 2,
    p50Nanos: 10_000_000,
    p95Nanos: 11_000_000,
    p99Nanos: 11_000_000,
    above16MillisCount: 0,
    above16MillisPercent: 0,
    above33MillisCount: 0,
    above33MillisPercent: 0,
    robustVariationPercent: 1,
    noisy: false,
  })
  const environment = {
    osName: 'Mac OS X', osVersion: '26.0', architecture: 'aarch64', cpu: 'Apple M1',
    memoryBytes: 7_000_000_000, javaVendor: 'Azul Systems, Inc.', javaVersion: '21',
    composeVersion: '1.11.1', skikoVersion: '0.144.6', renderApi: 'METAL',
    framebufferWidth: 1280, framebufferHeight: 720, contentScale: 2,
    refreshRateHz: 60, runnerImage: 'macos-26', runnerImageVersion: 'test',
  }
  const block = revision => ({
    schemaVersion: 1,
    suiteId: 'glass',
    scenarioId: 'pointer_sweep',
    protocolVersion: 1,
    revision,
    round: 0,
    order: revision === 'base' ? 0 : 1,
    environment,
    workloadDurationNanos: 4_000_000_000,
    samples: [
      { renderDurationNanos: 10_000_000, callbackIntervalNanos: null },
      { renderDurationNanos: 11_000_000, callbackIntervalNanos: 16_000_000 },
    ],
  })
  return {
    schemaVersion: 1,
    suiteId: 'glass',
    repository: expectedIdentity.repository,
    baseSha: expectedIdentity.baseSha,
    headSha: expectedIdentity.headSha,
    scenarios: [{
      id: 'pointer_sweep',
      baseProtocolVersion: 1,
      headProtocolVersion: 1,
      comparable: true,
      baseRender: metric(),
      headRender: metric(),
      baseInterval: metric(),
      headInterval: metric(),
      renderPairedDeltaPercent: 1,
      intervalPairedDeltaPercent: 1,
      blocks: [block('base'), block('head')],
    }],
    status: 'complete',
    diagnostic: null,
    ...overrides,
  }
}
```

- [ ] **Step 2: Run Node tests and verify failure**

```shell
node --test .github/scripts/desktop-glass-benchmark-report.test.mjs
```

Expected: module-not-found failure.

- [ ] **Step 3: Implement strict schema validation**

Export `validateArtifact` and `renderComment`. Parse from a `Buffer` only after checking
`buffer.byteLength <= 5 * 1024 * 1024`. For every object, require the exact allowed key set; require
schema `1`, suite `glass`, identifier regex `/^[a-z][a-z0-9_]{0,63}$/`, 40-character lowercase hex
SHAs, finite numeric summaries, integer nonnegative samples, at most 100,000 samples, and the
trusted repository/base/head values:

```javascript
function exactKeys(value, expected, path) {
  const actual = Object.keys(value).sort()
  const wanted = [...expected].sort()
  assert.deepEqual(actual, wanted, `${path} has unexpected keys`)
}

export function parseArtifactBuffer(buffer, expected) {
  assert.ok(buffer.byteLength <= 5 * 1024 * 1024, 'artifact exceeds five MiB')
  return validateArtifact(JSON.parse(buffer.toString('utf8')), expected)
}

export function validateArtifact(value, expected) {
  exactKeys(value, [
    'schemaVersion', 'suiteId', 'repository', 'baseSha', 'headSha', 'scenarios', 'status',
    'diagnostic',
  ], 'artifact')
  assert.equal(value.schemaVersion, 1)
  assert.equal(value.suiteId, 'glass')
  assert.equal(value.repository, expected.repository)
  assert.equal(value.baseSha, expected.baseSha)
  assert.equal(value.headSha, expected.headSha)
  assert.ok(value.status === 'complete' || value.status === 'failed')
  assert.ok(Array.isArray(value.scenarios) && value.scenarios.length <= 16)
  const sampleCount = value.scenarios.reduce(
    (total, scenario) => total + scenario.blocks.reduce(
      (blockTotal, block) => blockTotal + block.samples.length,
      0,
    ),
    0,
  )
  assert.ok(sampleCount <= 100_000, 'artifact has too many samples')
  value.scenarios.forEach(validateScenario)
  assert.ok(value.diagnostic === null || utf8Size(value.diagnostic) <= 2048)
  return value
}

function validateScenario(value) {
  exactKeys(value, [
    'id', 'baseProtocolVersion', 'headProtocolVersion', 'comparable',
    'baseRender', 'headRender', 'baseInterval', 'headInterval',
    'renderPairedDeltaPercent', 'intervalPairedDeltaPercent', 'blocks',
  ], 'scenario')
  assert.match(value.id, /^[a-z][a-z0-9_]{0,63}$/)
  assert.ok(value.id === 'pointer_sweep' || value.id === 'playground_drag')
  optionalPositiveInteger(value.baseProtocolVersion)
  positiveInteger(value.headProtocolVersion)
  assert.equal(typeof value.comparable, 'boolean')
  optionalMetric(value.baseRender)
  validateMetric(value.headRender)
  optionalMetric(value.baseInterval)
  validateMetric(value.headInterval)
  optionalFinite(value.renderPairedDeltaPercent)
  optionalFinite(value.intervalPairedDeltaPercent)
  assert.ok(Array.isArray(value.blocks) && value.blocks.length <= 12)
  value.blocks.forEach(validateBlock)
}

function validateMetric(value) {
  exactKeys(value, [
    'sampleCount', 'p50Nanos', 'p95Nanos', 'p99Nanos',
    'above16MillisCount', 'above16MillisPercent',
    'above33MillisCount', 'above33MillisPercent',
    'robustVariationPercent', 'noisy',
  ], 'metric')
  ;['sampleCount', 'p50Nanos', 'p95Nanos', 'p99Nanos',
    'above16MillisCount', 'above33MillisCount']
    .forEach(key => nonnegativeInteger(value[key]))
  ;['above16MillisPercent', 'above33MillisPercent', 'robustVariationPercent']
    .forEach(key => finiteNumber(value[key]))
  assert.equal(typeof value.noisy, 'boolean')
}

function validateBlock(value) {
  exactKeys(value, [
    'schemaVersion', 'suiteId', 'scenarioId', 'protocolVersion', 'revision',
    'round', 'order', 'environment', 'workloadDurationNanos', 'samples',
  ], 'block')
  assert.equal(value.schemaVersion, 1)
  assert.equal(value.suiteId, 'glass')
  assert.match(value.scenarioId, /^[a-z][a-z0-9_]{0,63}$/)
  positiveInteger(value.protocolVersion)
  assert.ok(value.revision === 'base' || value.revision === 'head')
  assert.ok(Number.isInteger(value.round) && value.round >= 0 && value.round <= 2)
  assert.ok(Number.isInteger(value.order) && value.order >= 0 && value.order <= 3)
  validateEnvironment(value.environment)
  nonnegativeInteger(value.workloadDurationNanos)
  assert.ok(Array.isArray(value.samples))
  value.samples.forEach(validateSample)
}

function validateEnvironment(value) {
  exactKeys(value, [
    'osName', 'osVersion', 'architecture', 'cpu', 'memoryBytes', 'javaVendor', 'javaVersion',
    'composeVersion', 'skikoVersion', 'renderApi', 'framebufferWidth', 'framebufferHeight',
    'contentScale', 'refreshRateHz', 'runnerImage', 'runnerImageVersion',
  ], 'environment')
  ;['osName', 'osVersion', 'architecture', 'cpu', 'javaVendor', 'javaVersion',
    'composeVersion', 'skikoVersion', 'renderApi']
    .forEach(key => boundedString(value[key], 256))
  optionalBoundedString(value.runnerImage, 256)
  optionalBoundedString(value.runnerImageVersion, 256)
  ;['memoryBytes', 'framebufferWidth', 'framebufferHeight', 'refreshRateHz']
    .forEach(key => nonnegativeInteger(value[key]))
  finiteNumber(value.contentScale)
  assert.equal(value.renderApi, 'METAL')
  assert.equal(value.framebufferWidth, 1280)
  assert.equal(value.framebufferHeight, 720)
}

function validateSample(value) {
  exactKeys(value, ['renderDurationNanos', 'callbackIntervalNanos'], 'sample')
  nonnegativeInteger(value.renderDurationNanos)
  if (value.callbackIntervalNanos !== null) nonnegativeInteger(value.callbackIntervalNanos)
}

function positiveInteger(value) {
  assert.ok(Number.isInteger(value) && value > 0)
}

function nonnegativeInteger(value) {
  assert.ok(Number.isInteger(value) && value >= 0)
}

function finiteNumber(value) {
  assert.ok(typeof value === 'number' && Number.isFinite(value))
}

function optionalFinite(value) {
  if (value !== null) finiteNumber(value)
}

function boundedString(value, limit) {
  assert.equal(typeof value, 'string')
  assert.ok(utf8Size(value) <= limit)
}

function optionalBoundedString(value, limit) {
  if (value !== null) boundedString(value, limit)
}

function optionalPositiveInteger(value) {
  if (value !== null) positiveInteger(value)
}

function optionalMetric(value) {
  if (value !== null) validateMetric(value)
}

function utf8Size(value) {
  return Buffer.byteLength(value, 'utf8')
}
```

Do not render generic HTML/Markdown from the artifact. Apply one `escapeMarkdown` function to every
artifact-provided string that enters the comment, including CPU, OS, versions, identifiers, and the
bounded diagnostic.

The trusted reporter must not trust the aggregate summaries produced by pull-request code. Validate
the exact registered scenario set, ABBA/head-only slots, protocol consistency, base-SHA coupling,
metadata bounds, fixed Metal/1280x720 environment, and every raw sample again. Recompute all render
and interval summaries, robust variation/noise flags, and paired deltas from the validated raw
blocks using the schema-1 formulas; reject the artifact if any supplied summary, comparability flag,
protocol field, or delta differs from the trusted recomputation. Use `Number.isSafeInteger` for raw
integer fields. Add tests that tamper with a summary while leaving raw samples unchanged, omit a
registered scenario, alter an ABBA slot, and exceed a metadata bound.

Require `status = complete` to contain exactly `pointer_sweep` and `playground_drag` with a null
diagnostic. Require `status = failed` to contain no scenarios and a nonblank bounded diagnostic;
render that escaped diagnostic as an infrastructure-failure comment rather than a metrics table.

- [ ] **Step 4: Render the fixed comment format**

```markdown
<!-- desktop-glass-benchmark -->
## Desktop Glass benchmark

`macos-26` · Apple M1 · Metal · protocol 1

| Scenario | Revision | Render p50 | Render p95 | Interval p95 | >16.67 ms | Delta | Noise |
|---|---:|---:|---:|---:|---:|---:|---:|
| pointer_sweep | base | 2.341 ms | 4.122 ms | 16.580 ms | 1.2% | — | low |
| pointer_sweep | PR | 2.438 ms | 4.301 ms | 16.610 ms | 1.5% | +4.2% | low |

These Skiko render-callback values are observational; they are not GPU completion or presentation
times and do not gate this pull request.
[Workflow run](https://github.com/chrisbanes/haze/actions/runs/123456789)
```

Convert nanoseconds to milliseconds with three decimal places. Render `not comparable` for a
protocol mismatch and `noisy` when either relevant metric has `noisy=true`.

Make the module executable for the workflow while keeping the functions importable by tests:

```javascript
if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [artifactPath, repository, baseSha, headSha, runUrl] = process.argv.slice(2)
  assert.ok(artifactPath && repository && baseSha && headSha && runUrl)
  const buffer = readFileSync(artifactPath)
  const artifact = parseArtifactBuffer(buffer, {
    repository,
    baseSha,
    headSha,
  })
  process.stdout.write(renderComment(artifact, runUrl))
}
```

Import `readFileSync` from `node:fs`, `pathToFileURL` from `node:url`, and strict `assert` from
`node:assert`.

- [ ] **Step 5: Add the trusted `workflow_run` workflow**

```yaml
name: Desktop Glass Benchmark Report

on:
  workflow_run:
    workflows: [Desktop Glass Benchmark]
    types: [completed]

permissions:
  actions: read
  contents: read
  pull-requests: write
```

Run on `ubuntu-24.04`. Check out only `${{ github.event.repository.default_branch }}` with
`persist-credentials: false`. Download `desktop-glass-benchmark` from
`${{ github.event.workflow_run.id }}` using `actions/download-artifact@v8` with
`github-token: ${{ secrets.GITHUB_TOKEN }}`.

Run the job only when `github.event.workflow_run.event == 'pull_request'`. Before validating or
commenting, require the fetched pull request's current head SHA to equal
`github.event.workflow_run.head_sha`; return without updating the comment for a stale completed run.

Use `actions/github-script@v9`. Read the PR number only from the event, fetch its trusted identities,
run the checked-in default-branch validator, and update the marker comment:

```javascript
const { execFileSync } = require('node:child_process')
const prNumber = context.payload.workflow_run.pull_requests[0]?.number
if (!prNumber) return
const { data: pr } = await github.rest.pulls.get({ ...context.repo, pull_number: prNumber })
const body = execFileSync('node', [
  '.github/scripts/desktop-glass-benchmark-report.mjs',
  'artifact/benchmark.json',
  `${context.repo.owner}/${context.repo.repo}`,
  pr.base.sha,
  pr.head.sha,
  context.payload.workflow_run.html_url,
], { encoding: 'utf8', maxBuffer: 1024 * 1024 })
const marker = '<!-- desktop-glass-benchmark -->'
const comments = await github.paginate(github.rest.issues.listComments, {
  ...context.repo,
  issue_number: prNumber,
  per_page: 100,
})
const existing = comments.find(comment =>
  comment.user?.type === 'Bot' && comment.body?.includes(marker),
)
if (existing) {
  await github.rest.issues.updateComment({ ...context.repo, comment_id: existing.id, body })
} else {
  await github.rest.issues.createComment({ ...context.repo, issue_number: prNumber, body })
}
```

An invalid artifact makes this step fail before either comment API call.

- [ ] **Step 6: Add reporter tests to normal Linux CI**

Add this step after JDK/Gradle setup in `linux_build`:

```yaml
- name: Test benchmark report renderer
  run: node --test .github/scripts/desktop-glass-benchmark-report.test.mjs
```

This runs only fast Node tests; it does not launch the Desktop benchmark.

- [ ] **Step 7: Run tests and commit**

```shell
node --test .github/scripts/desktop-glass-benchmark-report.test.mjs
git diff --check
git add \
  .github/scripts/desktop-glass-benchmark-report.mjs \
  .github/scripts/desktop-glass-benchmark-report.test.mjs \
  .github/workflows/desktop-glass-benchmark-report.yml \
  .github/workflows/build.yml
git commit -m "Report Desktop Glass benchmark results"
```

Expected: all Node tests pass and the reporting workflow has no permission beyond `actions: read`,
`contents: read`, and `pull-requests: write`.

---

### Task 9: End-to-End Verification

**Files:**

- Modify only files required to correct failures found by the commands below.

**Interfaces:**

- Verifies the complete module, host, suite, schema, and workflow contract.

- [ ] **Step 1: Run all benchmark unit tests and formatting**

```shell
./gradlew \
  :internal:benchmark-desktop:jvmTest \
  :internal:benchmark-desktop:spotlessCheck \
  :haze-benchmarks:glass:desktop:jvmTest \
  :haze-benchmarks:glass:desktop:spotlessCheck
node --test .github/scripts/desktop-glass-benchmark-report.test.mjs
```

Expected: `BUILD SUCCESSFUL` and all Node tests pass.

- [ ] **Step 2: Package and probe the real Desktop path**

```shell
./gradlew :haze-benchmarks:glass:desktop:packageUberJarForCurrentOS
SKIKO_RENDER_API=METAL java -Xms512m -Xmx512m \
  -jar haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar \
  probe
```

Expected: `METAL`, framebuffer `1280x720`, finite environment values, exit zero.

- [ ] **Step 3: Smoke both scenarios and aggregate them**

Run six smoke blocks for each scenario as `revision=head`, then aggregate them:

```shell
benchmark_smoke_dir="$(mktemp -d)"
mkdir -p "$benchmark_smoke_dir/raw"
benchmark_jar="haze-benchmarks/glass/desktop/build/compose/jars/desktop-macos-arm64-1.0.0.jar"
for scenario in pointer_sweep playground_drag; do
  for round in 0 1 2; do
    for order in 1 2; do
      SKIKO_RENDER_API=METAL java -Xms512m -Xmx512m -jar "$benchmark_jar" run \
        --scenario "$scenario" \
        --revision head \
        --round "$round" \
        --order "$order" \
        --output "$benchmark_smoke_dir/raw/$scenario-head-$round-$order.json" \
        --smoke
    done
  done
done
java -Xms512m -Xmx512m \
  -jar "$benchmark_jar" \
  aggregate \
  --input "$benchmark_smoke_dir/raw" \
  --repository chrisbanes/haze \
  --head-sha 0000000000000000000000000000000000000000 \
  --output "$benchmark_smoke_dir/benchmark.json"
```

Expected: both scenarios contain nonempty finite samples, no base summary, and no paired delta.

- [ ] **Step 4: Run repository verification**

```shell
./gradlew check
git diff --check
git status --short
```

Expected: all checks pass; only intentional implementation files are modified.

- [ ] **Step 5: Commit any verification corrections**

If Step 1-4 required changes:

```shell
git add settings.gradle.kts internal/benchmark-desktop haze-benchmarks/glass/desktop \
  .github/scripts .github/workflows
git commit -m "Polish Desktop Glass benchmark"
```

If no changes were required, do not create an empty commit.

---

## Plan Self-Review

- **Spec coverage:** Tasks 1-3 build the Glass-free shared runner and verify hosted Metal; Tasks
  4-5 add the isolated and Playground Glass suite; Task 6 implements ABBA aggregation, protocol
  compatibility, variation, JSON limits, and failure artifacts; Tasks 7-8 implement the
  unprivileged measurement and trusted comment workflows; Task 9 verifies the complete path.
- **Module isolation:** Only `:haze-benchmarks:glass:desktop` depends on Glass and sample code.
  Timed and native-window tasks remain outside root `check`, allowing CI to select the Glass suite
  independently.
- **Type consistency:** `DesktopBenchmarkScenario`, `DesktopInputEvent`, `BenchmarkCommand`,
  `BenchmarkBlockResult`, and `BenchmarkArtifact` keep the same signatures across producing and
  consuming tasks. Kotlin and Node schemas include the same explicit fields.
- **Security boundary:** Pull-request code runs without secrets or write permissions. The trusted
  workflow checks out only the default branch, derives identities through the GitHub API, validates
  every artifact field, and uses argument-array process execution rather than a shell.
- **No unresolved decisions:** Workload timing, input cadence, framebuffer, JVM options, runner,
  statistics, noise threshold, schema limits, task wiring, workflow paths, and action versions are
  all explicit.
