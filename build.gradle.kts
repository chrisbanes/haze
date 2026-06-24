// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Copy

plugins {
  id("dev.chrisbanes.root")

  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library) apply false
  alias(libs.plugins.baselineprofile) apply false
  alias(libs.plugins.cacheFixPlugin) apply false
  alias(libs.plugins.android.lint) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.mavenpublish) apply false
  alias(libs.plugins.metalava) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.poko) apply false
  alias(libs.plugins.dokka)
}

subprojects {
  tasks.withType<Copy>().configureEach {
    if (name.endsWith("TestDevelopmentExecutableCompileSync")) {
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
  }

  dependencies {
    components {
      listOf(
        "coil-core-iosarm64",
        "coil-core-iossimulatorarm64",
        "coil-core-js",
        "coil-core-jvm",
        "coil-core-macosarm64",
        "coil-core-wasm-js",
      ).forEach { module ->
        withModule("io.coil-kt.coil3:$module") {
          allVariants {
            withDependencies {
              removeIf { it.group == "org.jetbrains.skiko" && it.name == "skiko" }
            }
          }
        }
      }
    }
  }
}
