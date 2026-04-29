// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.test")
  id("androidx.baselineprofile")
}

android {
  namespace = "dev.chrisbanes.haze.baselineprofile"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
  }

  targetProjectPath = ":sample:android"
  experimentalProperties["android.experimental.self-instrumenting"] = true

  // TODO: ManagedVirtualDevice API changed in AGP 9.x / benchmark alpha
  // testOptions.managedDevices { ... }
}

// TODO: ManagedVirtualDevice API changed in AGP 9.x / benchmark alpha
// @Suppress("UnstableApiUsage")
// baselineProfile {
//   managedDevices += "pixel5Api30"
//   managedDevices += "pixel5Api34"
//   useConnectedDevices = false
//   enableEmulatorDisplay = false
// }

dependencies {
  implementation(libs.androidx.benchmark.macro)
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
}
