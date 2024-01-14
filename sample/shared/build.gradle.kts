// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

android {
  namespace = "dev.chrisbanes.haze.sample.shared"
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(projects.haze)
        implementation(libs.imageloader)
        api(compose.material3)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain)
    }

    val iosMain by getting {
      dependsOn(skikoMain)
    }

    val jvmMain by getting {
      dependsOn(skikoMain)
    }
  }
}
