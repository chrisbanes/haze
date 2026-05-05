# Compose v2 Test API Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate Compose test code from v1 (`androidx.compose.ui.test.junit4`) to v2 (CMP `compose.uiTest` accessor) across the screenshot test infrastructure and two instrumented test files.

**Architecture:** Replace `createAndroidComposeRule`/`createComposeRule` from `.junit4` with CMP v2 equivalents. Replace `RoborazziRule` with programmatic capture. Remove `@RunWith(AndroidJUnit4::class)` from instrumented tests. The `ScreenshotUiTest` interface and 55+ screenshot test methods are unchanged.

**Tech Stack:** Kotlin Multiplatform, JetBrains Compose 1.11.0-beta03, Roborazzi 1.60.0, Robolectric 4.16.1, assertk, kotlin.test

---

### Task 1: Migrate `ScreenshotTest.android.kt` — remove v1 rules, make `runScreenshotTest` self-contained

**Files:**
- Modify: `internal/screenshot-test/src/androidMain/kotlin/dev/chrisbanes/haze/test/ScreenshotTest.android.kt`
- Modify: `internal/screenshot-test/src/androidMain/kotlin/dev/chrisbanes/haze/test/HazeRoborazziDefaults.kt` (no changes needed, reference only)

- [ ] **Step 1: Read current file to understand full context**

Read `ScreenshotTest.android.kt` to confirm we have the complete file.

- [ ] **Step 2: Replace the entire file**

Replace the contents of `ScreenshotTest.android.kt` with:

```kotlin
// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import android.content.ContentProvider
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.AndroidComposeUiTestEnvironment
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.provideRoborazziContext
import com.github.takahirom.roborazzi.roboOutputName
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 32, 35], qualifiers = RobolectricDeviceQualifiers.Pixel5)
actual abstract class ScreenshotTest : ContextTest()

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalRoborazziApi::class,
  InternalRoborazziApi::class,
)
actual fun ScreenshotTest.runScreenshotTest(block: ScreenshotUiTest.() -> Unit) {
  @Suppress("UNCHECKED_CAST")
  val clazz =
    Class.forName("org.jetbrains.compose.resources.AndroidContextProvider") as Class<ContentProvider>
  Robolectric.setupContentProvider(clazz)

  val scenario = ActivityScenario.launch(ComponentActivity::class.java)
  val environment = AndroidComposeUiTestEnvironment<ComponentActivity> { scenario }
  try {
    environment.runTest {
      provideRoborazziContext().apply {
        setRuleOverrideRoborazziOptions(HazeRoborazziDefaults.roborazziOptions)
        setRuleOverrideOutputDirectory("screenshots/android")
      }
      createScreenshotUiTest().block()
    }
  } finally {
    // Close the scenario outside runTest to avoid getting stuck.
    scenario.close()
  }
}

@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
private fun <A : ComponentActivity> AndroidComposeUiTest<A>.createScreenshotUiTest() =
  object : ScreenshotUiTest {
    override fun setContent(content: @Composable () -> Unit) {
      this@createScreenshotUiTest.setContent(content)
    }

    override fun captureRoot(nameSuffix: String?) {
      val output = when {
        nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.png"
        else -> "${roboOutputName()}_$nameSuffix.png"
      }
      this@createScreenshotUiTest.onRoot().captureRoboImage(output)
    }

    override fun waitForIdle() {
      this@createScreenshotUiTest.waitForIdle()
    }
  }
```

- [ ] **Step 3: Verify `provideRoborazziContext` is available on Android classpath**

Check by examining the Roborazzi Android JAR:

Run:
```bash
jar tf $(find ~/.gradle/caches -path "*roborazzi-android*" -name "*.jar" 2>/dev/null | head -1) | grep -i provide
```

If `provideRoborazziContext` is NOT found, adjust the code: remove the `provideRoborazziContext()` block and use `captureRoboImage` with the full splash path instead. In that case, replace the capture in `captureRoot` with:

```kotlin
override fun captureRoot(nameSuffix: String?) {
  val name = when {
    nameSuffix.isNullOrEmpty() -> "${roboOutputName()}.png"
    else -> "${roboOutputName()}_$nameSuffix.png"
  }
  this@createScreenshotUiTest.onRoot().captureRoboImage("screenshots/android/$name")
}
```

And remove the `provideRoborazziContext` and related imports.

- [ ] **Step 4: Run screenshot tests to verify**

Run:
```bash
./gradlew :haze-screenshot-tests:test
```

Expected: All screenshot tests pass (or at minimum, compile and run without API errors; screenshots may differ and need regeneration).

- [ ] **Step 5: Commit**

```bash
git add internal/screenshot-test/src/androidMain/kotlin/dev/chrisbanes/haze/test/ScreenshotTest.android.kt
git commit -m "Migrate ScreenshotTest.android to Compose v2 test APIs"
```

---

### Task 2: Update `internal/screenshot-test/build.gradle.kts` — remove v1 dependencies

**Files:**
- Modify: `internal/screenshot-test/build.gradle.kts`

- [ ] **Step 1: Remove `compose.desktop.uiTestJUnit4` and `libs.roborazzi.junit` from androidMain dependencies**

Current (lines ~39-44 of the `androidMain` block):
```kotlin
androidMain {
  dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.compose.ui.test.manifest)
    implementation(compose.desktop.uiTestJUnit4)
    implementation(libs.robolectric)
    implementation(libs.roborazzi.core)
    implementation(libs.roborazzi.android)
    implementation(libs.roborazzi.compose)
    implementation(libs.roborazzi.junit)
  }
}
```

Replace with:
```kotlin
androidMain {
  dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.robolectric)
    implementation(libs.roborazzi.core)
    implementation(libs.roborazzi.android)
    implementation(libs.roborazzi.compose)
  }
}
```

- [ ] **Step 2: Verify build compiles**

Run:
```bash
./gradlew :internal:screenshot-test:compileKotlinAndroid :internal:screenshot-test:compileKotlinJvm
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add internal/screenshot-test/build.gradle.kts
git commit -m "Remove v1 JUnit4 and RoborazziRule deps from screenshot-test"
```

---

### Task 3: Migrate `RecompositionInstrumentationTestFixtures.kt` — update v1 type to v2

**Files:**
- Modify: `haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/RecompositionInstrumentationTestFixtures.kt`

- [ ] **Step 1: Replace `ComposeContentTestRule` import and receiver type**

Replace the import:
```kotlin
import androidx.compose.ui.test.junit4.ComposeContentTestRule
```

With:
```kotlin
import androidx.compose.ui.test.AndroidComposeUiTest
```

Replace the function signature:
```kotlin
internal fun ComposeContentTestRule.awaitIdleWithTimeout(description: String) {
```

With:
```kotlin
internal fun AndroidComposeUiTest<*>.awaitIdleWithTimeout(description: String) {
```

- [ ] **Step 2: Verify compile (this file only — full test compile comes later)**

Run:
```bash
./gradlew :haze-blur:compileDebugAndroidTestKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/RecompositionInstrumentationTestFixtures.kt
git commit -m "Migrate instrumentation test fixtures to Compose v2 test APIs"
```

---

### Task 4: Migrate instrumented test — recomposition loop

**Files:**
- Modify: `haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionLoopInstrumentationTest.kt`

- [ ] **Step 1: Replace imports, class declaration, and rule**

Replace the entire file's header section (imports + class declaration through the `@Rule` line).

**Old:**
```kotlin
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
// ... other imports ...
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlurVisualEffectRecompositionLoopInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun ComposeContentTestRule.setBlurEffectContent(
```

**New:**
```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.createAndroidComposeRule
// ... keep other imports ...
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BlurVisualEffectRecompositionLoopInstrumentationTest {

  val composeTestRule = createAndroidComposeRule()

  private fun AndroidComposeUiTest<*>.setBlurEffectContent(
```

- [ ] **Step 2: Remove `@RunWith` and `@get:Rule` — these are no longer needed**

Executed as part of Step 1 above. Verify by reading the file.

- [ ] **Step 3: Verify compile**

Run:
```bash
./gradlew :haze-blur:compileDebugAndroidTestKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionLoopInstrumentationTest.kt
git commit -m "Migrate recomposition loop instrumented test to Compose v2 test APIs"
```

---

### Task 5: Migrate instrumented test — recomposition count

**Files:**
- Modify: `haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionCountInstrumentationTest.kt`

- [ ] **Step 1: Apply the same changes as Task 4**

Replace imports and class declaration — identical pattern to Task 4.

**Old:**
```kotlin
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
// ... other imports ...
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlurVisualEffectRecompositionCountInstrumentationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  companion object {
    private const val RECOMPOSITION_THRESHOLD = 1
  }

  // ... tests ...

  private fun ComposeContentTestRule.setBlurEffectContent(
```

**New:**
```kotlin
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.createAndroidComposeRule
// ... keep other imports ...
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BlurVisualEffectRecompositionCountInstrumentationTest {

  val composeTestRule = createAndroidComposeRule()

  companion object {
    private const val RECOMPOSITION_THRESHOLD = 1
  }

  // ... tests ...

  private fun AndroidComposeUiTest<*>.setBlurEffectContent(
```

- [ ] **Step 2: Verify compile**

Run:
```bash
./gradlew :haze-blur:compileDebugAndroidTestKotlinAndroid
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add haze-blur/src/androidInstrumentedTest/kotlin/dev/chrisbanes/haze/blur/BlurVisualEffectRecompositionCountInstrumentationTest.kt
git commit -m "Migrate recomposition count instrumented test to Compose v2 test APIs"
```

---

### Task 6: Update `haze-blur/build.gradle.kts` — remove v1 dependencies

**Files:**
- Modify: `haze-blur/build.gradle.kts`

- [ ] **Step 1: Remove `androidx.compose.ui.test.junit4` and `androidx.test.ext.junit` from androidTest dependencies**

Current (`dependencies` block at bottom of file, lines 115-121):
```kotlin
dependencies {
  androidTestImplementation(libs.assertk)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(projects.internal.testUtils)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Replace with:
```kotlin
dependencies {
  androidTestImplementation(libs.assertk)
  androidTestImplementation(projects.internal.testUtils)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Note: `libs.assertk` stays (used in count tests for `isLessThanOrEqualTo`). `libs.androidx.compose.ui.test.manifest` stays (debug manifest for Compose test activity).

`compose.uiTest` is already present in `commonTest` (line 95), so no addition needed — the instrumented tests compile against `commonTest` source set.

- [ ] **Step 2: Verify full project build**

Run:
```bash
./gradlew build
```

Expected: All modules compile. Some tasks may be skipped (JS tests disabled).

- [ ] **Step 3: Commit**

```bash
git add haze-blur/build.gradle.kts
git commit -m "Remove v1 Compose test dependencies from haze-blur"
```

---

### Task 7: Regenerate screenshots and run full verification

**Files:**
- (No code changes — verification only)

- [ ] **Step 1: Run screenshot tests**

```bash
./gradlew :haze-screenshot-tests:test
```

Expected: All tests pass. Screenshots are captured to `screenshots/android/`.

- [ ] **Step 2: Regenerate screenshot baselines**

```bash
./gradlew :haze-screenshot-tests:recordRoborazzi
```

Expected: Screenshots written to `haze-screenshot-tests/screenshots/android/`.

- [ ] **Step 3: Run full build**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL across all modules.

- [ ] **Step 4: Run Spotless**

```bash
./gradlew spotlessApply
```

- [ ] **Step 5: Commit any remaining changes**

```bash
git add -A
git commit -m "Regenerate screenshots after Compose v2 test migration"
```
