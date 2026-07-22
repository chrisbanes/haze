// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
  jvm()

  sourceSets {
    jvmMain {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
        implementation(libs.kotlinx.serialization.json)
      }
    }
    jvmTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)
      }
    }
  }
}
