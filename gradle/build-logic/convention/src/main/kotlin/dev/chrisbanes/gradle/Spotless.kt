// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.LineEnding
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureSpotless() {
  with(pluginManager) {
    apply("com.diffplug.spotless")
  }

  spotless {
    // Workaround for https://github.com/diffplug/spotless/issues/1644
    lineEndings = LineEnding.PLATFORM_NATIVE

    val ktlintVersion = libs.findVersion("ktlint").get().requiredVersion

    kotlin {
      target("src/**/*.kt")
      ktlint(ktlintVersion)
      licenseHeaderFile(rootProject.file("spotless/copyright.txt"))
    }

    kotlinGradle {
      target("*.kts")
      ktlint(ktlintVersion)
      licenseHeaderFile(rootProject.file("spotless/copyright.txt"), "(^(?![\\/ ]\\**).*$)")
    }
  }
}

private fun Project.spotless(action: SpotlessExtension.() -> Unit) =
  extensions.configure<SpotlessExtension>(action)
