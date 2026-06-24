// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")

    configureJava()

    kotlin {
      applyDefaultHierarchyTemplate()

      compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xexpect-actual-classes")
      }

      targets.withType<KotlinMultiplatformAndroidLibraryTarget> {
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_11)
        }
      }

      targets.withType<KotlinJvmTarget> {
        compilerOptions {
          jvmTarget.set(JvmTarget.JVM_11)
        }
      }
    }

    tasks.withType<KotlinNativeCompile>().configureEach {
      compilerOptions {
        // Kotlin emits unsuppressable duplicate KLIB loader warnings for the
        // AndroidX/JetBrains Compose metadata mix used by native targets.
        allWarningsAsErrors.set(false)
      }
    }

    tasks.withType<KotlinJsTest>().configureEach {
      enabled = false
    }

    configureSpotless()
  }
}

fun KotlinMultiplatformExtension.addDefaultHazeTargets(project: Project) {
  jvm()

  if (!project.providers.gradleProperty("haze.disableAppleTargets").isPresent) {
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
  }

  @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
  wasmJs {
    browser()
  }

  js {
    browser()
  }
}

internal fun Project.kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
  extensions.configure<KotlinMultiplatformExtension>(action)
}

internal val Project.kotlin: KotlinMultiplatformExtension
  get() = extensions.getByType<KotlinMultiplatformExtension>()
