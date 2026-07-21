# Navigation3 Haze Sibling Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or
> superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prove and document a safe Navigation3 recipe in which persistent adaptive navigation
chrome samples the complete animated `NavDisplay` composite.

**Architecture:** A full-size `NavDisplay` owns one `hazeSource`. A sibling
`NavigationSuiteScaffoldLayout` is used as a navigation-only overlay with an empty content slot. Its
effect-backed navigation component is outside the source subtree, while Material 3 Adaptive still
owns bar or rail positioning.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3 Adaptive Navigation Suite,
Navigation3, Haze blur, Compose UI pixel capture, AssertK, Gradle.

## Global constraints

- Do not add a public Haze API or restore the removed `haze-navigation3` module.
- Do not weaken Haze's recursive ancestor-source protection.
- Keep the `NavDisplay` and effect-backed navigation component as siblings.
- Keep `NavDisplay` edge-to-edge; the adaptive scaffold layout's content slot must remain empty.
- Match the adaptive navigation suite test dependency to the repository's Material 3 version.
- Do not stage or commit implementation changes during task execution. The design-only commit
  `41ad4e5` already exists; commit and integration remain gated by `implement-issue` and
  `finishing-a-development-branch`.

## File structure

- Modify `gradle/libs.versions.toml`: add the Material 3 Adaptive Navigation Suite alias using the
  existing `compose-material3` version.
- Modify `haze-screenshot-tests/build.gradle.kts`: add the alias to `commonTest`.
- Modify `haze-screenshot-tests/src/commonTest/kotlin/dev/chrisbanes/haze/NavDisplaySourceScreenshotTest.kt`:
  replace the ignored scene-decorator comparison with the active sibling-layout regression.
- Modify `docs/recipes.md`: document the supported Navigation3 and adaptive-navigation layout.
- Modify `CHANGELOG.md`: add an Unreleased / Added entry for the issue #999 recipe.

## Task 1: Prove the adaptive sibling layout

- [x] Add `compose-material3-adaptive-navigation-suite` to the version catalog using module
  `org.jetbrains.compose.material3:material3-adaptive-navigation-suite` and
  `version.ref = "compose-material3"`.
- [x] Add `implementation(libs.compose.material3.adaptive.navigation.suite)` to the screenshot
  module's `commonTest` dependencies.
- [x] Replace the current failed overlay experiment with one shared source and two effect-backed
  sibling bars over a horizontally repeated scene pattern.
- [x] Position the control directly and the subject through a sibling
  `NavigationSuiteScaffoldLayout` using `NavigationSuiteType.NavigationBar` and `content = {}`.
- [x] On JVM, pause the transition at 500 ms and compare both bar interiors from the same capture
  with a `0.02` pixel tolerance. The observed fixed-layout distance is about `0.0135`, while the
  original broken architecture measured about `0.077`.
- [x] On Android host, reuse the shared fixture and assertion through the module's native-graphics
  Roborazzi harness with a `0.01` tolerance. Keep the transition midpoint assertion on JVM because
  direct Android node capture cannot redraw against a frozen clock.
- [x] Run the focused JVM test:

```bash
./gradlew :haze-screenshot-tests:jvmTest \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceJvmScreenshotTest.blur_navigationSuiteSibling_midTransition_matchesDirectSibling'
```

- [x] Run the shared layout smoke test on Android host:

```bash
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests 'dev.chrisbanes.haze.NavDisplaySourceAndroidScreenshotTest.blur_navigationSuiteSibling_matchesDirectSibling'
```

- [x] Run `git diff --check` and review the Task 1 diff without staging or committing.

## Task 2: Document the supported recipe

- [x] Add a `Navigation3 with adaptive navigation` section between Scaffold and Sticky Headers in
  `docs/recipes.md`.
- [x] Explain why per-scene sources miss the outer `AnimatedContent` transition and why a descendant
  effect cannot sample an active ancestor source.
- [x] Show the full-size `NavDisplay` source and sibling `NavigationSuiteScaffoldLayout` overlay.
- [x] Keep the scaffold layout content empty and make the Material navigation container transparent.
- [x] Explain that interactive content may need padding while decorative scene content remains
  edge-to-edge behind the bar or rail.
- [x] Add an Unreleased / Added changelog entry referencing issue #999.
- [x] Run `git diff --check` and review the documentation diff without staging or committing.

## Task 3: Verify the branch

- [x] Run formatting and the complete focused screenshot-test module:

```bash
./gradlew :haze-screenshot-tests:spotlessApply
./gradlew :haze-screenshot-tests:test :haze-screenshot-tests:spotlessCheck
```

- [x] Run `git diff --check`, `git status --short`, and inspect the complete branch diff.
- [x] Dispatch the required read-only code review. Address only high-confidence, in-scope findings.
- [x] Re-run affected verification after any fix.
- [x] Use `superpowers:verification-before-completion` before reporting success.
- [ ] Present `superpowers:finishing-a-development-branch` integration choices. Do not commit, push,
  open a pull request, or mutate issue #999 without explicit authorization.
