// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import org.gradle.api.Project

fun Project.configureKotlin() {
  // Configure Java to use our chosen language level. Kotlin will automatically pick this up
  configureJava()
}
