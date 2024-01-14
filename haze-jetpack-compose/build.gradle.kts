// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.android")
  id("androidx.baselineprofile")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("me.tylerbwong.gradle.metalava")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "dev.chrisbanes.haze.jetpackcompose"

  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
  testBuildType = "release"

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.jetpackcompose.compiler.get()
  }
}

dependencies {
  api(libs.androidx.compose.ui)

  testImplementation(kotlin("test"))
  testImplementation(projects.internal.screenshotTest)
}

baselineProfile {
  filter { include("dev.chrisbanes.haze.*") }
}

dependencies {
  baselineProfile(projects.internal.baselineProfileJetpackcompose)
}

metalava {
  filename.set("api/api.txt")
}
