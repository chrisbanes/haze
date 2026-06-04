// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class AndroidLibraryConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    with(target) {
      pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        pluginManager.apply("com.android.kotlin.multiplatform.library")
        configureKotlinMultiplatformAndroidLibrary()
      }
    }
  }
}
