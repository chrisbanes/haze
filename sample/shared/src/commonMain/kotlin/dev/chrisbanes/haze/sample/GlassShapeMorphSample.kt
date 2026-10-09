// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
public fun GlassShapeMorphSample(navController: NavHostController) {
  val hazeState = rememberHazeState()
  var menuExpanded by remember { mutableStateOf(false) }
  var chipExpanded by remember { mutableStateOf(false) }

  // These animated values are read only inside the Glass style below, so animating them
  // redraws the Glass surface without recomposing it.
  val cornerRadius = animateDpAsState(if (menuExpanded) 20.dp else 28.dp)
  val surfaceAlpha = animateFloatAsState(if (menuExpanded) 1f else 0.85f)

  // Styles compare by identity, so remember the style once. Its block runs again whenever
  // the state it reads changes.
  val menuStyle = remember {
    GlassStyle.regular.then {
      shape(RoundedCornerShape(cornerRadius.value))
      alpha(surfaceAlpha.value)
    }
  }

  // A percent corner size, such as CircleShape, already follows size-only changes, so this
  // style is constant.
  val chipStyle = remember { GlassStyle.regular.then { shape(CircleShape) } }

  Box(Modifier.fillMaxSize()) {
    Box(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .background(
          Brush.linearGradient(
            listOf(Color(0xFF1E3A8A), Color(0xFF9333EA), Color(0xFFF97316)),
          ),
        ),
    )

    if (LocalSampleNavigationEnabled.current) {
      IconButton(
        onClick = navController::navigateUp,
        modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).padding(8.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
      }
    }

    Column(
      modifier = Modifier.align(Alignment.Center),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
      // A button that grows into a menu. The size animates in layout, and the corner radius
      // and alpha animate through the remembered style.
      Column(
        Modifier
          .animateContentSize()
          .hazeGlass(input = HazeInput.Backdrop(hazeState), style = menuStyle)
          // Clip the ripple to the same animated radius, read in the draw phase.
          .graphicsLayer {
            shape = RoundedCornerShape(cornerRadius.value)
            clip = true
          }
          .clickable { menuExpanded = !menuExpanded }
          .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Menu", color = Color.White, fontWeight = FontWeight.SemiBold)
        if (menuExpanded) {
          listOf("New document", "Open recent", "Share", "Settings").forEach { item ->
            Text(item, color = Color.White, modifier = Modifier.width(180.dp))
          }
        }
      }

      // A pill whose width changes. CircleShape resolves against the current size on every
      // frame, so the shape follows without any state in the style.
      Box(
        Modifier
          .animateContentSize()
          .hazeGlass(input = HazeInput.Backdrop(hazeState), style = chipStyle)
          .clip(CircleShape)
          .clickable { chipExpanded = !chipExpanded }
          .padding(horizontal = 24.dp, vertical = 14.dp),
      ) {
        Text(
          if (chipExpanded) "Size-only change with a percent corner" else "Tap me",
          color = Color.White,
        )
      }
    }
  }
}
