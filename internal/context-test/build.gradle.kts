// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
}

android {
  namespace = "dev.chrisbanes.haze.internal.context"
}

kotlin {
  sourceSets {
    val androidMain by getting {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.robolectric)
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
