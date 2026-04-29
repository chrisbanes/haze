// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("com.android.library")
  id("com.android.kotlin.multiplatform.library")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.materials"
    compileSdk = 36
  }
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
