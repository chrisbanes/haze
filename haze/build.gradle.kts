// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets
plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("androidx.baselineprofile")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
  id("dev.drewhamilton.poko")
}

// Metalava writes an extra blank line at EOF for this module's API signature. Keep the checked-in
// signature stable so regeneration does not create a whitespace-only diff.
tasks.named("metalavaGenerateSignature").configure {
  val apiFile = File(project.projectDir, "api/api.txt")
  doLast {
    val contents = apiFile.readText()
    val normalized = contents.trimEnd() + "\n"
    if (contents != normalized) apiFile.writeText(normalized)
  }
}

kotlin {
  android {
    namespace = "dev.chrisbanes.haze"

    optimization {
      consumerKeepRules.publish = true
      consumerKeepRules.file("consumer-rules.pro")
    }

    withHostTest {
      isIncludeAndroidResources = true
    }

    withDeviceTest {
      instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
  }

  addDefaultHazeTargets(project, withSkikoMain = true)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(libs.compose.animation.core)
        api(libs.compose.ui)
        implementation(projects.hazeUtils)
        implementation(libs.androidx.collection)
        implementation(libs.androidx.lifecycle.runtime.compose)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.activity)
      }
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)

        implementation(libs.compose.foundation)
        implementation(libs.compose.ui.test)

        implementation(projects.internal.contextTest)
      }
    }

    named("androidHostTest") {
      dependencies {
        implementation(libs.compose.foundation)
        implementation(projects.internal.screenshotTest)
      }
    }

    named("androidDeviceTest") {
      dependencies {
        implementation(libs.androidx.activity.compose)
        implementation(libs.assertk)
        implementation(libs.androidx.compose.ui.test.manifest)
        implementation(libs.androidx.test.core)
        implementation(libs.androidx.test.runner)
        implementation(libs.compose.foundation)
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }

  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
    optIn.add("dev.chrisbanes.haze.InternalHazeApi")
  }
}

val enableAppleTests = providers.gradleProperty("haze.enableAppleTests").isPresent

/**
 * Disable native host and iOS sim tests by default. They have a high CI cost (mostly
 * linking), but CI can opt in to iOS simulator coverage with -Phaze.enableAppleTests.
 */
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest> {
  enabled = enableAppleTests
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest> {
  enabled = enableAppleTests
}

poko {
  pokoAnnotation.set("dev/chrisbanes/haze/Poko")
}

baselineProfile {
  filter {
    include("dev.chrisbanes.haze.**")
    exclude("dev.chrisbanes.haze.sample.**")
  }
}

dependencies {
  baselineProfile(projects.internal.benchmark)
}

tasks.withType<Test> {
  failOnNoDiscoveredTests.set(false)
}

// Compose resources plugin generates this task for withDeviceTest() even when
// no androidDeviceTest source set exists. Disable it to avoid outputDirectory errors.
tasks.configureEach {
  if (name == "copyAndroidDeviceTestComposeResourcesToAndroidAssets") {
    enabled = false
  }
}
