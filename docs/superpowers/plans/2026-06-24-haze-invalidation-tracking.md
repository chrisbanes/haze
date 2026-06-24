# Haze Invalidation Tracking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in tests that count Haze-owned layout and draw invalidation requests per tagged Haze node.

**Architecture:** Introduce a tiny internal invalidation tracker in `haze` common code. Haze nodes route explicit invalidation calls through internal helpers that record only when a test recorder is active, then call the real Compose invalidation API. Tests install the recorder, tag Haze nodes with a modifier-local marker, and assert per-tag invalidation counts.

**Tech Stack:** Kotlin Multiplatform, Compose Modifier.Node, Compose modifier locals, Compose UI tests, assertk, Gradle.

---

## File Structure

- Create `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt`
  - Internal tracking types, active-recorder holder, modifier-local tag key, tag modifier, and node helper functions.
- Create `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt`
  - Test-only assertion DSL backed by assertk. This keeps assertk out of `commonMain`.
- Modify `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`
  - Add modifier-local consumer support and replace direct explicit draw invalidations with `invalidateHazeDraw(...)`.
- Modify `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt`
  - Route `HazeEffectNodeVisualEffectContext.invalidateDraw()` through `invalidateHazeDraw(...)`.
- Create `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt`
  - Recorder tests and first integration coverage for Haze-owned invalidation requests.

## Task 1: Add the Test API and First Failing Tests

**Files:**
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt`
- Create: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt`
- Create: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt`

- [ ] **Step 1: Add failing tests for the planned API**

Create `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class HazeInvalidationTrackingTest : ContextTest() {

  @Test
  fun positionStrategyChange_recordsOneTaggedEffectDrawInvalidation() = runComposeUiTest {
    val hazeState = HazeState()

    withHazeInvalidationTracking {
      setContent {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          Spacer(
            Modifier
              .hazeInvalidationTag("effect")
              .hazeEffect(hazeState)
              .size(100.dp),
          )
        }
      }
      waitForIdle()

      clearHazeInvalidations()

      hazeState.positionStrategy = HazePositionStrategy.Screen
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsExactly(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun effectRequestedInvalidateDraw_recordsTaggedEffectDrawInvalidation() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = InvalidatingVisualEffect()
    val shouldInvalidate = mutableStateOf(false)

    withHazeInvalidationTracking {
      setContent {
        Box(Modifier.hazeSource(hazeState).size(100.dp)) {
          Spacer(
            Modifier
              .hazeInvalidationTag("effect")
              .hazeEffect(hazeState) {
                effect.shouldInvalidate = shouldInvalidate.value
                visualEffect = effect
              }
              .size(100.dp),
          )
        }
      }
      waitForIdle()

      clearHazeInvalidations()

      shouldInvalidate.value = true
      waitForIdle()

      assertHazeInvalidations("effect") {
        drawInvalidationsExactly(1)
        layoutInvalidationsExactly(0)
      }
    }
  }

  @Test
  fun noActiveRecorder_doesNotStoreInvalidationEvents() = runComposeUiTest {
    val hazeState = HazeState()

    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeInvalidationTag("effect")
            .hazeEffect(hazeState)
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    hazeState.positionStrategy = HazePositionStrategy.Screen
    waitForIdle()

    assertThat(hazeInvalidationEvents()).isEmpty()
  }
}

private class InvalidatingVisualEffect : VisualEffect {
  var shouldInvalidate = false

  override fun update(context: VisualEffectContext) {
    if (shouldInvalidate) {
      shouldInvalidate = false
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) = Unit
}
```

- [ ] **Step 2: Add the compiling surface with no recording yet**

Create `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier

internal enum class HazeInvalidationNodeType {
  Source,
  Effect,
}

internal enum class HazeInvalidationType {
  Draw,
  Layout,
}

internal enum class HazeInvalidationReason {
  DirtyFields,
  PreDraw,
  VisualEffect,
}

internal data class HazeInvalidationEvent(
  val tag: String?,
  val nodeType: HazeInvalidationNodeType,
  val invalidationType: HazeInvalidationType,
  val reason: HazeInvalidationReason,
)

internal fun Modifier.hazeInvalidationTag(tag: String): Modifier = this

internal fun withHazeInvalidationTracking(block: () -> Unit) {
  block()
}

internal fun clearHazeInvalidations() = Unit

internal fun hazeInvalidationEvents(): List<HazeInvalidationEvent> = emptyList()
```

Create `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt`:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

internal class HazeInvalidationAssertionScope internal constructor(
  private val tag: String,
) {
  fun drawInvalidationsExactly(count: Int) = Unit
  fun layoutInvalidationsExactly(count: Int) = Unit
}

internal fun assertHazeInvalidations(
  tag: String,
  block: HazeInvalidationAssertionScope.() -> Unit,
) {
  HazeInvalidationAssertionScope(tag).block()
}
```

- [ ] **Step 3: Run the tests and verify the first failure is meaningful**

Run:

```bash
./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.HazeInvalidationTrackingTest
```

Expected: test task runs and at least `positionStrategyChange_recordsOneTaggedEffectDrawInvalidation` fails because zero draw invalidations were recorded instead of one. If compilation fails because `--tests` does not match multiplatform test task filtering, run:

```bash
./gradlew :haze:jvmTest
```

Expected: compile succeeds and `HazeInvalidationTrackingTest` has assertion failures, not unresolved symbols.

## Task 2: Implement the Opt-In Recorder and Assertions

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt`
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt`
- Test: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt`

- [ ] **Step 1: Replace the stub tracker with an active-recorder implementation in commonMain**

Update `HazeInvalidationTracking.kt` to:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.Modifier

internal enum class HazeInvalidationNodeType {
  Source,
  Effect,
}

internal enum class HazeInvalidationType {
  Draw,
  Layout,
}

internal enum class HazeInvalidationReason {
  DirtyFields,
  PreDraw,
  VisualEffect,
}

internal data class HazeInvalidationEvent(
  val tag: String?,
  val nodeType: HazeInvalidationNodeType,
  val invalidationType: HazeInvalidationType,
  val reason: HazeInvalidationReason,
)

@PublishedApi
internal class HazeInvalidationRecorder {
  val events = mutableListOf<HazeInvalidationEvent>()
}

@PublishedApi
internal var activeHazeInvalidationRecorder: HazeInvalidationRecorder? = null

internal val isHazeInvalidationTrackingActive: Boolean
  get() = activeHazeInvalidationRecorder != null

internal inline fun recordHazeInvalidation(event: () -> HazeInvalidationEvent) {
  activeHazeInvalidationRecorder?.events?.add(event())
}

internal fun Modifier.hazeInvalidationTag(tag: String): Modifier = this

internal fun withHazeInvalidationTracking(block: () -> Unit) {
  val previousRecorder = activeHazeInvalidationRecorder
  val recorder = HazeInvalidationRecorder()
  activeHazeInvalidationRecorder = recorder
  try {
    block()
  } finally {
    activeHazeInvalidationRecorder = previousRecorder
  }
}

internal fun clearHazeInvalidations() {
  activeHazeInvalidationRecorder?.events?.clear()
}

internal fun hazeInvalidationEvents(): List<HazeInvalidationEvent> {
  return activeHazeInvalidationRecorder?.events.orEmpty()
}
```

- [ ] **Step 2: Replace the stub assertion DSL in commonTest**

Update `HazeInvalidationAssertions.kt` to:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import assertk.assertThat
import assertk.assertions.isEqualTo

internal class HazeInvalidationAssertionScope internal constructor(
  private val tag: String,
) {
  fun drawInvalidationsExactly(count: Int) {
    assertInvalidationsExactly(HazeInvalidationType.Draw, count)
  }

  fun layoutInvalidationsExactly(count: Int) {
    assertInvalidationsExactly(HazeInvalidationType.Layout, count)
  }

  private fun assertInvalidationsExactly(
    type: HazeInvalidationType,
    count: Int,
  ) {
    val matchingEvents = hazeInvalidationEvents().filter {
      it.tag == tag && it.invalidationType == type
    }
    assertThat(
      matchingEvents.size,
      "Haze $type invalidations for tag '$tag'. All events: ${hazeInvalidationEvents()}",
    ).isEqualTo(count)
  }
}

internal fun assertHazeInvalidations(
  tag: String,
  block: HazeInvalidationAssertionScope.() -> Unit,
) {
  HazeInvalidationAssertionScope(tag).block()
}
```

- [ ] **Step 3: Run the tests and keep the expected failure**

Run:

```bash
./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.HazeInvalidationTrackingTest
```

Expected: tests still fail with zero recorded draw invalidations. This confirms the recorder works but Haze is not routed through it yet.

## Task 3: Implement Per-Node Tags and Haze Draw Invalidation Helpers

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt`
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt`
- Test: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt`

- [ ] **Step 1: Add modifier-local tag support and node helpers**

Update `HazeInvalidationTracking.kt` imports and implementation:

```kotlin
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocal
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.modifier.modifierLocalProvider
import androidx.compose.ui.node.invalidateDraw
```

Add below the recorder functions:

```kotlin
private val ModifierLocalHazeInvalidationTag: ModifierLocal<String?> = modifierLocalOf { null }

internal fun Modifier.hazeInvalidationTag(tag: String): Modifier {
  return modifierLocalProvider(ModifierLocalHazeInvalidationTag) { tag }
}

private fun ModifierLocalModifierNode.currentHazeInvalidationTag(): String? {
  return if (isHazeInvalidationTrackingActive) {
    ModifierLocalHazeInvalidationTag.current
  } else {
    null
  }
}

internal fun HazeEffectNode.invalidateHazeDraw(reason: HazeInvalidationReason) {
  recordHazeInvalidation {
    HazeInvalidationEvent(
      tag = currentHazeInvalidationTag(),
      nodeType = HazeInvalidationNodeType.Effect,
      invalidationType = HazeInvalidationType.Draw,
      reason = reason,
    )
  }
  invalidateDraw()
}
```

Remove the earlier stub `internal fun Modifier.hazeInvalidationTag(tag: String): Modifier = this`.

- [ ] **Step 2: Make `HazeEffectNode` a modifier-local consumer and route direct invalidations**

In `HazeEffectNode.kt`, add this import:

```kotlin
import androidx.compose.ui.modifier.ModifierLocalModifierNode
```

Remove this import:

```kotlin
import androidx.compose.ui.node.invalidateDraw
```

Update the class declaration:

```kotlin
) : Modifier.Node(),
  CompositionLocalConsumerModifierNode,
  ModifierLocalModifierNode,
  GlobalPositionAwareModifierNode,
  LayoutAwareModifierNode,
  ObserverModifierNode,
  DrawModifierNode,
  TraversableNode,
  HazeEffectScope {
```

Update the pre-draw invalidation:

```kotlin
private val areaPreDrawListener by lazy(LazyThreadSafetyMode.NONE) {
  OnPreDrawListener {
    if (!needsPreDrawInvalidation) {
      needsPreDrawInvalidation = true
      invalidateHazeDraw(HazeInvalidationReason.PreDraw)
    }
  }
}
```

Update `invalidateIfNeeded()`:

```kotlin
if (invalidateRequired) {
  invalidateHazeDraw(HazeInvalidationReason.DirtyFields)
}
```

- [ ] **Step 3: Route `VisualEffectContext.invalidateDraw()` through tracking**

In `VisualEffectContext.kt`, remove:

```kotlin
import androidx.compose.ui.node.invalidateDraw
```

Replace:

```kotlin
override fun invalidateDraw() = node.invalidateDraw()
```

with:

```kotlin
override fun invalidateDraw() = node.invalidateHazeDraw(HazeInvalidationReason.VisualEffect)
```

- [ ] **Step 4: Run the tests and verify the first two pass**

Run:

```bash
./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.HazeInvalidationTrackingTest
```

Expected: all three tests pass. If the tag is `null`, inspect modifier ordering in the test and confirm `hazeInvalidationTag("effect")` appears before `hazeEffect(hazeState)`.

- [ ] **Step 5: Commit**

Run:

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt
git commit -m "Add Haze invalidation tracking test helper"
```

## Task 4: Add Bounded Invalidation Regression Coverage

**Files:**
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt`
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt`

- [ ] **Step 1: Add at-most assertions**

Add to `HazeInvalidationAssertionScope`:

```kotlin
fun drawInvalidationsAtMost(count: Int) {
  assertInvalidationsAtMost(HazeInvalidationType.Draw, count)
}

fun layoutInvalidationsAtMost(count: Int) {
  assertInvalidationsAtMost(HazeInvalidationType.Layout, count)
}

private fun assertInvalidationsAtMost(
  type: HazeInvalidationType,
  count: Int,
) {
  val matchingEvents = hazeInvalidationEvents().filter {
    it.tag == tag && it.invalidationType == type
  }
  assertThat(
    matchingEvents.size <= count,
    "Haze $type invalidations for tag '$tag' expected at most $count. " +
      "Actual=${matchingEvents.size}. All events: ${hazeInvalidationEvents()}",
  ).isEqualTo(true)
}
```

- [ ] **Step 2: Add source membership regression tests**

Append these tests to `HazeInvalidationTrackingTest`:

```kotlin
@Test
fun addingSourceNode_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
  val hazeState = HazeState()
  val showSource = mutableStateOf(false)

  withHazeInvalidationTracking {
    setContent {
      if (showSource.value) {
        Spacer(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(
        Modifier
          .hazeInvalidationTag("effect")
          .hazeEffect(hazeState)
          .size(100.dp),
      )
    }
    waitForIdle()

    clearHazeInvalidations()

    showSource.value = true
    waitForIdle()

    assertHazeInvalidations("effect") {
      drawInvalidationsAtMost(1)
      layoutInvalidationsExactly(0)
    }
  }
}

@Test
fun removingSourceNode_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
  val hazeState = HazeState()
  val showSource = mutableStateOf(true)

  withHazeInvalidationTracking {
    setContent {
      if (showSource.value) {
        Spacer(Modifier.hazeSource(hazeState).size(50.dp))
      }
      Spacer(
        Modifier
          .hazeInvalidationTag("effect")
          .hazeEffect(hazeState)
          .size(100.dp),
      )
    }
    waitForIdle()

    clearHazeInvalidations()

    showSource.value = false
    waitForIdle()

    assertHazeInvalidations("effect") {
      drawInvalidationsAtMost(1)
      layoutInvalidationsExactly(0)
    }
  }
}

@Test
fun multipleSimultaneousSourceChanges_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
  val hazeState = HazeState()
  val showSources = mutableStateOf(false)

  withHazeInvalidationTracking {
    setContent {
      if (showSources.value) {
        repeat(5) {
          Spacer(Modifier.hazeSource(hazeState).size(20.dp))
        }
      }
      Spacer(
        Modifier
          .hazeInvalidationTag("effect")
          .hazeEffect(hazeState)
          .size(100.dp),
      )
    }
    waitForIdle()

    clearHazeInvalidations()

    showSources.value = true
    waitForIdle()

    assertHazeInvalidations("effect") {
      drawInvalidationsAtMost(1)
      layoutInvalidationsExactly(0)
    }
  }
}
```

- [ ] **Step 3: Add effect block mutation regression test**

Append this test to `HazeInvalidationTrackingTest`:

```kotlin
@Test
fun effectBlockMutation_recordsBoundedTaggedEffectInvalidations() = runComposeUiTest {
  val hazeState = HazeState()
  val drawBehind = mutableStateOf(false)

  withHazeInvalidationTracking {
    setContent {
      Box(Modifier.hazeSource(hazeState).size(100.dp)) {
        Spacer(
          Modifier
            .hazeInvalidationTag("effect")
            .hazeEffect(hazeState) {
              drawContentBehind = drawBehind.value
            }
            .size(100.dp),
        )
      }
    }
    waitForIdle()

    clearHazeInvalidations()

    drawBehind.value = true
    waitForIdle()

    assertHazeInvalidations("effect") {
      drawInvalidationsAtMost(1)
      layoutInvalidationsExactly(0)
    }
  }
}
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
./gradlew :haze:jvmTest --tests dev.chrisbanes.haze.HazeInvalidationTrackingTest
```

Expected: all `HazeInvalidationTrackingTest` tests pass.

- [ ] **Step 5: Commit**

Run:

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt
git commit -m "Add Haze invalidation count regression tests"
```

## Task 5: Validate Formatting and Wider Test Impact

**Files:**
- Verify all modified files.

- [ ] **Step 1: Run Spotless**

Run:

```bash
./gradlew :haze:spotlessApply
```

Expected: command completes successfully. If files change, inspect the diff and include formatting changes in the final commit.

- [ ] **Step 2: Run Haze JVM tests**

Run:

```bash
./gradlew :haze:jvmTest
```

Expected: all Haze JVM tests pass.

- [ ] **Step 3: Run common verification if time allows**

Run:

```bash
./gradlew :haze:check
```

Expected: all checks for `haze` pass. If this is too slow or platform targets are unavailable locally, record the failure reason and rely on `:haze:jvmTest` plus `:haze:spotlessApply`.

- [ ] **Step 4: Inspect final diff**

Run:

```bash
git diff --stat HEAD
git diff HEAD -- haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt
```

Expected: diff is limited to the tracking helper, invalidation routing, and tests.

- [ ] **Step 5: Final commit if formatting changed**

If `spotlessApply` changed files after Task 4's commit, run:

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeInvalidationTracking.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationAssertions.kt haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeInvalidationTrackingTest.kt
git commit -m "Apply formatting to invalidation tracking tests"
```

If no formatting changes remain, do not create an empty commit.

## Self-Review

- Spec coverage: The plan implements explicit per-node `hazeInvalidationTag`, an opt-in recorder, Haze-owned draw invalidation routing, per-tag assertions, and layout-zero assertions. It keeps semantics `testTag` out of identity.
- Production overhead: The helper uses an active-recorder nullable check before event creation. The tag lookup happens only inside the event lambda, so it only runs when tracking is active.
- Scope: Initial layout invalidation coverage asserts zero layout events; no production layout invalidation helper is added until Haze has an actual layout invalidation call site.
- Test focus: Tests count Haze-owned invalidation requests, not resulting draw/layout passes.
