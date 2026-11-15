# Managed iOS simulator tests

Use the local `dev.chrisbanes.ios.managed-simulator` convention to run Kotlin/Native tests on a
fresh, slimmed simulator. The shared KMP convention applies it; it activates only with
`-Phaze.iosSimulator.managed=true`. Stock local runs and CI retain their existing behaviour.

## Run tests

1. Use Java 21, Xcode and an installed SimSlim CLI. Inspect installed device types and runtimes
   with `xcrun simctl list devicetypes` and `xcrun simctl list runtimes`. Choose explicit identifiers;
   the convention does not download runtimes or install SimSlim.
2. Run the relevant test tasks through the Gradle-run skill. For example, with the corresponding
   device type and runtime installed:

   ```sh
   ./gradlew :haze:iosSimulatorArm64Test :haze-blur:iosSimulatorArm64Test \
     :haze-glass:iosSimulatorArm64Test \
     -Phaze.iosSimulator.managed=true \
     -Phaze.iosSimulator.deviceType=com.apple.CoreSimulator.SimDeviceType.iPhone-17 \
     -Phaze.iosSimulator.runtime=com.apple.CoreSimulator.SimRuntime.iOS-27-0 \
     --no-scan
   ```

   Managed mode also enables core `haze` simulator tests, which are otherwise gated by
   `-Phaze.enableAppleTests`. It does not enable macOS host tests. Existing test filters still apply.
3. Confirm the requested test reports contain executed tests. A successful preparation task or
   compilation alone is not a test pass.

The build creates one `HazeGradle-<uuid>` simulator, applies SimSlim's default profile, verifies
its overrides, and supplies its UDID to the selected native test tasks with standalone mode off.
Modules share the device and serialize their simulator tests. Separate Gradle builds create
separate devices. Preparation and managed tests run afresh rather than reusing test results.
Preparation can run alongside compilation; a later compile failure still triggers device cleanup.

Optional properties:

- `haze.iosSimulator.executable`: SimSlim executable path; defaults to `simslim` on PATH.
- `haze.iosSimulator.xcrun`: Xcode launcher path; defaults to `/usr/bin/xcrun`.
- `haze.iosSimulator.profile`: JSON profile path, relative to the repository root or absolute.
  The same profile is passed to `on` and `verify`. Keep services required by the tests enabled;
  verification checks service overrides, not application correctness.

## Cleanup and failure recovery

The shared build service deletes its owned simulator after its consumers finish, including when
preparation or tests fail. CoreSimulator deletion also shuts the device down. Existing simulators
are never selected, reset or deleted. A missing SimSlim executable or failed slimming is a build
failure; managed mode does not silently fall back to stock.

A forcibly killed daemon or machine crash can prevent cleanup. The first configured KMP project's
`build/managed-ios-simulators/HazeGradle-<uuid>.txt` records the device name, type, runtime and,
when creation completed, UDID. Check that exact name and UDID against `xcrun simctl list devices`
and confirm no build still owns it before deleting the orphan:

```sh
simslim delete <recorded-udid>
```

Deletion permanently removes that disposable simulator's data. Remove its record after successful
recovery; normal cleanup removes the record automatically. If interruption happened during creation
before the UDID was recorded, find the exact recorded device name in the inventory first. Keep
records until recovery is complete; `clean` may remove them.

To reproduce a failure on stock, rerun without the managed properties and use the normal
`-Phaze.enableAppleTests` gate for core tests. This creates no managed simulator. For interactive
sample work, use T3's device tools rather than taking over a simulator owned by a running test build.
