// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.sample.shared"
    androidResources.enable = true

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  addDefaultHazeTargets(project)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(projects.hazeBlur)
        api(projects.hazeLiquidglass)
        api(projects.hazeMaterials)

        api(libs.androidx.navigation.compose)
        api(libs.kotlinx.serialization.json)

        implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}") {
          exclude(group = "org.jetbrains.skiko", module = "skiko")
        }
        implementation(libs.coil.ktor)
        implementation(libs.ktor.core)

        api(libs.compose.material3)
        api(libs.compose.material.icons)
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

    androidMain {
      dependencies {
        implementation(libs.ktor.cio)

        implementation(libs.androidx.media3.exoplayer)
        implementation(libs.androidx.media3.ui)
      }
    }

    named("androidHostTest") {
      dependencies {
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.compose.ui.test.manifest)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    if (!project.providers.gradleProperty("haze.disableAppleTargets").isPresent) {
      iosMain {
        dependsOn(skikoMain)

        dependencies {
          implementation(libs.ktor.darwin)
        }
      }
    }

    jvmMain {
      dependsOn(skikoMain)

      dependencies {
        implementation(libs.ktor.cio)
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
      }
    }

    named("wasmJsMain") {
      dependsOn(skikoMain)

      dependencies {
        implementation(npm("ws", "8.18.3"))
      }
    }

    named("jsMain") {
      dependsOn(skikoMain)

      dependencies {
        implementation(npm("ws", "8.18.3"))
      }
    }

    named("wasmJsTest") {
      dependencies {
        implementation(npm("ws", "8.18.3"))
      }
    }

    named("jsTest") {
      dependencies {
        implementation(npm("ws", "8.18.3"))
      }
    }
  }

  targets.withType<KotlinNativeTarget>().configureEach {
    binaries.framework {
      isStatic = true
      baseName = "HazeSamplesKt"
    }
  }
}

// Disable JS tests; they currently fail due to missing browser-side runtime support.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest> {
  enabled = false
}
