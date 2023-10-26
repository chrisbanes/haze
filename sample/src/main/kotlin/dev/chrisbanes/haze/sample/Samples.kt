// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable

val Samples = buildList {
  addAll(LazyColumnSamples)
}

data class Sample(
  val title: String,
  val content: @Composable (PaddingValues) -> Unit,
)
