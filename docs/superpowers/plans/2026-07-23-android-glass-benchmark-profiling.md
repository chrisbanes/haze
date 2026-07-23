# Android Glass Benchmark Profiling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local, physical-device profiling suite for the API 33+ Android
`haze-glass` RuntimeShader renderer using realistic Gallery journeys and controlled invalidation
scenarios.

**Architecture:** Keep `internal/benchmark` as the Macrobenchmark driver and the Android sample
application as its target. Add one Android-only profiling destination with a deterministic
ready/start/complete protocol, add stable completion semantics to Product and Playground, and
instrument existing Glass stage boundaries through Haze's internal tracing abstraction.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, AndroidX Macrobenchmark 1.5.0-alpha07,
UI Automator 2.4.0, Perfetto, Android host tests, JUnit 4.

## Global Constraints

- Run performance measurements locally on a physical Android device.
- Require API 33 or newer and profile only the `RuntimeShader` path.
- Use release-like, non-debuggable benchmark builds.
- Use `CompilationMode.Full`, warm process startup, and eight measured iterations.
- Keep navigation, scene selection, initial composition, and settling outside measured blocks.
- Use generated local artwork and deterministic in-app animations; do not load network content.
- Do not change public `haze-glass` APIs or rendering behavior.
- Do not add CI execution, thresholds, revision comparison, fallback-path benchmarks, power
  metrics, Baseline Profile changes, parameter sweeps, or another target application.
- Pin the device to 60 Hz when supported and record device model, API level, and refresh rate with
  profiling results.

---

## File Structure

- Create `sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenario.kt`
  - Define scenario identifiers, phases, deterministic frames, and the state machine.
- Create `sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSample.kt`
  - Render the Android-only controlled profiling destination and automation semantics.
- Modify `sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/Samples.android.kt`
  - Register the profiling destination only on Android.
- Create `sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenarioTest.kt`
  - Verify stable identifiers, state transitions, and scenario isolation.
- Create `sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSampleTest.kt`
  - Verify selection and the ready/start/complete semantics protocol.
- Modify `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassProductSample.kt`
  - Expose the settled Product page as a stable test tag.
- Modify `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt`
  - Count completed autoplay loops and expose the count as a stable test tag.
- Modify `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassProductSampleTest.kt`
  - Verify settled-page tags.
- Modify `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSampleTest.kt`
  - Verify the completed-loop tag is forwarded by the content composable.
- Create `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassTraceSection.kt`
  - Own stable trace section names.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
  - Trace preparation and budget resolution.
- Modify `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
  - Trace runtime draw, stage recording, interaction, group-alpha, and final composition.
- Create `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassTraceSectionTest.kt`
  - Verify trace names remain unique, stable, and Android-safe.
- Create `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassBenchmarkSupport.kt`
  - Own device requirements, metrics, package name, and shared benchmark constants.
- Modify `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/UiAutomator.kt`
  - Add Product, Playground, and controlled-scenario navigation helpers.
- Create `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassGalleryBenchmark.kt`
  - Measure Product paging and one complete Playground timeline loop.
- Create `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassProfilingBenchmark.kt`
  - Measure the eight controlled scenarios.
- Create `internal/benchmark/README.md`
  - Document physical-device preparation, commands, outputs, and trace interpretation.

---

### Task 1: Define The Controlled Scenario Model

**Files:**

- Create: `sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenario.kt`
- Create: `sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenarioTest.kt`

**Interfaces:**

- Consumes: Compose `Offset`, `Dp`, and snapshot state primitives.
- Produces:
  - `GlassProfilingScenario(id: String, glassEnabled: Boolean)`
  - `GlassProfilingPhase(id: String)`
  - `GlassProfilingFrame`
  - `glassProfilingFrame(scenario: GlassProfilingScenario, progress: Float)`
  - `GlassProfilingState.select`, `start`, `updateProgress`, and `complete`

- [ ] **Step 1: Write the failing scenario and state-machine tests**

Create
`sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenarioTest.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassProfilingScenarioTest {
  @Test
  fun scenarioIds_areStableAndUnique() {
    assertEquals(
      listOf(
        "effect_attach",
        "retained_reuse",
        "interaction_update",
        "optical_update",
        "depth_update",
        "blur_update",
        "source_update",
        "source_update_no_glass",
      ),
      GlassProfilingScenario.entries.map(GlassProfilingScenario::id),
    )
    assertEquals(
      GlassProfilingScenario.entries.size,
      GlassProfilingScenario.entries.map(GlassProfilingScenario::id).toSet().size,
    )
  }

  @Test
  fun noGlassControl_isTheOnlyDisabledScenario() {
    GlassProfilingScenario.entries.forEach { scenario ->
      assertEquals(
        scenario == GlassProfilingScenario.SourceUpdateNoGlass,
        !scenario.glassEnabled,
        scenario.id,
      )
    }
  }

  @Test
  fun frames_changeOnlyTheInputNamedByTheScenario() {
    val expected = mapOf(
      GlassProfilingScenario.EffectAttach to emptySet(),
      GlassProfilingScenario.RetainedReuse to setOf("markerOffset"),
      GlassProfilingScenario.InteractionUpdate to setOf("pressed"),
      GlassProfilingScenario.OpticalUpdate to setOf("lightPosition"),
      GlassProfilingScenario.DepthUpdate to setOf("depth"),
      GlassProfilingScenario.BlurUpdate to setOf("blurRadius"),
      GlassProfilingScenario.SourceUpdate to setOf("sourceOffset"),
      GlassProfilingScenario.SourceUpdateNoGlass to setOf("sourceOffset"),
    )

    expected.forEach { (scenario, expectedChanges) ->
      val early = glassProfilingFrame(scenario, 0.25f)
      val late = glassProfilingFrame(scenario, 0.75f)
      assertEquals(expectedChanges, changedFields(early, late), scenario.id)
    }
  }

  @Test
  fun state_enforcesSelectingReadyRunningCompleteOrder() {
    val state = GlassProfilingState()
    assertEquals(GlassProfilingPhase.Selecting, state.phase)
    assertFalse(state.start())

    state.select(GlassProfilingScenario.SourceUpdate)
    assertEquals(GlassProfilingPhase.Ready, state.phase)
    assertTrue(state.start())
    assertEquals(GlassProfilingPhase.Running, state.phase)

    state.updateProgress(0.4f)
    assertEquals(0.4f, state.progress)
    state.complete()
    assertEquals(GlassProfilingPhase.Complete, state.phase)
    assertEquals(1f, state.progress)
    assertFalse(state.start())
  }

  @Test
  fun state_rejectsInvalidProgressAndSelectionDuringRun() {
    val state = GlassProfilingState()
    state.select(GlassProfilingScenario.BlurUpdate)
    assertTrue(state.start())

    assertFailsWith<IllegalArgumentException> { state.updateProgress(Float.NaN) }
    assertFailsWith<IllegalArgumentException> { state.updateProgress(1.1f) }
    assertFailsWith<IllegalStateException> {
      state.select(GlassProfilingScenario.DepthUpdate)
    }
  }
}

private fun changedFields(
  first: GlassProfilingFrame,
  second: GlassProfilingFrame,
): Set<String> = buildSet {
  if (first.sourceOffset != second.sourceOffset) add("sourceOffset")
  if (first.markerOffset != second.markerOffset) add("markerOffset")
  if (first.lightPosition != second.lightPosition) add("lightPosition")
  if (first.depth != second.depth) add("depth")
  if (first.blurRadius != second.blurRadius) add("blurRadius")
  if (first.pressed != second.pressed) add("pressed")
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
rtk ./gradlew :sample:shared:testAndroidHostTest \
  --tests dev.chrisbanes.haze.sample.GlassProfilingScenarioTest
```

Expected: FAIL during Kotlin compilation because `GlassProfilingScenario`,
`GlassProfilingFrame`, and `GlassProfilingState` do not exist.

- [ ] **Step 3: Add the deterministic scenario model and state machine**

Create
`sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenario.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val GLASS_PROFILING_DURATION_MILLIS: Int = 3_000

internal enum class GlassProfilingScenario(
  val id: String,
  val glassEnabled: Boolean = true,
) {
  EffectAttach("effect_attach"),
  RetainedReuse("retained_reuse"),
  InteractionUpdate("interaction_update"),
  OpticalUpdate("optical_update"),
  DepthUpdate("depth_update"),
  BlurUpdate("blur_update"),
  SourceUpdate("source_update"),
  SourceUpdateNoGlass("source_update_no_glass", glassEnabled = false),
}

internal enum class GlassProfilingPhase(val id: String) {
  Selecting("selecting"),
  Ready("ready"),
  Running("running"),
  Complete("complete"),
}

@Immutable
internal data class GlassProfilingFrame(
  val sourceOffset: Float = 0f,
  val markerOffset: Float = 0f,
  val lightPosition: Offset = Offset(0.25f, 0.25f),
  val depth: Float = 0.5f,
  val blurRadius: Dp = 14.dp,
  val pressed: Boolean = false,
)

internal fun glassProfilingFrame(
  scenario: GlassProfilingScenario,
  progress: Float,
): GlassProfilingFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val base = GlassProfilingFrame()
  return when (scenario) {
    GlassProfilingScenario.EffectAttach -> base
    GlassProfilingScenario.RetainedReuse -> base.copy(
      markerOffset = lerp(-0.4f, 0.4f, progress),
    )
    GlassProfilingScenario.InteractionUpdate -> base.copy(
      pressed = progress < 0.5f,
    )
    GlassProfilingScenario.OpticalUpdate -> base.copy(
      lightPosition = Offset(lerp(0.2f, 0.8f, progress), 0.2f),
    )
    GlassProfilingScenario.DepthUpdate -> base.copy(
      depth = lerp(0.15f, 0.85f, progress),
    )
    GlassProfilingScenario.BlurUpdate -> base.copy(
      blurRadius = lerp(4f, 28f, progress).dp,
    )
    GlassProfilingScenario.SourceUpdate,
    GlassProfilingScenario.SourceUpdateNoGlass,
    -> base.copy(sourceOffset = lerp(-0.08f, 0.08f, progress))
  }
}

@Stable
internal class GlassProfilingState {
  var scenario: GlassProfilingScenario? by mutableStateOf(null)
    private set
  var phase: GlassProfilingPhase by mutableStateOf(GlassProfilingPhase.Selecting)
    private set
  var progress: Float by mutableFloatStateOf(0f)
    private set

  fun select(value: GlassProfilingScenario) {
    check(phase != GlassProfilingPhase.Running)
    scenario = value
    progress = 0f
    phase = GlassProfilingPhase.Ready
  }

  fun start(): Boolean {
    if (phase != GlassProfilingPhase.Ready) return false
    progress = 0f
    phase = GlassProfilingPhase.Running
    return true
  }

  fun updateProgress(value: Float) {
    check(phase == GlassProfilingPhase.Running)
    require(value.isFinite() && value in 0f..1f)
    progress = value
  }

  fun complete() {
    check(phase == GlassProfilingPhase.Running)
    progress = 1f
    phase = GlassProfilingPhase.Complete
  }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
  start + (end - start) * fraction
```

- [ ] **Step 4: Run the focused test**

Run:

```bash
rtk ./gradlew :sample:shared:testAndroidHostTest \
  --tests dev.chrisbanes.haze.sample.GlassProfilingScenarioTest
```

Expected: PASS.

- [ ] **Step 5: Commit the scenario model**

```bash
git add \
  sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenario.kt \
  sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenarioTest.kt
git commit -m "Add Glass profiling scenario model"
```

---

### Task 2: Build The Android Profiling Destination

**Files:**

- Create: `sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSample.kt`
- Modify: `sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/Samples.android.kt`
- Create: `sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSampleTest.kt`

**Interfaces:**

- Consumes: Task 1's scenario identifiers, frame resolver, phase state, and 3,000 ms duration.
- Produces:
  - `AndroidGlassProfiling : Sample`
  - `GlassProfilingSampleContent(state, onBack, modifier)`
  - Resource-id-compatible tags:
    - `glass_profiling_select_<scenario-id>`
    - `glass_profiling_selected_<scenario-id>`
    - `glass_profiling_phase_<phase-id>`
    - `glass_profiling_start`

- [ ] **Step 1: Write the failing Compose protocol tests**

Create
`sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSampleTest.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GlassProfilingSampleTest : ContextTest() {
  @Test
  fun androidSamples_registersTheProfilingDestination() {
    assertTrue(Samples.any { it.title == "Glass — Profiling" })
  }

  @Test
  fun noGlassScenario_exposesReadyStartAndCompleteProtocol() = runComposeUiTest {
    setContent {
      GlassProfilingSampleContent(
        state = remember { GlassProfilingState() },
        onBack = {},
      )
    }

    onNodeWithTag("glass_profiling_select_source_update_no_glass").performClick()
    onNodeWithTag("glass_profiling_selected_source_update_no_glass").assertIsDisplayed()
    onNodeWithTag("glass_profiling_phase_ready").assertIsDisplayed()
    onNodeWithTag("glass_profiling_start").performClick()
    onNodeWithTag("glass_profiling_phase_complete").assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run the protocol test and verify it fails**

Run:

```bash
rtk ./gradlew :sample:shared:testAndroidHostTest \
  --tests dev.chrisbanes.haze.sample.GlassProfilingSampleTest
```

Expected: FAIL during compilation because `GlassProfilingSampleContent` and
`AndroidGlassProfiling` do not exist.

- [ ] **Step 3: Register the Android-only destination**

Update `Samples.android.kt` so its list and new sample object are:

```kotlin
actual val Samples: List<Sample> = buildList {
  addAll(CommonSamples)
  add(AndroidExoPlayer)
  add(AndroidGlassProfiling)
}

@Serializable
data object AndroidGlassProfiling : Sample {
  override val title: String = "Glass — Profiling"

  @Composable
  override fun Content(navController: NavHostController, blurEnabled: Boolean) {
    GlassProfilingSampleContent(
      state = remember { GlassProfilingState() },
      onBack = navController::navigateUp,
    )
  }
}
```

Add the required `androidx.compose.runtime.remember` import.

- [ ] **Step 4: Implement the deterministic profiling content**

Create `GlassProfilingSample.kt` with these exact constants and top-level boundaries:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val ProfilingSurfaceSize = DpSize(280.dp, 180.dp)
private val ProfilingOptics = GlassOptics.Absolute(
  refractionStrength = 0.7f,
  refractionHeight = 0.25f,
  refractionScale = 15f,
  depth = 0.5f,
  blurRadius = 14.dp,
)

@Composable
internal fun GlassProfilingSampleContent(
  state: GlassProfilingState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scenario = state.scenario
  if (scenario == null) {
    GlassProfilingScenarioPicker(
      onScenarioSelected = state::select,
      onBack = onBack,
      modifier = modifier,
    )
  } else {
    GlassProfilingScene(
      state = state,
      scenario = scenario,
      onBack = onBack,
      modifier = modifier,
    )
  }
}

@Composable
private fun GlassProfilingScenarioPicker(
  onScenarioSelected: (GlassProfilingScenario) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Button(onClick = onBack) { Text("Back") }
    GlassProfilingScenario.entries.forEach { scenario ->
      Button(
        onClick = { onScenarioSelected(scenario) },
        modifier = Modifier.testTag("glass_profiling_select_${scenario.id}"),
      ) {
        Text(scenario.id)
      }
    }
  }
}

@Composable
private fun GlassProfilingScene(
  state: GlassProfilingState,
  scenario: GlassProfilingScenario,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val interactionSource = remember { MutableInteractionSource() }
  val density = LocalDensity.current
  val surfaceSizePx = with(density) {
    Size(ProfilingSurfaceSize.width.toPx(), ProfilingSurfaceSize.height.toPx())
  }
  val effect = remember(scenario, interactionSource) {
    GlassVisualEffect().apply {
      optics = ProfilingOptics
      shape = RoundedCornerShape(32.dp)
      this.interactionSource = interactionSource
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      pressed {
        animate(
          toSpec = GlassDefaults.pressAnimationSpec,
          fromSpec = GlassDefaults.releaseAnimationSpec,
        ) {
          lightingIntensity(1f)
          refractionMultiplier(1.08f)
          scale(0.98f)
        }
      }
    }
  }

  LaunchedEffect(state.phase, scenario, effect, interactionSource, surfaceSizePx) {
    if (state.phase != GlassProfilingPhase.Running) return@LaunchedEffect
    when (scenario) {
      GlassProfilingScenario.EffectAttach -> {
        repeat(8) { androidx.compose.runtime.withFrameNanos {} }
      }
      GlassProfilingScenario.InteractionUpdate -> {
        val press = PressInteraction.Press(
          Offset(surfaceSizePx.width * 0.5f, surfaceSizePx.height * 0.5f),
        )
        state.updateProgress(0.25f)
        interactionSource.emit(press)
        delay(GLASS_PROFILING_DURATION_MILLIS.toLong() / 2)
        state.updateProgress(0.75f)
        interactionSource.emit(PressInteraction.Release(press))
        delay(GLASS_PROFILING_DURATION_MILLIS.toLong() / 2)
      }
      else -> {
        Animatable(0f).animateTo(
          targetValue = 1f,
          animationSpec = tween(
            durationMillis = GLASS_PROFILING_DURATION_MILLIS,
            easing = LinearEasing,
          ),
        ) {
          state.updateProgress(value)
          effect.applyProfilingFrame(
            scenario = scenario,
            frame = glassProfilingFrame(scenario, value),
            surfaceSize = surfaceSizePx,
          )
        }
      }
    }
    state.complete()
  }

  val attachGlass =
    scenario != GlassProfilingScenario.EffectAttach ||
      state.phase != GlassProfilingPhase.Ready

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF10131A))
      .testTag("glass_profiling_selected_${scenario.id}"),
  ) {
    Canvas(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState),
    ) {
      val frame = glassProfilingFrame(scenario, state.progress)
      val translatedX = size.width * frame.sourceOffset
      drawRect(Color(0xFF172554))
      repeat(12) { index ->
        val x = translatedX + size.width * index / 11f
        drawLine(
          color = if (index % 2 == 0) Color.Cyan else Color.Magenta,
          start = Offset(x, 0f),
          end = Offset(size.width - x, size.height),
          strokeWidth = 4f,
        )
      }
    }

    if (scenario.glassEnabled && attachGlass) {
      Box(
        Modifier
          .align(Alignment.Center)
          .size(ProfilingSurfaceSize)
          .hazeEffect(hazeState) {
            inputScale = HazeInputScale.Auto
            visualEffect = effect
          }
          .testTag("glass_profiling_surface"),
      )
    }

    if (scenario == GlassProfilingScenario.RetainedReuse) {
      Canvas(Modifier.fillMaxSize()) {
        val frame = glassProfilingFrame(scenario, state.progress)
        drawCircle(
          color = Color.Yellow,
          radius = 6.dp.toPx(),
          center = Offset(
            x = size.width * (0.5f + frame.markerOffset),
            y = 16.dp.toPx(),
          ),
        )
      }
    }

    Column(
      modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = scenario.id,
        modifier = Modifier.testTag("glass_profiling_phase_${state.phase.id}"),
      )
      if (state.phase == GlassProfilingPhase.Ready) {
        Button(
          onClick = { state.start() },
          modifier = Modifier.testTag("glass_profiling_start"),
        ) {
          Text("Start")
        }
      }
    }
  }
}

private fun GlassVisualEffect.applyProfilingFrame(
  scenario: GlassProfilingScenario,
  frame: GlassProfilingFrame,
  surfaceSize: Size,
) {
  when (scenario) {
    GlassProfilingScenario.OpticalUpdate -> {
      lightPosition = Offset(
        x = surfaceSize.width * frame.lightPosition.x,
        y = surfaceSize.height * frame.lightPosition.y,
      )
    }
    GlassProfilingScenario.DepthUpdate,
    GlassProfilingScenario.BlurUpdate,
    -> {
      optics = ProfilingOptics.copy(
        depth = frame.depth,
        blurRadius = frame.blurRadius,
      )
    }
    GlassProfilingScenario.EffectAttach,
    GlassProfilingScenario.RetainedReuse,
    GlassProfilingScenario.InteractionUpdate,
    GlassProfilingScenario.SourceUpdate,
    GlassProfilingScenario.SourceUpdateNoGlass,
    -> Unit
  }
}
```

Keep `state.progress` reads inside the `Canvas` draw lambdas. Do not read progress in composition;
the controlled animation must invalidate drawing or effect state rather than recompose the whole
screen every frame.

- [ ] **Step 5: Run the protocol and scenario tests**

Run:

```bash
rtk ./gradlew :sample:shared:testAndroidHostTest \
  --tests dev.chrisbanes.haze.sample.GlassProfilingScenarioTest \
  --tests dev.chrisbanes.haze.sample.GlassProfilingSampleTest
```

Expected: PASS. The UI test should auto-advance the Compose clock through the 3,000 ms no-Glass
animation and observe `glass_profiling_phase_complete`.

- [ ] **Step 6: Apply formatting and commit**

```bash
rtk ./gradlew :sample:shared:spotlessApply
git add \
  sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSample.kt \
  sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/GlassProfilingScenario.kt \
  sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/Samples.android.kt \
  sample/shared/src/androidHostTest/kotlin/dev/chrisbanes/haze/sample/GlassProfilingSampleTest.kt
git commit -m "Add Android Glass profiling sample"
```

---

### Task 3: Add Stable Completion Semantics To Gallery Journeys

**Files:**

- Modify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassProductSample.kt`
- Modify: `sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassProductSampleTest.kt`
- Modify: `sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSampleTest.kt`

**Interfaces:**

- Consumes: Existing Product `pagerState.settledPage` and Playground autoplay loop.
- Produces:
  - `glass_product_page_<settled-page>`
  - `glass_playground_loop_<completed-loop-count>`

- [ ] **Step 1: Add failing Product and Playground semantics assertions**

In `GlassProductSampleTest.nextAndFavoriteActions_updatePlainUiState`, assert the initial tag before
the click and the settled tag after the click:

```kotlin
    onNodeWithTag("glass_product_page_0").assertIsDisplayed()
    onNodeWithContentDescription("Next artwork").performClick()
    onNodeWithTag("glass_product_page_1").assertIsDisplayed()
```

Add this test to `GlassPlaygroundSampleTest`:

```kotlin
  @Test
  fun content_exposesCompletedTimelineLoop() = runComposeUiTest {
    setContent {
      GlassPlaygroundSampleContent(
        progressProvider = { 0f },
        dragOffsetProvider = { Offset.Zero },
        isPlaying = true,
        recordingMode = true,
        completedLoopCount = 2,
        onPlayPause = {},
        onReset = {},
        onRecordingModeChanged = {},
        onBack = {},
        onDragStart = {},
        onDrag = { _, _ -> },
        onDragEnd = {},
      )
    }

    onNodeWithTag("glass_playground_loop_2").assertIsDisplayed()
  }
```

Add imports for `assertIsDisplayed` and `onNodeWithTag` where missing.

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
rtk ./gradlew :sample:shared:jvmTest \
  --tests dev.chrisbanes.haze.sample.GlassProductSampleTest \
  --tests dev.chrisbanes.haze.sample.GlassPlaygroundSampleTest
```

Expected: FAIL because neither dynamic journey tag exists and
`GlassPlaygroundSampleContent` has no `completedLoopCount` parameter.

- [ ] **Step 3: Expose the settled Product page**

Change the root `BoxWithConstraints` in `GlassProductSampleContent` to:

```kotlin
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .testTag("glass_product_page_${pagerState.settledPage}"),
  ) {
```

Do not remove the existing `glass_product_pager` tag.

- [ ] **Step 4: Count and expose completed Playground loops**

In `GlassPlaygroundState`, add:

```kotlin
  var completedLoopCount by mutableIntStateOf(0)
    private set
```

Import `mutableIntStateOf`. In `runAutoplayLoop`, increment the count immediately after
`progressAnimation.snapTo(0f)`:

```kotlin
      progressAnimation.snapTo(0f)
      completedLoopCount++
```

Reset it in `reset()`:

```kotlin
    completedLoopCount = 0
```

Pass the value from `GlassPlaygroundSample`:

```kotlin
    completedLoopCount = state.completedLoopCount,
```

Add this parameter after `recordingMode` in `GlassPlaygroundSampleContent`:

```kotlin
  completedLoopCount: Int = 0,
```

Apply the tag to its root:

```kotlin
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .testTag("glass_playground_loop_$completedLoopCount"),
  ) {
```

- [ ] **Step 5: Run common and Android-host sample tests**

Run:

```bash
rtk ./gradlew \
  :sample:shared:jvmTest \
  :sample:shared:testAndroidHostTest \
  :sample:shared:spotlessCheck
```

Expected: PASS.

- [ ] **Step 6: Commit the Gallery automation semantics**

```bash
git add \
  sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassProductSample.kt \
  sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSample.kt \
  sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassProductSampleTest.kt \
  sample/shared/src/commonTest/kotlin/dev/chrisbanes/haze/sample/GlassPlaygroundSampleTest.kt
git commit -m "Expose Glass Gallery benchmark phases"
```

---

### Task 4: Instrument Glass Runtime Stages

**Files:**

- Create: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassTraceSection.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt`
- Modify: `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt`
- Create: `haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassTraceSectionTest.kt`

**Interfaces:**

- Consumes: Existing `dev.chrisbanes.haze.trace` expect/actual abstraction.
- Produces stable markers:
  - `HazeGlass.prepare`
  - `HazeGlass.runtimeDraw`
  - `HazeGlass.source`
  - `HazeGlass.blur`
  - `HazeGlass.depth`
  - `HazeGlass.optical`
  - `HazeGlass.detail`
  - `HazeGlass.rim`
  - `HazeGlass.interactionOptical`
  - `HazeGlass.interactionDetail`
  - `HazeGlass.interactionLighting`
  - `HazeGlass.groupAlpha`
  - `HazeGlass.compose`

- [ ] **Step 1: Write the failing trace-name contract**

Create `GlassTraceSectionTest.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassTraceSectionTest {
  @Test
  fun names_areStableUniqueAndWithinAndroidLimit() {
    assertEquals(
      listOf(
        "HazeGlass.prepare",
        "HazeGlass.runtimeDraw",
        "HazeGlass.source",
        "HazeGlass.blur",
        "HazeGlass.depth",
        "HazeGlass.optical",
        "HazeGlass.detail",
        "HazeGlass.rim",
        "HazeGlass.interactionOptical",
        "HazeGlass.interactionDetail",
        "HazeGlass.interactionLighting",
        "HazeGlass.groupAlpha",
        "HazeGlass.compose",
      ),
      GlassTraceSection.all,
    )
    assertEquals(GlassTraceSection.all.size, GlassTraceSection.all.toSet().size)
    assertTrue(GlassTraceSection.all.all { it.length <= 127 })
  }
}
```

- [ ] **Step 2: Run the contract test and verify it fails**

Run:

```bash
rtk ./gradlew :haze-glass:jvmTest \
  --tests dev.chrisbanes.haze.glass.GlassTraceSectionTest
```

Expected: FAIL during compilation because `GlassTraceSection` does not exist.

- [ ] **Step 3: Add the stable trace-name owner**

Create `GlassTraceSection.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

internal object GlassTraceSection {
  const val Prepare = "HazeGlass.prepare"
  const val RuntimeDraw = "HazeGlass.runtimeDraw"
  const val Source = "HazeGlass.source"
  const val Blur = "HazeGlass.blur"
  const val Depth = "HazeGlass.depth"
  const val Optical = "HazeGlass.optical"
  const val Detail = "HazeGlass.detail"
  const val Rim = "HazeGlass.rim"
  const val InteractionOptical = "HazeGlass.interactionOptical"
  const val InteractionDetail = "HazeGlass.interactionDetail"
  const val InteractionLighting = "HazeGlass.interactionLighting"
  const val GroupAlpha = "HazeGlass.groupAlpha"
  const val Compose = "HazeGlass.compose"

  val all = listOf(
    Prepare,
    RuntimeDraw,
    Source,
    Blur,
    Depth,
    Optical,
    Detail,
    Rim,
    InteractionOptical,
    InteractionDetail,
    InteractionLighting,
    GroupAlpha,
    Compose,
  )
}
```

- [ ] **Step 4: Trace preparation without changing delegate selection**

Import `dev.chrisbanes.haze.trace` in `GlassVisualEffect.kt`. Change `prepareDraw` to:

```kotlin
  override fun DrawScope.prepareDraw(context: VisualEffectContext) {
    trace(GlassTraceSection.Prepare) {
      val previousBudget = preparedRenderBudget
      prepareRenderBudget(context, runtimeShaderSupported = isRuntimeShaderGlassSupported())
      if (previousBudget::class != preparedRenderBudget::class) {
        needsDelegateSelection = true
      }
      selectDelegateForDraw(context)
      with(delegate) { prepareDraw(context) }
    }
  }
```

- [ ] **Step 5: Wrap only executed RuntimeShader stage work**

Import `dev.chrisbanes.haze.trace` in `RuntimeShaderGlassDelegate.kt`.

Change only the opening of `draw` from:

```kotlin
  override fun DrawScope.draw(context: VisualEffectContext) {
```

to:

```kotlin
  override fun DrawScope.draw(context: VisualEffectContext) =
    trace(GlassTraceSection.RuntimeDraw) {
```

The existing closing brace remains the closing brace of the inline `trace` block. Do not move or
rewrite the existing draw body.

Within that body, replace the source, blur, and depth blocks with:

```kotlin
      val source = requireRetainedStage(
        if (shouldRecordSource) {
          trace(GlassTraceSection.Source) { recordSource(context, params) }
        } else {
          retainedSource()
        },
        ::clearRetainedOutput,
      ) ?: return

      val blurred = requireRetainedStage(
        if (invalidation.blur) {
          trace(GlassTraceSection.Blur) {
            recordBlurredIfNeeded(source, params, effects)
          }
        } else {
          retainedBlurInput(source, params, effects)
        },
        ::clearRetainedOutput,
      ) ?: return

      val depthInput = requireRetainedStage(
        if (invalidation.depth) {
          trace(GlassTraceSection.Depth) {
            recordDepthInput(source, blurred, params.depth)
          }
        } else {
          retainedDepthInput(source, blurred, params.depth)
        },
        ::clearRetainedOutput,
      ) ?: return
```

Replace the optical, detail, and rim blocks with:

```kotlin
      val optical = requireRetainedStage(
        if (invalidation.optical) {
          trace(GlassTraceSection.Optical) {
            recordOptical(depthInput, params, effects)
          }
        } else {
          layers.optical
        },
        ::clearRetainedOutput,
      ) ?: return
      val refractionDetail = effects.refractionDetail?.let {
        requireRetainedStage(
          if (invalidation.detail) {
            trace(GlassTraceSection.Detail) {
              recordRefractionDetail(source, params, it)
            }
          } else {
            layers.refractionDetail?.takeUnless { layer -> layer.isReleased }
          },
          ::clearRetainedOutput,
        ) ?: return
      }
      requireRetainedStage(
        if (invalidation.rim) {
          trace(GlassTraceSection.Rim) { recordRimIfNeeded(params, effects) }
        } else {
          retainedRim(effects)
        },
        ::clearRetainedOutput,
      ) ?: return
```

Replace the three interaction-recording branches with:

```kotlin
      val completedOptical = if (interactionUniforms.hasOptics) {
        requireRetainedStage(
          trace(GlassTraceSection.InteractionOptical) {
            recordInteractionOptical(
              input = depthInput,
              key = render.opticalKey,
              uniforms = interactionUniforms,
            )
          },
          ::clearRetainedOutput,
        ) ?: return
      } else {
        optical
      }
      val completedRefractionDetail = if (
        interactionUniforms.hasOptics && effects.refractionDetail != null
      ) {
        requireRetainedStage(
          trace(GlassTraceSection.InteractionDetail) {
            recordInteractionRefractionDetail(
              input = source,
              key = effects.refractionDetail.key,
              uniforms = interactionUniforms,
            )
          },
          ::clearRetainedOutput,
        ) ?: return
      } else {
        refractionDetail
      }
      if (interactionUniforms.hasLighting) {
        requireRetainedStage(
          trace(GlassTraceSection.InteractionLighting) {
            recordInteractionLighting(
              key = GlassInteractionLightingKey(
                coordinates = params.coordinates,
                edgeSoftnessPx = params.edgeSoftnessPx,
                cornerRadii = params.cornerRadii,
              ),
              uniforms = interactionUniforms,
            )
          },
          ::clearRetainedOutput,
        ) ?: return
      }
```

Replace the alpha/composition block with:

```kotlin
      if (render.alpha >= 1f) {
        trace(GlassTraceSection.Compose) {
          drawCompletedOutput(completedOptical, completedRefractionDetail, context, params)
        }
      } else {
        val groupAlpha = requireRetainedStage(
          layers.groupAlpha.layer?.takeUnless { it.isReleased },
          ::clearRetainedOutput,
        ) ?: return
        val groupCompositeSize = requireRetainedStage(
          render.groupCompositeSize,
          ::clearRetainedOutput,
        ) ?: return
        trace(GlassTraceSection.GroupAlpha) {
          recordAndDrawGlassGroupAlpha(
            layer = groupAlpha,
            alpha = render.alpha,
            size = groupCompositeSize,
          ) {
            trace(GlassTraceSection.Compose) {
              drawCompletedOutput(
                completedOptical,
                completedRefractionDetail,
                context,
                params,
              )
            }
          }
        }
      }
```

Do not trace retained-stage branches that perform no recording; marker counts must represent actual
work.

Wrap the foreground composition without changing its body. Change only:

```kotlin
  override fun DrawScope.drawForeground(context: VisualEffectContext) {
```

to:

```kotlin
  override fun DrawScope.drawForeground(context: VisualEffectContext) =
    trace(GlassTraceSection.Compose) {
```

The existing closing brace becomes the closing brace of the inline `trace` block.

- [ ] **Step 6: Run trace contract and renderer integration tests**

Run:

```bash
rtk ./gradlew \
  :haze-glass:jvmTest \
  :haze-glass:testAndroidHostTest \
  :haze-glass:spotlessCheck
```

Expected: PASS. Existing stage-record-count tests must remain unchanged because tracing adds
observability only.

- [ ] **Step 7: Commit trace instrumentation**

```bash
git add \
  haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassTraceSection.kt \
  haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/GlassVisualEffect.kt \
  haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/RuntimeShaderGlassDelegate.kt \
  haze-glass/src/commonTest/kotlin/dev/chrisbanes/haze/glass/GlassTraceSectionTest.kt
git commit -m "Trace Glass rendering stages"
```

---

### Task 5: Add Shared Macrobenchmark Support And Gallery Journeys

**Files:**

- Create: `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassBenchmarkSupport.kt`
- Modify: `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/UiAutomator.kt`
- Create: `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassGalleryBenchmark.kt`

**Interfaces:**

- Consumes: Task 3's Product page and Playground loop tags and Task 4's runtime-draw marker.
- Produces:
  - `requireGlassBenchmarkDevice()`
  - `glassMetrics(includeMemory, requireRuntimeMarker)`
  - Product and Playground UI Automator helpers
  - `GlassGalleryBenchmark.productPager`
  - `GlassGalleryBenchmark.playgroundTimeline`

- [ ] **Step 1: Add physical-device and metric support**

Create `GlassBenchmarkSupport.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceSectionMetric
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

internal const val GLASS_TARGET_PACKAGE = "dev.chrisbanes.haze.sample.android"
internal const val GLASS_BENCHMARK_ITERATIONS = 8
internal const val GLASS_RUNTIME_DRAW_SECTION = "HazeGlass.runtimeDraw"

internal fun requireGlassBenchmarkDevice() {
  assumeTrue(
    "Glass profiling requires API 33 or newer",
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
  )
  assumeFalse(
    "Glass profiling requires a physical device",
    isProbablyEmulator(),
  )
}

@OptIn(ExperimentalMetricApi::class)
internal fun glassMetrics(
  includeMemory: Boolean,
  requireRuntimeMarker: Boolean = true,
): List<Metric> = buildList {
  add(FrameTimingMetric())
  if (requireRuntimeMarker) {
    add(
      TraceSectionMetric(
        sectionName = GLASS_RUNTIME_DRAW_SECTION,
        mode = TraceSectionMetric.Mode.Count,
        label = "hazeGlassRuntimeDraw",
      ),
    )
  }
  if (includeMemory) {
    add(MemoryUsageMetric(MemoryUsageMetric.Mode.Max))
  }
}

private fun isProbablyEmulator(): Boolean =
  Build.FINGERPRINT.startsWith("generic") ||
    Build.FINGERPRINT.startsWith("unknown") ||
    Build.MODEL.contains("google_sdk", ignoreCase = true) ||
    Build.MODEL.contains("Emulator", ignoreCase = true) ||
    Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
    Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
    Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
    Build.PRODUCT.contains("sdk", ignoreCase = true)
```

- [ ] **Step 2: Add stable UI Automator helpers**

Append these helpers to `UiAutomator.kt`:

```kotlin
internal fun UiDevice.navigateToGlassProduct() {
  findSampleListItem(By.res("Glass — Product")).click()
  waitForObject(By.res("glass_product_page_0"))
}

internal fun UiDevice.advanceGlassProduct() {
  waitForObject(By.desc("Next artwork")).click()
  waitForObject(By.res("glass_product_page_1"))
}

internal fun UiDevice.navigateToGlassPlayground() {
  findSampleListItem(By.res("Glass — Playground")).click()
  waitForObject(By.res("glass_playground_loop_1"), timeout = 20.seconds)
}

internal fun UiDevice.awaitNextGlassPlaygroundLoop() {
  waitForObject(By.res("glass_playground_loop_2"), timeout = 20.seconds)
}

internal fun UiDevice.navigateToGlassProfiling(scenarioId: String) {
  findSampleListItem(By.res("Glass — Profiling")).click()
  waitForObject(By.res("glass_profiling_select_$scenarioId")).click()
  waitForObject(By.res("glass_profiling_selected_$scenarioId"))
  waitForObject(By.res("glass_profiling_phase_ready"))
}

internal fun UiDevice.runGlassProfilingScenario() {
  waitForObject(By.res("glass_profiling_start")).click()
  waitForObject(By.res("glass_profiling_phase_complete"), timeout = 10.seconds)
}
```

Add `import kotlin.time.Duration.Companion.seconds` only if it is not already present.

- [ ] **Step 3: Add the realistic Gallery benchmarks**

Create `GlassGalleryBenchmark.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class GlassGalleryBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Before
  fun requireDevice() {
    requireGlassBenchmarkDevice()
  }

  @Test
  fun productPager() {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(includeMemory = true),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassProduct()
      },
    ) {
      device.advanceGlassProduct()
    }
  }

  @Test
  fun playgroundTimeline() {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(includeMemory = true),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassPlayground()
      },
    ) {
      device.awaitNextGlassPlaygroundLoop()
    }
  }
}
```

- [ ] **Step 4: Compile the benchmark APK**

Run:

```bash
rtk ./gradlew :internal:benchmark:assemble
```

Expected: PASS. Fix API opt-ins or imports before attempting a device run.

- [ ] **Step 5: Dry-run the Gallery class on a connected API 33+ physical device**

Run:

```bash
rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark
```

Expected: PASS. Product reaches page 1, Playground reaches loop 2, and neither wait times out.

- [ ] **Step 6: Commit the Gallery benchmarks**

```bash
git add \
  internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassBenchmarkSupport.kt \
  internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/UiAutomator.kt \
  internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassGalleryBenchmark.kt
git commit -m "Add Glass Gallery Macrobenchmarks"
```

---

### Task 6: Add Controlled Profiling Macrobenchmarks

**Files:**

- Create: `internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassProfilingBenchmark.kt`

**Interfaces:**

- Consumes: Task 2's controlled protocol and Task 5's metrics, constants, device guard, and UI
  Automator helpers.
- Produces one named benchmark method per controlled scenario.

- [ ] **Step 1: Add the controlled benchmark class**

Create `GlassProfilingBenchmark.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 33)
@RunWith(AndroidJUnit4::class)
class GlassProfilingBenchmark {
  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  @Before
  fun requireDevice() {
    requireGlassBenchmarkDevice()
  }

  @Test
  fun effectAttach() = measureScenario("effect_attach", includeMemory = true)

  @Test
  fun retainedReuse() = measureScenario("retained_reuse")

  @Test
  fun interactionUpdate() = measureScenario("interaction_update")

  @Test
  fun opticalUpdate() = measureScenario("optical_update")

  @Test
  fun depthUpdate() = measureScenario("depth_update")

  @Test
  fun blurUpdate() = measureScenario("blur_update")

  @Test
  fun sourceUpdate() = measureScenario("source_update", includeMemory = true)

  @Test
  fun sourceUpdateNoGlass() = measureScenario(
    scenarioId = "source_update_no_glass",
    requireRuntimeMarker = false,
  )

  private fun measureScenario(
    scenarioId: String,
    includeMemory: Boolean = false,
    requireRuntimeMarker: Boolean = true,
  ) {
    benchmarkRule.measureRepeated(
      packageName = GLASS_TARGET_PACKAGE,
      metrics = glassMetrics(
        includeMemory = includeMemory,
        requireRuntimeMarker = requireRuntimeMarker,
      ),
      compilationMode = CompilationMode.Full(),
      startupMode = StartupMode.WARM,
      iterations = GLASS_BENCHMARK_ITERATIONS,
      setupBlock = {
        startActivityAndWait()
        device.navigateToGlassProfiling(scenarioId)
      },
    ) {
      device.runGlassProfilingScenario()
    }
  }
}
```

- [ ] **Step 2: Compile the benchmark APK**

Run:

```bash
rtk ./gradlew :internal:benchmark:assemble
```

Expected: PASS.

- [ ] **Step 3: Dry-run every controlled scenario on the physical device**

Run:

```bash
rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark
```

Expected: PASS. Every iteration reaches `ready`, starts once, and reaches `complete` within ten
seconds.

- [ ] **Step 4: Run one real source-update profile and inspect marker output**

Run:

```bash
rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdate
```

Expected:

- `FrameTimingMetric` reports `frameOverrunMs` and `frameDurationCpuMs` distributions.
- `hazeGlassRuntimeDrawCount` is positive in every measured iteration.
- Memory output includes heap/RSS/GPU submetrics.
- Perfetto trace files are copied under
  `internal/benchmark/build/outputs/connected_android_test_additional_output/`.

- [ ] **Step 5: Commit the controlled benchmarks**

```bash
git add internal/benchmark/src/main/kotlin/dev/chrisbanes/haze/GlassProfilingBenchmark.kt
git commit -m "Add controlled Glass profiling benchmarks"
```

---

### Task 7: Document And Verify The Local Profiling Workflow

**Files:**

- Create: `internal/benchmark/README.md`
- Verify all files from Tasks 1–6.

**Interfaces:**

- Consumes: All benchmark names, scenario identifiers, metrics, trace markers, and result paths.
- Produces: A self-contained local operator guide.

- [ ] **Step 1: Write the benchmark guide**

Create `internal/benchmark/README.md` with these sections and facts:

```markdown
# Android benchmarks

## Glass profiling requirements

- Physical Android device on API 33 or newer.
- Release-like, non-debuggable target build.
- Device sufficiently charged and cool before measurement.
- Display fixed at 60 Hz when supported; otherwise use one fixed supported rate.
- Debugger detached and unrelated background work minimized.

Record the device model, API level, and selected refresh rate with saved results.

## Validate automation

Run all Glass benchmarks without meaningful measurements:

```shell
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark,dev.chrisbanes.haze.GlassProfilingBenchmark
```

## Run a profile

Run one controlled scenario:

```shell
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdate
```

Run the realistic journeys:

```shell
./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark
```

## Results

JSON reports and Perfetto traces are copied to:

```text
internal/benchmark/build/outputs/connected_android_test_additional_output/
```

Use `frameOverrunMs` for deadline misses and `frameDurationCpuMs` for UI-thread and RenderThread
cost. A positive `hazeGlassRuntimeDrawCount` confirms that a Glass scenario used the modern runtime
delegate. The no-Glass control intentionally omits that metric.

Open representative traces in Android Studio or Perfetto. Application markers describe CPU-side
preparation, recording, and submission; they do not directly measure GPU shader duration. Inspect
system frame-timeline and GPU data when attributing GPU cost.

Expected Glass markers include:

- `HazeGlass.prepare`
- `HazeGlass.runtimeDraw`
- `HazeGlass.source`
- `HazeGlass.blur`
- `HazeGlass.depth`
- `HazeGlass.optical`
- `HazeGlass.detail`
- `HazeGlass.rim`
- `HazeGlass.interactionOptical`
- `HazeGlass.interactionDetail`
- `HazeGlass.interactionLighting`
- `HazeGlass.groupAlpha`
- `HazeGlass.compose`
```

Use normal `./gradlew` commands in repository documentation; the local agent running the plan must
continue prefixing its own shell commands with `rtk`.

- [ ] **Step 2: Run functional verification**

Run:

```bash
rtk ./gradlew \
  :haze-glass:jvmTest \
  :haze-glass:testAndroidHostTest \
  :sample:shared:jvmTest \
  :sample:shared:testAndroidHostTest \
  :internal:benchmark:assemble \
  :haze-glass:spotlessCheck \
  :sample:shared:spotlessCheck \
  :internal:benchmark:spotlessCheck
```

Expected: PASS.

- [ ] **Step 3: Validate all device automation**

Run on the prepared physical device:

```bash
rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark,dev.chrisbanes.haze.GlassProfilingBenchmark
```

Expected: PASS without readiness, navigation, or completion timeouts.

- [ ] **Step 4: Inspect one trace from each scenario family**

Run real profiles for:

```bash
rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassGalleryBenchmark#productPager

rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#sourceUpdate

rtk ./gradlew :internal:benchmark:connectedCheck \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.GlassProfilingBenchmark#interactionUpdate
```

Open one trace from each run. Confirm:

- Product contains `HazeGlass.runtimeDraw` for multiple surfaces.
- Source update repeatedly records source, blur, depth, and optical stages.
- Interaction update shows interaction markers while the retained base stages remain mostly absent.
- App trace section duration is not reported as GPU shader duration.

- [ ] **Step 5: Run repository hygiene checks**

Run:

```bash
rtk git diff --check
rtk git status --short
```

Expected: no whitespace errors and only the intended benchmark, sample, tracing, test, and
documentation files changed.

- [ ] **Step 6: Commit the guide and final verification state**

```bash
git add internal/benchmark/README.md
git commit -m "Document local Glass profiling workflow"
```

---

## Self-Review

- Spec coverage: Tasks 1–3 implement controlled and realistic deterministic workloads; Task 4 adds
  stable internal tracing; Tasks 5–6 add frame timing, runtime-marker, and selected maximum-memory
  metrics; Task 7 documents physical-device preparation, result locations, and CPU-versus-GPU trace
  interpretation.
- Scope: The plan adds no CI job, threshold, revision comparator, fallback benchmark, power metric,
  Baseline Profile change, public API, parameter sweep, or target application.
- Scenario consistency: The sample and benchmark use the same eight explicit identifiers.
  `source_update_no_glass` is the only scenario that disables the RuntimeShader marker metric.
- Protocol consistency: Controlled benchmarks always select a scenario outside measurement, wait
  for `ready`, click `start`, and end at `complete`.
- Metric consistency: Product, Playground, `effect_attach`, and `source_update` collect maximum
  memory; all Glass-enabled scenarios collect `FrameTimingMetric` and the
  `HazeGlass.runtimeDraw` count.
- Test strategy: New behavioral code starts with a failing focused test. Observability-only trace
  wrapping is protected by a stable-name contract and the existing renderer integration suites.
- Placeholder scan: Every task names exact files, interfaces, commands, expected results, and code
  shapes. No incomplete implementation steps remain.
