# Migration Helpers Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add deprecation typealiases and helper overloads to ease migration from Haze v1.x to v2.0

**Architecture:** Provide backward-compatible APIs in `haze-blur` module through typealiases in old package location and deprecated overloads for common usage patterns. All helpers marked `@Deprecated` with ReplaceWith for IDE auto-fix.

**Tech Stack:** Kotlin, Kotlin Multiplatform, Compose Multiplatform

**Reference:** Design doc at `docs/plans/2026-03-22-migration-helpers-design.md`

---

## Prerequisites

Ensure you're on the `main` branch with v2 codebase:
```bash
git checkout main
git pull origin main
```

---

## Task 1: Create Migration Typealiases

**Files:**
- Create: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`

**Step 1: Create the typealiases file**

Create `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt` with:

```kotlin
// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze

import androidx.compose.runtime.ProvidableCompositionLocal
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeTint
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle

/**
 * Migration typealias. Use [HazeBlurStyle] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeBlurStyle", "dev.chrisbanes.haze.blur.HazeBlurStyle")
)
typealias HazeStyle = HazeBlurStyle

/**
 * Migration typealias. Use [HazeTint] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeTint", "dev.chrisbanes.haze.blur.HazeTint")
)
typealias HazeTint = dev.chrisbanes.haze.blur.HazeTint

/**
 * Migration typealias. Use [HazeProgressive] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeProgressive", "dev.chrisbanes.haze.blur.HazeProgressive")
)
typealias HazeProgressive = dev.chrisbanes.haze.blur.HazeProgressive

/**
 * Migration helper. Use [LocalHazeBlurStyle] from `dev.chrisbanes.haze.blur` package instead.
 */
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("LocalHazeBlurStyle", "dev.chrisbanes.haze.blur.LocalHazeBlurStyle")
)
val LocalHazeStyle: ProvidableCompositionLocal<HazeBlurStyle>
    get() = LocalHazeBlurStyle
```

**Step 2: Verify file compiles**

Run: `./gradlew :haze-blur:compileCommonMainKotlinMetadata --dry-run`

Expected: Build configuration resolves without errors

**Step 3: Run actual compilation**

Run: `./gradlew :haze-blur:compileCommonMainKotlinMetadata`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt
git commit -m "feat: add migration typealiases for v1 to v2 transition

Adds deprecated typealiases for classes moved to dev.chrisbanes.haze.blur:
- HazeStyle -> HazeBlurStyle
- HazeTint -> HazeTint
- HazeProgressive -> HazeProgressive
- LocalHazeStyle -> LocalHazeBlurStyle

All include @ReplaceWith for IDE auto-fix support."
```

---

## Task 2: Add Style Parameter Overload

**Files:**
- Modify: `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeEffectScope.kt`

**Step 1: Check if HazeEffectScope.kt exists**

Run: `ls -la haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/`

If `HazeEffectScope.kt` doesn't exist, create it. If it exists, check its content first.

**Step 2: Add the overload function**

Add to `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeEffectScope.kt`:

```kotlin
// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Migration helper overload that accepts a style parameter directly.
 *
 * In v1, you could write:
 * ```kotlin
 * Modifier.hazeEffect(state, style = HazeMaterials.thin())
 * ```
 *
 * In v2, this becomes:
 * ```kotlin
 * Modifier.hazeEffect(state) {
 *   blurEffect {
 *     style = HazeMaterials.thin()
 *   }
 * }
 * ```
 *
 * This overload supports the v1 pattern during migration.
 */
@Deprecated(
    "Style parameter moved to blurEffect {} block. See migration guide.",
    ReplaceWith(
        "Modifier.hazeEffect(state) { blurEffect { this.style = style } }",
        "dev.chrisbanes.haze.blur.blurEffect"
    )
)
inline fun Modifier.hazeEffect(
    state: HazeState,
    style: HazeBlurStyle,
    crossinline block: HazeEffectScope.() -> Unit = {}
): Modifier = hazeEffect(state) {
    blurEffect {
        this.style = style
    }
    block()
}
```

**Step 3: Verify imports are correct**

Ensure these imports exist at the top of the file:
```kotlin
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
```

**Step 4: Compile to verify**

Run: `./gradlew :haze-blur:compileCommonMainKotlinMetadata`

Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeEffectScope.kt
git commit -m "feat: add migration overload for style parameter

Adds deprecated hazeEffect(state, style) overload that wraps
style in blurEffect {} block. Supports v1 API pattern during
transition with @ReplaceWith for IDE auto-migration."
```

---

## Task 3: Update API Dump Files

**Files:**
- Modify: `haze-blur/api/api.txt`

**Step 1: Generate new API dump**

Run: `./gradlew :haze-blur:apiDump`

Expected: BUILD SUCCESSFUL, api.txt updated

**Step 2: Review API changes**

Run: `git diff haze-blur/api/api.txt`

Verify:
- Typealiases appear in `dev.chrisbanes.haze` package
- New `hazeEffect` overload appears in `dev.chrisbanes.haze.blur` package
- All deprecations are marked correctly

**Step 3: Commit API changes**

```bash
git add haze-blur/api/api.txt
git commit -m "chore: update API dump for migration helpers

Adds typealiases and overload to public API surface."
```

---

## Task 4: Verify Build Passes

**Step 1: Run full build check**

Run: `./gradlew :haze-blur:build --dry-run`

Expected: All tasks resolve without errors

**Step 2: Run actual build**

Run: `./gradlew :haze-blur:build`

Expected: BUILD SUCCESSFUL

**Step 3: Run spotless check**

Run: `./gradlew :haze-blur:spotlessCheck`

Expected: BUILD SUCCESSFUL (no formatting issues)

**Step 4: Fix any formatting issues if needed**

Run: `./gradlew :haze-blur:spotlessApply`

Then commit any changes:
```bash
git add -A
git commit -m "style: apply spotless formatting" || echo "No changes to commit"
```

---

## Task 5: Test Migration Scenarios

**Files:**
- Create temporary test: `/tmp/migration_test.kt` (for manual verification)

**Step 1: Create test script**

Create `/tmp/migration_test.kt`:

```kotlin
// Test that demonstrates migration helpers work

import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle  // Should trigger deprecation warning with quick fix
import dev.chrisbanes.haze.HazeTint   // Should trigger deprecation warning with quick fix
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeTint as NewHazeTint
import dev.chrisbanes.haze.blur.hazeEffect

fun testMigration(state: HazeState) {
    // v1 pattern with typealias - should compile with deprecation warning
    val style: HazeStyle = HazeBlurStyle()
    
    // v1 pattern with style parameter - should compile with deprecation warning
    Modifier.hazeEffect(state, style = HazeBlurStyle())
    
    // v2 pattern - no warnings
    Modifier.hazeEffect(state) {
        blurEffect {
            this.style = HazeBlurStyle()
        }
    }
}
```

**Step 2: Manual IDE verification**

Open the project in IntelliJ IDEA or Android Studio:

1. Open any sample file
2. Add: `import dev.chrisbanes.haze.HazeStyle`
3. Verify: Deprecation warning appears with quick fix option
4. Apply quick fix: Should change to `import dev.chrisbanes.haze.blur.HazeBlurStyle`

**Step 3: Document verification steps**

Add a note to the commit message or docs about IDE support.

---

## Task 6: Update Migration Documentation (Optional)

**Files:**
- Modify: `docs/migrating-2.0.md`

**Step 1: Add section about migration helpers**

Add after the "Step-by-Step Migration" section:

```markdown
## Migration Helpers (v2.0.0-alpha01+)

To ease the transition, v2.0.0-alpha01 includes temporary migration helpers:

### Typealiases for Package Changes

The following typealiases are provided in the old package location:

- `dev.chrisbanes.haze.HazeStyle` → `dev.chrisbanes.haze.blur.HazeBlurStyle`
- `dev.chrisbanes.haze.HazeTint` → `dev.chrisbanes.haze.blur.HazeTint`
- `dev.chrisbanes.haze.HazeProgressive` → `dev.chrisbanes.haze.blur.HazeProgressive`
- `dev.chrisbanes.haze.LocalHazeStyle` → `dev.chrisbanes.haze.blur.LocalHazeBlurStyle`

These are marked `@Deprecated` with `@ReplaceWith` annotations, allowing your IDE to automatically fix imports.

### Style Parameter Overload

A deprecated overload supports the v1 pattern temporarily:

```kotlin
// v1 pattern (deprecated but works)
Modifier.hazeEffect(state, style = HazeMaterials.thin())

// v2 pattern (recommended)
Modifier.hazeEffect(state) {
  blurEffect {
    style = HazeMaterials.thin()
  }
}
```

**Note:** These helpers are temporary and may be removed in v2.1.0. Migrate to the new APIs as soon as convenient.
```

**Step 2: Commit documentation update**

```bash
git add docs/migrating-2.0.md
git commit -m "docs: document migration helpers

Adds section explaining the temporary typealiases and overloads
provided to ease migration from v1 to v2."
```

---

## Summary

After completing this plan, you will have:

1. ✅ Typealiases in old package location with deprecation warnings and ReplaceWith
2. ✅ Style parameter overload for common migration pattern
3. ✅ Updated API dumps reflecting new public APIs
4. ✅ Passing build and spotless checks
5. ✅ Updated migration documentation

All changes are additive and backward-compatible within the v2.x series.
