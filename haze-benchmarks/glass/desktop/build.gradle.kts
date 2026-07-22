// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  jvm()
  compilerOptions { optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi") }
  sourceSets {
    jvmMain.dependencies {
      implementation(projects.internal.benchmarkDesktop)
      implementation(projects.hazeGlass)
      implementation(projects.sample.shared)
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.assertk)
    }
  }
}

compose.desktop.application {
  mainClass = "dev.chrisbanes.haze.benchmark.desktop.glass.MainKt"
}
