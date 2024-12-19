// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun DialogSample(navigator: Navigator) {
  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = "Haze Dialog sample") },
        navigationIcon = {
          IconButton(onClick = navigator::navigateUp) {
            @Suppress("DEPRECATION")
            Icon(Icons.Default.ArrowBack, null)
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    },
  ) { innerPadding ->
    val hazeState = remember { HazeState() }
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
      Dialog(onDismissRequest = { showDialog = false }) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = .5f),
          shape = MaterialTheme.shapes.extraLarge,
          // We can't use Haze tint with dialogs, as the tint will display a scrim over the
          // background content. Instead we need to set a translucent background on the
          // dialog content.
          color = MaterialTheme.colorScheme.surface.copy(alpha = 0.2f),
          contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
          Box(
            Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular()),
          ) {
            // empty
          }
        }
      }
    }

    LazyVerticalGrid(
      modifier = Modifier.hazeSource(state = hazeState),
      columns = GridCells.Fixed(4),
      contentPadding = innerPadding,
      verticalArrangement = Arrangement.spacedBy(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      items(40) {
        Card(
          modifier = Modifier.height(100.dp),
          onClick = { showDialog = true },
        ) {
          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
          ) {
            Text(text = "Card $it")
          }
        }
      }
    }
  }
}
