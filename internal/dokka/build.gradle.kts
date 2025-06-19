// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("org.jetbrains.dokka")
}

kotlin {
  addDefaultHazeTargets()
}

dependencies {
  dokka(projects.haze)
  dokka(projects.hazeMaterials)
}

android {
  namespace = "dev.chrisbanes.haze.docs"
}

dokka {
  moduleName.set("Haze")
}
