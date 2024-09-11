// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


pluginManagement {
  includeBuild("gradle/build-logic")

  repositories {
    google {
      content {
        includeGroupByRegex(".*google.*")
        includeGroupByRegex(".*android.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()

    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev") {
      content {
        includeGroupByRegex("org.jetbrains.compose.*")
        includeGroupByRegex("org.jetbrains.skiko.*")
        includeGroupByRegex("org.jetbrains.androidx.*")
      }
    }

    mavenLocal()
  }
}

plugins {
  id("com.gradle.develocity") version "3.18.1"
}

val isCi: Boolean get() = !System.getenv("CI").isNullOrEmpty()

develocity {
  buildScan {
    termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
    termsOfUseAgree.set("yes")

    if (isCi) {
      publishing.onlyIf { true }
      tag("CI")
    }

    uploadInBackground.set(!isCi)
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// https://docs.gradle.org/7.6/userguide/configuration_cache.html#config_cache:stable
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

rootProject.name = "haze-root"

include(
  ":haze",
  ":haze-materials",
  ":internal:benchmark",
  ":internal:screenshot-test",
  ":sample:shared",
  ":sample:android",
  ":sample:desktop",
  ":sample:web",
)
