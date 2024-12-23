// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.mutableStateListOf

class Navigator {

  private val backStack = mutableStateListOf<Sample>()

  val currentSample: Sample?
    get() = backStack.lastOrNull()

  fun navigateTo(sample: Sample) {
    backStack.add(sample)
  }

  fun navigateUp() {
    if (backStack.isNotEmpty()) {
      backStack.removeLast()
    }
  }
}
