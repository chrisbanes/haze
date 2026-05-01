# VisualEffect API Cleanup Design

## Goal

Clean up the new `VisualEffect` API before it settles by removing internal-only hooks from the public surface, clarifying lifecycle and ownership semantics, and tightening tests and docs around the intended extension model.

## Context

The current `VisualEffect` API is promising, but it still exposes several details that exist primarily to support the built-in blur implementation. That makes the public abstraction harder to understand for third-party effect authors and creates API traps around lifecycle, draw ordering, and configuration.

The cleanup can make breaking changes because this surface is still marked `ExperimentalHazeApi`.

## Problems To Fix

### 1. Ownership semantics are implicit

`VisualEffect` instances behave like single-owner objects in practice, but the public API does not make that clear. `HazeEffectNode` attaches whichever instance it is given, while `BlurVisualEffect` maintains one mutable attachment/delegate state. Reusing a single effect instance across multiple nodes is therefore unsafe or at least ambiguous.

### 2. The public API leaks blur-specific concerns

Hooks like `calculateInputScaleFactor()` and the public `VisualEffectContext.visualEffect` property exist mainly to support blur internals. They do not pull their weight as generic extension points and make the public surface larger and less coherent.

### 3. Lifecycle is asymmetric

`attach(context)` and `update(context)` receive context, but `detach()` does not. This makes teardown harder for effects that need access to host-provided resources or context-derived state.

### 4. Geometry timing is not explicit

`attach()` can run before layout and positioning have populated geometry. The API does not currently state that `position`, `size`, `layerSize`, or `layerOffset` may be unspecified or zero during attach.

### 5. Draw-order and clipping semantics are harder to read than necessary

The API currently splits draw-order control and clipping policy across multiple knobs whose names do not clearly communicate precedence or scope.

### 6. Bounds semantics are underspecified

`calculateLayerBounds()` receives different coordinate spaces depending on mode, but the contract only explains that input and output must share a coordinate space. It does not make the mode difference explicit.

### 7. Public docs encourage an expensive usage pattern

Examples that allocate `BlurVisualEffect()` inside the `hazeEffect {}` block encourage repeated detach/attach churn, because that block runs during node updates rather than only during initial composition.

### 8. Tests do not pin the intended behavior

The current lifecycle tests cover the happy path, but not the main API traps: shared effect instances, attach-before-geometry, mode-specific layer bounds, or the cleanup of blur-specific internal behavior.

## Design

### Public API changes

The public `VisualEffect` interface should be simplified to focus on lifecycle, drawing, memory pressure, draw-order policy, clipping policy, and layer-bounds expansion:

```kotlin
@ExperimentalHazeApi
public interface VisualEffect {
  public fun DrawScope.draw(context: VisualEffectContext)

  public fun attach(context: VisualEffectContext): Unit = Unit
  public fun update(context: VisualEffectContext): Unit = Unit
  public fun detach(context: VisualEffectContext): Unit = Unit

  public fun onTrimMemory(
    context: VisualEffectContext,
    level: TrimMemoryLevel,
  ): Unit = Unit

  public fun shouldDrawContentBehind(context: VisualEffectContext): Boolean = false

  public fun shouldClipToNodeBounds(): Boolean = false
  public fun shouldPreferClipToAreaBounds(): Boolean = false

  public fun calculateLayerBounds(
    rect: Rect,
    density: Density,
  ): Rect = rect

  public companion object {
    public val Empty: VisualEffect get() = EmptyVisualEffect
  }
}
```

Key public changes:

- Remove `@Stable` from `VisualEffect`.
- Replace `detach()` with `detach(context)`.
- Change `shouldDrawContentBehind` from a `DrawScope` extension to a plain method.
- Rename clipping hooks so their scope is obvious.
- Remove `calculateInputScaleFactor()` from the public interface.

### VisualEffectContext changes

`VisualEffectContext` should remain a host/context abstraction rather than a back-reference to the attached effect.

Changes:

- Remove `val visualEffect: VisualEffect` from the public interface.
- Keep geometry, configuration, platform accessors, and invalidation APIs.
- Expand KDoc to explicitly state that geometry may be unresolved during `attach()`.
- Clarify that `calculateLayerBounds()` is called with different coordinate spaces depending on mode:
  - background mode: rect is in the same root/screen-aligned space used by area positioning
  - foreground mode: rect is local to the effect node

## Internal implementation strategy

### HazeEffectNode

`HazeEffectNode` remains the lifecycle host.

Changes:

- Pass `visualEffectContext` to `detach(context)` when replacing effects and when the node detaches.
- Remove the `visualEffect` property from `HazeEffectNodeVisualEffectContext`.
- Add or document a single-owner contract for `VisualEffect` instances.
- Update node KDoc and `Modifier.hazeEffect` KDoc/examples to stop suggesting per-update effect allocation.

Single-owner handling options were considered:

- document single-owner semantics only
- add a runtime guard for double attachment

The chosen path is to document single-owner semantics and add a runtime guard that throws when the same `VisualEffect` instance is attached to more than one node at the same time. This keeps the contract explicit and fails fast when callers accidentally share one mutable effect instance across nodes.

### Blur internals

Blur-specific policy should move out of the generic public API and stay internal to `haze-blur`.

Changes:

- Move all input-scale-factor decisions into blur internals.
- Replace any `context.visualEffect` reads with direct access to the current `BlurVisualEffect` instance.
- Convert generic helpers that only really support blur into blur-specific helpers.
- Stop using `shouldDrawContentBehind()` as a place to trigger delegate-update side effects.
- Move delegate selection/update work into `update()` or another explicit blur setup path.

This design intentionally does not introduce a new internal SPI such as `VisualEffectInternal`. The codebase currently has one built-in effect family, so a smaller cleanup is preferable to a speculative abstraction.

### API ergonomics and docs

The preferred public configuration path for blur should be `blurEffect {}` rather than assigning a newly-created `BlurVisualEffect()` from inside `hazeEffect {}`.

Docs changes:

- Update `Modifier.hazeEffect` KDoc examples.
- Update migration examples if they still imply effect allocation in the update block.
- Review samples and tests for any remaining discouraged pattern.

## Testing strategy

### Lifecycle tests

Add or update tests to cover:

- `detach(context)` being called on replacement and node detach
- `attach()` running before geometry is resolved
- reusing one `VisualEffect` across multiple nodes throws a clear error describing the single-owner contract

### Blur behavior tests

Add or update tests to cover:

- delegate selection/update no longer depending on `shouldDrawContentBehind()`
- blur-specific input scaling still behaving correctly after removing the generic hook

### Bounds and policy tests

Add targeted tests for:

- `calculateLayerBounds()` in both background and foreground modes
- draw-behind precedence if both node-level and effect-level knobs remain
- clipping policy behavior after the naming cleanup

### Verification commands

At minimum, implementation work should verify with:

- `./gradlew :haze:test`
- `./gradlew :haze-blur:test`
- `./gradlew check`

## Non-goals

- Designing a general internal SPI hierarchy for multiple future effect families
- Making `VisualEffect` instances structurally comparable by value
- Preserving source or binary compatibility for this experimental API when it conflicts with a cleaner design

## Expected outcome

After this cleanup, `VisualEffect` will read as a smaller and more intentional extension point, `VisualEffectContext` will stop leaking internal ownership details, blur internals will own blur-specific behavior, and tests/docs will better describe the intended lifecycle and usage model.
