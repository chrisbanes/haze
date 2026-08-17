// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("dev.drewhamilton.poko")
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
        api(projects.hazeGlass)
        implementation(projects.hazeUtils)
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
        implementation(libs.compose.navigation3.ui)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.screenshotTest)
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

roborazzi {
  outputDir.set(project.layout.projectDirectory.dir("screenshots"))

  @OptIn(ExperimentalRoborazziApi::class)
  separateOutputDirs.set(true)
}

poko {
  pokoAnnotation.set("dev/chrisbanes/haze/Poko")
}

tasks.withType<Test> {
  failOnNoDiscoveredTests.set(false)
  systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"

  // Android host screenshots combine Robolectric, Compose, and Skia; the full suite exceeds 512m
  // in isolation. Keep 2g scoped here and serialize it after jvmTest to limit peak usage; profile
  // before broadening this setting or increasing the heap.
  if (name == "testAndroidHostTest") {
    maxHeapSize = "2g"
  }

  addTestOutputListener(
    object : TestOutputListener {
      override fun onOutput(
        testDescriptor: TestDescriptor,
        outputEvent: TestOutputEvent,
      ) {
        outputEvent.message.lineSequence()
          .filter { it.contains("Roborazzi image diff:") }
          .forEach { line ->
            logger.lifecycle("${testDescriptor.className} > ${testDescriptor.name}: ${line.trim()}")
          }
      }
    },
  )
}

tasks.register("test") {
  dependsOn("jvmTest", "testAndroidHostTest")
}

tasks.configureEach {
  if (name == "testAndroidHostTest") {
    mustRunAfter("jvmTest")
  }
}

// Compose resources plugin generates this task for withDeviceTest() even when
// no androidDeviceTest source set exists. Disable it to avoid outputDirectory errors.
tasks.configureEach {
  if (name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets") {
    enabled = false
  }
}
