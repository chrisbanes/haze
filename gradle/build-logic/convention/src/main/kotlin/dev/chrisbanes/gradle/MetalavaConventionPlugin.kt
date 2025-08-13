// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import me.tylerbwong.gradle.metalava.extension.MetalavaExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class MetalavaConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    with(pluginManager) {
      apply("me.tylerbwong.gradle.metalava")
    }

    metalava {
      filename.set("api/api.txt")

      excludedSourceSets.setFrom(
        target.kotlin
          .sourceSets
          .filter { it.name.contains("test", ignoreCase = true) }
          .flatMap { it.kotlin.sourceDirectories }
      )

      version.set("1.0.0-alpha13")
    }

    tasks.named { it.startsWith("metalavaCheckCompatibility") }.configureEach {
      dependsOn(tasks.named { it.startsWith("generateResourceAccessors") })
    }
  }
}

private fun Project.metalava(action: MetalavaExtension.() -> Unit) = extensions.configure<MetalavaExtension>(action)
