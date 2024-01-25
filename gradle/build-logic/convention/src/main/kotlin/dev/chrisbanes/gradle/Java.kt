// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

fun Project.configureJava() {
  java {
    toolchain {
      // Build with JDK 21
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  }

  // Set Java language version to 11
  tasks.withType<JavaCompile>().configureEach { options.release.set(11) }
}

private fun Project.java(action: JavaPluginExtension.() -> Unit) = extensions.configure<JavaPluginExtension>(action)
