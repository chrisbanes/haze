// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.navigation3"

    withHostTest {}
  }

  addDefaultHazeTargets(project)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(libs.compose.navigation3.ui)
        implementation(libs.compose.foundation)
      }
    }
  }
}
