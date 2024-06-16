// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  ComposeViewport(viewportContainerId = "Sample") {
    PageLoadNotify()
    Samples("Haze Samples")
  }
}

external fun onLoadFinished()

@Composable
fun PageLoadNotify() {
  LaunchedEffect(Unit) {
    onLoadFinished()
  }
}
