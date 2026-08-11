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

/**
 * Whether the Apple (iOS + macOS) Kotlin/Native targets are configured for this build.
 *
 * Non-Apple hosts cannot build them, so CI opts out with `-Phaze.disableAppleTargets`.
 */
val Project.appleTargetsEnabled: Boolean
  get() = !providers.gradleProperty("haze.disableAppleTargets").isPresent

fun KotlinMultiplatformExtension.addDefaultHazeTargets(
  project: Project,
  withSkikoMain: Boolean = false,
) {
  jvm()

  if (project.appleTargetsEnabled) {
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

  if (withSkikoMain) {
    val skikoMain = sourceSets.maybeCreate("skikoMain").apply {
      dependsOn(sourceSets.getByName("commonMain"))
    }
    if (project.appleTargetsEnabled) {
      // appleMain is the common parent of iosMain and macosMain, both of which render via Skiko.
      sourceSets.getByName("appleMain").dependsOn(skikoMain)
    }
    sourceSets.getByName("jvmMain").dependsOn(skikoMain)
    sourceSets.getByName("wasmJsMain").dependsOn(skikoMain)
    sourceSets.getByName("jsMain").dependsOn(skikoMain)
  }
}

internal fun Project.kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
  extensions.configure<KotlinMultiplatformExtension>(action)
}

internal val Project.kotlin: KotlinMultiplatformExtension
  get() = extensions.getByType<KotlinMultiplatformExtension>()
