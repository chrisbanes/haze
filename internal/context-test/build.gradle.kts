// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("com.android.library")
  id("com.android.kotlin.multiplatform.library")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.internal.context"
    compileSdk = 36
  }
  addDefaultHazeTargets()

  sourceSets {
    androidMain {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.compose.ui.test.manifest)
        api(libs.robolectric)
      }
    }
  }
}
