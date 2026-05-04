# Shared test-utils module — Design

**Date:** 2026-05-04
**Status:** Approved
**Branch:** `issue-918-quick-hedgehog`

## Motivation

The `RecompositionCounter` composable is duplicated in 3 locations because Kotlin
Multiplatform + AGP does not allow `androidInstrumentedTest` source sets to depend
on `commonTest` source sets. This creates a maintenance burden: any change to the
helper must be replicated across all copies.

## Approach

Create a new internal MPP module `internal/test-utils` that houses shared test
utilities, following the same conventions as the existing `internal/context-test`
module. Both `haze` and `haze-blur` will depend on it from all test source sets
(`commonTest` and `androidInstrumentedTest`).

## Module structure

```
internal/test-utils/
├── build.gradle.kts
└── src/
    └── commonMain/kotlin/dev/chrisbanes/haze/test/
        └── RecompositionCounter.kt
```

### `build.gradle.kts`

```kotlin
plugins {
    id("dev.chrisbanes.android.library")
    id("dev.chrisbanes.kotlin.multiplatform")
}

android {
    namespace = "dev.chrisbanes.haze.internal.testutils"
}

kotlin {
    addDefaultHazeTargets()

    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.runtime)
            }
        }
    }
}
```

### `RecompositionCounter.kt`

Single copy at package `dev.chrisbanes.haze.test`, matching `ContextTest`'s
package. Same content as the current copies.

## Consumer changes

### `haze/build.gradle.kts`

Add `implementation(projects.internal.testUtils)` to `commonTest` and
`androidInstrumentedTest` source set blocks.

### `haze-blur/build.gradle.kts`

Same addition.

### `settings.gradle.kts`

```kotlin
include(":internal:test-utils")
```

## Files to delete

| File | Reason |
|------|--------|
| `haze/src/commonTest/.../test/RecompositionCounter.kt` | Replaced by shared module |
| `haze/src/androidInstrumentedTest/.../test/RecompositionCounter.kt` | Replaced by shared module |
| `haze-blur/src/commonTest/.../blur/test/RecompositionCounter.kt` | Replaced by shared module |

## Import updates

- `haze-blur` test files change `dev.chrisbanes.haze.blur.test.RecompositionCounter`
  → `dev.chrisbanes.haze.test.RecompositionCounter`
- `haze` test files already use the correct package — no change needed.

## Out of scope

The structural similarity between `haze` and `haze-blur` test suites (e.g.,
`RecompositionCountTest` vs `BlurVisualEffectRecompositionCountTest`) is not
addressed. These test different visual effect node types and the similarity is
intentional.
