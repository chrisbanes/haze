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
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.utils"
  }

  addDefaultHazeTargets(project)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.compose.ui)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.tracing)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    if (!project.providers.gradleProperty("haze.disableAppleTargets").isPresent) {
      iosMain {
        dependsOn(skikoMain)
      }

      macosMain {
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
  }
}
