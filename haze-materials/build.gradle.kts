// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("androidx.baselineprofile")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("me.tylerbwong.gradle.metalava")
}

android {
  namespace = "dev.chrisbanes.haze.materials"
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        implementation(compose.material3)
      }
    }
  }
}

metalava {
  filename.set("api/api.txt")
}
