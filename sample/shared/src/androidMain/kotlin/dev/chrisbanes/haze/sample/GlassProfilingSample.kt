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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassMergePrototype
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay

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
    modifier = modifier
      .fillMaxSize()
      .testTag("glass_profiling_picker")
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
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
  val previousPlatformBackdropEnabled = remember {
    HazeFeatureFlags.isPlatformBackdropEnabled
  }
  HazeFeatureFlags.isPlatformBackdropEnabled = scenario.usesBackdrop
  DisposableEffect(Unit) {
    onDispose {
      HazeFeatureFlags.isPlatformBackdropEnabled = previousPlatformBackdropEnabled
    }
  }

  val hazeState = rememberHazeState()
  val interactionSource = remember { MutableInteractionSource() }
  val density = LocalDensity.current
  val surfaceSize = glassProfilingScenarioSize(scenario) { state.progress }
  val surfaceSizePx = with(density) {
    Size(surfaceSize.width.toPx(), surfaceSize.height.toPx())
  }
  val effectSurfaceSizePx = profilingEffectSize(surfaceSizePx, scenario.effectCount)
  val styleSurfaceSizePx = if (scenario.resizesLayoutBounds) {
    with(density) {
      profilingEffectSize(
        Size(ProfilingSurfaceSize.width.toPx(), ProfilingSurfaceSize.height.toPx()),
        scenario.effectCount,
      )
    }
  } else {
    effectSurfaceSizePx
  }
  val animationSizeKey = if (scenario.resizesLayoutBounds) null else surfaceSizePx
  val backgroundColor = MaterialTheme.colorScheme.surface
  var effectFrame by remember(scenario) {
    mutableStateOf(glassProfilingFrame(scenario, progress = 0f))
  }
  val styleFrame = if (profilingStyleUsesFrame(scenario)) effectFrame else null
  val styles = remember(scenario, styleFrame, styleSurfaceSizePx, backgroundColor) {
    List(scenario.effectCount) {
      profilingGlassStyle(
        scenario,
        styleFrame ?: glassProfilingFrame(scenario, progress = 0f),
        backgroundColor,
        cornerRadius = { effectFrame.cornerRadius },
      )
    }
  }

  LaunchedEffect(state.phase, scenario, interactionSource, animationSizeKey) {
    if (state.phase == GlassProfilingPhase.Settling) {
      repeat(GLASS_PROFILING_SETTLING_FRAMES) {
        androidx.compose.runtime.withFrameNanos {}
      }
      state.markReady()
      return@LaunchedEffect
    }
    if (state.phase != GlassProfilingPhase.Running) return@LaunchedEffect
    when (scenario) {
      GlassProfilingScenario.EffectAttach,
      GlassProfilingScenario.EffectAttach3,
      GlassProfilingScenario.EffectAttach9,
      GlassProfilingScenario.EffectReattach,
      -> {
        val startNanos = androidx.compose.runtime.withFrameNanos { it }
        repeat(8) { androidx.compose.runtime.withFrameNanos {} }
        val elapsedMillis = (
          androidx.compose.runtime.withFrameNanos { it } - startNanos
          ) / 1_000_000
        delay((GLASS_PROFILING_DURATION_MILLIS - elapsedMillis).coerceAtLeast(0))
      }
      GlassProfilingScenario.InteractionUpdate,
      GlassProfilingScenario.InteractionUpdate9,
      -> {
        val press = PressInteraction.Press(
          Offset(effectSurfaceSizePx.width * 0.5f, effectSurfaceSizePx.height * 0.5f),
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
          effectFrame = glassProfilingFrame(scenario, value)
        }
      }
    }
    state.complete()
  }

  val attachGlass = shouldAttachProfilingGlass(scenario, state.phase)

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFF10131A)),
  ) {
    Canvas(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .testTag("glass_profiling_source"),
    ) {
      val frame = glassProfilingFrame(
        scenario = scenario,
        progress = glassProfilingSourceProgress(scenario) { state.progress },
      )
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
      val mergeLayer = scenario.mergeLayer
      if (mergeLayer != null) {
        GlassMergeProfilingSurface(
          hazeState = hazeState,
          layer = mergeLayer,
          style = styles.single(),
          performanceMode = scenario.performanceMode,
          modifier = Modifier
            .align(Alignment.Center)
            .testTag("glass_profiling_surface"),
        )
      } else {
        GlassProfilingEffectGrid(
          hazeState = hazeState,
          usesBackdrop = scenario.usesBackdrop,
          styles = styles,
          performanceMode = scenario.performanceMode,
          interactionSource = interactionSource,
          drawProgress = if (scenario.steadyDraw) {
            { state.progress }
          } else {
            null
          },
          modifier = Modifier
            .align(Alignment.Center)
            .size(surfaceSize)
            .testTag("glass_profiling_surface"),
        )
      }
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

    if (scenario.steadyDraw) {
      Canvas(Modifier.fillMaxSize()) {
        val progress = state.progress
        drawCircle(
          color = Color.Transparent,
          radius = 1f,
          center = Offset(progress * size.width, 0f),
        )
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.TopStart)
        .padding(16.dp)
        .testTag("glass_profiling_selected_${scenario.id}"),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = scenario.id,
        modifier = Modifier.testTag("glass_profiling_phase_${state.phase.id}"),
      )
      if (scenario.mergeLayer != null) {
        Text(
          text = glassProfilingMergeAreas(density),
          modifier = Modifier.testTag("glass_profiling_merge_areas"),
        )
      }
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

@Composable
private fun GlassProfilingEffectGrid(
  hazeState: dev.chrisbanes.haze.HazeState,
  usesBackdrop: Boolean,
  styles: List<GlassStyle>,
  performanceMode: HazePerformanceMode,
  interactionSource: MutableInteractionSource,
  drawProgress: (() -> Float)?,
  modifier: Modifier = Modifier,
) {
  val rowCount = if (styles.size <= 3) 1 else 3
  val columnCount = styles.size / rowCount
  Column(modifier) {
    repeat(rowCount) { rowIndex ->
      Row(Modifier.fillMaxWidth().weight(1f)) {
        repeat(columnCount) { columnIndex ->
          val effectIndex = rowIndex * columnCount + columnIndex
          Box(
            Modifier
              .fillMaxHeight()
              .weight(1f)
              .drawWithContent {
                drawProgress?.invoke()
                drawContent()
              }
              .hazeGlass(
                input = if (usesBackdrop) {
                  HazeInput.Backdrop(hazeState)
                } else {
                  HazeInput.Sources(hazeState)
                },
                style = styles[effectIndex],
                performanceMode = performanceMode,
                interactionSource = interactionSource,
                interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
              )
              .testTag("glass_profiling_surface_$effectIndex"),
          )
        }
      }
    }
  }
}

/**
 * The merge prototype's two pills (#1438). Both layer modes fill the same container-sized frame and
 * place the pills at the same offsets, so only the layer topology differs. No interaction source is
 * passed: the prototype does not wire interaction stages for merging.
 */
@Composable
private fun GlassMergeProfilingSurface(
  hazeState: dev.chrisbanes.haze.HazeState,
  layer: GlassProfilingMergeLayer,
  style: GlassStyle,
  performanceMode: HazePerformanceMode,
  modifier: Modifier = Modifier,
) {
  Box(modifier.size(ProfilingMergeContainerSize)) {
    when (layer) {
      GlassProfilingMergeLayer.Merged -> Box(
        Modifier
          .fillMaxSize()
          .hazeGlass(HazeInput.Sources(hazeState), style, performanceMode)
          .testTag("glass_profiling_surface_0"),
      )
      GlassProfilingMergeLayer.Independent -> listOf(ProfilingMergePillA, ProfilingMergePillB)
        .forEachIndexed { index, pill ->
          Box(
            Modifier
              .offset(pill.left, pill.top)
              .size(ProfilingMergePillSize)
              .hazeGlass(HazeInput.Sources(hazeState), style, performanceMode)
              .testTag("glass_profiling_surface_$index"),
          )
        }
    }
  }
}

internal fun profilingGlassStyle(
  scenario: GlassProfilingScenario,
  frame: GlassProfilingFrame,
  backgroundColor: Color,
  cornerRadius: () -> Dp?,
): GlassStyle = GlassStyle.regular.then {
  backgroundColor(backgroundColor)
  scenario.opticsOverride?.let(::optics)
  if (scenario.fullChroma) {
    chromaticAberrationMode(ChromaticAberrationMode.Full)
    chromaticAberrationStrength(0.3f)
  }
  if (!scenario.rimEnabled) specularIntensity(0f)
  when (scenario) {
    GlassProfilingScenario.MergePrototypeMerged -> mergePrototype(
      GlassMergePrototype(
        rectA = ProfilingMergePillA,
        shapeA = RoundedCornerShape(ProfilingMergePillRadius),
        rectB = ProfilingMergePillB,
        shapeB = RoundedCornerShape(ProfilingMergePillRadius),
        spacing = ProfilingMergeSpacing,
      ),
    )
    GlassProfilingScenario.MergePrototypeIndependent -> {
      shape(RoundedCornerShape(ProfilingMergePillRadius))
    }
    GlassProfilingScenario.OpticalUpdate -> {
      lightPosition(
        BiasAlignment(
          horizontalBias = frame.lightPosition.x * 2f - 1f,
          verticalBias = frame.lightPosition.y * 2f - 1f,
        ),
      )
    }
    GlassProfilingScenario.DepthUpdate,
    GlassProfilingScenario.BlurUpdate,
    -> {
      optics(
        (scenario.opticsOverride ?: GlassOptics()).copy(
          depth = OpticalSizeValue.Fixed(frame.depth),
          blurRadius = OpticalSizeValue.Fixed(frame.blurRadius),
        ),
      )
    }
    GlassProfilingScenario.ShapeUpdateBalanced,
    GlassProfilingScenario.ShapeUpdateBalanced9,
    -> {
      // The style is remembered once and passed unchanged each frame; the node re-evaluates this
      // read of the animated radius state instead of the style being rebuilt (ADR-0011).
      shape(RoundedCornerShape(checkNotNull(cornerRadius())))
    }
    GlassProfilingScenario.EffectAttach,
    GlassProfilingScenario.EffectAttach3,
    GlassProfilingScenario.EffectAttach9,
    GlassProfilingScenario.EffectReattach,
    GlassProfilingScenario.StableQuality,
    GlassProfilingScenario.ResizeQuality,
    GlassProfilingScenario.ResizeQuality9,
    GlassProfilingScenario.BackdropStableQuality,
    GlassProfilingScenario.StableBalanced,
    GlassProfilingScenario.StablePerformance,
    GlassProfilingScenario.StableFixed0,
    GlassProfilingScenario.StableFixed25,
    GlassProfilingScenario.StableFixed33,
    GlassProfilingScenario.StableFixed50,
    GlassProfilingScenario.StableFixed75,
    GlassProfilingScenario.StableFixed100,
    GlassProfilingScenario.SteadyFull3,
    GlassProfilingScenario.SteadyFull9,
    GlassProfilingScenario.SteadyProgressive,
    GlassProfilingScenario.SteadyProgressive9,
    GlassProfilingScenario.SteadyFullChroma,
    GlassProfilingScenario.SteadyFullChroma9,
    GlassProfilingScenario.SteadyNoRim,
    GlassProfilingScenario.SteadyNoRim9,
    GlassProfilingScenario.SteadyNoRefraction,
    GlassProfilingScenario.SteadyNoRefraction9,
    GlassProfilingScenario.SteadyNoBlur,
    GlassProfilingScenario.SteadyNoBlur9,
    GlassProfilingScenario.SteadyDepth50,
    GlassProfilingScenario.SteadyPerformanceNine,
    GlassProfilingScenario.SteadyNoGlass,
    GlassProfilingScenario.RetainedReuse,
    GlassProfilingScenario.InteractionUpdate,
    GlassProfilingScenario.InteractionUpdate9,
    GlassProfilingScenario.SourceUpdateQuality,
    GlassProfilingScenario.BackdropSourceUpdateQuality,
    GlassProfilingScenario.SourceUpdateBalanced,
    GlassProfilingScenario.SourceUpdatePerformance,
    GlassProfilingScenario.SourceUpdateFixed0,
    GlassProfilingScenario.SourceUpdateFixed25,
    GlassProfilingScenario.SourceUpdateFixed33,
    GlassProfilingScenario.SourceUpdateFixed50,
    GlassProfilingScenario.SourceUpdateFixed75,
    GlassProfilingScenario.SourceUpdateFixed100,
    GlassProfilingScenario.SourceUpdate9,
    GlassProfilingScenario.BackdropSourceUpdate9,
    GlassProfilingScenario.SourceUpdateNoGlass,
    -> Unit
  }
}.let { style ->
  // The merge prototype wires no interaction stages, so its styles carry no pressed state.
  if (scenario.mergeLayer != null) {
    style
  } else {
    style.then {
      pressed {
        animate(
          toSpec = DefaultGlassPressAnimationSpec,
          fromSpec = DefaultGlassReleaseAnimationSpec,
        ) {
          lightingIntensity(1f)
          refractionMultiplier(1.08f)
          scale(0.98f)
        }
      }
    }
  }
}

internal fun profilingStyleUsesFrame(scenario: GlassProfilingScenario): Boolean = when (scenario) {
  GlassProfilingScenario.OpticalUpdate,
  GlassProfilingScenario.DepthUpdate,
  GlassProfilingScenario.BlurUpdate,
  -> true
  else -> false
}

private fun profilingEffectSize(surfaceSize: Size, effectCount: Int): Size {
  val rowCount = if (effectCount <= 3) 1 else 3
  val columnCount = effectCount / rowCount
  return Size(
    width = surfaceSize.width / columnCount,
    height = surfaceSize.height / rowCount,
  )
}
