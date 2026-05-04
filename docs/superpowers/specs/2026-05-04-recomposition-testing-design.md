# Automated Testing for Recomposition Bugs — Design Spec

**Date:** 2026-05-04  
**Issue:** [#918](https://github.com/chrisbanes/haze/issues/918) — `HazeEffectNode.onObservedReadsChanged` infinite snapshot observation loop  
**Goal:** Add automated test coverage that catches recomposition bugs (infinite loops and excessive recompositions) before they reach users.

---

## Background

Haze 2.0.0-alpha01 introduced a pluggable `VisualEffect` system where `HazeEffectNode` implements `ObserverModifierNode` to react to snapshot-observed state changes. Issue #918 demonstrated that a bug in `onObservedReadsChanged` can create an infinite observation loop on Android, causing ANRs. Currently, the project has no tests targeting recomposition behavior, snapshot observation cycles, or per-state-change invalidation counts.

---

## Design Principles

1. **Immediate failure for infinite loops** — Any detected synchronous observation cycle is a hard test failure.
2. **Threshold-based failure for excess** — Recomposition counts must stay within documented bounds; developers adjust thresholds only when Compose internals legitimately change.
3. **Layered coverage** — Fast node-level tests for PR feedback, plus real Compose UI tests for platform-specific behavior.
4. **Reuse existing patterns** — Use `runComposeUiTest`, `assertk`, and the `ContextTest` base class already present in the project.

---

## Approach A: Snapshot Observation Cycle Detector (Node-Level)

### Purpose
Detect infinite `onObservedReadsChanged` → `updateEffect` → state-read → `onObservedReadsChanged` cycles without spinning up the full Compose UI pipeline.

### Mechanism
- Build a `SnapshotObservationHarness` test helper that implements the minimal `ObserverModifierNode` contract required by both `HazeEffectNode` and `HazeSourceNode`.
- Instantiate either node directly, attach it to the harness, and invoke `onObservedReadsChanged()`.
- The harness registers a `Snapshot` apply observer to track which snapshot states are read during `updateEffect()`.
- If a state mutation triggers `onObservedReadsChanged()` again *before* the harness advances to the next simulated frame, the harness records a cycle.
- After a configurable max depth (e.g., 10 iterations), the test fails and prints the cycle trace.

### File Locations
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/SnapshotObservationHarness.kt`
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/SnapshotObservationCycleTest.kt`

### Scenarios to Cover
1. **`HazeEffectNode` — area list mutation** → assert no synchronous cycle.
2. **`HazeEffectNode` — position strategy mutation** → assert no synchronous cycle.
3. **`HazeSourceNode` — area bounds update** → assert no synchronous cycle.
4. **`HazeSourceNode` — resolved strategy mutation** → assert no synchronous cycle.
5. **Combined effect + source** → both nodes attached to the same `HazeState`, mutate areas → assert neither node cycles.

### Pros & Cons
| Pros | Cons |
|---|---|
| Fast (< 100 ms per test) | Does not catch issues that only appear inside the full Compose runtime |
| Runs on `common` JVM target | Requires maintaining a fake `ObserverModifierNode` harness |
| Precise root-cause reporting (cycle trace) | |

---

## Approach B: Compose UI Recomposition Counter (Integration-Level)

### Purpose
Catch excessive recompositions (not just infinite loops) by counting how many times `hazeEffect()` and `hazeSource()` subtrees recompose per state change.

### Mechanism
- Add a `RecompositionCounter` test utility that wraps a composable and counts recompositions using `SideEffect` + `mutableIntStateOf`.
- In `runComposeUiTest` scenarios, compose a UI with `hazeSource()` and `hazeEffect()` nodes, wrap the affected subtrees with `RecompositionCounter`, and perform state mutations.
- After each mutation + `waitForIdle()`, assert that the counter increased by at most the expected threshold (typically 1, or 2 when a cross-window strategy promotion is expected).

### Scenarios to Cover
1. **Single area added** → counter = 1 after idle.
2. **Area removed** → counter = 1 after idle.
3. **`HazeState.positionStrategy` changed** → counter = 1 after idle.
4. **Split-screen resize (strategy promotion)** → counter ≤ 2.
5. **`blurEnabled` toggled** → counter = 1 after idle.
6. **Five areas added simultaneously** → counter = 1 (batch invalidation).
7. **Rapid alternating mutations** → counter stays bounded per mutation.

### File Locations
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt`
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt`

### Pros & Cons
| Pros | Cons |
|---|---|
| Runs in the real Compose runtime | Slower than node-level tests |
| Catches platform-specific invalidation | Thresholds may need tuning when Compose updates |
| Validates user-visible performance | |

---

## Test Infrastructure

### Shared Utilities
Both approaches live in `haze/src/commonTest/` and rely on:
- `runComposeUiTest` from `androidx.compose.ui.test.ExperimentalTestApi`
- `assertk` assertions
- `ContextTest` base class from `internal/context-test`

### CI Integration
- **Node-level tests (A)** run as part of `./gradlew testDebug` / `./gradlew test` on the common JVM target.
- **Recomposition counters (B)** run on Android and Desktop in the existing multiplatform `commonTest` matrix.
- When a threshold is exceeded, the failure message prints the actual count and the expected maximum so developers can distinguish a regression from a legitimate Compose behavior change.

---

## Out of Scope

- Screenshot tests are **not** modified for this work; visual correctness is already covered by `haze-screenshot-tests`.
- Macrobenchmarks in `internal/benchmark/` are **not** expanded; frame-timing regressions are a separate concern.
- Web (WASM/JS) targets are **not** explicitly included in the initial PR; the `commonTest` sources should compile for web, but CI validation focuses on Android and Desktop where the bug was reported.

---

## Open Questions

1. What is the exact threshold for split-screen strategy promotion — is it reliably ≤ 2 recompositions, or should the test allow ≤ 3?
2. Do we want a `@FlakyTest` annotation or retry logic for recomposition counters on CI emulators with variable performance?
