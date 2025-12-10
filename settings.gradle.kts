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

    // Compose Multiplatform pre-releases
    // maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")

    mavenLocal()
  }
}

plugins {
  id("com.gradle.develocity") version "4.3"
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
  ":haze-blur",
  ":haze-utils",
  ":haze-materials",
  ":haze-screenshot-tests",
  ":internal:benchmark",
  ":internal:context-test",
  ":internal:dokka",
  ":internal:screenshot-test",
  ":sample:shared",
  ":sample:android",
  ":sample:desktop",
  ":sample:web",
  ":sample:macos",
)
