// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Changes the captured content without moving the single, progressive, or merged surfaces. */
@Composable
public fun GlassRefreshSample(navController: NavHostController) {
  var alternate by remember { mutableStateOf(false) }
  GlassRefreshSampleContent(alternate, onChangeBackground = { alternate = !alternate }, onBack = navController::navigateUp)
}

@Composable
public fun GlassRefreshSampleContent(alternate: Boolean, onChangeBackground: () -> Unit, onBack: () -> Unit) {
  val hazeState = rememberHazeState()
  val regular = remember { GlassStyle.regular.then { shape(RoundedCornerShape(20.dp)) } }
  val progressive = remember {
    regular.then { optics { copy(progressive = HazeProgressive.verticalGradient()) } }
  }
  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier.fillMaxSize().hazeSource(hazeState).drawBehind {
        val stripe = 24.dp.toPx()
        val colors = if (alternate) listOf(Color.Blue, Color.Yellow) else listOf(Color.Red, Color.White)
        val count = (size.width / stripe).toInt() + 1
        repeat(count) { index ->
          drawRect(colors[index % 2], Offset(index * stripe, 0f), Size(stripe, size.height))
        }
      },
    )
    Column(
      Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Button(onClick = onChangeBackground, modifier = Modifier.testTag("glass_refresh_change")) { Text("Change background") }
      }
      Box(Modifier.fillMaxWidth().height(120.dp).hazeGlass(input = HazeInput.Sources(hazeState), style = regular)) {
        Text("Single", Modifier.align(Alignment.Center), color = Color.Black)
      }
      Box(Modifier.fillMaxWidth().height(120.dp).hazeGlass(input = HazeInput.Sources(hazeState), style = progressive)) {
        Text("Progressive", Modifier.align(Alignment.Center), color = Color.Black)
      }
      Box(Modifier.fillMaxWidth().height(120.dp).hazeGlassContainer(24.dp)) {
        Box(Modifier.align(Alignment.CenterStart).size(160.dp, 100.dp).hazeGlass(input = HazeInput.Sources(hazeState), style = regular))
        Box(Modifier.align(Alignment.CenterStart).offset(x = 130.dp).size(160.dp, 100.dp).hazeGlass(input = HazeInput.Sources(hazeState), style = regular))
        Text("Merged", Modifier.align(Alignment.CenterStart).offset(x = 110.dp), color = Color.Black)
      }
    }
  }
}
