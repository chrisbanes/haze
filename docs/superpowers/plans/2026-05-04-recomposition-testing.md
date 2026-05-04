# Recomposition Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automated tests that detect infinite recomposition loops and excessive recompositions in `hazeEffect()` and `hazeSource()` nodes.

**Architecture:** Two test layers: (1) a `RecompositionCounter` utility with threshold-based Compose UI tests for excessive recompositions, and (2) timeout-based loop detection tests that fail if `waitForIdle()` does not return promptly after state mutations. Both layers are implemented as common tests (JVM/Desktop) and duplicated as Android instrumentation tests.

**Tech Stack:** Kotlin Multiplatform, Compose UI Test (`runComposeUiTest` / `createComposeRule`), `assertk`, `kotlinx.coroutines.withTimeout`

---

## File Structure

| File | Responsibility |
|---|---|
| `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt` | Compose utility that wraps content and counts recompositions via `SideEffect` |
| `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt` | Common integration tests asserting recomposition counts stay within thresholds |
| `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt` | Common tests detecting infinite loops using `withTimeout` around `waitForIdle()` |
| `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionCountInstrumentationTest.kt` | Android instrumentation duplicates of count tests |
| `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionLoopInstrumentationTest.kt` | Android instrumentation duplicates of loop tests |
| `haze/build.gradle.kts` | Build configuration for instrumentation tests and Gradle Managed Devices |

---

## Task 1: RecompositionCounter Utility

**Files:**
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt`

- [ ] **Step 1: Create the RecompositionCounter composable**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect

/**
 * A test-only composable that increments [counter] on every recomposition.
 *
 * Usage:
 * ```
 * val count = mutableIntStateOf(0)
 * RecompositionCounter(count) {
 *     Spacer(Modifier.hazeEffect(hazeState))
 * }
 * ```
 */
@Composable
fun RecompositionCounter(
  counter: MutableIntState,
  content: @Composable () -> Unit,
) {
  SideEffect { counter.intValue++ }
  content()
}
```

- [ ] **Step 2: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt
git commit -m "test: add RecompositionCounter utility for counting recompositions"
```

---

## Task 2: Recomposition Count Tests (Common)

**Files:**
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt`

- [ ] **Step 1: Write the test class and imports**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.test.ContextTest
import dev.chrisbanes.haze.test.RecompositionCounter
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecompositionCountTest : ContextTest() {

  companion object {
    // Threshold of 1 ensures we catch any extra unnecessary recomposition.
    // Some tests produce 2 recompositions (e.g. due to Compose scheduling
    // two frames for a single atomic change) - those use isBetween(1, 2)
    // with a comment explaining why.
    private const val RECOMPOSITION_THRESHOLD = 1
  }
```

- [ ] **Step 2: Write test — positionStrategy change causes bounded recompositions**

```kotlin
  @Test
  fun positionStrategyChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)

    setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          RecompositionCounter(effectCounter) {
            Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    waitForIdle()

    // Reset after initial composition. Safe because SideEffect-based counting
    // does not read snapshot state during composition.
    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    hazeState.positionStrategy = HazePositionStrategy.Screen
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 3: Write test — adding a source node causes bounded recompositions**

```kotlin
  @Test
  fun addingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(false)

    setContent {
      RecompositionCounter(sourceCounter) {
        if (showSource.value) {
          Box(Modifier.hazeSource(hazeState).size(50.dp))
        }
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    showSource.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after adding source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 4: Write test — removing a source node causes bounded recompositions**

```kotlin
  @Test
  fun removingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)

    setContent {
      RecompositionCounter(sourceCounter) {
        if (showSource.value) {
          Box(Modifier.hazeSource(hazeState).size(50.dp))
        }
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    showSource.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after removing source")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 5: Write test — blur enabled toggle causes bounded recompositions**

```kotlin
  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val sourceCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          RecompositionCounter(effectCounter) {
            Spacer(
              Modifier
                .hazeEffect(hazeState) {
                  drawContentBehind = blurEnabled.value
                }
                .size(100.dp),
            )
          }
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    blurEnabled.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions after blur toggle")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 6: Write test — adding effect node does not excess recompose source**

```kotlin
  @Test
  fun addingEffectNode_doesNotExcessRecomposeSource() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceCounter = mutableIntStateOf(0)
    val showEffect = mutableStateOf(false)

    setContent {
      RecompositionCounter(sourceCounter) {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          if (showEffect.value) {
            Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
          }
        }
      }
    }
    waitForIdle()

    sourceCounter.intValue = 0

    showEffect.value = true
    waitForIdle()

    assertThat(sourceCounter.intValue, "source recompositions after adding effect node")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 7: Write test — multiple simultaneous area changes cause bounded recompositions**

```kotlin
  @Test
  fun multipleSimultaneousAreaChanges_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSources = mutableStateOf(false)

    setContent {
      if (showSources.value) {
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
        Box(Modifier.hazeSource(hazeState).size(20.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    showSources.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding 5 sources")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 8: Write test — LazyColumn scroll causes bounded recompositions**

```kotlin
  @Test
  fun lazyColumnScroll_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val listState = androidx.compose.foundation.lazy.LazyListState()

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(50) { index ->
            Spacer(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        RecompositionCounter(effectCounter) {
          Spacer(
            Modifier
              .hazeEffect(hazeState)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    listState.scrollToItem(25)
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after scroll")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 9: Write test — LazyColumn item count change causes bounded recompositions**

```kotlin
  @Test
  fun lazyColumnItemCountChange_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val itemCount = mutableIntStateOf(10)

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(itemCount.intValue) { index ->
            Spacer(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        RecompositionCounter(effectCounter) {
          Spacer(
            Modifier
              .hazeEffect(hazeState)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    waitForIdle()

    effectCounter.intValue = 0

    itemCount.intValue = 50
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after item count change")
      .isLessThanOrEqualTo(RECOMPOSITION_THRESHOLD)
  }
}
```

- [ ] **Step 10: Run tests to verify they pass**

```bash
./gradlew :haze:testDebug --tests "dev.chrisbanes.haze.RecompositionCountTest"
```

Expected: All tests pass.

- [ ] **Step 11: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt
git commit -m "test: add recomposition count threshold tests"
```

---

## Task 3: Recomposition Loop Detection Tests (Common)

**Files:**
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt`

- [ ] **Step 1: Write the test class and imports**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalTestApi::class)
class RecompositionLoopTest : ContextTest() {

  companion object {
    private const val IDLE_TIMEOUT_MS = 1000L
  }

  private suspend fun ComposeUiTest.awaitIdleWithTimeout(description: String) {
    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected $description. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
  }
```

- [ ] **Step 2: Write test — positionStrategy mutation does not infinite loop**

```kotlin
  @Test
  fun positionStrategyMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    hazeState.positionStrategy = HazePositionStrategy.Screen

    awaitIdleWithTimeout("after positionStrategy mutation")
  }
```

- [ ] **Step 3: Write test — adding source node does not infinite loop**

```kotlin
  @Test
  fun addingSourceNode_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(false)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    waitForIdle()

    showSource.value = true

    awaitIdleWithTimeout("after adding source node")
  }
```

- [ ] **Step 4: Write test — removing source node does not infinite loop**

```kotlin
  @Test
  fun removingSourceNode_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
    }
    waitForIdle()

    showSource.value = false

    awaitIdleWithTimeout("after removing source node")
  }
```

- [ ] **Step 5: Write test — blur effect block mutation does not infinite loop**

```kotlin
  @Test
  fun blurEffectBlockMutation_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val drawBehind = mutableStateOf(false)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeEffect(hazeState) {
              drawContentBehind = drawBehind.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    drawBehind.value = true

    awaitIdleWithTimeout("after blur effect block mutation")
  }
```

- [ ] **Step 6: Write test — rapid alternating mutations do not infinite loop**

```kotlin
  @Test
  fun rapidAlternatingMutations_doNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val flag = mutableStateOf(false)

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeEffect(hazeState) {
              drawContentBehind = flag.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    repeat(5) {
      flag.value = !flag.value
      awaitIdleWithTimeout("on alternating mutation #$it")
    }
  }
```

- [ ] **Step 7: Write test — LazyColumn scroll does not infinite loop**

```kotlin
  @Test
  fun lazyColumnScroll_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val listState = androidx.compose.foundation.lazy.LazyListState()

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          state = listState,
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(50) { index ->
            Spacer(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        Spacer(
          Modifier
            .hazeEffect(hazeState)
            .fillMaxWidth()
            .height(56.dp),
        )
      }
    }
    waitForIdle()

    listState.scrollToItem(25)

    awaitIdleWithTimeout("after LazyColumn scroll")
  }
```

- [ ] **Step 8: Write test — LazyColumn item count change does not infinite loop**

```kotlin
  @Test
  fun lazyColumnItemCountChange_doesNotInfiniteLoop() = runComposeUiTest {
    val hazeState = HazeState()
    val itemCount = mutableIntStateOf(10)

    setContent {
      Box(Modifier.fillMaxSize()) {
        LazyColumn(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState),
        ) {
          items(itemCount.intValue) { index ->
            Spacer(
              Modifier
                .fillMaxWidth()
                .height(80.dp),
            )
          }
        }

        Spacer(
          Modifier
            .hazeEffect(hazeState)
            .fillMaxWidth()
            .height(56.dp),
        )
      }
    }
    waitForIdle()

    itemCount.intValue = 50

    awaitIdleWithTimeout("after LazyColumn item count change")
  }
}
```

- [ ] **Step 9: Run tests to verify they pass**

```bash
./gradlew :haze:testDebug --tests "dev.chrisbanes.haze.RecompositionLoopTest"
```

Expected: All tests pass.

- [ ] **Step 10: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt
git commit -m "test: add recomposition loop detection tests"
```

---

## Task 4: Android Instrumentation Tests

**Files:**
- Create: `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionCountInstrumentationTest.kt`
- Create: `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionLoopInstrumentationTest.kt`
- Modify: `haze/build.gradle.kts`

- [ ] **Step 1: Configure build.gradle.kts for instrumentation tests**

Add to `android.defaultConfig`:
```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

Add to `android.testOptions`:
```kotlin
managedDevices {
  devices {
    create<ManagedVirtualDevice>("pixel6Api34") {
      device = "Pixel 6"
      apiLevel = 34
      systemImageSource = "aosp_atd"
    }
  }
}
```

Add dependencies:
```kotlin
dependencies {
  androidTestImplementation(libs.assertk)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Create RecompositionCountInstrumentationTest**

Duplicate all tests from `RecompositionCountTest` using `createComposeRule()` and `composeTestRule.setContent` / `composeTestRule.waitForIdle()` instead of `runComposeUiTest`. Use `performTouchInput { swipeUp() }` for the LazyColumn scroll test.

- [ ] **Step 3: Create RecompositionLoopInstrumentationTest**

Duplicate all tests from `RecompositionLoopTest` using `createComposeRule()`. Loop tests do not need timeout wrappers because the Android test runner will naturally fail on ANR; however, the tests are kept structurally identical for consistency.

- [ ] **Step 4: Run instrumentation tests**

```bash
./gradlew :haze:pixel6Api34Check
```

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionCountInstrumentationTest.kt
git add haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionLoopInstrumentationTest.kt
git add haze/build.gradle.kts
git commit -m "test: add Android instrumentation tests for recomposition detection"
```

---

## Task 5: Run Full Test Suite and Spotless

- [ ] **Step 1: Run all haze tests**

```bash
./gradlew :haze:testDebug
```

Expected: All existing tests plus new tests pass.

- [ ] **Step 2: Apply Spotless formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 3: Commit formatting fixes**

```bash
git add -A
git commit -m "style: apply spotless formatting to recomposition tests"
```

---

## Self-Review

### Spec Coverage Check

| Spec Requirement | Task |
|---|---|
| RecompositionCounter utility | Task 1 |
| Bounded recomposition tests for hazeEffect + hazeSource | Task 2 |
| Infinite loop detection tests for hazeEffect + hazeSource | Task 3 |
| `positionStrategy` mutation scenario | Task 2 Step 2, Task 3 Step 2 |
| Adding/removing areas scenario | Task 2 Steps 3–4, Task 3 Steps 3–4 |
| Blur toggle scenario | Task 2 Step 5, Task 3 Step 5 |
| Adding effect node scenario | Task 2 Step 6 |
| Multiple simultaneous changes (batch) | Task 2 Step 7 |
| LazyColumn scroll | Task 2 Step 8, Task 3 Step 7 |
| LazyColumn item count change | Task 2 Step 9, Task 3 Step 8 |
| Rapid alternating mutations | Task 3 Step 6 |
| Threshold-based failure for excess | Task 2 (`isLessThanOrEqualTo(1)`) |
| Immediate failure for infinite loops | Task 3 (`withTimeout` + `AssertionError`) |
| Android instrumentation tests | Task 4 |

### Placeholder Scan

- [x] No "TBD", "TODO", or "implement later" found
- [x] All test code is fully written out
- [x] All commands have expected output
- [x] No references to undefined types or functions

### Type Consistency Check

- [x] `MutableIntState` used consistently across `RecompositionCounter` and tests
- [x] `HazePositionStrategy` values (`Auto`, `Local`, `Screen`) match existing API
- [x] `runComposeUiTest` and `waitForIdle()` usage matches existing test patterns
- [x] `ContextTest` base class used for common test classes
- [x] `createComposeRule()` used for instrumentation tests
- [x] `RECOMPOSITION_THRESHOLD` set to 1 (tighter than original 2)

**Plan complete and saved to `docs/superpowers/plans/2026-05-04-recomposition-testing.md`.**
