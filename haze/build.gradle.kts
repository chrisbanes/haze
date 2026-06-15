// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import dev.chrisbanes.gradle.addDefaultHazeTargets
import org.jetbrains.compose.ExperimentalComposeLibrary

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

  addDefaultHazeTargets(project)
  explicitApi()

  sourceSets {
    commonMain {
      dependencies {
        api(compose.ui)
        implementation(projects.hazeUtils)
        implementation(compose.foundation)
        implementation(libs.androidx.collection)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.activity)
        implementation(libs.androidx.tracing)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    if (!project.providers.gradleProperty("haze.disableAppleTargets").isPresent) {
      iosMain {
        dependsOn(skikoMain)
      }

      macosMain {
        dependsOn(skikoMain)
      }
    }

    jvmMain {
      dependsOn(skikoMain)
    }

    wasmJsMain {
      dependsOn(skikoMain)
    }

    jsMain {
      dependsOn(skikoMain)
    }

    commonTest {
      dependencies {
        implementation(kotlin("test"))
        implementation(libs.assertk)

        @OptIn(ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)

        implementation(projects.internal.contextTest)
        implementation(projects.internal.testUtils)
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

// https://youtrack.jetbrains.com/issue/CMP-4906
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest> {
  enabled = false
}

val enableAppleTests = providers.gradleProperty("haze.enableAppleTests").isPresent

/**
 * Disable Mac host and iOS sim tests by default. They have a high CI cost (mostly
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
  filter { include("dev.chrisbanes.haze.*") }
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
