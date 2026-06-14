# iOS CI Tests Design

## Context

The build workflow currently has a `mac_build` job on `macos-26`, but it only runs
`compileKotlinIosArm64`. The Linux build runs `check assembleDebug` with
`-Phaze.disableAppleTargets`, so iOS simulator tests are not covered by CI.

The `haze` module exposes `iosSimulatorArm64Test`, but its native simulator and host
test tasks are disabled by default because linking is expensive. Gradle can run every
subproject task named `iosSimulatorArm64Test` when that task name is invoked from the
root project, which gives broad iOS simulator coverage without a custom aggregate task.

## Goals

- Run all iOS simulator tests exposed by the Gradle build on CI.
- Keep local `check` fast by preserving default native test disablement where it exists.
- Keep the CI command easy to run locally and automatically include new modules that add
  `iosSimulatorArm64Test`.
- Keep deploy gated by the iOS compile and simulator-test coverage already represented by
  `mac_build`.

## Non-Goals

- Do not run iOS device tests for `iosArm64`; CI should continue using `iosArm64` as a
  compile/publish-shape check only.
- Do not maintain a custom root aggregate for iOS simulator tests.
- Do not change the Linux build's `-Phaze.disableAppleTargets` behavior.

## Design

Use Gradle's built-in task-name matching by invoking `iosSimulatorArm64Test` from the
root project. This intentionally runs every subproject task with that name, including
library, internal, and sample modules that expose iOS simulator tests.

Update `haze/build.gradle.kts` so native simulator and host tests remain disabled by
default, but can be enabled through a Gradle property such as `haze.enableAppleTests`.
This keeps the original local-cost behavior while giving CI an intentional opt-in path.

Update `.github/workflows/build.yml` so the existing `mac_build` job runs both the iOS
device-target compile check and all iOS simulator test tasks:

```bash
./gradlew compileKotlinIosArm64 iosSimulatorArm64Test -Phaze.enableAppleTests
```

The existing deploy job can continue depending on `mac_build`, because `mac_build` will
now represent both iOS compile validation and iOS simulator test validation.

## Reports

Keep the existing `mac-reports` upload on failure and include test-result directories as
well as reports:

- `**/build/reports/**`
- `**/build/test-results/**`

This should make simulator test failures easier to inspect from GitHub Actions artifacts.

## Verification

Before merging, validate the Gradle task graph and the intended commands:

```bash
./gradlew iosSimulatorArm64Test -Phaze.enableAppleTests --dry-run
./gradlew compileKotlinIosArm64 iosSimulatorArm64Test -Phaze.enableAppleTests --dry-run
```

If local macOS execution is available, run the non-dry-run simulator task once:

```bash
./gradlew iosSimulatorArm64Test -Phaze.enableAppleTests
```
