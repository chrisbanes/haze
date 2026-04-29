// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("com.android.library")
  id("com.android.kotlin.multiplatform.library")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.sample.shared"
    compileSdk = 36
  }
  addDefaultHazeTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(projects.hazeBlur)
        api(projects.hazeMaterials)

        api(libs.androidx.navigation.compose)
        api(libs.kotlinx.serialization.json)

        implementation(libs.coil.compose)
        implementation(libs.coil.ktor)
        implementation(libs.ktor.core)

        api(compose.material3)
        api(libs.compose.material.icons)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.ktor.cio)

        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.ui)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    iosMain {
      dependsOn(skikoMain)

      dependencies {
        implementation(libs.ktor.darwin)
      }
    }

    macosMain {
      dependsOn(skikoMain)

      dependencies {
        implementation(libs.ktor.darwin)
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

    named("jsMain") {
      dependsOn(skikoMain)
    }
  }

  targets.withType<KotlinNativeTarget>().configureEach {
    binaries.framework {
      isStatic = true
      baseName = "HazeSamplesKt"
    }
  }
}
