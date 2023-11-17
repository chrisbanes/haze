// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.chrisbanes.haze.sample.Samples

fun main() = application {
  Window(
    title = "Haze Sample",
    onCloseRequest = ::exitApplication,
  ) {
    Samples("Haze Samples")
  }
}
