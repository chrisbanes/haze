# iOS CI Tests Design

## Context

The build workflow currently has a `mac_build` job on `macos-26`, but it only runs
`compileKotlinIosArm64`. The Linux build runs `check assembleDebug` with
`-Phaze.disableAppleTargets`, so iOS simulator tests are not covered by CI.

The `haze` module exposes `iosSimulatorArm64Test`, but its native simulator and host
test tasks are disabled by default because linking is expensive. Other modules with
common tests, notably `haze-blur` and `haze-liquidglass`, expose matching simulator test
tasks and should be included in the correctness gate.

## Goals

- Run iOS simulator tests on CI for the library modules that currently have common tests.
- Keep local `check` fast by preserving default native test disablement where it exists.
- Keep the CI command easy to run locally and easy to extend when more modules need iOS
  test coverage.
- Keep deploy gated by the iOS compile and simulator-test coverage already represented by
  `mac_build`.

## Non-Goals

- Do not run iOS device tests for `iosArm64`; CI should continue using `iosArm64` as a
  compile/publish-shape check only.
- Do not enable every internal, fixture, docs, or sample module's simulator tests
  automatically.
- Do not change the Linux build's `-Phaze.disableAppleTargets` behavior.

## Design

Add a root Gradle aggregate task named `iosSimulatorArm64Tests`. It will explicitly depend
on the intended simulator test tasks:

- `:haze:iosSimulatorArm64Test`
- `:haze-blur:iosSimulatorArm64Test`
- `:haze-liquidglass:iosSimulatorArm64Test`

The task list should be explicit rather than discovered automatically. This avoids
accidentally gating CI on fixture, internal, documentation, or sample modules that expose
Apple targets but are not library correctness gates.

Update `haze/build.gradle.kts` so native simulator and host tests remain disabled by
default, but can be enabled through a Gradle property such as `haze.enableAppleTests`.
This keeps the original local-cost behavior while giving CI an intentional opt-in path.

Update `.github/workflows/build.yml` so the existing `mac_build` job runs both the iOS
device-target compile check and the aggregate simulator test task:

```bash
./gradlew compileKotlinIosArm64 iosSimulatorArm64Tests -Phaze.enableAppleTests
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
./gradlew iosSimulatorArm64Tests -Phaze.enableAppleTests --dry-run
./gradlew compileKotlinIosArm64 iosSimulatorArm64Tests -Phaze.enableAppleTests --dry-run
```

If local macOS execution is available, run the non-dry-run aggregate task once:

```bash
./gradlew iosSimulatorArm64Tests -Phaze.enableAppleTests
```
