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
        implementation(compose.foundation)
        implementation(compose.material3)
      }
    }

    val androidUnitTest by getting {
      dependencies {
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.compose.ui.test.manifest)

        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTestJUnit4)

        implementation(libs.robolectric)

        implementation(libs.roborazzi.core)
        implementation(libs.roborazzi.compose)
        implementation(libs.roborazzi.junit)
      }
    }

    val jvmTest by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(kotlin("test"))
        implementation(libs.roborazzi.core)
        implementation(libs.roborazzi.composedesktop)
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
