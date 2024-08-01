// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Simple pager item which displays an image
 */
@Composable
internal fun ImageItem(
  text: String,
  modifier: Modifier = Modifier,
) {
  Surface(modifier) {
    Box {
      AsyncImage(
        model = rememberRandomSampleImageUrl(width = 400),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
      )

      Text(
        text = text,
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(MaterialTheme.colorScheme.surface)
          .sizeIn(minWidth = 40.dp, minHeight = 40.dp)
          .padding(8.dp)
          .wrapContentSize(Alignment.Center),
      )
    }
  }
}

private val rangeForRandom = (0..100000)

fun randomSampleImageUrl(
  seed: Int = rangeForRandom.random(),
  width: Int = 300,
  height: Int = width,
): String = "https://picsum.photos/seed/$seed/$width/$height"

/**
 * Remember a URL generate by [randomSampleImageUrl].
 */
@Composable
fun rememberRandomSampleImageUrl(
  seed: Int = rangeForRandom.random(),
  width: Int = 300,
  height: Int = width,
): String = rememberSaveable { randomSampleImageUrl(seed, width, height) }
