// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.android.library")
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish")
  id("me.tylerbwong.gradle.metalava")
}

android {
  namespace = "dev.chrisbanes.haze"
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
    }

    val iosMain by getting {
      dependsOn(skikoMain)
    }

    val jvmMain by getting {
      dependsOn(skikoMain)
    }
  }
}

metalava {
  filename.set("api/api.txt")
}
