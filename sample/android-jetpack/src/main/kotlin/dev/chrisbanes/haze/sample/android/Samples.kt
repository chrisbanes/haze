// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

val Samples = listOf(
  Sample("Scaffold") { ScaffoldSample(it) },
  Sample("Credit Card") { CreditCardSample(it) },
  Sample("Images List") { ImagesList(it) },
  Sample("Dialog") { DialogSample(it) },
)

data class Sample(
  val title: String,
  val content: @Composable (Navigator) -> Unit,
)

fun interface Navigator {
  fun navigateUp()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Samples(appTitle: String) {
  MaterialTheme {
    var currentSample by remember { mutableStateOf<Sample?>(null) }

    val navigator = remember {
      Navigator { currentSample = null }
    }

    Crossfade(targetState = currentSample) { sample ->
      if (sample != null) {
        sample.content(navigator)
      } else {
        Scaffold(
          topBar = {
            TopAppBar(
              title = { Text(text = currentSample?.title ?: appTitle) },
              navigationIcon = {
                if (currentSample != null) {
                  IconButton(onClick = { currentSample = null }) {
                    Icon(
                      imageVector = Icons.Default.ArrowBack,
                      contentDescription = "Navigate back",
                    )
                  }
                }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          },
        ) { contentPadding ->
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
          ) {
            items(Samples) { sample ->
              ListItem(
                headlineContent = { Text(text = sample.title) },
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { currentSample = sample },
              )
            }
          }
        }
      }
    }
  }
}
