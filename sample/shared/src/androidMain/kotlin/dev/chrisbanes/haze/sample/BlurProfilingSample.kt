// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
internal fun BlurProfilingSampleContent(
  state: BlurProfilingState,
  navController: NavHostController,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scenario = state.scenario
  if (scenario == null) {
    BlurProfilingScenarioPicker(
      onScenarioSelected = state::select,
      onBack = onBack,
      modifier = modifier,
    )
  } else {
    BlurProfilingScene(
      state = state,
      scenario = scenario,
      navController = navController,
      modifier = modifier,
    )
  }
}

@Composable
private fun BlurProfilingScenarioPicker(
  onScenarioSelected: (BlurProfilingScenario) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .testTag("blur_profiling_picker")
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Button(onClick = onBack) { Text("Back") }
    BlurProfilingScenario.entries.forEach { scenario ->
      Button(
        onClick = { onScenarioSelected(scenario) },
        modifier = Modifier.testTag("blur_profiling_select_${scenario.id}"),
      ) {
        Text(scenario.id)
      }
    }
  }
}

@Composable
private fun BlurProfilingScene(
  state: BlurProfilingState,
  scenario: BlurProfilingScenario,
  navController: NavHostController,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(state.phase, scenario) {
    if (state.phase == BlurProfilingPhase.Settling) {
      repeat(BLUR_PROFILING_SETTLING_FRAMES) {
        androidx.compose.runtime.withFrameNanos {}
      }
      state.markReady()
      return@LaunchedEffect
    }
    if (state.phase != BlurProfilingPhase.Running) return@LaunchedEffect
    Animatable(0f).animateTo(
      targetValue = 1f,
      animationSpec = tween(
        durationMillis = BLUR_PROFILING_DURATION_MILLIS,
        easing = LinearEasing,
      ),
    ) {
      state.updateProgress(value)
    }
    state.complete()
  }

  val sourceOffset = if (scenario.updatesSource) {
    { blurProfilingSourceOffset(scenario, progress = state.progress) }
  } else {
    null
  }
  val profilingDrawProgress = if (scenario.updatesSource) {
    null
  } else {
    { state.progress }
  }

  Box(modifier = modifier.fillMaxSize()) {
    ScaffoldSample(
      navController = navController,
      effect = SampleEffect.Blur,
      mode = scenario.mode,
      performanceMode = scenario.performanceMode,
      sourceOffset = sourceOffset,
      profilingDrawProgress = profilingDrawProgress,
    )
    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .background(Color.Black.copy(alpha = 0.6f))
        .padding(16.dp)
        .testTag("blur_profiling_selected_${scenario.id}"),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = scenario.id,
        color = Color.White,
        modifier = Modifier.testTag("blur_profiling_phase_${state.phase.id}"),
      )
      if (state.phase == BlurProfilingPhase.Ready) {
        Button(
          onClick = { state.start() },
          modifier = Modifier.testTag("blur_profiling_start"),
        ) {
          Text("Start")
        }
      }
    }
  }
}
