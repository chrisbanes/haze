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

android {
  namespace = "dev.chrisbanes.haze.materials"
}

kotlin {
  addDefaultHazeTargets()
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.hazeBlur)
        implementation(compose.material3)
      }
    }
  }
}
