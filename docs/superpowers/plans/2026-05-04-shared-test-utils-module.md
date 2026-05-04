# Shared test-utils module — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate 3 copies of `RecompositionCounter.kt` by extracting it into a shared `internal/test-utils` MPP module.

**Architecture:** New `internal/test-utils` module mirrors `internal/context-test` conventions. It houses `RecompositionCounter` in `commonMain` at package `dev.chrisbanes.haze.test`. Both `haze` and `haze-blur` depend on it from `commonTest` (KMP) and `androidTest` (AGP) source sets.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Gradle with type-safe accessors

---

### Task 1: Create `internal/test-utils` build file

**Files:**
- Create: `internal/test-utils/build.gradle.kts`

- [ ] **Step 1: Write build.gradle.kts**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
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

- [ ] **Step 2: Add module to settings.gradle.kts**

Add `":internal:test-utils"` after `":internal:context-test"` in `settings.gradle.kts:66`:

```kotlin
include(
  ":haze",
  ":haze-blur",
  ":haze-utils",
  ":haze-materials",
  ":haze-screenshot-tests",
  ":internal:benchmark",
  ":internal:context-test",
  ":internal:test-utils",
  ":internal:dokka",
  ":internal:screenshot-test",
  ":sample:shared",
  ":sample:android",
  ":sample:desktop",
  ":sample:web",
  ":sample:macos",
)
```

- [ ] **Step 3: Sync Gradle to verify module resolves**

```bash
./gradlew :internal:test-utils:tasks --quiet 2>&1 | head -5
```

Expected: Lists tasks (may show "No tasks" but should not show "not found").

---

### Task 2: Create `RecompositionCounter.kt` in the new module

**Files:**
- Create: `internal/test-utils/src/commonMain/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt`

- [ ] **Step 1: Write the file**

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect

/**
 * A test-only composable that increments [counter] on every recomposition.
 *
 * Usage:
 * ```
 * val count = mutableIntStateOf(0)
 * RecompositionCounter(count) {
 *     Spacer(Modifier.hazeEffect(hazeState))
 * }
 * ```
 */
@Composable
fun RecompositionCounter(
  counter: MutableIntState,
  content: @Composable () -> Unit,
) {
  SideEffect { counter.intValue++ }
  content()
}
```

- [ ] **Step 2: Verify compilation**

```bash
./gradlew :internal:test-utils:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

---

### Task 3: Add `test-utils` dependency to `haze`

**Files:**
- Modify: `haze/build.gradle.kts`

- [ ] **Step 1: Add to commonTest source set**

In `haze/build.gradle.kts`, after line 100 (`implementation(projects.internal.contextTest)`), add:

```kotlin
        implementation(projects.internal.testUtils)
```

The `commonTest` block should look like:

```kotlin
    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)

        @OptIn(ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.testUtils)
      }
    }
```

- [ ] **Step 2: Add to androidTest (instrumentation) dependencies**

In `haze/build.gradle.kts`, in the `dependencies` block (around line 141-148), after `androidTestImplementation(libs.androidx.test.ext.junit)` (line 146), add:

```kotlin
  androidTestImplementation(projects.internal.testUtils)
```

The block should look like:

```kotlin
dependencies {
  baselineProfile(projects.internal.benchmark)

  androidTestImplementation(libs.assertk)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(projects.internal.testUtils)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew :haze:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

---

### Task 4: Add `test-utils` dependency to `haze-blur`

**Files:**
- Modify: `haze-blur/build.gradle.kts`

- [ ] **Step 1: Add to commonTest source set**

In `haze-blur/build.gradle.kts`, after line 97 (`implementation(projects.internal.contextTest)`), add:

```kotlin
        implementation(projects.internal.testUtils)
```

The `commonTest` block should look like:

```kotlin
    commonTest {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))

        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.testUtils)
      }
    }
```

- [ ] **Step 2: Add to androidTest (instrumentation) dependencies**

In `haze-blur/build.gradle.kts`, in the `dependencies` block (around line 114-119), after `androidTestImplementation(libs.androidx.test.ext.junit)` (line 117), add:

```kotlin
  androidTestImplementation(projects.internal.testUtils)
```

The block should look like:

```kotlin
dependencies {
  androidTestImplementation(libs.assertk)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(projects.internal.testUtils)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 3: Verify compilation**

```bash
./gradlew :haze-blur:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

---

### Task 5: Update imports in `haze-blur` test files

**Files:**
- Modify: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionCountTest.kt`
- Modify: `haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionCountInstrumentationTest.kt`

- [ ] **Step 1: Update BlurVisualEffectRecompositionCountTest.kt import**

Change line 21:
```
import dev.chrisbanes.haze.blur.test.RecompositionCounter
```
to:
```
import dev.chrisbanes.haze.test.RecompositionCounter
```

- [ ] **Step 2: Update BlurVisualEffectRecompositionCountInstrumentationTest.kt import**

Change line 20:
```
import dev.chrisbanes.haze.blur.test.RecompositionCounter
```
to:
```
import dev.chrisbanes.haze.test.RecompositionCounter
```

- [ ] **Step 3: Verify compilation of both test source sets**

```bash
./gradlew :haze-blur:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

---

### Task 6: Delete the 3 duplicate `RecompositionCounter.kt` files

**Files:**
- Delete: `haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt`
- Delete: `haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt`
- Delete: `haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/test/RecompositionCounter.kt`

- [ ] **Step 1: Determine exact file paths**

```bash
find haze/src/commonTest -name "RecompositionCounter.kt" -not -path "*/blur/*"
find haze/src/androidInstrumentedTest -name "RecompositionCounter.kt"
find haze-blur/src/commonTest -name "RecompositionCounter.kt"
```

Expected: 3 file paths printed.

- [ ] **Step 2: Delete the files**

```bash
rm haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt
rm haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt
rm haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/test/RecompositionCounter.kt
```

- [ ] **Step 3: Remove empty parent directories if any**

```bash
test -d "haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/test" && rmdir "haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/test" 2>/dev/null; true
```

---

### Task 7: Full build and test verification

- [ ] **Step 1: Full build**

```bash
./gradlew :haze:build :haze-blur:build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run common tests**

```bash
./gradlew :haze:allTests :haze-blur:allTests
```

Expected: All tests pass (BUILD SUCCESSFUL).

- [ ] **Step 3: Commit**

```bash
git add settings.gradle.kts internal/test-utils/ haze/build.gradle.kts haze-blur/build.gradle.kts
git add haze/src/commonTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt
git add haze/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/test/RecompositionCounter.kt
git add haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/test/RecompositionCounter.kt
git add haze-blur/src/commonTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionCountTest.kt
git add haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionCountInstrumentationTest.kt
git commit -m "refactor: extract RecompositionCounter into shared internal/test-utils module"
```
