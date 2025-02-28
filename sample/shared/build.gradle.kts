// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

android {
  namespace = "dev.chrisbanes.haze.sample.shared"
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(projects.hazeMaterials)

        implementation(libs.coil.compose)
        implementation(libs.coil.ktor)
        implementation(libs.ktor.core)

        api(compose.material3)
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
