// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.GradleException

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

    named("androidHostTest") {
      dependencies {
        implementation(libs.compose.ui.test.junit4)
        implementation(libs.androidx.activity)
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

val screenshotMatrixCase = providers.gradleProperty("screenshotMatrixCase")
val screenshotMatrixRecord = providers.gradleProperty("roborazzi.test.record")

val requestsFullScreenshotMatrix = gradle.startParameter.taskNames.any {
  it.substringAfterLast(':') == "verifyScreenshotMatrixFull"
}

if (requestsFullScreenshotMatrix) {
  require(screenshotMatrixCase.orNull == null) {
    "verifyScreenshotMatrixFull cannot be narrowed with -PscreenshotMatrixCase"
  }
  require(screenshotMatrixRecord.orNull != "true") {
    "verifyScreenshotMatrixFull cannot record baselines"
  }
}

tasks.withType<Test>().configureEach {
  screenshotMatrixCase.orNull?.let {
    systemProperty("haze.screenshot.matrix.case", it)
  }
}

fun requireFullScreenshotMatrixInputs() {
  check(screenshotMatrixCase.orNull == null) {
    "verifyScreenshotMatrixFull cannot be narrowed with -PscreenshotMatrixCase"
  }
  check(screenshotMatrixRecord.orNull != "true") {
    "verifyScreenshotMatrixFull cannot record baselines"
  }
}

val verifyScreenshotMatrixPr = tasks.register("verifyScreenshotMatrixPr") {
  group = "verification"
  description = "Verifies legacy screenshots and every enrolled host screenshot matrix case."
  dependsOn("jvmTest", "testAndroidHostTest")
  doFirst {
    check(screenshotMatrixCase.orNull == null) {
      "verifyScreenshotMatrixPr cannot be narrowed with -PscreenshotMatrixCase"
    }
    check(screenshotMatrixRecord.orNull != "true") {
      "verifyScreenshotMatrixPr cannot record baselines"
    }
  }
}

val verifyScreenshotMatrixDevice = tasks.register("verifyScreenshotMatrixDevice") {
  group = "verification"
  description = "Fails closed until fresh qualified-device native backdrop evidence is verified."
  doLast {
    throw GradleException(
      "Screenshot matrix device verification requires fresh Pixel 6 Android 37.2 native " +
        "backdrop artifacts; host screenshots cannot satisfy the full gate.",
    )
  }
}

tasks.register("verifyScreenshotMatrixFull") {
  group = "verification"
  description = "Verifies host coverage and requires qualified-device native backdrop evidence."
  dependsOn(verifyScreenshotMatrixPr, verifyScreenshotMatrixDevice)
  doFirst { requireFullScreenshotMatrixInputs() }
}

// Compose resources plugin generates this task for withDeviceTest() even when
// no androidDeviceTest source set exists. Disable it to avoid outputDirectory errors.
tasks.configureEach {
  if (name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets") {
    enabled = false
  }
}
