# Glass Gallery Screenshot Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Glass Gallery screenshot suite and its 20 baselines from `:haze-screenshot-tests` into a dedicated `:sample:screenshot-tests` module without changing rendered pixels.

**Architecture:** `:sample:screenshot-tests` is an Android-host and JVM/Desktop-only Kotlin Multiplatform test module. It depends on `:sample:shared` for the UI under test and `:internal:screenshot-test` for the reusable Roborazzi harness; `:sample:shared` remains free of screenshot tooling and `:haze-screenshot-tests` returns to library-only visual contracts.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Multiplatform, Android host tests, Desktop JVM tests, Robolectric, Roborazzi, Gradle.

## Global Constraints

- Register the module as `:sample:screenshot-tests` at `sample/screenshot-tests`.
- Target Android host tests and JVM/Desktop only; do not add Apple, JS, or Wasm targets.
- Depend on `:sample:shared` for Gallery UI and `:internal:screenshot-test` for the screenshot harness.
- Do not add Roborazzi or screenshot-test dependencies to `:sample:shared`.
- Keep `:haze-screenshot-tests` focused on library-level visual contracts and remove its dependency on `:sample:shared`.
- Preserve the exact bytes and filenames of all 20 existing Glass Gallery WebP baselines during the move.
- Keep the existing `dev.chrisbanes.haze` test package and class names so baseline filenames remain stable.
- Run all repository shell commands through `rtk`.

---

### Task 1: Create the sample screenshot module and move the Gallery suite

**Files:**
- Modify: `settings.gradle.kts`
- Create: `sample/screenshot-tests/build.gradle.kts`
- Move: `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt` to `sample/screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt`
- Move: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt` to `sample/screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt`
- Move: `haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt` to `sample/screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt`
- Move: `haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt` to `sample/screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt`
- Move: the 10 `GlassGallery*.webp` files from `haze-screenshot-tests/screenshots/android/` to `sample/screenshot-tests/screenshots/android/`
- Move: the 10 `GlassGallery*.webp` files from `haze-screenshot-tests/screenshots/desktop/` to `sample/screenshot-tests/screenshots/desktop/`

**Interfaces:**
- Consumes: `GlassProductSampleContent`, `GlassPlaygroundSampleContent`, `GlassLabScreenshotContent`, and `SamplesTheme` from `:sample:shared`; `ScreenshotTest`, `ScreenshotUiTest`, `runScreenshotTest`, and `captureRoot` from `:internal:screenshot-test`.
- Produces: `:sample:screenshot-tests:jvmTest`, `:sample:screenshot-tests:testAndroidHostTest`, `:sample:screenshot-tests:recordRoborazziJvm`, `:sample:screenshot-tests:recordRoborazziAndroidHostTest`, and aggregate `:sample:screenshot-tests:test` tasks using baselines owned by the sample tree.

- [ ] **Step 1: Register the module**

Add this entry beside the existing sample modules in `settings.gradle.kts`:

```kotlin
":sample:screenshot-tests",
```

- [ ] **Step 2: Create the Android/JVM-only screenshot build**

Create `sample/screenshot-tests/build.gradle.kts` with:

```kotlin
// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.sample.screenshots"
    androidResources.enable = true

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  jvm()

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
  }

  sourceSets {
    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.compose.ui.test)
        implementation(projects.internal.contextTest)
        implementation(projects.internal.screenshotTest)
        implementation(projects.sample.shared)
      }
    }

    jvmTest {
      kotlin.srcDir("src/jvmTest/kotlin")

      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

roborazzi {
  outputDir.set(project.layout.projectDirectory.dir("screenshots"))

  @OptIn(ExperimentalRoborazziApi::class)
  separateOutputDirs.set(true)
}

tasks.withType<Test> {
  failOnNoDiscoveredTests.set(false)
  systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"

  if (name == "testAndroidHostTest") {
    maxHeapSize = "2g"
  }

  addTestOutputListener(
    object : TestOutputListener {
      override fun onOutput(
        testDescriptor: TestDescriptor,
        outputEvent: TestOutputEvent,
      ) {
        outputEvent.message.lineSequence()
          .filter { it.contains("Roborazzi image diff:") }
          .forEach { line ->
            logger.lifecycle("${testDescriptor.className} > ${testDescriptor.name}: ${line.trim()}")
          }
      }
    },
  )
}

tasks.register("test") {
  dependsOn("jvmTest", "testAndroidHostTest")
}

tasks.configureEach {
  if (name == "testAndroidHostTest") {
    mustRunAfter("jvmTest")
  }
}
```

- [ ] **Step 3: Verify the new module exposes the expected screenshot tasks**

Run:

```bash
rtk ./gradlew :sample:screenshot-tests:tasks --all
```

Expected: PASS, with `jvmTest`, `testAndroidHostTest`, `recordRoborazziJvm`,
`recordRoborazziAndroidHostTest`, and aggregate `test` tasks listed before any source or baseline moves.

- [ ] **Step 4: Move the test sources without changing their packages or contents**

Run:

```bash
rtk mkdir -p sample/screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze
rtk mkdir -p sample/screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze
rtk mkdir -p sample/screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze
rtk git mv haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt sample/screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/GlassGalleryScreenshotAssertions.kt
rtk git mv haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt sample/screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryPortraitAndroidScreenshotTest.kt
rtk git mv haze-screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt sample/screenshot-tests/src/androidHostTest/kotlin/dev/chrisbanes/haze/GlassGalleryLandscapeAndroidScreenshotTest.kt
rtk git mv haze-screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt sample/screenshot-tests/src/jvmTest/kotlin/dev/chrisbanes/haze/GlassGalleryDesktopScreenshotTest.kt
```

- [ ] **Step 5: Move the baseline files as byte-identical renames**

Run:

```bash
rtk mkdir -p sample/screenshot-tests/screenshots/android
rtk mkdir -p sample/screenshot-tests/screenshots/desktop
rtk git mv haze-screenshot-tests/screenshots/android/GlassGallery*.webp sample/screenshot-tests/screenshots/android/
rtk git mv haze-screenshot-tests/screenshots/desktop/GlassGallery*.webp sample/screenshot-tests/screenshots/desktop/
rtk git diff --summary --find-renames=100%
```

Expected: all 20 WebP files are reported as 100% renames. Do not record new baselines to make this step pass.

- [ ] **Step 6: Run the moved suite on both rendering paths**

Run:

```bash
rtk ./gradlew :sample:screenshot-tests:jvmTest :sample:screenshot-tests:testAndroidHostTest
```

Expected: PASS with the existing 20 Desktop and Android baselines found under `sample/screenshot-tests/screenshots/`.

- [ ] **Step 7: Apply formatting and commit the independently working module**

Run:

```bash
rtk ./gradlew :sample:screenshot-tests:spotlessApply :sample:screenshot-tests:spotlessCheck
rtk git diff --check
rtk git add settings.gradle.kts sample/screenshot-tests haze-screenshot-tests/src haze-screenshot-tests/screenshots
rtk git commit -m "Move Glass Gallery screenshots into sample"
```

Expected: the commit contains the new module configuration plus source and baseline renames; `:haze-screenshot-tests` still has its temporary `projects.sample.shared` dependency until Task 2.

### Task 2: Remove the old ownership and verify repository integration

**Files:**
- Modify: `haze-screenshot-tests/build.gradle.kts`
- Modify: `docs/superpowers/plans/2026-07-17-glass-gallery-sample-suite.md`

**Interfaces:**
- Consumes: the working `:sample:screenshot-tests` module and tasks from Task 1.
- Produces: a library-only `:haze-screenshot-tests` dependency graph, documentation that points to the final module boundary, and a fully verified branch ready to update draft PR #1041.

- [ ] **Step 1: Remove the reverse dependency from the library screenshot module**

Delete this line from `haze-screenshot-tests/build.gradle.kts`:

```kotlin
implementation(projects.sample.shared)
```

- [ ] **Step 2: Document that the focused migration supersedes the original Task 8 paths**

Immediately below `### Task 8: Add Android and Desktop Showcase Screenshots` in `docs/superpowers/plans/2026-07-17-glass-gallery-sample-suite.md`, add:

```markdown
> **Final ownership:** The implementation was subsequently moved from `:haze-screenshot-tests` to
> the dedicated `:sample:screenshot-tests` module. See
> `docs/superpowers/plans/2026-07-18-glass-gallery-screenshot-ownership.md` for the final paths,
> dependency boundary, and verification commands.
```

- [ ] **Step 3: Verify `:haze-screenshot-tests` no longer consumes sample code**

Run:

```bash
rtk ./gradlew :haze-screenshot-tests:compileTestKotlinJvm
rtk ./gradlew :haze-screenshot-tests:dependencies --configuration jvmTestCompileClasspath
```

Expected: compilation passes, and the dependency report does not contain `project :sample:shared`.

- [ ] **Step 4: Verify the moved suite, sample code, and representative library screenshots**

Run:

```bash
rtk ./gradlew spotlessCheck :sample:shared:jvmTest :sample:android:assembleDebug
rtk ./gradlew :sample:screenshot-tests:jvmTest :sample:screenshot-tests:testAndroidHostTest
rtk ./gradlew :haze-screenshot-tests:jvmTest --tests dev.chrisbanes.haze.GlassScreenshotTest
rtk ./gradlew :haze-screenshot-tests:testAndroidHostTest --tests dev.chrisbanes.haze.GlassScreenshotTest
```

Expected: every command passes. The sample screenshot tasks compare all 20 Gallery baselines from their new location; the targeted library commands confirm the remaining Glass renderer contracts still run in `:haze-screenshot-tests`.

- [ ] **Step 5: Confirm the final diff and commit the cleanup**

Run:

```bash
rtk git diff --check
rtk git status --short
rtk git add haze-screenshot-tests/build.gradle.kts docs/superpowers/plans/2026-07-17-glass-gallery-sample-suite.md
rtk git commit -m "Finish Glass Gallery screenshot ownership move"
```

Expected: the worktree is clean after the commit, all Gallery screenshot sources and baselines live only under `sample/screenshot-tests`, and `haze-screenshot-tests/build.gradle.kts` has no `projects.sample.shared` dependency.

- [ ] **Step 6: Push and update the draft PR validation commands**

Push `cb/glass-gallery-samples`, then update PR #1041 so its validation section names:

```text
./gradlew spotlessCheck :sample:shared:jvmTest :sample:android:assembleDebug
./gradlew :sample:screenshot-tests:jvmTest :sample:screenshot-tests:testAndroidHostTest
./gradlew :haze-screenshot-tests:jvmTest --tests dev.chrisbanes.haze.GlassScreenshotTest
./gradlew :haze-screenshot-tests:testAndroidHostTest --tests dev.chrisbanes.haze.GlassScreenshotTest
```

Expected: PR #1041 remains a draft, targets `main`, and reports `cb/glass-gallery-samples` at the final local HEAD.
