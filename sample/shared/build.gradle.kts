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
    commonMain {
      dependencies {
        implementation(projects.haze)
        implementation(projects.hazeMaterials)

        implementation(libs.coil.compose)
        implementation(libs.coil.ktor)
        implementation(libs.ktor.core)

        api(compose.material3)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.ktor.cio)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    iosMain {
      dependsOn(skikoMain)

      dependencies {
        implementation(libs.ktor.cio)
      }
    }

    jvmMain {
      dependsOn(skikoMain)

      dependencies {
        implementation(libs.ktor.cio)
      }
    }

    named("wasmJsMain") {
      dependsOn(skikoMain)
    }
  }
}
