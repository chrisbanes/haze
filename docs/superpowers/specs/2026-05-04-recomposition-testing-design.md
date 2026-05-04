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
3. **Layered coverage** — Fast common tests for PR feedback, plus real Android instrumentation tests for platform-specific behavior.
4. **Reuse existing patterns** — Use `runComposeUiTest`, `assertk`, and the `ContextTest` base class already present in the project.

---

## Approach: Compose UI Recomposition Counter (Integration-Level)

### Purpose
Catch excessive recompositions and infinite loops by counting how many times `hazeEffect()` and `hazeSource()` subtrees recompose per state change, and by using timeouts to detect loops that prevent `waitForIdle()` from returning.

### Mechanism
- Add a `RecompositionCounter` test utility that wraps a composable and counts recompositions using `SideEffect` + `mutableIntStateOf`.
- In `runComposeUiTest` scenarios (common tests), compose a UI with `hazeSource()` and `hazeEffect()` nodes, wrap the affected subtrees with `RecompositionCounter`, and perform state mutations.
- After each mutation + `waitForIdle()`, assert that the counter increased by at most the expected threshold (1 for most scenarios).
- For loop detection, wrap `waitForIdle()` in `withTimeout(IDLE_TIMEOUT_MS)` and fail if it does not return promptly.
- Duplicate the full test suite as Android instrumentation tests using `createComposeRule()` to validate on a real Android runtime.

### Scenarios to Cover

#### Recomposition Count Tests
1. **`positionStrategy` changed** → counter ≤ 1.
2. **Area added** → counter ≤ 1.
3. **Area removed** → counter ≤ 1.
4. **`blurEnabled` / `drawContentBehind` toggled** → counter ≤ 1.
5. **Effect node added** → source counter ≤ 1.
6. **Five areas added simultaneously** → counter ≤ 1 (batch invalidation).
7. **LazyColumn scroll** → counter ≤ 1.
8. **LazyColumn item count change** → counter ≤ 1.

#### Recomposition Loop Detection Tests
1. **`positionStrategy` mutation** → `waitForIdle()` returns within timeout.
2. **Adding source node** → `waitForIdle()` returns within timeout.
3. **Removing source node** → `waitForIdle()` returns within timeout.
4. **Blur effect block mutation** → `waitForIdle()` returns within timeout.
5. **Rapid alternating mutations** → `waitForIdle()` returns within timeout for each toggle.
6. **LazyColumn scroll** → `waitForIdle()` returns within timeout.
7. **LazyColumn item count change** → `waitForIdle()` returns within timeout.

### File Locations
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt`
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionCountTest.kt`
- `haze/src/commonTest/kotlin/dev/chrisbanes/haze/RecompositionLoopTest.kt`
- `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionCountInstrumentationTest.kt`
- `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/RecompositionLoopInstrumentationTest.kt`

### Pros & Cons
| Pros | Cons |
|---|---|
| Runs in the real Compose runtime | Slower than node-level tests |
| Catches platform-specific invalidation | Thresholds may need tuning when Compose updates |
| Validates user-visible performance | |
| Android instrumentation tests catch device-specific behavior | |

---

## Test Infrastructure

### Shared Utilities
Tests rely on:
- `runComposeUiTest` from `androidx.compose.ui.test.ExperimentalTestApi` (common tests)
- `createComposeRule()` from `androidx.compose.ui.test.junit4` (instrumentation tests)
- `assertk` assertions
- `ContextTest` base class from `internal/context-test`

### CI Integration
- **Common tests** run as part of `./gradlew :haze:testDebug` / `./gradlew test` on the JVM and Android targets.
- **Instrumentation tests** run on Android Gradle Managed Devices (Pixel 6, API 34) via `./gradlew :haze:pixel6Api34Check`.
- When a threshold is exceeded, the failure message prints the actual count and the expected maximum so developers can distinguish a regression from a legitimate Compose behavior change.

---

## Out of Scope

- Screenshot tests are **not** modified for this work; visual correctness is already covered by `haze-screenshot-tests`.
- Macrobenchmarks in `internal/benchmark/` are **not** expanded; frame-timing regressions are a separate concern.
- Web (WASM/JS) targets are **not** explicitly included in the initial PR; the `commonTest` sources should compile for web, but CI validation focuses on Android and Desktop where the bug was reported.
- Node-level `SnapshotObservationHarness` tests were considered but not implemented; the integration-level tests provide sufficient coverage.

---

## Open Questions

1. What is the exact threshold for split-screen strategy promotion — is it reliably ≤ 1 recompositions, or should the test allow ≤ 2?
2. Do we want a `@FlakyTest` annotation or retry logic for recomposition counters on CI emulators with variable performance?
