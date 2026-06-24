// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  // Publishing is disabled until API and visual quality are finalized.
  id("dev.chrisbanes.metalava")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.liquidglass"

    withHostTest {}
  }

  addDefaultHazeTargets(project)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        implementation(projects.hazeUtils)
        implementation(libs.compose.ui)
        implementation(libs.compose.foundation)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)
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

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
    optIn.add("dev.chrisbanes.haze.InternalHazeApi")
  }
}
