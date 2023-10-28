// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  // apply plugin: "com.vanniktech.maven.publish"
}

android {
  namespace = "dev.chrisbanes.haze"
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.ui)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain)
    }

    val androidMain by getting {
      dependencies {
        api("androidx.compose.ui:ui:1.6.0-alpha08")
      }
    }

    val iosMain by getting {
      dependsOn(skikoMain)
    }

    val jvmMain by getting {
      dependsOn(skikoMain)
    }
  }
}
