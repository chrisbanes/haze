// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.internal.screenshot"
    androidResources.enable = true
  }

  jvm()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.internal.contextTest)

        api(libs.compose.components.resources)
        api(libs.compose.foundation)
        api(libs.compose.material3)

        api(libs.compose.ui.test)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.compose.ui.test.manifest)

        implementation(libs.compose.ui.test.junit4)

        implementation(libs.robolectric)

        implementation(libs.roborazzi.core)
        implementation(libs.roborazzi.android)
        implementation(libs.roborazzi.compose)
        implementation(libs.roborazzi.junit)
      }
    }

    jvmMain {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.compose.ui.test.junit4)

        implementation(libs.roborazzi.core)
        implementation(libs.roborazzi.compose.desktop)
      }
    }
  }
}
