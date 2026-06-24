// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

class KotlinAndroidConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    tasks.withType<KotlinCompilationTask<*>>().configureEach {
      compilerOptions {
        allWarningsAsErrors.set(true)
      }
    }

    configureSpotless()
  }
}
