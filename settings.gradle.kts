// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


pluginManagement {
  includeBuild("gradle/build-logic")

  repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
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
  id("com.gradle.enterprise") version "3.16.2"
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

rootProject.name = "haze-root"

include(
  ":haze",
  ":haze-jetpack-compose",
  ":internal:baseline-profile",
  ":internal:screenshot-test",
  ":sample:shared",
  ":sample:android",
  ":sample:desktop",
)
