// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "dev.chrisbanes.haze.internal.screenshot"
}

kotlin {
  jvm()
  androidTarget {
    publishLibraryVariants("release")
  }
  iosSimulatorArm64()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.internal.contextTest)

        api(compose.components.resources)
        api(compose.foundation)
        api(compose.material3)

        api(libs.kotest.framework.engine)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.compose.ui.test.manifest)

        implementation(compose.desktop.uiTestJUnit4)

        implementation(libs.robolectric)

        implementation(libs.roborazzi.core)
        implementation(libs.roborazzi.android)
        implementation(libs.roborazzi.compose)
      }
    }

    jvmMain {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(compose.desktop.uiTestJUnit4)

        implementation(libs.roborazzi.core)
        implementation(libs.roborazzi.compose.desktop)
      }
    }

    iosMain {
      dependencies {
        implementation(libs.roborazzi.compose.ios)
      }
    }
  }
}
