// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.rememberHazeState

@Composable
public fun GlassLabSample(navController: NavHostController) {
  var state by remember { mutableStateOf(GlassLabState()) }
  var recordingMode by rememberSaveable { mutableStateOf(false) }

  GlassLabSampleContent(
    state = state,
    recordingMode = recordingMode,
    onStateChanged = { state = it },
    onRecordingModeChanged = { recordingMode = it },
    onBack = navController::navigateUp,
  )
}

@Composable
internal fun GlassLabSampleContent(
  state: GlassLabState,
  recordingMode: Boolean,
  onStateChanged: (GlassLabState) -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    if (landscape) {
      Row(Modifier.fillMaxSize()) {
        LabSpecimen(
          hazeState = hazeState,
          state = state,
          recordingMode = recordingMode,
          onRevealChrome = { onRecordingModeChanged(false) },
          modifier = Modifier.weight(0.6f).fillMaxHeight(),
        )
        LabControls(
          state = state,
          recordingMode = recordingMode,
          onStateChanged = onStateChanged,
          modifier = Modifier.weight(0.4f).fillMaxHeight(),
        )
      }
    } else {
      Column(Modifier.fillMaxSize()) {
        LabSpecimen(
          hazeState = hazeState,
          state = state,
          recordingMode = recordingMode,
          onRevealChrome = { onRecordingModeChanged(false) },
          modifier = Modifier.weight(0.52f).fillMaxWidth(),
        )
        LabControls(
          state = state,
          recordingMode = recordingMode,
          onStateChanged = onStateChanged,
          modifier = Modifier.weight(0.48f).fillMaxWidth(),
        )
      }
    }

    AnimatedVisibility(
      visible = !recordingMode,
      modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
    ) {
      DemoChrome(
        hazeState = hazeState,
        onBack = onBack,
        onEnterRecordingMode = { onRecordingModeChanged(true) },
        onReset = { onStateChanged(state.reset()) },
      )
    }
  }
}

@Composable
public fun GlassLabScreenshotContent(
  preset: GlassLabPresetId,
  backdrop: GlassGalleryBackdropId,
  modifier: Modifier = Modifier,
) {
  GlassLabSampleContent(
    state = GlassLabState(preset = preset, backdrop = backdrop, style = glassLabPresetStyle(preset)),
    recordingMode = true,
    onStateChanged = {},
    onRecordingModeChanged = {},
    onBack = {},
    modifier = modifier,
  )
}

@Composable
private fun LabSpecimen(
  hazeState: HazeState,
  state: GlassLabState,
  recordingMode: Boolean,
  onRevealChrome: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier) {
    GalleryBackdrop(
      hazeState = hazeState,
      artworkIndex = 0,
      backdrop = state.backdrop,
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(recordingMode) {
          detectTapGestures {
            if (recordingMode) onRevealChrome()
          }
        },
    )
    GlassSurface(
      hazeState = hazeState,
      style = state.style,
      shape = RoundedCornerShape(32.dp),
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth(0.85f)
        .sizeIn(maxWidth = 360.dp, maxHeight = 240.dp)
        .heightIn(max = 240.dp)
        .pointerInput(Unit) { detectTapGestures {} }
        .semantics { contentDescription = "Glass specimen" },
    ) {
      Text(
        text = state.preset.name,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun LabControls(
  state: GlassLabState,
  recordingMode: Boolean,
  onStateChanged: (GlassLabState) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Material", style = MaterialTheme.typography.titleMedium)
    LabPresetSelector(state = state, onStateChanged = onStateChanged)
    Text("Backdrop", style = MaterialTheme.typography.titleMedium)
    LabBackdropSelector(state = state, onStateChanged = onStateChanged)
    TextButton(onClick = { onStateChanged(state.copy(advancedExpanded = !state.advancedExpanded)) }) {
      Text("Advanced")
    }
    AnimatedVisibility(visible = !recordingMode) {
      Text("Choose a material preset", style = MaterialTheme.typography.bodyMedium)
    }
    AnimatedVisibility(visible = state.advancedExpanded) {
      LabAdvancedControls(state = state, onStateChanged = onStateChanged)
    }
  }
}

@Composable
private fun LabPresetSelector(state: GlassLabState, onStateChanged: (GlassLabState) -> Unit) {
  LabSegmentedButtonRow(selectedIndex = SelectableGlassLabPresets.indexOf(state.preset)) {
    SelectableGlassLabPresets.forEachIndexed { index, preset ->
      SegmentedButton(
        selected = state.preset == preset,
        onClick = { onStateChanged(state.selectPreset(preset)) },
        shape = SegmentedButtonDefaults.itemShape(index, SelectableGlassLabPresets.size),
        modifier = Modifier.widthIn(min = LabSegmentedButtonMinWidth),
      ) { Text(preset.name, maxLines = 1) }
    }
  }
}

@Composable
private fun LabBackdropSelector(state: GlassLabState, onStateChanged: (GlassLabState) -> Unit) {
  LabSegmentedButtonRow(selectedIndex = state.backdrop.ordinal) {
    GlassGalleryBackdropId.entries.forEachIndexed { index, backdrop ->
      SegmentedButton(
        selected = state.backdrop == backdrop,
        onClick = { onStateChanged(state.copy(backdrop = backdrop)) },
        shape = SegmentedButtonDefaults.itemShape(index, GlassGalleryBackdropId.entries.size),
        modifier = Modifier.widthIn(min = LabSegmentedButtonMinWidth),
      ) { Text(backdrop.name, maxLines = 1) }
    }
  }
}

private val LabSegmentedButtonMinWidth = 80.dp
private val LabSegmentedButtonRowMinWidth = 400.dp

@Composable
private fun LabSegmentedButtonRow(
  selectedIndex: Int,
  content: @Composable SingleChoiceSegmentedButtonRowScope.() -> Unit,
) {
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val rowWidth = maxWidth.coerceAtLeast(LabSegmentedButtonRowMinWidth)
    val scrollState = rememberScrollState()
    val buttonWidthPx = with(LocalDensity.current) { LabSegmentedButtonMinWidth.roundToPx() }
    val viewportWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }

    LaunchedEffect(selectedIndex, scrollState.maxValue) {
      if (selectedIndex >= 0) {
        val selectedStart = selectedIndex * buttonWidthPx
        val selectedEnd = selectedStart + buttonWidthPx
        scrollState.scrollTo(
          when {
            selectedStart < scrollState.value -> selectedStart
            selectedEnd > scrollState.value + viewportWidthPx -> selectedEnd - viewportWidthPx
            else -> scrollState.value
          },
        )
      }
    }

    Box(Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier.width(rowWidth),
        content = content,
      )
    }
  }
}

@Composable
private fun LabAdvancedControls(state: GlassLabState, onStateChanged: (GlassLabState) -> Unit) {
  val default = GlassDefaults.style
  val absolute = (state.style.optics as? GlassOptics.Absolute) ?: GlassOptics.Absolute()
  val lighting = state.style.lighting.withDefaults(default.lighting)
  val color = state.style.color.withDefaults(default.color)
  val rendering = state.style.rendering.withDefaults(default.rendering)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Optics", style = MaterialTheme.typography.titleMedium)
    LabSlider("Refraction", absolute.refractionStrength, 0f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = absolute.copy(refractionStrength = value)) })
    }
    LabSlider("Depth", absolute.depth, 0f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = absolute.copy(depth = value)) })
    }
    LabSlider("Blur", absolute.blurRadius.value, 0f..32f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = absolute.copy(blurRadius = value.dp)) })
    }
    Text("Lighting", style = MaterialTheme.typography.titleMedium)
    LabSlider("Specular", lighting.specularIntensity, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, lighting = lighting.copy(specularIntensity = value)) },
      )
    }
    LabSlider("Ambient", lighting.ambientResponse, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, lighting = lighting.copy(ambientResponse = value)) },
      )
    }
    Text("Colour", style = MaterialTheme.typography.titleMedium)
    LabSlider("Contrast", color.contrast, -1f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = absolute, color = color.copy(contrast = value)) })
    }
    LabSlider("Chroma", color.chromaMultiplier, 0f..2f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, color = color.copy(chromaMultiplier = value)) },
      )
    }
    Text("Rendering", style = MaterialTheme.typography.titleMedium)
    LabSlider("Edge softness", rendering.edgeSoftness.value, 0f..24f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, rendering = rendering.copy(edgeSoftness = value.dp)) },
      )
    }
    LabSlider("Chromatic", rendering.chromaticAberrationStrength, 0f..0.4f) { value ->
      onStateChanged(
        state.editStyle {
          it.copy(optics = absolute, rendering = rendering.copy(chromaticAberrationStrength = value))
        },
      )
    }
  }
}

@Composable
private fun LabSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
  Column {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Slider(value = value, onValueChange = onValueChange, valueRange = range)
  }
}

private fun dev.chrisbanes.haze.glass.GlassLighting.withDefaults(
  defaults: dev.chrisbanes.haze.glass.GlassLighting,
) = copy(
  specularIntensity = specularIntensity.orDefault(defaults.specularIntensity),
  specularExponent = specularExponent.orDefault(defaults.specularExponent),
  fresnelExponent = fresnelExponent.orDefault(defaults.fresnelExponent),
  ambientResponse = ambientResponse.orDefault(defaults.ambientResponse),
  lightPosition = if (lightPosition != androidx.compose.ui.geometry.Offset.Unspecified) {
    lightPosition
  } else {
    defaults.lightPosition
  },
)

private fun dev.chrisbanes.haze.glass.GlassColor.withDefaults(
  defaults: dev.chrisbanes.haze.glass.GlassColor,
) = copy(
  alpha = alpha.orDefault(defaults.alpha),
  contrast = contrast.orDefault(defaults.contrast),
  whitePoint = whitePoint.orDefault(defaults.whitePoint),
  chromaMultiplier = chromaMultiplier.orDefault(defaults.chromaMultiplier),
)

private fun dev.chrisbanes.haze.glass.GlassRendering.withDefaults(
  defaults: dev.chrisbanes.haze.glass.GlassRendering,
) = copy(
  edgeSoftness = edgeSoftness.orDefault(defaults.edgeSoftness),
  contentNormalBlend = contentNormalBlend.orDefault(defaults.contentNormalBlend),
  surfaceProfile = surfaceProfile ?: defaults.surfaceProfile,
  chromaticAberrationStrength = chromaticAberrationStrength.orDefault(defaults.chromaticAberrationStrength),
  chromaticAberrationMode = chromaticAberrationMode ?: defaults.chromaticAberrationMode,
)

private fun Float.orDefault(default: Float): Float = if (isNaN()) default else this

private fun Dp.orDefault(default: Dp): Dp = if (this != Dp.Unspecified) this else default
