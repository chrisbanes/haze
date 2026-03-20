# Configurable Position Strategy for Haze

**Issue**: [#881](https://github.com/chrisbanes/haze/issues/881) — `hazeEffect` blur offset/misalignment in split-window modes (Huawei Parallel Space, Vivo)

**Root cause**: Android uses `positionOnScreen()` which returns full-screen coordinates. In split-window modes the app renders in a subset of the screen, so the offset calculation between source and effect is wrong.

**Fix**: Make the position calculation strategy configurable on `HazeState`, defaulting to `positionInRoot()` (local coordinates) which works correctly in split-window modes. Auto-promote to screen coordinates when cross-window scenarios (dialogs/popups) are detected.

## New API

### `HazePositionStrategy` (sealed interface, commonMain)

```kotlin
public sealed interface HazePositionStrategy {
  /** positionInRoot() — correct within a single composition root. */
  data object Local : HazePositionStrategy

  /** positionOnScreen() on Android, positionInWindow() on Skiko. Needed for cross-window. */
  data object Screen : HazePositionStrategy

  /** Default. Uses Local; auto-promotes to Screen when different windowIds are detected. */
  data object Auto : HazePositionStrategy
}
```

### On `HazeState`

```kotlin
class HazeState {
  var positionStrategy: HazePositionStrategy by mutableStateOf(HazePositionStrategy.Auto)
  // ... existing members
}
```

### On `rememberHazeState`

```kotlin
@Composable
fun rememberHazeState(
  positionStrategy: HazePositionStrategy = HazePositionStrategy.Auto,
): HazeState = remember {
  HazeState().apply { this.positionStrategy = positionStrategy }
}
```

## Position resolution

### Move `positionForHaze` to commonMain

Replace the `expect`/`actual` `positionForHaze()` with a single common implementation that takes the resolved strategy:

```kotlin
// Utils.kt (commonMain)
internal fun LayoutCoordinates.positionForHaze(
  strategy: HazePositionStrategy,
): Offset = when (strategy) {
  HazePositionStrategy.Local, HazePositionStrategy.Auto -> {
    positionInRoot()
  }
  HazePositionStrategy.Screen -> {
    positionForHazeScreen()
  }
}
```

Keep a platform `expect`/`actual` only for the screen-coordinates path:

```kotlin
// Utils.kt (commonMain)
internal expect fun LayoutCoordinates.positionForHazeScreen(): Offset

// Utils.android.kt
internal actual fun LayoutCoordinates.positionForHazeScreen(): Offset = positionOnScreen()

// Utils.skiko.kt
internal actual fun LayoutCoordinates.positionForHazeScreen(): Offset = try {
  positionInWindow()
} catch (t: Throwable) {
  Offset.Unspecified
}
```

### Auto-promotion in `HazeEffectNode`

In `HazeEffectNode.updateEffect()`, after areas are resolved:

1. Read `state?.positionStrategy` (observed — triggers re-run on change).
2. If `Auto`: check `areas.any { it.windowId != windowId }`. If true, resolved = `Screen`; else resolved = `Local`.
3. Store the resolved strategy. If it changed from last time, the position is stale — nodes will re-query on the next layout pass since the dirty tracker fires `invalidateDraw()`.

Both `HazeSourceNode` and `HazeEffectNode` read `state.positionStrategy` in their `onPositioned` and pass it to `positionForHaze(strategy)`. Since `positionStrategy` is `mutableStateOf`, changes trigger `onObservedReadsChanged` and subsequent re-layout/re-query.

### Auto-promotion feedback loop

When `HazeEffectNode` detects cross-window and the strategy is `Auto`, it needs to signal both itself and the source nodes to re-query with screen coordinates. The simplest approach:

- `HazeState` gets an internal `resolvedStrategy` property (also `mutableStateOf`).
- `HazeEffectNode.updateEffect()` writes `state.resolvedStrategy = Screen` when cross-window is detected, or `Local` otherwise.
- Both nodes read `state.resolvedStrategy` (not the public `positionStrategy`) when computing positions.
- Since `resolvedStrategy` is observable state, changing it triggers re-composition/re-observation in the source nodes too.

## Property naming

`HazeArea.positionOnScreen` becomes semantically incorrect when using `Local` strategy. Options:
1. Rename to `position` — cleaner but API-breaking.
2. Keep `positionOnScreen` and document that it's the "position used for offset calculation" regardless of actual coordinate space.

Decision: rename to `position`. This is v2 so the API break is acceptable.

## Files changed

| File | Change |
|------|--------|
| `Haze.kt` | Add `positionStrategy` to `HazeState`, `resolvedStrategy` internal, param on `rememberHazeState` |
| `Utils.kt` (commonMain) | Move `positionForHaze(strategy)` here, add `expect positionForHazeScreen()` |
| `Utils.android.kt` | Replace `positionForHaze()` actual with `positionForHazeScreen()` actual |
| `Utils.skiko.kt` | Replace `positionForHaze()` actual with `positionForHazeScreen()` actual |
| `HazeSourceNode.kt` | Read `state.resolvedStrategy`, pass to `positionForHaze()` |
| `HazeEffectNode.kt` | Compute resolved strategy, write to `state.resolvedStrategy`, pass to `positionForHaze()` |
| `HazeArea` | Rename `positionOnScreen` to `position` |
| `VisualEffectContext.kt` | Update `positionOnScreen` references |
| `BlurHelpers.kt` | Update `positionOnScreen` references |
| `HazePositionStrategy.kt` | New file for the sealed interface |

## User-facing usage

```kotlin
// Default — fixes split-window automatically, dialogs auto-promoted
val state = rememberHazeState()

// Force screen coordinates (e.g. known cross-window scenario)
val state = rememberHazeState(positionStrategy = HazePositionStrategy.Screen)

// Force local coordinates (e.g. known same-window, avoid screen coord overhead)
val state = rememberHazeState(positionStrategy = HazePositionStrategy.Local)
```
