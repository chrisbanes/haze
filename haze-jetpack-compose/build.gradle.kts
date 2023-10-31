// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.android")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
}

android {
  namespace = "dev.chrisbanes.haze.jetpackcompose"

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.jetpackcompose.compiler.get()
  }
}

dependencies {
  api(libs.androidx.compose.ui)
}
