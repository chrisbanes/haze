// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.snapper.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.chrisbanes.snapper.rememberSnapperFlingBehavior

internal val LazyColumnSamples = listOf(
  Sample(title = "LazyColumn sample") { LazyColumnSample() },
)

@OptIn(ExperimentalSnapperApi::class)
@Composable
private fun LazyColumnSample() {
  val lazyListState = rememberLazyListState()

  LazyColumn(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(lazyListState = lazyListState),
    contentPadding = PaddingValues(top = 200.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    items(20) { index ->
      ImageItem(
        text = "$index",
        modifier = Modifier
          .fillMaxWidth()
          .height(200.dp),
      )
    }
  }
}
