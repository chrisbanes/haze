// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
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
    state = GlassLabState(preset = preset, backdrop = backdrop),
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
  val interactionSource = remember { MutableInteractionSource() }
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
      interactionSource = interactionSource,
      interactionStyle = state.interaction.style,
      modifier = Modifier
        .align(Alignment.Center)
        .fillMaxWidth(0.85f)
        .sizeIn(maxWidth = 360.dp, maxHeight = 240.dp)
        .pointerInput(Unit) { detectTapGestures {} }
        .focusable(interactionSource = interactionSource)
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
internal fun LabControls(
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
    AnimatedVisibility(visible = !recordingMode) {
      Text("Choose a material preset", style = MaterialTheme.typography.bodyMedium)
    }
    LabChipGroup(
      title = "Material",
      options = SelectableGlassLabPresets,
      selected = state.preset,
      onSelected = { onStateChanged(state.selectPreset(it)) },
    )
    LabChipGroup(
      title = "Backdrop",
      options = GlassGalleryBackdropId.entries,
      selected = state.backdrop,
      onSelected = { onStateChanged(state.copy(backdrop = it)) },
    )
    LabChipGroup(
      title = "Interaction",
      options = GlassLabInteractionMode.entries,
      selected = state.interaction,
      onSelected = { onStateChanged(state.copy(interaction = it)) },
    )
    TextButton(onClick = { onStateChanged(state.copy(advancedExpanded = !state.advancedExpanded)) }) {
      Text("Advanced")
    }
    AnimatedVisibility(visible = state.advancedExpanded) {
      LabAdvancedControls(state = state, onStateChanged = onStateChanged)
    }
  }
}

/**
 * A labelled group of single-choice chips. Chips size themselves to their label and wrap onto as
 * many lines as they need, so the group always fits the available width.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Enum<T>> LabChipGroup(
  title: String,
  options: List<T>,
  selected: T,
  onSelected: (T) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      for (option in options) {
        FilterChip(
          selected = option == selected,
          onClick = { onSelected(option) },
          label = { Text(option.name, maxLines = 1) },
        )
      }
    }
  }
}

@Composable
private fun LabAdvancedControls(state: GlassLabState, onStateChanged: (GlassLabState) -> Unit) {
  val values = state.styleValues
  val absolute = (values.optics as? GlassOptics.Absolute) ?: GlassOptics.Absolute()
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
    LabSlider("Specular", values.specularIntensity, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, specularIntensity = value) },
      )
    }
    LabSlider("Ambient", values.ambientResponse, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, ambientResponse = value) },
      )
    }
    Text("Colour", style = MaterialTheme.typography.titleMedium)
    LabSlider("Contrast", values.contrast, -1f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = absolute, contrast = value) })
    }
    LabSlider("Chroma", values.chromaMultiplier, 0f..2f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, chromaMultiplier = value) },
      )
    }
    Text("Rendering", style = MaterialTheme.typography.titleMedium)
    LabSlider("Edge softness", values.edgeSoftness.value, 0f..24f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, edgeSoftness = value.dp) },
      )
    }
    LabSlider("Chromatic", values.chromaticAberrationStrength, 0f..0.4f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = absolute, chromaticAberrationStrength = value) },
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
