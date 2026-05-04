# Plan: Make Recomposition Tests More Realistic

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Update the recomposition tests to use realistic layouts — specifically a `LazyColumn` as the `hazeSource` with a separate `hazeEffect` overlay (like a top app bar), mirroring the pattern from `ScaffoldSample`.

**Why:** The current tests use trivial `Box` + `Spacer` compositions where source and effect are parent-child. Real usage (e.g., `ScaffoldSample`) places `hazeSource` on a `LazyVerticalGrid` and `hazeEffect` on sibling composables (top bar, bottom bar). Tests should reflect this to catch real-world recomposition issues.

**Architecture:** Add new test methods to the existing `RecompositionCountTest` and `RecompositionLoopTest` (plus their instrumentation variants) that use `LazyColumn` + `Box` overlay layout. Keep existing simple tests as unit-level regression tests.

---

## Pattern Reference

The realistic layout pattern from `ScaffoldSample` (lines 143–163):
```
Scaffold {
  topBar = LargeTopAppBar(Modifier.hazeEffect(hazeState))  // effect: sibling
  LazyVerticalGrid(Modifier.hazeSource(hazeState))          // source: scrollable
}
```

For tests, we simplify to:
```
Box {
  LazyColumn(Modifier.hazeSource(hazeState))       // source: scrollable list
  Box(Modifier.hazeEffect(hazeState))               // effect: overlay
}
```

---

## Task 1: Add LazyColumn-based tests to RecompositionCountTest (commonTest)

**Files:**
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt`

- [ ] **Step 1: Add imports for LazyColumn and related APIs**

Add to the existing imports:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
```

- [ ] **Step 2: Add test — LazyColumn scroll causes bounded recompositions**

This test scrolls the LazyColumn (which is the hazeSource) and verifies the hazeEffect overlay doesn't excessively recompose. This mirrors the real pattern where a user scrolls a list under a blurred top bar.

```kotlin
@Test
fun lazyColumnScroll_causesBoundedRecompositions() = runComposeUiTest {
  val hazeState = HazeState()
  val effectCounter = mutableIntStateOf(0)
  val listState = rememberLazyListState()

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

- [ ] **Step 3: Add test — LazyColumn item count change causes bounded recompositions**

This test adds items to the LazyColumn while an effect overlay is present, simulating dynamic content loading.

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
```

- [ ] **Step 4: Run tests to verify**

```bash
./gradlew :haze:testDebug --tests "dev.chrisbanes.haze.RecompositionCountTest"
```

- [ ] **Step 5: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt
git commit -m "test: add LazyColumn-based recomposition count tests"
```

---

## Task 2: Add LazyColumn-based tests to RecompositionLoopTest (commonTest)

**Files:**
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt`

- [ ] **Step 1: Add imports for LazyColumn and related APIs**

Add to the existing imports:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
```

- [ ] **Step 2: Add test — LazyColumn scroll does not infinite loop**

```kotlin
@Test
fun lazyColumnScroll_doesNotInfiniteLoop() = runComposeUiTest {
  val hazeState = HazeState()
  val listState = rememberLazyListState()

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

  try {
    withTimeout(IDLE_TIMEOUT_MS) {
      waitForIdle()
    }
  } catch (e: TimeoutCancellationException) {
    throw AssertionError(
      "Infinite recomposition loop detected after LazyColumn scroll. " +
        "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
      e,
    )
  }
}
```

- [ ] **Step 3: Add test — LazyColumn item count change does not infinite loop**

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

  try {
    withTimeout(IDLE_TIMEOUT_MS) {
      waitForIdle()
    }
  } catch (e: TimeoutCancellationException) {
    throw AssertionError(
      "Infinite recomposition loop detected after LazyColumn item count change. " +
        "waitForIdle() did not return within ${IDLE_TIMEOUT_MS}ms.",
      e,
    )
  }
}
```

- [ ] **Step 4: Run tests to verify**

```bash
./gradlew :haze:testDebug --tests "dev.chrisbanes.haze.RecompositionLoopTest"
```

- [ ] **Step 5: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt
git commit -m "test: add LazyColumn-based recomposition loop tests"
```

---

## Task 3: Mirror changes in Android instrumentation tests

**Files:**
- Modify: `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionCountInstrumentationTest.kt`
- Modify: `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionLoopInstrumentationTest.kt`

- [ ] **Step 1: Add the same LazyColumn tests to RecompositionCountInstrumentationTest**

Add the same two tests from Task 1 (Steps 2–3), adapted to use `composeTestRule` instead of `runComposeUiTest`:
- `lazyColumnScroll_causesBoundedRecompositions`
- `lazyColumnItemCountChange_causesBoundedRecompositions`

Add necessary imports:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
```

- [ ] **Step 2: Add the same LazyColumn tests to RecompositionLoopInstrumentationTest**

Add the same two tests from Task 2 (Steps 2–3), adapted to use the `assertIdleWithinTimeout` helper:
- `lazyColumnScroll_doesNotInfiniteLoop`
- `lazyColumnItemCountChange_doesNotInfiniteLoop`

Add the same imports as above.

- [ ] **Step 3: Run instrumentation tests to verify**

```bash
./gradlew :haze:pixel6Api34DebugAndroidTest
```

- [ ] **Step 4: Commit**

```bash
git add haze/src/androidInstrumentedTest/
git commit -m "test: add LazyColumn-based instrumentation tests"
```

---

## Task 4: Run full test suite and Spotless

- [ ] **Step 1: Run all haze tests**

```bash
./gradlew :haze:testDebug
```

- [ ] **Step 2: Apply Spotless formatting**

```bash
./gradlew spotlessApply
```

- [ ] **Step 3: Commit formatting fixes (if any)**

```bash
git add -A
git commit -m "style: apply spotless formatting"
```

---

## Self-Review

### What Changed

| File | Change |
|---|---|
| `RecompositionCountTest.kt` | Added 2 new tests using LazyColumn as hazeSource |
| `RecompositionLoopTest.kt` | Added 2 new tests using LazyColumn as hazeSource |
| `RecompositionCountInstrumentationTest.kt` | Added 2 mirrored instrumentation tests |
| `RecompositionLoopInstrumentationTest.kt` | Added 2 mirrored instrumentation tests |

### Why Keep Existing Tests

The existing `Box` + `Spacer` tests serve as fast, minimal unit tests for the recomposition detection logic. The new LazyColumn tests serve as integration tests that verify behavior under realistic scrollable layouts. Both are valuable.

### Spec Coverage

| New Scenario | Count Test | Loop Test |
|---|---|---|
| LazyColumn scroll | Task 1 Step 2 | Task 2 Step 2 |
| LazyColumn item count change | Task 1 Step 3 | Task 2 Step 3 |
