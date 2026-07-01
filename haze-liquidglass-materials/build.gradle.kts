// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  // Publishing is disabled until haze-liquidglass is published.
  id("dev.chrisbanes.metalava")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.liquidglass.materials"
  }

  addDefaultHazeTargets(project)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.hazeLiquidglass)
        implementation(libs.compose.material3)
      }
    }
  }
}
