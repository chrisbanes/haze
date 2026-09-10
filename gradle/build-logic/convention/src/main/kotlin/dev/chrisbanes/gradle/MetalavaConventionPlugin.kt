// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import me.tylerbwong.gradle.metalava.extension.MetalavaExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private const val METALAVA_VERSION = "1.0.0-alpha14"

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

      version.set(METALAVA_VERSION)
    }

    // The upstream plugin resolves its default version while applying, before the extension above
    // is configured. Keep the task classpath aligned with the version declared here.
    configurations.named("metalava") {
      resolutionStrategy.force("com.android.tools.metalava:metalava:$METALAVA_VERSION")
    }

    tasks.named { it.startsWith("metalavaCheckCompatibility") }.configureEach {
      dependsOn(tasks.named { it.startsWith("generateResourceAccessors") })
    }
  }
}

private fun Project.metalava(action: MetalavaExtension.() -> Unit) = extensions.configure<MetalavaExtension>(action)
