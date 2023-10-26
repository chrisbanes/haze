// Copyright 2023, Christopher Banes and the project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.snapper.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.chrisbanes.snapper.rememberSnapperFlingBehavior

internal val LazyRowSamples = listOf(
  Sample(title = "LazyRow sample") { LazyRowSample() },
)

@OptIn(ExperimentalSnapperApi::class)
@Composable
private fun LazyRowSample() {
  val lazyListState = rememberLazyListState()

  LazyRow(
    state = lazyListState,
    flingBehavior = rememberSnapperFlingBehavior(lazyListState),
    contentPadding = PaddingValues(16.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    items(20) { index ->
      ImageItem(
        text = "$index",
        modifier = Modifier
          .width(160.dp)
          .aspectRatio(3 / 4f),
      )
    }
  }
}
