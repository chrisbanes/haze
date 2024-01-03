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
  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.foundation)
        api(compose.material3)
      }
    }

    val commonJvmMain by creating {
      dependsOn(commonMain)

      dependencies {
        api(libs.roborazzi.core)
      }
    }

    val androidMain by getting {
      dependsOn(commonJvmMain)

      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.compose.ui.test.manifest)

        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTestJUnit4)

        implementation(libs.robolectric)

        implementation(libs.roborazzi.compose)
        implementation(libs.roborazzi.junit)
      }
    }

    val jvmMain by getting {
      dependsOn(commonJvmMain)

      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.roborazzi.composedesktop)
      }
    }
  }

  targets.configureEach {
    compilations.configureEach {
      compilerOptions.configure {
        freeCompilerArgs.add("-Xexpect-actual-classes")
      }
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs += "-Xcontext-receivers"
  }
}
