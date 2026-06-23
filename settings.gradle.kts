// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import org.gradle.caching.http.HttpBuildCache

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
  id("com.gradle.develocity") version "4.4.3"
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

fun gradleProperty(name: String): String? =
  providers.gradleProperty(name).orNull?.takeIf(String::isNotBlank)

fun gradleBooleanProperty(name: String): Boolean =
  providers.gradleProperty(name).map(String::toBoolean).getOrElse(false)

val remoteBuildCacheUrl = gradleProperty("remoteBuildCacheUrl")
val remoteBuildCacheUsername = gradleProperty("remoteBuildCacheUsername")
val remoteBuildCachePassword = gradleProperty("remoteBuildCachePassword")
val isRemoteBuildCacheEnabled = gradleBooleanProperty("remoteBuildCacheEnabled")

buildCache {
  remote<HttpBuildCache> {
    if (
      isRemoteBuildCacheEnabled &&
      remoteBuildCacheUrl != null &&
      remoteBuildCacheUsername != null &&
      remoteBuildCachePassword != null
    ) {
      isEnabled = true
      isPush = gradleBooleanProperty("remoteBuildCachePush")
      url = uri(remoteBuildCacheUrl)
      credentials {
        username = remoteBuildCacheUsername
        password = remoteBuildCachePassword
      }
    } else {
      isEnabled = false
    }
  }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// https://docs.gradle.org/7.6/userguide/configuration_cache.html#config_cache:stable
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")

rootProject.name = "haze-root"

include(
  ":haze",
  ":haze-blur",
  ":haze-liquidglass",
  ":haze-utils",
  ":haze-materials",
  ":haze-screenshot-tests",
  ":internal:benchmark",
  ":internal:context-test",
  ":internal:test-utils",
  ":internal:dokka",
  ":internal:screenshot-test",
  ":sample:shared",
  ":sample:android",
  ":sample:desktop",
  ":sample:web",
)

if (!providers.gradleProperty("haze.disableAppleTargets").isPresent) {
  include(":sample:macos")
}
