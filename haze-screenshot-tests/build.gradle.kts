// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.kotest")
  id("com.google.devtools.ksp")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "dev.chrisbanes.haze.screenshots"

  testOptions {
    unitTests {
      isIncludeAndroidResources = true

      all {
        it.useJUnitPlatform()
        it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
      }
    }
  }
}

kotlin {
  jvm()
  androidTarget()
  iosSimulatorArm64()

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

        implementation(libs.kotest.framework.engine)

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
