// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("io.github.takahirom.roborazzi")
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze.sample.screenshots"
    androidResources.enable = true

    withHostTest {
      isIncludeAndroidResources = true
    }
  }

  jvm()

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
  }

  sourceSets {
    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.compose.ui.test)
        implementation(projects.internal.screenshotTest)
        implementation(projects.sample.shared)
      }
    }

    jvmTest {
      dependencies {
        implementation(libs.coil.compose)
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

tasks.withType<Test> {
  failOnNoDiscoveredTests.set(false)
  systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"

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
