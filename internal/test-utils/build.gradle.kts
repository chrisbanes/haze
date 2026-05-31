// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

android {
  namespace = "dev.chrisbanes.haze.internal.testutils"
}

kotlin {
  addDefaultHazeTargets(project)

  sourceSets {
    commonMain {
      dependencies {
        implementation(compose.runtime)
      }
    }
  }
}
