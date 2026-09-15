// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SourceInputContractsSample(
  navController: NavHostController,
  effect: SampleEffect,
) {
  val navigationEnabled = LocalSampleNavigationEnabled.current
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Source selection and retention") },
        navigationIcon = {
          if (navigationEnabled) {
            IconButton(onClick = navController::navigateUp) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      Text("Independent source selection", style = MaterialTheme.typography.titleMedium)
      SourceSelectionExample(effect)
      Text("Disappearing and replaced source", style = MaterialTheme.typography.titleMedium)
      SourceRetentionExample(effect)
    }
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun SourceSelectionExample(effect: SampleEffect) {
  val hazeState = rememberHazeState()
  Box(Modifier.fillMaxWidth().height(160.dp)) {
    Box(Modifier.fillMaxSize().hazeSource(hazeState, key = "selected").background(Color(0xff006c4c)))
    Box(Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter).hazeSource(hazeState, key = "excluded").background(Color(0xff6b3fa0)))
    val input = HazeInput.Sources(hazeState, selection = HazeSourceSelection.All.where { it.key == "selected" })
    val effectModifier = when (effect) {
      SampleEffect.Blur -> Modifier.hazeBlur(input, HazeBlurStyle { blurRadius(18.dp) })
      SampleEffect.Glass -> Modifier.hazeGlass(input, GlassStyle.regular)
    }
    Box(
      Modifier
        .align(Alignment.BottomCenter)
        .padding(12.dp)
        .then(effectModifier)
        .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
        .padding(12.dp),
    ) {
      Text("Only the green source is selected", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun SourceRetentionExample(effect: SampleEffect) {
  val hazeState = rememberHazeState()
  var sourceVisible by remember { mutableStateOf(true) }
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = { sourceVisible = !sourceVisible }) {
      Text(if (sourceVisible) "Remove source" else "Restore source")
    }
    Box(Modifier.fillMaxWidth().height(160.dp)) {
      if (sourceVisible) {
        Box(Modifier.fillMaxSize().hazeSource(hazeState, key = "replaceable").background(Color(0xff2457a5)))
      }
      Row(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        val retainedInput = HazeInput.Sources(hazeState)
        val retainedEffectModifier = when (effect) {
          SampleEffect.Blur -> Modifier.hazeBlur(retainedInput, HazeBlurStyle { blurRadius(18.dp) })
          SampleEffect.Glass -> Modifier.hazeGlass(retainedInput, GlassStyle.regular)
        }
        Box(
          Modifier
            .weight(1f)
            .then(retainedEffectModifier)
            .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        ) {
          Text("Keep last frame", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
        val clearInput = HazeInput.Sources(hazeState, retention = HazeSourceRetention.ClearWhenUnavailable)
        val clearEffectModifier = when (effect) {
          SampleEffect.Blur -> Modifier.hazeBlur(clearInput, HazeBlurStyle { blurRadius(18.dp) })
          SampleEffect.Glass -> Modifier.hazeGlass(clearInput, GlassStyle.regular)
        }
        Box(
          Modifier
            .weight(1f)
            .then(clearEffectModifier)
            .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        ) {
          Text("Clear when unavailable", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}
