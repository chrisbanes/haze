# Haze Invalidation Tracking Tests

## Goal

Add tests that keep Haze-owned layout and draw invalidation requests under control. The tests should count invalidations requested by Haze itself, not draw/layout work triggered by scroll, animation, platform scheduling, or Compose internals.

## Design

Introduce an internal, test-only invalidation tracking path inspired by Dejavu's per-node counting style. Haze nodes will route explicit invalidation calls through small internal helpers, such as `invalidateHazeDraw(reason)`, instead of calling Compose invalidation APIs directly.

The helper records an event only when a test has installed an active recorder. In production, no recorder is installed, so the cost is limited to a nullable check before calling the real Compose invalidation API. Event creation, tag lookup, and list mutation must happen only on the active test path.

Tracked events should include:

- `tag`: explicit per-node test identity, nullable for untagged nodes.
- `nodeType`: source or effect.
- `invalidationType`: draw or layout.
- `reason`: compact reason enum or string, such as pre-draw, position, areas, layer bounds, or effect block mutation.

## Node Identity

Tests will identify nodes explicitly with a test-facing marker modifier:

```kotlin
Modifier
  .hazeInvalidationTag("header-effect")
  .hazeEffect(state)
```

The marker should be implemented with a modifier-local value so Haze nodes can read the nearest tag when recording an event. The marker must appear before the Haze modifier in the chain. Semantics `testTag` should remain separate and should not be used as the Haze tracking identity, because Haze modifier nodes do not have a clean common API for reading surrounding semantics.

Untagged events may still be recorded so assertion failures can report useful diagnostics. Per-node assertions should require a tag.

## Test API

Tests should opt in explicitly through a helper that installs the recorder for the duration of a Compose UI test:

```kotlin
runHazeInvalidationTrackingUiTest {
  setContent {
    Box(
      Modifier
        .hazeInvalidationTag("header-effect")
        .hazeEffect(state),
    )
  }

  resetHazeInvalidations()

  state.positionStrategy = HazePositionStrategy.Screen
  waitForIdle()

  assertHazeInvalidations("header-effect") {
    drawInvalidationsAtMost(1)
    layoutInvalidationsExactly(0)
  }
}
```

The first tests should mirror the existing recomposition-count scenarios: position strategy changes, adding/removing source nodes, effect block mutation, and multiple simultaneous source changes. Scroll-specific tests should be avoided unless they assert only Haze-owned invalidation requests, because actual draw calls during scroll are expected noise.

## Production Constraints

Production behavior must remain unchanged:

- No public API should be added.
- No tracking data should be allocated unless a test recorder is active.
- No modifier-local tag lookup should happen unless a recorder is active.
- Existing explicit invalidation behavior should be preserved exactly after recording.

## Open Implementation Detail

The current Haze code has explicit draw invalidation calls in `HazeEffectNode`. The tracking model should include layout invalidation from the start, but layout assertions will initially prove that Haze does not request layout invalidation until such calls exist.
