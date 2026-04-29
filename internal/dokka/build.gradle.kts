// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("com.android.library")
  id("com.android.kotlin.multiplatform.library")
  id("org.jetbrains.dokka")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.docs"
    compileSdk = 36
  }
  addDefaultHazeTargets()
}

dependencies {
  dokka(projects.haze)
  dokka(projects.hazeBlur)
  dokka(projects.hazeMaterials)
  dokka(projects.hazeUtils)
}

dokka {
  moduleName.set("Haze")
}
