// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
}

android {
  namespace = "dev.chrisbanes.haze.internal.context"
}

kotlin {
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

  targets.configureEach {
    compilations.configureEach {
      compileTaskProvider {
        compilerOptions {
          freeCompilerArgs.add("-Xexpect-actual-classes")
        }
      }
    }
  }
}
