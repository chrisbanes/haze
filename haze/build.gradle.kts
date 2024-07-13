// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("androidx.baselineprofile")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("dev.chrisbanes.metalava")
  id("io.github.takahirom.roborazzi")
}

android {
  namespace = "dev.chrisbanes.haze"
  defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

kotlin {
  sourceSets {
    commonMain {
      dependencies {
        api(compose.ui)
        implementation(compose.foundation)
      }
    }

    androidMain {
      dependencies {
        implementation(libs.androidx.collection)
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

    named("wasmJsMain") {
      dependsOn(skikoMain)
    }

    named("jsMain") {
      dependsOn(skikoMain)
    }

    val screenshotTest by creating {
      dependsOn(commonTest.get())

      dependencies {
        implementation(kotlin("test"))
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-Xcontext-receivers")
  }
}

baselineProfile {
  filter { include("dev.chrisbanes.haze.*") }
}

dependencies {
  baselineProfile(projects.internal.benchmark)
}
