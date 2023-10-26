// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.snapper.sample

import androidx.compose.runtime.Composable

@OptIn(ExperimentalStdlibApi::class)
val Samples = buildList {
  addAll(LazyRowSamples)
  addAll(LazyColumnSamples)
}

data class Sample(
  val title: String,
  val content: @Composable () -> Unit,
)
