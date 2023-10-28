// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@OptIn(ExperimentalKotlinGradlePluginApi::class)
class KotlinMultiplatformConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    with(pluginManager) {
      apply("org.jetbrains.kotlin.multiplatform")
    }

    extensions.configure<KotlinMultiplatformExtension> {
      targetHierarchy.default()

      jvm()
      if (pluginManager.hasPlugin("com.android.library")) {
        androidTarget()
      }

      listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
      ).forEach { target ->
        target.binaries.framework {
          baseName = path.substring(1).replace(':', '-')
        }
      }

      configureSpotless()
      configureKotlin()
    }
  }
}
