// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets
import dev.chrisbanes.gradle.appleTargetsEnabled
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("dev.drewhamilton.poko")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.sample.shared"
    androidResources.enable = true

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  addDefaultHazeTargets(project, withSkikoMain = true)

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(projects.hazeBlur)
        api(projects.hazeGlass)
        api(projects.hazeMaterials)

        api(libs.androidx.navigation.compose)

        implementation("io.coil-kt.coil3:coil-compose:${libs.versions.coil.get()}") {
          exclude(group = "org.jetbrains.skiko", module = "skiko")
        }
        implementation(libs.coil.ktor)

        api(libs.compose.material3)
        implementation(libs.compose.material3.adaptive.navigation.suite)
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

    if (project.appleTargetsEnabled) {
      appleMain {
        dependencies {
          implementation(libs.ktor.darwin)
        }
      }
    }

    jvmMain {
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
      dependencies {
        implementation(npm("ws", "8.18.3"))
      }
    }

    named("jsMain") {
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

  // Only iOS consumes this framework, via the Xcode sample project. :sample:macos links the
  // klib directly, so macosArm64 must not build a framework that nothing reads.
  targets.withType<KotlinNativeTarget>()
    .matching { it.konanTarget.family == Family.IOS }
    .configureEach {
      binaries.framework {
        isStatic = true
        baseName = "HazeSamplesKt"
      }
    }
}

poko {
  pokoAnnotation.set("dev/chrisbanes/haze/Poko")
}
