// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
  id("dev.drewhamilton.poko")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.blur"
    compileSdk = 36
  }
  addDefaultHazeTargets()
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(libs.compose.multiplatform.ui)
        implementation(libs.compose.multiplatform.foundation)
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

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    iosMain {
      dependsOn(skikoMain)
    }

    macosMain {
      dependsOn(skikoMain)
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
