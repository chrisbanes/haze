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

android {
  namespace = "dev.chrisbanes.haze"

  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    consumerProguardFiles("consumer-rules.pro")
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

kotlin {
  addDefaultHazeTargets()

  sourceSets {
    commonMain {
      dependencies {
        api(compose.ui)
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

    iosMain {
      dependsOn(skikoMain)
    }

    macosMain {
      dependsOn(skikoMain)
    }

    jvmMain {
      dependsOn(skikoMain)
    }

    wasmJsMain {
      dependsOn(skikoMain)

      dependencies {
        implementation(libs.kotlinx.datetime)
      }
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
      }
    }

    jvmTest {
      dependencies {
        implementation(compose.desktop.currentOs)
      }
    }
  }
}

// https://youtrack.jetbrains.com/issue/CMP-4906
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest> {
  enabled = false
}

/**
 * Disable Mac host and iOS sim tests. They have a high CI cost (mostly linking) but
 * provide little value over the quicker JVM + Android tests
 */
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest> {
  enabled = false
}
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeHostTest> {
  enabled = false
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
  compilerOptions {
    optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
  }
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
