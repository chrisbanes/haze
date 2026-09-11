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
val screenshotMatrixSdk = providers.gradleProperty("screenshotMatrixSdk")

tasks.withType<Test>().configureEach {
  screenshotMatrixCase.orNull?.let { systemProperty("haze.screenshot.matrix.case", it) }
  if (name == "testAndroidHostTest") {
    screenshotMatrixSdk.orNull?.let { systemProperty("robolectric.enabledSdks", it) }
  }
}

val matrixPreflight = tasks.register("screenshotMatrixPreflight") {
  val caseFilter = screenshotMatrixCase.orNull
  val sdkFilter = screenshotMatrixSdk.orNull
  val record = providers.gradleProperty("roborazzi.test.record").orNull
  val systemRecord = providers.systemProperty("roborazzi.test.record").orNull
  val verify = providers.gradleProperty("roborazzi.test.verify").orNull
  val systemVerify = providers.systemProperty("roborazzi.test.verify").orNull
  val recordingTask = gradle.startParameter.taskNames.any {
    it.substringAfterLast(':').startsWith("recordRoborazzi")
  }
  doLast {
    check(caseFilter == null && sdkFilter == null) { "PR matrix verification cannot be filtered" }
    check(record != "true" && systemRecord != "true" && !recordingTask) {
      "PR matrix verification cannot record baselines"
    }
    check(verify == "true" && systemVerify != "false") { "PR matrix verification requires Roborazzi verification" }
  }
}

// The preflight must complete before test dependencies can record or execute anything.
// It runs only when a coverage gate is requested, so focused record/test commands remain available.
tasks.withType<Test>().configureEach { mustRunAfter(matrixPreflight) }

val verifyScreenshotMatrixJvm = tasks.register<Exec>("verifyScreenshotMatrixJvm") {
  group = "verification"
  dependsOn(matrixPreflight, "jvmTest")
  commandLine(
    "python3",
    rootProject.file("scripts/verify_screenshot_matrix.py"),
    "desktop",
    layout.buildDirectory.file("test-results/jvmTest/TEST-dev.chrisbanes.haze.ScreenshotMatrixDesktopTest.xml").get().asFile,
  )
}
val verifyScreenshotMatrixAndroidCases = tasks.register<Exec>("verifyScreenshotMatrixAndroidCases") {
  group = "verification"
  dependsOn(matrixPreflight, "testAndroidHostTest")
  commandLine(
    "python3",
    rootProject.file("scripts/verify_screenshot_matrix.py"),
    "android",
    layout.buildDirectory.file("test-results/testAndroidHostTest/TEST-dev.chrisbanes.haze.ScreenshotMatrixAndroidTest.xml").get().asFile,
  )
}
val verifyScreenshotMatrixAndroidNative = tasks.register<Exec>("verifyScreenshotMatrixAndroidNative") {
  group = "verification"
  dependsOn(matrixPreflight, "testAndroidHostTest")
  commandLine(
    "python3",
    rootProject.file("scripts/verify_screenshot_matrix.py"),
    "android-native",
    layout.buildDirectory.file("test-results/testAndroidHostTest/TEST-dev.chrisbanes.haze.ScreenshotMatrixNativeBackdropAndroidTest.xml").get().asFile,
  )
}
val verifyScreenshotMatrixNativeBackdropProof = tasks.register<Exec>("verifyScreenshotMatrixNativeBackdropProof") {
  group = "verification"
  dependsOn(matrixPreflight, "testAndroidHostTest")
  commandLine(
    "python3",
    rootProject.file("scripts/verify_screenshot_matrix.py"),
    "native-backdrop-proof",
    layout.buildDirectory.file("test-results/testAndroidHostTest/TEST-dev.chrisbanes.haze.BackdropAndroidRegressionTest.xml").get().asFile,
  )
}
val verifyScreenshotMatrixAndroid = tasks.register("verifyScreenshotMatrixAndroid") {
  group = "verification"
  description = "Verifies fallback and native Backdrop Robolectric screenshot coverage."
  dependsOn(
    verifyScreenshotMatrixAndroidCases,
    verifyScreenshotMatrixAndroidNative,
    verifyScreenshotMatrixNativeBackdropProof,
  )
}
tasks.register("verifyScreenshotMatrixPr") {
  group = "verification"
  description = "Verifies legacy library screenshots and complete enrolled host matrix coverage."
  dependsOn(verifyScreenshotMatrixJvm, verifyScreenshotMatrixAndroid)
}

// Compose resources plugin generates this task for withDeviceTest() even when
// no androidDeviceTest source set exists. Disable it to avoid outputDirectory errors.
tasks.configureEach {
  if (name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets") {
    enabled = false
  }
}
