// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("androidx.baselineprofile")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
  id("io.github.takahirom.roborazzi")
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

      all {
        it.systemProperties["robolectric.pixelCopyRenderMode"] = "hardware"
      }
    }
  }
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(compose.ui)
        implementation(compose.foundation)
        implementation(libs.androidx.lifecycle.compose)
      }
    }

    androidMain {
      dependencies {
        // Needed to upgrade Jetpack Compose
        // Can remove this once CMP goes stable
        api(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.foundation)
        implementation(libs.androidx.core)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain.get())
    }

    iosMain {
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

    val screenshotTest by creating {
      dependsOn(commonTest.get())

      dependencies {
        implementation(projects.internal.screenshotTest)
      }
    }

    jvmTest {
      dependsOn(screenshotTest)
    }

    androidUnitTest {
      dependsOn(screenshotTest)
    }
  }
}

// https://youtrack.jetbrains.com/issue/CMP-4906
tasks.named("jsBrowserTest").configure {
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
