// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

private val ProfilingSurfaceSize = DpSize(280.dp, 180.dp)
private val ProfilingOptics = GlassOptics.Absolute(
  refractionStrength = 0.7f,
  refractionHeight = 0.25f,
  refractionScale = 15f,
  depth = 0.5f,
  blurRadius = 14.dp,
)

@Composable
internal fun GlassProfilingSampleContent(
  state: GlassProfilingState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scenario = state.scenario
  if (scenario == null) {
    GlassProfilingScenarioPicker(
      onScenarioSelected = state::select,
      onBack = onBack,
      modifier = modifier,
    )
  } else {
    GlassProfilingScene(
      state = state,
      scenario = scenario,
      onBack = onBack,
      modifier = modifier,
    )
  }
}

@Composable
private fun GlassProfilingScenarioPicker(
  onScenarioSelected: (GlassProfilingScenario) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Button(onClick = onBack) { Text("Back") }
    GlassProfilingScenario.entries.forEach { scenario ->
      Button(
        onClick = { onScenarioSelected(scenario) },
        modifier = Modifier.testTag("glass_profiling_select_${scenario.id}"),
      ) {
        Text(scenario.id)
      }
    }
  }
}

@Composable
private fun GlassProfilingScene(
  state: GlassProfilingState,
  scenario: GlassProfilingScenario,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val interactionSource = remember { MutableInteractionSource() }
  val density = LocalDensity.current
  val surfaceSizePx = with(density) {
    Size(ProfilingSurfaceSize.width.toPx(), ProfilingSurfaceSize.height.toPx())
  }
  val effect = remember(scenario, interactionSource) {
    GlassVisualEffect().apply {
      optics = ProfilingOptics
      shape = RoundedCornerShape(32.dp)
      this.interactionSource = interactionSource
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
      pressed {
        animate(
          toSpec = GlassDefaults.pressAnimationSpec,
          fromSpec = GlassDefaults.releaseAnimationSpec,
        ) {
          lightingIntensity(1f)
          refractionMultiplier(1.08f)
          scale(0.98f)
        }
      }
    }
  }

  LaunchedEffect(state.phase, scenario, effect, interactionSource, surfaceSizePx) {
    if (state.phase != GlassProfilingPhase.Running) return@LaunchedEffect
    when (scenario) {
      GlassProfilingScenario.EffectAttach -> {
        repeat(8) { androidx.compose.runtime.withFrameNanos {} }
      }
      GlassProfilingScenario.InteractionUpdate -> {
        val press = PressInteraction.Press(
          Offset(surfaceSizePx.width * 0.5f, surfaceSizePx.height * 0.5f),
        )
        state.updateProgress(0.25f)
        interactionSource.emit(press)
        delay(GLASS_PROFILING_DURATION_MILLIS.toLong() / 2)
        state.updateProgress(0.75f)
        interactionSource.emit(PressInteraction.Release(press))
        delay(GLASS_PROFILING_DURATION_MILLIS.toLong() / 2)
      }
      else -> {
        Animatable(0f).animateTo(
          targetValue = 1f,
          animationSpec = tween(
            durationMillis = GLASS_PROFILING_DURATION_MILLIS,
            easing = LinearEasing,
          ),
        ) {
          state.updateProgress(value)
          effect.applyProfilingFrame(
            scenario = scenario,
            frame = glassProfilingFrame(scenario, value),
            surfaceSize = surfaceSizePx,
          )
        }
      }
    }
    state.complete()
  }

  val attachGlass =
    scenario != GlassProfilingScenario.EffectAttach ||
      state.phase != GlassProfilingPhase.Ready

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF10131A))
      .testTag("glass_profiling_selected_${scenario.id}"),
  ) {
    Canvas(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState),
    ) {
      val frame = glassProfilingFrame(scenario, state.progress)
      val translatedX = size.width * frame.sourceOffset
      drawRect(Color(0xFF172554))
      repeat(12) { index ->
        val x = translatedX + size.width * index / 11f
        drawLine(
          color = if (index % 2 == 0) Color.Cyan else Color.Magenta,
          start = Offset(x, 0f),
          end = Offset(size.width - x, size.height),
          strokeWidth = 4f,
        )
      }
    }

    if (scenario.glassEnabled && attachGlass) {
      Box(
        Modifier
          .align(Alignment.Center)
          .size(ProfilingSurfaceSize)
          .hazeEffect(hazeState) {
            inputScale = HazeInputScale.Auto
            visualEffect = effect
          }
          .testTag("glass_profiling_surface"),
      )
    }

    if (scenario == GlassProfilingScenario.RetainedReuse) {
      Canvas(Modifier.fillMaxSize()) {
        val frame = glassProfilingFrame(scenario, state.progress)
        drawCircle(
          color = Color.Yellow,
          radius = 6.dp.toPx(),
          center = Offset(
            x = size.width * (0.5f + frame.markerOffset),
            y = 16.dp.toPx(),
          ),
        )
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = scenario.id,
        modifier = Modifier.testTag("glass_profiling_phase_${state.phase.id}"),
      )
      if (state.phase == GlassProfilingPhase.Ready) {
        Button(
          onClick = { state.start() },
          modifier = Modifier.testTag("glass_profiling_start"),
        ) {
          Text("Start")
        }
      }
    }
  }
}

private fun GlassVisualEffect.applyProfilingFrame(
  scenario: GlassProfilingScenario,
  frame: GlassProfilingFrame,
  surfaceSize: Size,
) {
  when (scenario) {
    GlassProfilingScenario.OpticalUpdate -> {
      lightPosition = Offset(
        x = surfaceSize.width * frame.lightPosition.x,
        y = surfaceSize.height * frame.lightPosition.y,
      )
    }
    GlassProfilingScenario.DepthUpdate,
    GlassProfilingScenario.BlurUpdate,
    -> {
      optics = ProfilingOptics.copy(
        depth = frame.depth,
        blurRadius = frame.blurRadius,
      )
    }
    GlassProfilingScenario.EffectAttach,
    GlassProfilingScenario.RetainedReuse,
    GlassProfilingScenario.InteractionUpdate,
    GlassProfilingScenario.SourceUpdate,
    GlassProfilingScenario.SourceUpdateNoGlass,
    -> Unit
  }
}
