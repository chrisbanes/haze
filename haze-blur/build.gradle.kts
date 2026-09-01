// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
  id("dev.drewhamilton.poko")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.blur"
    androidResources.enable = true

    withHostTest {
      isIncludeAndroidResources = true
    }

    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

      @Suppress("UnstableApiUsage")
      managedDevices {
        localDevices {
          create("pixel6Api34") {
            device = "Pixel 6"
            sdkVersion = 34
            systemImageSource = "aosp_atd"
          }
        }
      }
    }
  }

  addDefaultHazeTargets(project, withSkikoMain = true)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(libs.compose.ui)
        implementation(libs.compose.animation.core)
        implementation(libs.androidx.collection)
        implementation(projects.hazeUtils)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.core)
      }
    }

    named("androidDeviceTest") {
      dependencies {
        implementation(libs.assertk)
        implementation(libs.androidx.activity)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.compose.ui.test.manifest)
        implementation(libs.androidx.test.runner)
        implementation(libs.compose.foundation)
        implementation(projects.internal.contextTest) {
          exclude(group = "org.robolectric", module = "robolectric")
        }
      }
    }

    named("androidHostTest") {
      dependencies {
        implementation(libs.androidx.activity)
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.compose.foundation)
        implementation(libs.compose.material3)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))

        implementation(libs.compose.foundation)
        implementation(libs.compose.ui.test)

        implementation(projects.internal.contextTest)
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
    optIn.add("dev.chrisbanes.haze.InternalHazeApi")
  }
}

poko {
  pokoAnnotation.set("dev/chrisbanes/haze/Poko")
}
