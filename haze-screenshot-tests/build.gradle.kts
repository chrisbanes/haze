// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("com.android.kotlin.multiplatform.library")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.screenshots"
    compileSdk = 36
  }
  jvm()

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
  }

  sourceSets {
    commonMain {
      dependencies {
        api(projects.hazeBlur)
        api(libs.compose.multiplatform.foundation)
        api(compose.material3)
        api(compose.components.resources)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)

        implementation(libs.compose.multiplatform.ui.test)

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
