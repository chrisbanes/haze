# Managed iOS simulator tests

The shared KMP convention applies [Simsteward 0.0.1](https://github.com/chrisbanes/simsteward/releases/tag/0.0.1)
from Maven Central. Managed mode is opt-in with `-Psimsteward.enabled=true`; stock local runs and CI
retain their existing behaviour. Haze defaults to slim simulators and one simulator per pool.

## Run tests

1. Use Java 21, Xcode and an installed SimSlim CLI. Inspect installed device types and runtimes
   with `xcrun simctl list devicetypes` and `xcrun simctl list runtimes`. The plugin uses installed
   runtimes; it does not download them or install SimSlim.
2. Run the relevant test tasks through the Gradle-run skill. For example, with these identifiers
   installed:

   ```sh
   ./gradlew :haze:iosSimulatorArm64Test :haze-blur:iosSimulatorArm64Test \
     :haze-glass:iosSimulatorArm64Test \
     -Psimsteward.enabled=true \
     -Psimsteward.ios.deviceType=com.apple.CoreSimulator.SimDeviceType.iPhone-17 \
     -Psimsteward.ios.runtime=com.apple.CoreSimulator.SimRuntime.iOS-27-0 \
     --no-scan
   ```

   Managed mode also enables core `haze` simulator tests, which are otherwise gated by
   `-Phaze.enableAppleTests`. It does not enable macOS host tests. Existing test filters still apply.
   Omit the device and runtime pins to let Simsteward select a compatible installed device and the
   newest installed runtime. Pin identifiers for repeatable comparisons.
3. Confirm the requested test reports contain executed tests. Compilation or an up-to-date task is
   not evidence of a fresh test execution. Use `--rerun-tasks` when a fresh run is required.

Simsteward leases a build-owned simulator to each executing test task and returns the lease when
its tests finish or fail. Modules with the same simulator spec share a pool; Haze's cap of one
serializes their simulator use. Separate specs have separate pools. Separate builds own their own
devices, though simultaneous runs of the same task in one checkout can collide on its task name.
All owned devices are deleted at build end. Up-to-date tests create no simulator.

## Configuration and migration

Use Simsteward's native Gradle properties; the former local convention's properties are removed:

| Former property | Simsteward property |
| --- | --- |
| `haze.iosSimulator.managed` | `simsteward.enabled` |
| `haze.iosSimulator.deviceType` | `simsteward.ios.deviceType` |
| `haze.iosSimulator.runtime` | `simsteward.ios.runtime` |
| `haze.iosSimulator.profile` | `simsteward.ios.profile` |
| `haze.iosSimulator.executable` | `simsteward.simslim` |
| `haze.iosSimulator.xcrun` | `simsteward.xcrun` |

A relative profile path resolves against the repository root. Without a profile, SimSlim's default
applies. Keep services required by the tests enabled; `verify` checks service overrides rather than
application correctness. Failed slimming fails the test task instead of falling back to stock.
Use `-Psimsteward.slim=false` to test managed stock simulators while retaining lifecycle ownership.
The pool cap can be changed with `-Psimsteward.maxSimulators=<count>`.

## Cleanup and failure recovery

Simsteward records ownership under `~/.gradle/simsteward/records` (or the configured Gradle user
home). If deletion fails or a daemon dies before cleanup, the next leasing build reaps recorded
simulators whose owning daemon has exited. Reaping can also be requested without running tests:

```sh
./gradlew :haze:simstewardReap --no-scan
```

Reaping protects devices owned by live daemons and devices created with a different `xcrun`.
Unrecorded devices are reported for manual inspection. Confirm the exact UDID belongs to an idle,
orphaned Simsteward device before manually deleting it; deletion permanently removes its data.
The previous convention's `HazeGradle-*` records are not adopted by Simsteward and require manual
recovery if any remain. Existing personal simulators are never selected, reset or deleted.

To reproduce a failure with Kotlin's normal simulator handling, omit `-Psimsteward.enabled=true`
and use `-Phaze.enableAppleTests` for core tests. For interactive sample work, use T3's device tools
rather than taking over a simulator owned by a running test build.
