// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.application")
  id("dev.chrisbanes.kotlin.android")
  id("dev.chrisbanes.compose")
  id("androidx.baselineprofile")
}

android {
  namespace = "dev.chrisbanes.haze.sample.android"

  defaultConfig {
    versionCode = 1
    versionName = "1.0"
  }

  buildFeatures {
    compose = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.jetpackcompose.compiler.get()
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(
        "proguard-rules.pro",
        getDefaultProguardFile("proguard-android-optimize.txt"),
      )
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
  implementation(projects.hazeJetpackCompose)

  implementation(libs.imageloader)

  implementation(libs.androidx.core)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)

  baselineProfile(projects.internal.baselineProfileJetpackcompose)
}
