// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(projects.sample.shared)
        implementation(compose.desktop.currentOs)

        //FIX MAIN DISPATCHER for JVM
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "dev.chrisbanes.haze.sample.desktop.MainKt"
  }
}
