// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.screenshots"
    androidResources.enable = true

    withHostTest {
      isIncludeAndroidResources = true
    }

    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  jvm()

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
  }

  sourceSets {
    commonMain {
      dependencies {
        api(projects.hazeBlur)
        api(projects.hazeLiquidglass)
        api(libs.compose.foundation)
        api(libs.compose.material3)
        api(libs.compose.components.resources)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)

        implementation(libs.compose.ui.test)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.screenshotTest)
      }
    }

    jvmTest {
      kotlin.srcDir("src/jvmTest/kotlin")

      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

roborazzi {
  outputDir.set(project.layout.projectDirectory.dir("screenshots"))
}

tasks.withType<Test> {
  failOnNoDiscoveredTests.set(false)
  systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
}

tasks.register("test") {
  dependsOn("jvmTest", "testAndroidHostTest")
}

// Compose resources plugin generates this task for withDeviceTest() even when
// no androidDeviceTest source set exists. Disable it to avoid outputDirectory errors.
tasks.configureEach {
  if (name == "finalizeTestRoborazziAndroidHostTest") {
    mustRunAfter("finalizeTestRoborazziJvm")
  }

  if (name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets") {
    enabled = false
  }
}
