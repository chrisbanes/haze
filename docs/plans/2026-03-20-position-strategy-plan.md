# Configurable Position Strategy — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix hazeEffect blur misalignment in split-window modes by making the position calculation strategy configurable on HazeState.

**Architecture:** Add a `HazePositionStrategy` sealed interface (`Local`, `Screen`, `Auto`) on `HazeState`. Move `positionForHaze()` to commonMain dispatching on the resolved strategy. `HazeEffectNode` auto-promotes `Auto` to `Screen` when cross-window is detected, writing to an internal `resolvedStrategy` on `HazeState` that both source and effect nodes observe.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose (Modifier.Node API), expect/actual for screen coordinates only.

**Design doc:** `docs/plans/2026-03-20-position-strategy-design.md`

---

### Task 1: Add `HazePositionStrategy` sealed interface

**Files:**
- Create: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazePositionStrategy.kt`

**Step 1: Create the sealed interface**

```kotlin
// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Stable

/**
 * Strategy for how Haze calculates positions of source and effect nodes.
 *
 * @see HazeState.positionStrategy
 */
@Stable
public sealed interface HazePositionStrategy {
  /**
   * Uses `positionInRoot()` coordinates.
   *
   * This works correctly when source and effect are in the same composition root
   * (same window). This is the most common case and handles split-window modes
   * (e.g. Huawei Parallel Space) correctly.
   *
   * Does **not** work for cross-window scenarios like dialogs or popups.
   */
  public data object Local : HazePositionStrategy

  /**
   * Uses screen-level coordinates (`positionOnScreen()` on Android,
   * `positionInWindow()` on Desktop/Skiko).
   *
   * Required when source and effect are in different windows (e.g. dialogs, popups).
   */
  public data object Screen : HazePositionStrategy

  /**
   * The default strategy. Uses [Local] coordinates, but automatically promotes to
   * [Screen] when it detects that source areas are in a different window than the
   * effect node.
   */
  public data object Auto : HazePositionStrategy
}
```

**Step 2: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazePositionStrategy.kt
git commit -m "feat: add HazePositionStrategy sealed interface"
```

---

### Task 2: Add `positionStrategy` and `resolvedStrategy` to `HazeState`

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt`

**Step 1: Add properties to `HazeState`**

In `Haze.kt`, add to the `HazeState` class:

```kotlin
public var positionStrategy: HazePositionStrategy by mutableStateOf(HazePositionStrategy.Auto)

internal var resolvedStrategy: HazePositionStrategy by mutableStateOf(HazePositionStrategy.Local)
```

**Step 2: Add parameter to `rememberHazeState`**

Change:
```kotlin
@Composable
public fun rememberHazeState(): HazeState = remember { HazeState() }
```

To:
```kotlin
@Composable
public fun rememberHazeState(
  positionStrategy: HazePositionStrategy = HazePositionStrategy.Auto,
): HazeState = remember { HazeState() }.apply {
  this.positionStrategy = positionStrategy
}
```

Note: `positionStrategy` assignment is outside `remember` so it updates if the composable is recomposed with a different value.

**Step 3: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt
git commit -m "feat: add positionStrategy to HazeState and rememberHazeState"
```

---

### Task 3: Move `positionForHaze` to commonMain, keep platform `expect` for screen only

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/Utils.kt`
- Modify: `haze/src/androidMain/kotlin/dev/chrisbanes/haze/Utils.android.kt`
- Modify: `haze/src/skikoMain/kotlin/dev/chrisbanes/haze/Utils.skiko.kt`

**Step 1: Update `Utils.kt` (commonMain)**

Replace:
```kotlin
internal expect fun LayoutCoordinates.positionForHaze(): Offset
```

With:
```kotlin
import androidx.compose.ui.layout.positionInRoot

internal fun LayoutCoordinates.positionForHaze(
  strategy: HazePositionStrategy,
): Offset = when (strategy) {
  HazePositionStrategy.Local, HazePositionStrategy.Auto -> positionInRoot()
  HazePositionStrategy.Screen -> positionForHazeScreen()
}

internal expect fun LayoutCoordinates.positionForHazeScreen(): Offset
```

**Step 2: Update `Utils.android.kt`**

Replace:
```kotlin
import androidx.compose.ui.layout.positionOnScreen

/**
 * We use positionOnScreen on Android, to support dialogs, popup windows, etc.
 */
internal actual fun LayoutCoordinates.positionForHaze(): Offset = positionOnScreen()
```

With:
```kotlin
import androidx.compose.ui.layout.positionOnScreen

internal actual fun LayoutCoordinates.positionForHazeScreen(): Offset = positionOnScreen()
```

**Step 3: Update `Utils.skiko.kt`**

Replace:
```kotlin
import androidx.compose.ui.layout.positionInWindow

/**
 * We only look at window coordinates on Desktop. There's currently no external windows used
 * on Skiko platforms (AFAIK), so there's no need to look at screen coordinates.
 */
internal actual fun LayoutCoordinates.positionForHaze(): Offset = try {
  positionInWindow()
} catch (t: Throwable) {
  Offset.Unspecified
}
```

With:
```kotlin
import androidx.compose.ui.layout.positionInWindow

internal actual fun LayoutCoordinates.positionForHazeScreen(): Offset = try {
  positionInWindow()
} catch (t: Throwable) {
  Offset.Unspecified
}
```

**Step 4: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/Utils.kt \
       haze/src/androidMain/kotlin/dev/chrisbanes/haze/Utils.android.kt \
       haze/src/skikoMain/kotlin/dev/chrisbanes/haze/Utils.skiko.kt
git commit -m "refactor: move positionForHaze to common, keep platform expect for screen only"
```

---

### Task 4: Rename `HazeArea.positionOnScreen` to `position`

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt` (HazeArea class + reset())

**Step 1: Rename the property**

In the `HazeArea` class, change:
```kotlin
public var positionOnScreen: Offset by mutableStateOf(Offset.Unspecified)
    internal set
```
To:
```kotlin
public var position: Offset by mutableStateOf(Offset.Unspecified)
    internal set
```

**Step 2: Update `bounds` computed property**

Change:
```kotlin
internal val bounds: Rect?
    get() = when {
      size.isSpecified && positionOnScreen.isSpecified -> Rect(positionOnScreen, size)
      else -> null
    }
```
To:
```kotlin
internal val bounds: Rect?
    get() = when {
      size.isSpecified && position.isSpecified -> Rect(position, size)
      else -> null
    }
```

**Step 3: Update `toString()`**

Change `positionOnScreen=$positionOnScreen` to `position=$position`.

**Step 4: Update `reset()` function**

Change `positionOnScreen = Offset.Unspecified` to `position = Offset.Unspecified`.

**Step 5: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/Haze.kt
git commit -m "refactor: rename HazeArea.positionOnScreen to position"
```

---

### Task 5: Update `HazeSourceNode` to use resolved strategy

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeSourceNode.kt`

**Step 1: Update `onPositioned`**

Change line 161:
```kotlin
area.positionOnScreen = coordinates.positionForHaze()
```
To:
```kotlin
area.position = coordinates.positionForHaze(state.resolvedStrategy)
```

**Step 2: Update `onPlaced` guard**

Change line 144:
```kotlin
if (area.positionOnScreen.isUnspecified) {
```
To:
```kotlin
if (area.position.isUnspecified) {
```

**Step 3: Update log messages**

Update the log string at lines 166-168 to reference `position` instead of `positionOnScreen`.

**Step 4: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeSourceNode.kt
git commit -m "feat: HazeSourceNode reads resolvedStrategy for position calculation"
```

---

### Task 6: Update `HazeEffectNode` — position calculation and auto-promotion

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt`

**Step 1: Rename `_positionOnScreen` to `_position` and `positionOnScreen` to `position`**

In the backing field (line 72) and public getter (line 81):
```kotlin
private var _position: Offset = Offset.Unspecified
    set(value) { ... }

public val position: Offset get() = _position
```

Update all internal references to use `position` instead of `positionOnScreen` (lines 238, 255, 266, 370, 383, 386, 409, 448, 460).

**Step 2: Update `onPositioned` to use resolved strategy**

Change line 255:
```kotlin
_positionOnScreen = coordinates.positionForHaze()
```
To:
```kotlin
val resolvedStrategy = state?.resolvedStrategy ?: HazePositionStrategy.Local
_position = coordinates.positionForHaze(resolvedStrategy)
```

And line 261:
```kotlin
offset = rootLayoutCoords.positionForHaze(),
```
To:
```kotlin
offset = rootLayoutCoords.positionForHaze(resolvedStrategy),
```

**Step 3: Add auto-promotion logic in `updateEffect()`**

After `windowId = getWindowId()` (line 333) and after areas are resolved (~line 367), add:

```kotlin
// Auto-promote position strategy when cross-window is detected
state?.let { hazeState ->
  val newResolved = when (hazeState.positionStrategy) {
    HazePositionStrategy.Auto -> {
      if (_areas.any { it.windowId != null && it.windowId != windowId }) {
        HazePositionStrategy.Screen
      } else {
        HazePositionStrategy.Local
      }
    }
    else -> hazeState.positionStrategy
  }
  if (hazeState.resolvedStrategy != newResolved) {
    hazeState.resolvedStrategy = newResolved
  }
}
```

Place this after `_areas` is assigned but before `updateAreaOffsets()`.

**Step 4: Update content draw area reference**

Line 370:
```kotlin
contentDrawArea.positionOnScreen = positionOnScreen
```
To:
```kotlin
contentDrawArea.position = position
```

**Step 5: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/HazeEffectNode.kt
git commit -m "feat: HazeEffectNode uses resolved strategy with auto-promotion"
```

---

### Task 7: Update `VisualEffectContext` — rename `positionOnScreen` to `position`

**Files:**
- Modify: `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt`

**Step 1: Rename in the interface**

Change:
```kotlin
public val positionOnScreen: Offset
```
To:
```kotlin
public val position: Offset
```

Update the doc comment accordingly.

**Step 2: Rename in the implementation class**

Change:
```kotlin
override val positionOnScreen: Offset get() = node.positionOnScreen
```
To:
```kotlin
override val position: Offset get() = node.position
```

**Step 3: Commit**

```bash
git add haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffectContext.kt
git commit -m "refactor: rename VisualEffectContext.positionOnScreen to position"
```

---

### Task 8: Update `BlurHelpers.kt` — use `position` instead of `positionOnScreen`

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt`

**Step 1: Update line 183**

Change:
```kotlin
translate(layerOffset - context.positionOnScreen) {
```
To:
```kotlin
translate(layerOffset - context.position) {
```

**Step 2: Update line 186**

Change:
```kotlin
area.positionOnScreen.takeOrElse { Offset.Zero }
```
To:
```kotlin
area.position.takeOrElse { Offset.Zero }
```

**Step 3: Commit**

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/BlurHelpers.kt
git commit -m "refactor: update BlurHelpers to use renamed position property"
```

---

### Task 9: Fix remaining references across the codebase

**Files:**
- Any remaining files referencing `positionOnScreen` (logs, tests, screenshot tests, API dump, baseline profiles)

**Step 1: Search for remaining `positionOnScreen` references**

Run:
```bash
grep -r "positionOnScreen" --include="*.kt" haze/ haze-blur/
```

Fix each occurrence. Common locations:
- Log messages in `HazeSourceNode.kt` and `HazeEffectNode.kt` (already handled in Tasks 5-6)
- `DirtyFields.ScreenPosition` name — keep as-is (internal, name is fine)
- `rootBoundsOnScreen` — keep as-is (this is genuinely screen/root bounds, different concept)

**Step 2: Regenerate API dump**

Run:
```bash
./gradlew :haze:metalavaGenerateSignature
```

Or the equivalent task to update `haze/api/api.txt`. This captures the new public API surface.

**Step 3: Commit**

```bash
git add -A
git commit -m "chore: fix remaining positionOnScreen references and update API dump"
```

---

### Task 10: Write tests for position strategy

**Files:**
- Modify: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeComposeUnitTests.kt`

**Step 1: Write test for default Auto strategy using Local coordinates**

```kotlin
@Test
fun testDefaultPositionStrategyIsAuto() = runComposeUiTest {
  val state = HazeState()
  assertThat(state.positionStrategy).isEqualTo(HazePositionStrategy.Auto)
  assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
}
```

**Step 2: Write test for explicit Local strategy**

```kotlin
@Test
fun testLocalPositionStrategy() = runComposeUiTest {
  val state = HazeState().apply {
    positionStrategy = HazePositionStrategy.Local
  }
  setContent {
    Box(Modifier.hazeSource(state)) {
      Box(Modifier.hazeEffect(state))
    }
  }
  waitForIdle()
  assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Local)
}
```

**Step 3: Write test for explicit Screen strategy**

```kotlin
@Test
fun testScreenPositionStrategy() = runComposeUiTest {
  val state = HazeState().apply {
    positionStrategy = HazePositionStrategy.Screen
  }
  setContent {
    Box(Modifier.hazeSource(state)) {
      Box(Modifier.hazeEffect(state))
    }
  }
  waitForIdle()
  assertThat(state.resolvedStrategy).isEqualTo(HazePositionStrategy.Screen)
}
```

**Step 4: Run tests**

```bash
./gradlew :haze:jvmTest --tests "dev.chrisbanes.haze.HazeComposeUnitTests" -v
```

Expected: All tests pass.

**Step 5: Commit**

```bash
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/HazeComposeUnitTests.kt
git commit -m "test: add unit tests for HazePositionStrategy"
```

---

### Task 11: Build and verify

**Step 1: Run full check**

```bash
./gradlew check assembleDebug -Pandroidx.baselineprofile.skipgeneration
```

Expected: Clean build, all tests pass.

**Step 2: Fix any compilation errors from missed renames**

If any `positionOnScreen` references remain, fix them. Grep to verify:

```bash
grep -rn "positionOnScreen" --include="*.kt" haze/ haze-blur/ | grep -v "DirtyFields\|rootBounds\|baseline-prof\|api/"
```

Expected: No output (all references updated except `rootBoundsOnScreen` and generated files).

**Step 3: Commit any fixes**

```bash
git add -A
git commit -m "fix: address remaining compilation issues from position rename"
```
