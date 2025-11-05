// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "dev.chrisbanes.haze.screenshots"

  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true

      all {
        it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
      }
    }
  }
}

kotlin {
  jvm()
  androidTarget()

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
  }

  sourceSets {
    commonMain {
      dependencies {
        api(projects.haze)
        api(compose.foundation)
        api(compose.material3)
        api(compose.components.resources)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)

        @OptIn(ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.screenshotTest)
      }
    }
  }
}

roborazzi {
  outputDir.set(project.layout.projectDirectory.dir("screenshots"))
}

tasks.withType<Test> {
  failOnNoDiscoveredTests.set(false)
}
