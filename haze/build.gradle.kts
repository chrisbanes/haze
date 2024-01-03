// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("me.tylerbwong.gradle.metalava")
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
  testBuildType = "release"
}

kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        api(compose.ui)
      }
    }

    val skikoMain by creating {
      dependsOn(commonMain)

      dependencies {
        implementation(compose.foundation)
      }
    }

    val iosMain by getting {
      dependsOn(skikoMain)
    }

    val jvmMain by getting {
      dependsOn(skikoMain)
    }

    val commonTest by getting {
      dependencies {
        implementation(kotlin("test"))
        implementation(projects.internal.screenshotTest)
      }
    }
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions {
    freeCompilerArgs += "-Xcontext-receivers"
  }
}

metalava {
  filename.set("api/api.txt")
}
