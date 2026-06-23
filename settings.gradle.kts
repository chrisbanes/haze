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

val remoteBuildCacheUrl = providers.gradleProperty("remoteBuildCacheUrl")
val remoteBuildCacheUsername = providers.gradleProperty("remoteBuildCacheUsername")
val remoteBuildCachePassword = providers.gradleProperty("remoteBuildCachePassword")
val remoteBuildCacheUrlValue = remoteBuildCacheUrl.orNull?.takeIf(String::isNotBlank)
val remoteBuildCacheUsernameValue = remoteBuildCacheUsername.orNull?.takeIf(String::isNotBlank)
val remoteBuildCachePasswordValue = remoteBuildCachePassword.orNull?.takeIf(String::isNotBlank)
val isRemoteBuildCacheEnabled = providers.gradleProperty("remoteBuildCacheEnabled")
  .map(String::toBoolean)
  .getOrElse(false) &&
  remoteBuildCacheUrlValue != null &&
  remoteBuildCacheUsernameValue != null &&
  remoteBuildCachePasswordValue != null
val isRemoteBuildCachePushEnabled = isRemoteBuildCacheEnabled &&
  providers.gradleProperty("remoteBuildCachePush")
    .map(String::toBoolean)
    .getOrElse(false)

buildCache {
  remote<HttpBuildCache> {
    isEnabled = isRemoteBuildCacheEnabled
    isPush = isRemoteBuildCachePushEnabled

    if (isRemoteBuildCacheEnabled) {
      url = uri(remoteBuildCacheUrlValue!!)
      credentials {
        username = remoteBuildCacheUsernameValue!!
        password = remoteBuildCachePasswordValue!!
      }
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
