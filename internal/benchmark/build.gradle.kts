// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.test")
  id("dev.chrisbanes.kotlin.android")
  id("androidx.baselineprofile")
}

@Suppress("UnstableApiUsage")
android {
  namespace = "dev.chrisbanes.haze.baselineprofile"

  defaultConfig {
    minSdk = 28
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
  }

  targetProjectPath = ":sample:android"
  experimentalProperties["android.experimental.self-instrumenting"] = true

  testOptions.managedDevices.localDevices {
    create("pixel5Api30") {
      device = "Pixel 5"
      sdkVersion = 30
      systemImageSource = "aosp"
    }

    create("pixel5Api34") {
      device = "Pixel 5"
      sdkVersion = 34
      systemImageSource = "aosp"
    }
  }
}

@Suppress("UnstableApiUsage")
baselineProfile {
  managedDevices += "pixel5Api30"
  managedDevices += "pixel5Api34"
  useConnectedDevices = false
  enableEmulatorDisplay = false
}

dependencies {
  implementation(libs.androidx.benchmark.macro)
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.uiautomator)
}
