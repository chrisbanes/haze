// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


plugins {
  id("dev.chrisbanes.root")

  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.baselineprofile) apply false
  alias(libs.plugins.cacheFixPlugin) apply false
  alias(libs.plugins.android.lint) apply false
  alias(libs.plugins.android.test) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.spotless) apply false
  alias(libs.plugins.compose.multiplatform) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.mavenpublish) apply false
  alias(libs.plugins.metalava) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.dokka)
}
