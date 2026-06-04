# Upgrade AGP 8.13.2 → 9.2.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Android Gradle Plugin from 8.13.2 to 9.2.1 to stay current before 8.x falls out of support.

**Architecture:** Version bump + fix-forward approach. Change the version, build, iteratively fix compilation errors and deprecation warnings. No new AGP 9 features adopted.

**Tech Stack:** AGP 9.2.1, Gradle 9.5.1, Kotlin 2.3.21, compileSdk 36

---

## File Structure

| File | Role |
|------|------|
| `gradle/libs.versions.toml` | Version catalog — AGP version lives here |
| `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/Android.kt` | Shared Android config using `BaseExtension` — primary migration target |
| `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/AndroidApplicationConventionPlugin.kt` | Applies `com.android.application` + cache-fix plugin |
| `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/AndroidLibraryConventionPlugin.kt` | Applies `com.android.library` + cache-fix plugin |
| `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/AndroidTestConventionPlugin.kt` | Applies `com.android.test` + cache-fix plugin |
| `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/KotlinMultiplatformConventionPlugin.kt` | KMP config — uses `androidTarget` |
| `gradle/build-logic/convention/build.gradle.kts` | Build logic dependencies — `android-gradlePlugin` |
| `gradle.properties` | Gradle/Android properties |
| `docs/superpowers/specs/2026-05-31-upgrade-agp-9-design.md` | Design spec (already written) |

---

### Task 1: Bump AGP version

**Files:**
- Modify: `gradle/libs.versions.toml:2`

- [ ] **Step 1: Change AGP version in version catalog**

In `gradle/libs.versions.toml`, line 2, change:
```toml
agp = "8.13.2"
```
to:
```toml
agp = "9.2.1"
```

- [ ] **Step 2: Verify the change is correct**

Run: `grep 'agp = ' gradle/libs.versions.toml`
Expected: `agp = "9.2.1"`

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "Bump AGP to 9.2.1"
```

---

### Task 2: First build — discover breakage

**Files:**
- None yet (diagnostic step)

- [ ] **Step 1: Run debug build to see what breaks**

Run: `./gradlew assembleDebug 2>&1 | tail -100`
Expected: Build errors. Read the output carefully to identify:
1. Compilation errors in `gradle/build-logic/` (API changes in `BaseExtension`, `AndroidComponentsExtension`, etc.)
2. Plugin application errors (cache-fix plugin incompatibility)
3. Configuration errors (deprecated Gradle properties)
4. Dependency resolution failures

- [ ] **Step 2: Categorize the errors**

Document the errors found. Common patterns:
- `Unresolved reference: compileSdkVersion` → API renamed
- `Cannot access class 'BaseExtension'` → class removed/relocated
- `Plugin 'org.gradle.android.cache-fix' is incompatible` → plugin version issue
- Property warnings/errors in `gradle.properties`

---

### Task 3: Fix build logic compilation errors

**Files:**
- Modify: `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/Android.kt`
- Modify: `gradle/build-logic/convention/build.gradle.kts` (if AGP dependency coordinate changed)

This task is conditional — fix exactly the errors discovered in Task 2. Below are the most likely scenarios.

- [ ] **Step 1: Fix `BaseExtension` API changes (if any)**

If `BaseExtension` APIs changed, update `Android.kt`. The current code:

```kotlin
import com.android.build.gradle.BaseExtension

fun Project.configureAndroid() {
  android {
    compileSdkVersion(Versions.COMPILE_SDK)
    defaultConfig {
      minSdk = Versions.MIN_SDK
      targetSdk = Versions.TARGET_SDK
    }
    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_11
      targetCompatibility = JavaVersion.VERSION_11
    }
  }
  // ...
}

private fun Project.android(action: BaseExtension.() -> Unit) =
    extensions.configure<BaseExtension>(action)
```

Likely migrations:
- `compileSdkVersion(X)` → `compileSdk = X`
- `BaseExtension` → `CommonExtension<*, *, *, *, *, *, *, *>` or keep using `BaseExtension` if it still exists

Apply the exact fix needed based on the compiler error.

- [ ] **Step 2: Fix `AndroidComponentsExtension` changes (if any)**

Current code in `Android.kt`:

```kotlin
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasUnitTestBuilder

androidComponents {
  beforeVariants(selector().withBuildType("release")) { variantBuilder ->
    (variantBuilder as? HasUnitTestBuilder)?.apply {
      enableUnitTest = false
    }
  }
}
```

If `HasUnitTestBuilder` moved or `enableUnitTest` renamed, apply the fix based on the compiler error.

- [ ] **Step 3: Fix build-logic dependency (if needed)**

If the AGP Maven coordinate changed (e.g., `com.android.tools.build:gradle` artifact renamed), update `gradle/build-logic/convention/build.gradle.kts`:

```kotlin
dependencies {
  compileOnly(libs.android.gradlePlugin)  // may need new coordinate
  // ...
}
```

And update `gradle/libs.versions.toml` library entry if needed:
```toml
android-gradlePlugin = { module = "com.android.tools.build:gradle", version.ref = "agp" }
```

- [ ] **Step 4: Verify build-logic compiles**

Run: `./gradlew -p gradle/build-logic assemble`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit build logic fixes**

```bash
git add gradle/build-logic/
git commit -m "Fix build-logic for AGP 9.2.1 API changes"
```

---

### Task 4: Fix plugin and property issues

**Files:**
- Modify: `gradle.properties` (if properties need updating)
- Modify: `gradle/libs.versions.toml` (if cache-fix plugin version needs bumping)
- Modify: `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/AndroidApplicationConventionPlugin.kt` (if cache-fix removed)
- Modify: `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/AndroidLibraryConventionPlugin.kt` (if cache-fix removed)
- Modify: `gradle/build-logic/convention/src/main/kotlin/dev/chrisbanes/gradle/AndroidTestConventionPlugin.kt` (if cache-fix removed)

- [ ] **Step 1: Check cache-fix plugin compatibility**

Run: `./gradlew assembleDebug 2>&1 | grep -i "cache-fix\|incompatible\|obsolete"`
Expected: Either no cache-fix errors (plugin works), or errors about incompatibility.

- [ ] **Step 2a: If cache-fix plugin is compatible — skip to Step 3**

No changes needed.

- [ ] **Step 2b: If cache-fix plugin is incompatible — try version bump**

Check latest version at https://plugins.gradle.org/plugin/org.gradle.android.cache-fix

Update `gradle/libs.versions.toml`:
```toml
cacheFixPlugin = { id = "org.gradle.android.cache-fix", version = "<latest>" }
```

Re-run build. If still incompatible, proceed to Step 2c.

- [ ] **Step 2c: If no compatible cache-fix version exists — remove plugin**

Remove from `gradle/libs.versions.toml`:
```toml
# Delete this line:
cacheFixPlugin = { id = "org.gradle.android.cache-fix", version = "3.0.3" }
```

Remove from `build.gradle.kts` (root):
```kotlin
# Delete this line:
alias(libs.plugins.cacheFixPlugin) apply false
```

Remove from each convention plugin that applies it:

`AndroidApplicationConventionPlugin.kt` — remove `apply("org.gradle.android.cache-fix")`
`AndroidLibraryConventionPlugin.kt` — remove `apply("org.gradle.android.cache-fix")`
`AndroidTestConventionPlugin.kt` — remove `apply("org.gradle.android.cache-fix")`

- [ ] **Step 3: Review and fix Gradle properties**

In `gradle.properties`, check these properties:

```properties
android.suppressUnsupportedCompileSdk=34
kotlin.mpp.androidSourceSetLayoutVersion=2
```

- If the build warns about `android.suppressUnsupportedCompileSdk` being obsolete, remove it
- If the build warns about `kotlin.mpp.androidSourceSetLayoutVersion=2` being default, remove it
- If `android.defaults.buildfeatures.*` properties cause errors, update or remove them

- [ ] **Step 4: Commit property/plugin fixes**

```bash
git add gradle.properties gradle/libs.versions.toml gradle/build-logic/
git commit -m "Fix Gradle properties and plugin compatibility for AGP 9.2.1"
```

---

### Task 5: Full project build verification

**Files:**
- None (verification step)

- [ ] **Step 1: Run full debug build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run unit tests**

Run: `./gradlew testDebug`
Expected: All tests pass

- [ ] **Step 3: Run lint**

Run: `./gradlew lintDebug`
Expected: No new errors (warnings acceptable)

- [ ] **Step 4: Run Spotless formatting**

Run: `./gradlew spotlessApply`
Expected: Formatting applied cleanly

- [ ] **Step 5: Commit any formatting changes**

```bash
git add -A
git commit -m "Apply Spotless formatting after AGP upgrade"
```

---

### Task 6: Screenshot test verification

**Files:**
- Possibly: `haze-screenshot-tests/src/test/screenshots/` (reference images)

- [ ] **Step 1: Run screenshot tests**

Run: `./gradlew :haze-screenshot-tests:test`
Expected: Either pass (no rendering changes) or fail (rendering differences from AGP upgrade).

- [ ] **Step 2a: If screenshot tests pass — skip to Step 3**

No changes needed.

- [ ] **Step 2b: If screenshot tests fail — review diffs and regenerate**

Check the diff images in `haze-screenshot-tests/build/outputs/roborazzi/`. If the differences are minor rendering changes (font hinting, anti-aliasing) caused by the AGP upgrade:

Run: `./gradlew :haze-screenshot-tests:recordRoborazzi`
Expected: New reference images recorded

- [ ] **Step 3: Commit updated screenshot references (if any)**

```bash
git add haze-screenshot-tests/src/test/screenshots/
git commit -m "Update screenshot references for AGP 9.2.1 rendering changes"
```

---

### Task 7: Full build and final verification

**Files:**
- None (final verification)

- [ ] **Step 1: Run full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify no deprecation warnings**

Run: `./gradlew build 2>&1 | grep -i "deprecat\|obsolete\|removed"`
Expected: No AGP-related deprecation warnings. Kotlin/other deprecation warnings are acceptable.

- [ ] **Step 3: Final commit (if any remaining changes)**

```bash
git add -A
git commit -m "Complete AGP 9.2.1 upgrade verification"
```

---

## Verification Summary

| Check | Command | Expected |
|-------|---------|----------|
| Build-logic compiles | `./gradlew -p gradle/build-logic assemble` | SUCCESS |
| Debug build | `./gradlew assembleDebug` | SUCCESS |
| Unit tests | `./gradlew testDebug` | All pass |
| Lint | `./gradlew lintDebug` | No new errors |
| Screenshot tests | `./gradlew :haze-screenshot-tests:test` | Pass or regenerate |
| Full build | `./gradlew build` | SUCCESS |
| Formatting | `./gradlew spotlessApply` | Clean |
