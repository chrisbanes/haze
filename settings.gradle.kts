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
val isRemoteBuildCachePushEnabled = gradleBooleanProperty("remoteBuildCachePush")
val isRemoteBuildCacheEnabled =
  remoteBuildCacheUrl != null &&
    remoteBuildCacheUsername != null &&
    remoteBuildCachePassword != null

buildCache {
  local {
    isEnabled = !isCi || !isRemoteBuildCacheEnabled
    if (!isEnabled) {
      logger.lifecycle("Local build cache disabled because remote build cache is enabled on CI")
    }
  }

  remote<HttpBuildCache> {
    if (isRemoteBuildCacheEnabled) {
      isEnabled = true
      isPush = isRemoteBuildCachePushEnabled
      url = uri(checkNotNull(remoteBuildCacheUrl))
      credentials {
        username = remoteBuildCacheUsername
        password = remoteBuildCachePassword
      }
      logger.lifecycle("Remote build cache enabled (push: $isRemoteBuildCachePushEnabled)")
    } else {
      isEnabled = false
      logger.lifecycle(
        "Remote build cache disabled " +
          "(url present: ${remoteBuildCacheUrl != null}, " +
          "username present: ${remoteBuildCacheUsername != null}, " +
          "password present: ${remoteBuildCachePassword != null})",
      )
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
