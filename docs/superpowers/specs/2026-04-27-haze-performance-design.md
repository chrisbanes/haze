# Haze Performance Improvements

**Date:** 2026-04-27
**Status:** Draft
**Scope:** `haze`, `haze-blur` modules

## Motivation

Four performance issues identified in the core rendering pipeline, all in
the hot draw path. These compound during scrolling and with multiple
source areas.

## Fix 1: Persistent GraphicsLayer in Blur Draw Path

**Severity:** High
**Files:** `haze-blur/src/commonMain/.../BlurRenderEffectVisualEffect.kt`,
`haze-blur/src/commonMain/.../BlurHelpers.kt`

**Problem:** `createScaledContentLayer()` calls
`graphicsContext.createGraphicsLayer()` every frame, records source areas
into it, then `releaseGraphicsLayer()` discards it. On Android this maps
to native RenderNode alloc/dealloc on every frame in the draw pass.

**Solution:** Store a single `GraphicsLayer` as a field on the blur render
effect delegate. Allocate once (or when scaled size changes). Each frame,
call `layer.record()` to re-record — this replaces previous drawing
commands without destroying the native resource. Release only on
`detach()`.

**Trade-offs:**
- Eliminates alloc/dealloc overhead entirely
- `record()` still runs each frame (no way around this — content changes)
- One extra field to manage, straightforward lifecycle

## Fix 2: Dirty-Gated Sections in `updateEffect()`

**Severity:** High
**Files:** `haze/src/commonMain/.../HazeEffectNode.kt`

**Problem:** `updateEffect()` runs on any snapshot state change and
recomputes everything: invokes user block, iterates areas for listeners,
`findNearestAncestor`, filters/sorts areas, recalculates offsets, and
computes layer bounds — even when only one property changed.

**Solution:** Extend the existing `Bitmask` dirty tracking pattern to guard
expensive sections:

| Section | Gated by DirtyFields |
|---|---|
| `findNearestAncestor` + area filter/sort | `Areas` |
| `updateAreaOffsets()` | `ScreenPosition` or `Areas` |
| Layer bounds calculation | `ScreenPosition`, `Size`, `Areas`, `ExpandLayer`, or `ClipToAreas` |

The user `block` invocation and trivial property copies always run (needed
for correctness). The dirty mask gates the iterative work.

**Trade-offs:**
- Uses existing zero-allocation `Bitmask` pattern
- Risk: stale data if gating is wrong → mitigated by clear, conservative
  flag selection

## Fix 3: Snapshot Observer Edge in `record {}` Block

**Severity:** Medium
**Files:** `haze-blur/src/commonMain/.../BlurHelpers.kt`

**Problem:** `area.contentLayer` (a `mutableStateOf`) is read inside
`layer.record {}` without `Snapshot.withoutReadObservation`, creating
unintended snapshot observation edges that can trigger spurious
recompositions.

**Solution:** Wrap the read in `Snapshot.withoutReadObservation`:

```kotlin
val areaLayer = Snapshot.withoutReadObservation {
    area.contentLayer
        ?.takeUnless { it.isReleased }
        ?.takeUnless { it.size.width <= 0 || it.size.height <= 0 }
}
```

The node's `observeReads`/`updateEffect` mechanism already handles
composition-level observation. This read is purely for the current draw.

**Verified:** The foreground blurring path (`HazeEffectNode.kt:334`) does
NOT have this issue — its `record {}` block only calls
`drawContentSafely()`.

## Fix 4: Pre-Draw Listener Debounce Gate

**Severity:** Medium
**Files:** `haze/src/commonMain/.../HazeEffectNode.kt`

**Problem:** When cross-window detection fires, a pre-draw listener
(`OnPreDrawListener(::invalidateDraw)`) is attached to every source area.
Each area's pre-draw triggers `invalidateDraw()` on the effect node.
With multiple scrolling areas, this cascades.

**Solution:** Add a `needsPreDrawInvalidation` boolean flag:
- Pre-draw listener sets it to `true` (no-op if already true)
- `onPostDraw()` resets it to `false`
- Only call `invalidateDraw()` when it transitions from `false` to `true`

Multiple pre-draws in one frame result in exactly one invalidation.

**Trade-offs:**
- Trivially correct, single boolean, zero allocations
- Same listener infrastructure, just debounced

## Non-Goals

- No API surface changes
- No platform support changes (RenderScript, Skiko all preserved)
- No changes to progressive blur multi-pass (warned about in docs, expected
  cost)
- No changes to RenderEffect cache key comparison (LRU, acceptable
  overhead)

## Verification

- Existing screenshot tests in `haze-screenshot-tests` cover rendering
  correctness
- Manual profiling: Android macrobenchmark or systrace to confirm reduced
  alloc/dealloc and frame time
- Run `./gradlew build` to confirm all platforms compile
