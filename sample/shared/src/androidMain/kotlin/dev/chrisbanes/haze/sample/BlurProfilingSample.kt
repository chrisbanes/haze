// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import kotlinx.coroutines.flow.first

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
  val previousPlatformBackdropEnabled = remember {
    HazeFeatureFlags.isPlatformBackdropEnabled
  }
  HazeFeatureFlags.isPlatformBackdropEnabled = scenario.usesBackdrop
  DisposableEffect(Unit) {
    onDispose {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousPlatformBackdropEnabled
    }
  }

  val lifecycle = LocalLifecycleOwner.current.lifecycle
  LaunchedEffect(state.phase, scenario, lifecycle) {
    if (state.phase == BlurProfilingPhase.Settling) {
      // Navigation exposes incoming semantics before it finishes handing off pointer input.
      lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
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
  val profilingDrawProgress = if (scenario.updatesSource && !scenario.usesBackdrop) {
    null
  } else {
    { state.progress }
  }

  Box(modifier = modifier.fillMaxSize()) {
    if (scenario.noiseTintProperty != null) {
      BlurNoiseTintProfilingScene(state, scenario)
    } else {
      ScaffoldSample(
        navController = navController,
        effect = SampleEffect.Blur,
        mode = scenario.mode,
        performanceMode = scenario.performanceMode,
        sourceOffset = sourceOffset,
        sourceDrawProgress = if (scenario.updatesSource) ({ state.progress }) else null,
        profilingDrawProgress = profilingDrawProgress,
        useBackdrop = scenario.usesBackdrop,
      )
    }
    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
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
