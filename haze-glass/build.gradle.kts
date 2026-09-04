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
    namespace = "dev.chrisbanes.haze.glass"
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
        api(libs.compose.animation.core)
        api(libs.compose.foundation)
        implementation(projects.hazeUtils)
        implementation(libs.compose.ui)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)
        implementation(libs.compose.ui.test)
        implementation(projects.internal.contextTest)
      }
    }

    named("androidDeviceTest") {
      dependencies {
        implementation(libs.assertk)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.compose.ui.test.manifest)
        implementation(libs.androidx.test.espresso.core)
        implementation(libs.androidx.test.runner)
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

dependencies {
  add("androidHostTestImplementation", libs.androidx.activity)
}

poko {
  pokoAnnotation.set("dev/chrisbanes/haze/Poko")
}
