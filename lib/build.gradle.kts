// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  // apply plugin: "com.vanniktech.maven.publish"
}

android {
  namespace = "dev.chrisbanes.haze"
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.foundation)
      }
    }
  }
}
