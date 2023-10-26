// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.application")
  id("dev.chrisbanes.kotlin.android")
  id("dev.chrisbanes.compose")
}

android {
  namespace = "dev.chrisbanes.haze.sample.android"

  defaultConfig {
    versionCode = 1
    versionName = "1.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
  }

  packaging {
    resources.excludes += setOf(
      // Exclude AndroidX version files
      "META-INF/*.version",
      // Exclude consumer proguard files
      "META-INF/proguard/*",
      // Exclude the Firebase/Fabric/other random properties files
      "/*.properties",
      "fabric/*.properties",
      "META-INF/*.properties",
      // License files
      "LICENSE*",
      // Exclude Kotlin unused files
      "META-INF/**/previous-compilation-data.bin",
    )
  }
}

dependencies {
  implementation(projects.sample.shared)
  implementation(libs.androidx.core)
  implementation(libs.androidx.activity.compose)
}
