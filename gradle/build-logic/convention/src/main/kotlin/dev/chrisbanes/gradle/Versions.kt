// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

object Versions {
  const val COMPILE_SDK = 37
  const val MIN_SDK = 23
  const val TARGET_SDK = 37

  // Every SDK a Robolectric @Config, robolectric.properties or the screenshot matrix runs on. A new
  // level must be added here, or its test class fails with `no artifacts found for DependencyJar`.
  val ROBOLECTRIC_SDKS = listOf(28, 30, 31, 32, 33, 35, 36, 37)
}
