// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.FluentMaterials
import dev.chrisbanes.haze.materials.HazeMaterials

@Composable
fun MaterialsSample(@Suppress("UNUSED_PARAMETER") navigator: Navigator) {
  val hazeState = remember { HazeState() }

  Box {
    AsyncImage(
      model = rememberRandomSampleImageUrl(width = 720, height = 1280),
      contentScale = ContentScale.Crop,
      contentDescription = null,
      modifier = Modifier
        .haze(state = hazeState)
        .fillMaxSize(),
    )

    Column(
      verticalArrangement = Arrangement.spacedBy(24.dp),
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(24.dp),
    ) {
      Spacer(Modifier.height(400.dp))

      Card {
        Text(
          text = "HazeMaterials - Light",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }

      SamplesTheme(useDarkColors = false) {
        HazeMaterialsRow(
          hazeState = hazeState,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Card(modifier = Modifier.padding(top = 24.dp)) {
        Text(
          text = "HazeMaterials - Dark",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }

      SamplesTheme(useDarkColors = true) {
        HazeMaterialsRow(
          hazeState = hazeState,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Card(modifier = Modifier.padding(top = 24.dp)) {
        Text(
          text = "CupertinoMaterials - Light",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }

      SamplesTheme(useDarkColors = false) {
        CupertinoMaterialsRow(
          hazeState = hazeState,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Card {
        Text(
          text = "CupertinoMaterials - Dark",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }

      SamplesTheme(useDarkColors = true) {
        CupertinoMaterialsRow(
          hazeState = hazeState,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Card(modifier = Modifier.padding(top = 24.dp)) {
        Text(
          text = "FluentMaterials - Light",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }

      SamplesTheme(useDarkColors = false) {
        FluentMaterialsRow(
          hazeState = hazeState,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Card {
        Text(
          text = "FluentMaterials - Dark",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(16.dp),
        )
      }

      SamplesTheme(useDarkColors = true) {
        FluentMaterialsRow(
          hazeState = hazeState,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      Spacer(Modifier.height(400.dp))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun HazeMaterialsRow(hazeState: HazeState, modifier: Modifier = Modifier) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    MaterialsCard(
      name = "Ultra Thin",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = HazeMaterials.ultraThin(),
    )

    MaterialsCard(
      name = "Thin",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = HazeMaterials.thin(),
    )

    MaterialsCard(
      name = "Regular",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = HazeMaterials.regular(),
    )

    MaterialsCard(
      name = "Thick",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = HazeMaterials.thick(),
    )

    MaterialsCard(
      name = "Ultra Thick",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = HazeMaterials.ultraThick(),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun CupertinoMaterialsRow(hazeState: HazeState, modifier: Modifier = Modifier) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    MaterialsCard(
      name = "Ultra Thin",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = CupertinoMaterials.ultraThin(),
    )

    MaterialsCard(
      name = "Thin",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = CupertinoMaterials.thin(),
    )

    MaterialsCard(
      name = "Regular",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = CupertinoMaterials.regular(),
    )

    MaterialsCard(
      name = "Thick",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = CupertinoMaterials.thick(),
    )
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun FluentMaterialsRow(hazeState: HazeState, modifier: Modifier = Modifier) {
  FlowRow(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    MaterialsCard(
      name = "Accent Acrylic Base",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.accentAcrylicBase(),
    )

    MaterialsCard(
      name = "Accent Acrylic Default",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.accentAcrylicDefault(),
    )

    MaterialsCard(
      name = "Thin Acrylic",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.thinAcrylic(),
    )

    MaterialsCard(
      name = "Acrylic Base",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.acrylicBase(),
    )

    MaterialsCard(
      name = "Acrylic Default",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.acrylicDefault(),
    )

    MaterialsCard(
      name = "Mica",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.mica(),
    )

    MaterialsCard(
      name = "MicaAlt",
      shape = MaterialTheme.shapes.large,
      state = hazeState,
      style = FluentMaterials.micaAlt(),
    )
  }
}

@Composable
private fun MaterialsCard(
  name: String,
  shape: Shape,
  state: HazeState,
  style: HazeStyle,
  modifier: Modifier = Modifier,
) {
  Card(
    shape = shape,
    colors = CardDefaults.cardColors(
      containerColor = Color.Transparent,
      contentColor = MaterialTheme.colorScheme.onSurface,
    ),
    modifier = modifier.size(160.dp),
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .hazeChild(state = state, style = style)
        .padding(16.dp),
    ) {
      Text(name)
    }
  }
}
