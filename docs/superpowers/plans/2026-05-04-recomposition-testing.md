# Recomposition Testing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automated tests that detect infinite recomposition loops and excessive recompositions in `hazeEffect()` and `hazeSource()` nodes.

**Architecture:** Two test layers: (1) a `RecompositionCounter` utility with threshold-based Compose UI tests for excessive recompositions, and (2) timeout-based loop detection tests that fail if `waitForIdle()` does not return promptly after state mutations.

**Tech Stack:** Kotlin Multiplatform, Compose UI Test (`runComposeUiTest`), `assertk`, `kotlinx.coroutines.withTimeout`

---

## File Structure

| File | Responsibility |
|---|---|
| `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt` | Compose utility that wraps content and counts recompositions via `SideEffect` |
| `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt` | Integration tests asserting recomposition counts stay within thresholds for state mutations |
| `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt` | Tests that detect infinite `onObservedReadsChanged` loops using `withTimeout` around `waitForIdle()` |

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

## Task 2: Recomposition Count Tests

**Files:**
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt`

- [ ] **Step 1: Write the test class and imports**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isAtMost
import assertk.assertions.isEqualTo
import dev.chrisbanes.haze.test.ContextTest
import dev.chrisbanes.haze.test.RecompositionCounter
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class RecompositionCountTest : ContextTest() {

  companion object {
    // Threshold for normal state mutations. Most should cause exactly 1 recomposition.
    private const val RECOMPOSITION_THRESHOLD = 2
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

    // Reset counters after initial composition
    effectCounter.intValue = 0
    sourceCounter.intValue = 0

    hazeState.positionStrategy = HazePositionStrategy.Screen
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions")
      .isAtMost(RECOMPOSITION_THRESHOLD)
    assertThat(sourceCounter.intValue, "source recompositions")
      .isAtMost(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 3: Write test — adding a source node causes bounded recompositions**

```kotlin
  @Test
  fun addingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(false)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    // Reset counter after initial composition
    effectCounter.intValue = 0

    // Adding a source node adds an area to hazeState, which should trigger
    // at most RECOMPOSITION_THRESHOLD recompositions in the effect node
    showSource.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding source")
      .isAtMost(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 4: Write test — removing a source node causes bounded recompositions**

```kotlin
  @Test
  fun removingSourceNode_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val showSource = mutableStateOf(true)

    setContent {
      if (showSource.value) {
        Box(Modifier.hazeSource(hazeState).size(50.dp))
      }
      RecompositionCounter(effectCounter) {
        Spacer(Modifier.hazeEffect(hazeState).size(100.dp))
      }
    }
    waitForIdle()

    // Reset counter after initial composition
    effectCounter.intValue = 0

    showSource.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after removing source")
      .isAtMost(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 5: Write test — blur enabled toggle causes bounded recompositions**

```kotlin
  @Test
  fun blurEnabledToggle_causesBoundedRecompositions() = runComposeUiTest {
    val hazeState = HazeState()
    val effectCounter = mutableIntStateOf(0)
    val blurEnabled = mutableStateOf(true)

    setContent {
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
    waitForIdle()

    // Reset counter after initial composition
    effectCounter.intValue = 0

    blurEnabled.value = false
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after blur toggle")
      .isAtMost(RECOMPOSITION_THRESHOLD)
  }
```

- [ ] **Step 6: Write test — multiple simultaneous area changes cause bounded recompositions**

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

    // Reset counter after initial composition
    effectCounter.intValue = 0

    // All 5 sources appear simultaneously; effect should still only recompose
    // at most RECOMPOSITION_THRESHOLD times (batch invalidation)
    showSources.value = true
    waitForIdle()

    assertThat(effectCounter.intValue, "effect recompositions after adding 5 sources")
      .isAtMost(RECOMPOSITION_THRESHOLD)
  }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
./gradlew :haze:testDebug --tests "dev.chrisbanes.haze.RecompositionCountTest"
```

Expected: All tests pass with output similar to:
```
RecompositionCountTest > positionStrategyChange_causesBoundedRecompositions PASSED
RecompositionCountTest > addingSourceNode_causesBoundedRecompositions PASSED
RecompositionCountTest > removingSourceNode_causesBoundedRecompositions PASSED
RecompositionCountTest > blurEnabledToggle_causesBoundedRecompositions PASSED
RecompositionCountTest > multipleSimultaneousAreaChanges_causesBoundedRecompositions PASSED
```

- [ ] **Step 8: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt
git commit -m "test: add recomposition count threshold tests"
```

---

## Task 3: Recomposition Loop Detection Tests

**Files:**
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt`

- [ ] **Step 1: Write the test class and imports**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
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
    // If waitForIdle() takes longer than this, we assume an infinite recomposition loop.
    private const val IDLE_TIMEOUT_MS = 1000L
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

    // Mutate positionStrategy. If onObservedReadsChanged triggers itself
    // indefinitely, waitForIdle will never return.
    hazeState.positionStrategy = HazePositionStrategy.Screen

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after positionStrategy mutation. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
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

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after adding source node. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
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

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after removing source node. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
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

    try {
      withTimeout(IDLE_TIMEOUT_MS) {
        waitForIdle()
      }
    } catch (e: TimeoutCancellationException) {
      throw AssertionError(
        "Infinite recomposition loop detected after blur effect block mutation. " +
          "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
        e,
      )
    }
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

    // Rapidly toggle state multiple times
    repeat(5) {
      flag.value = !flag.value
      try {
        withTimeout(IDLE_TIMEOUT_MS) {
          waitForIdle()
        }
      } catch (e: TimeoutCancellationException) {
        throw AssertionError(
          "Infinite recomposition loop detected on alternating mutation #$it. " +
            "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
          e,
        )
      }
    }
  }
}
```

- [ ] **Step 7: Run tests to verify they pass**

```bash
./gradlew :haze:testDebug --tests "dev.chrisbanes.haze.RecompositionLoopTest"
```

Expected: All tests pass with output similar to:
```
RecompositionLoopTest > positionStrategyMutation_doesNotInfiniteLoop PASSED
RecompositionLoopTest > addingSourceNode_doesNotInfiniteLoop PASSED
RecompositionLoopTest > removingSourceNode_doesNotInfiniteLoop PASSED
RecompositionLoopTest > blurEffectBlockMutation_doesNotInfiniteLoop PASSED
RecompositionLoopTest > rapidAlternatingMutations_doNotInfiniteLoop PASSED
```

- [ ] **Step 8: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt
git commit -m "test: add recomposition loop detection tests"
```

---

## Task 4: Run Full Test Suite and Spotless

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
| Multiple simultaneous changes (batch) | Task 2 Step 6 |
| Rapid alternating mutations | Task 3 Step 6 |
| Threshold-based failure for excess | Task 2 (`isAtMost(RECOMPOSITION_THRESHOLD)`) |
| Immediate failure for infinite loops | Task 3 (`withTimeout` + `AssertionError`) |

### Placeholder Scan

- [x] No "TBD", "TODO", or "implement later" found
- [x] All test code is fully written out
- [x] All commands have expected output
- [x] No references to undefined types or functions

### Type Consistency Check

- [x] `MutableIntState` used consistently across `RecompositionCounter` and tests
- [x] `HazePositionStrategy` values (`Auto`, `Local`, `Screen`) match existing API
- [x] `runComposeUiTest` and `waitForIdle()` usage matches existing test patterns
- [x] `ContextTest` base class used for all test classes

**Plan complete and saved to `docs/superpowers/plans/2026-05-04-recomposition-testing.md`.**
