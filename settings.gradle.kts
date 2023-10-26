// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0


pluginManagement {
  includeBuild("gradle/build-logic")

  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()

    // Prerelease versions of Compose Multiplatform
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")

    // Used for snapshots if needed
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    google()
    mavenLocal()
  }
}

plugins {
  id("com.gradle.enterprise") version "3.15.1"
}

val isCi = providers.environmentVariable("CI").isPresent

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"

    publishAlwaysIf(isCi)
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// https://docs.gradle.org/7.6/userguide/configuration_cache.html#config_cache:stable
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

rootProject.name = "haze"

include(":lib")
include(":sample")
