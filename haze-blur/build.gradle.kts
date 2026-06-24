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

  addDefaultHazeTargets(project)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(libs.compose.ui)
        implementation(libs.compose.foundation)
        implementation(libs.androidx.collection)
        implementation(projects.hazeUtils)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.activity)
        implementation(libs.androidx.tracing)
      }
    }

    named("androidDeviceTest") {
      dependencies {
        implementation(libs.assertk)
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.compose.ui.test.manifest)
        implementation(projects.internal.testUtils)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    if (!project.providers.gradleProperty("haze.disableAppleTargets").isPresent) {
      iosMain {
        dependsOn(skikoMain)
      }
    }

    jvmMain {
      dependsOn(skikoMain)
    }

    wasmJsMain {
      dependsOn(skikoMain)
    }

    jsMain {
      dependsOn(skikoMain)
    }

    commonTest {
      dependencies {
        implementation(libs.assertk)
        implementation(kotlin("test"))

        implementation(libs.compose.ui.test)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.testUtils)
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

// Disable JS tests; they currently fail due to missing browser-side runtime support.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest> {
  enabled = false
}
