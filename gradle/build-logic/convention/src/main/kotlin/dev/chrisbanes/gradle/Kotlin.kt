// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.Project
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

fun Project.configureKotlin() {
  // Configure Java to use our chosen language level. Kotlin will automatically pick this up
  configureJava()

  tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
      allWarningsAsErrors.set(true)

      if (this is KotlinJvmCompilerOptions) {
        // Target JVM 11 bytecode
        jvmTarget.set(JvmTarget.JVM_11)
      }
    }
  }
}
