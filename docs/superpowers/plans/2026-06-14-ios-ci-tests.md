# iOS CI Tests Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable iOS simulator unit tests on CI while preserving fast default local checks.

**Architecture:** Add a root Gradle aggregate task that explicitly names the library modules included in the iOS simulator correctness gate. Make `haze` native simulator and host tests opt-in through a Gradle property, then update the existing macOS CI job to run the iOS compile check and aggregate simulator tests together.

**Tech Stack:** Gradle Kotlin DSL, Kotlin Multiplatform, Kotlin/Native iOS simulator tests, GitHub Actions.

---

## File Structure

- Modify `build.gradle.kts`: defines the root aggregate task `iosSimulatorArm64Tests`.
- Modify `haze/build.gradle.kts`: makes native simulator and host tests opt-in through `haze.enableAppleTests`.
- Modify `.github/workflows/build.yml`: runs the aggregate task in `mac_build` and uploads test-result artifacts.

### Task 1: Add Root iOS Simulator Test Aggregate

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Verify the aggregate task does not exist yet**

Run:

```bash
./gradlew iosSimulatorArm64Tests --dry-run
```

Expected: FAIL with a Gradle message that task `iosSimulatorArm64Tests` was not found in root project `haze-root`.

- [ ] **Step 2: Register the aggregate task**

In `build.gradle.kts`, add this block after the `plugins` block:

```kotlin
tasks.register("iosSimulatorArm64Tests") {
  description = "Runs iOS simulator tests for library modules."
  group = org.gradle.language.base.plugins.LifecycleBasePlugin.VERIFICATION_GROUP

  dependsOn(
    ":haze:iosSimulatorArm64Test",
    ":haze-blur:iosSimulatorArm64Test",
    ":haze-liquidglass:iosSimulatorArm64Test",
  )
}
```

Use the fully qualified `LifecycleBasePlugin` name so no import is needed.

- [ ] **Step 3: Verify the aggregate task resolves**

Run:

```bash
./gradlew iosSimulatorArm64Tests --dry-run
```

Expected: PASS. Output includes these task paths:

```text
:haze:iosSimulatorArm64Test SKIPPED
:haze-blur:iosSimulatorArm64Test SKIPPED
:haze-liquidglass:iosSimulatorArm64Test SKIPPED
:iosSimulatorArm64Tests SKIPPED
```

- [ ] **Step 4: Commit the aggregate task**

Run:

```bash
git add build.gradle.kts
git commit -m "Add iOS simulator test aggregate"
```

### Task 2: Make Haze Native Tests Opt-In

**Files:**
- Modify: `haze/build.gradle.kts`

- [ ] **Step 1: Capture current disabled-test behavior**

Run:

```bash
./gradlew :haze:iosSimulatorArm64Test --dry-run
```

Expected: PASS. Output includes `:haze:iosSimulatorArm64Test SKIPPED`, confirming the task exists but is not executed in dry-run mode.

- [ ] **Step 2: Replace unconditional native test disablement**

In `haze/build.gradle.kts`, replace the existing native-test disable block:

```kotlin
/**
 * Disable Mac host and iOS sim tests. They have a high CI cost (mostly linking) but
 * provide little value over the quicker JVM + Android tests
 */
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest> {
  enabled = false
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest> {
  enabled = false
}
```

with:

```kotlin
val enableAppleTests = providers.gradleProperty("haze.enableAppleTests").isPresent

/**
 * Disable Mac host and iOS sim tests by default. They have a high CI cost (mostly
 * linking), but CI can opt in to iOS simulator coverage with -Phaze.enableAppleTests.
 */
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest> {
  enabled = enableAppleTests
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest> {
  enabled = enableAppleTests
}
```

- [ ] **Step 3: Verify default task graph still resolves**

Run:

```bash
./gradlew :haze:iosSimulatorArm64Test --dry-run
```

Expected: PASS. Output includes:

```text
:haze:iosSimulatorArm64Test SKIPPED
```

- [ ] **Step 4: Verify opt-in task graph resolves**

Run:

```bash
./gradlew :haze:iosSimulatorArm64Test -Phaze.enableAppleTests --dry-run
```

Expected: PASS. Output includes:

```text
:haze:iosSimulatorArm64Test SKIPPED
```

- [ ] **Step 5: Commit the opt-in property**

Run:

```bash
git add haze/build.gradle.kts
git commit -m "Make Haze native tests opt-in"
```

### Task 3: Update macOS CI to Run iOS Tests

**Files:**
- Modify: `.github/workflows/build.yml`

- [ ] **Step 1: Verify current CI command references compile only**

Run:

```bash
rg -n "Compile iOS arm64 native target|compileKotlinIosArm64|build/test-results" .github/workflows/build.yml
```

Expected: output includes `Compile iOS arm64 native target` and `run: ./gradlew compileKotlinIosArm64`; output does not include `build/test-results`.

- [ ] **Step 2: Update the macOS Gradle step**

In `.github/workflows/build.yml`, replace:

```yaml
      - name: Compile iOS arm64 native target
        run: ./gradlew compileKotlinIosArm64
```

with:

```yaml
      - name: Compile iOS arm64 target and run simulator tests
        run: ./gradlew compileKotlinIosArm64 iosSimulatorArm64Tests -Phaze.enableAppleTests
```

- [ ] **Step 3: Upload test results on macOS failures**

In `.github/workflows/build.yml`, replace:

```yaml
          path: |
            **/build/reports/**
```

with:

```yaml
          path: |
            **/build/reports/**
            **/build/test-results/**
```

- [ ] **Step 4: Verify the workflow text**

Run:

```bash
rg -n "Compile iOS arm64 target and run simulator tests|iosSimulatorArm64Tests|-Phaze.enableAppleTests|build/test-results" .github/workflows/build.yml
```

Expected: output includes all four searched terms.

- [ ] **Step 5: Commit the CI update**

Run:

```bash
git add .github/workflows/build.yml
git commit -m "Run iOS simulator tests on CI"
```

### Task 4: Final Verification

**Files:**
- No file edits.

- [ ] **Step 1: Verify the aggregate command intended for local reproduction**

Run:

```bash
./gradlew iosSimulatorArm64Tests -Phaze.enableAppleTests --dry-run
```

Expected: PASS. Output includes:

```text
:haze:iosSimulatorArm64Test SKIPPED
:haze-blur:iosSimulatorArm64Test SKIPPED
:haze-liquidglass:iosSimulatorArm64Test SKIPPED
```

- [ ] **Step 2: Verify the full CI macOS command graph**

Run:

```bash
./gradlew compileKotlinIosArm64 iosSimulatorArm64Tests -Phaze.enableAppleTests --dry-run
```

Expected: PASS. Output includes:

```text
:haze:compileKotlinIosArm64 SKIPPED
:haze-blur:compileKotlinIosArm64 SKIPPED
:haze-liquidglass:compileKotlinIosArm64 SKIPPED
:haze:iosSimulatorArm64Test SKIPPED
:haze-blur:iosSimulatorArm64Test SKIPPED
:haze-liquidglass:iosSimulatorArm64Test SKIPPED
```

- [ ] **Step 3: Run the real iOS simulator aggregate on macOS**

Run:

```bash
./gradlew iosSimulatorArm64Tests -Phaze.enableAppleTests
```

Expected: PASS. The command links and executes iOS simulator tests for `haze`, `haze-blur`, and `haze-liquidglass`.

- [ ] **Step 4: Run formatting/checks for touched files**

Run:

```bash
./gradlew spotlessKotlinGradleCheck
```

Expected: PASS.

- [ ] **Step 5: Inspect final diff**

Run:

```bash
git status --short
git log --oneline -4
```

Expected: working tree is clean. The latest commits are:

```text
Run iOS simulator tests on CI
Make Haze native tests opt-in
Add iOS simulator test aggregate
Document iOS CI test design
```
