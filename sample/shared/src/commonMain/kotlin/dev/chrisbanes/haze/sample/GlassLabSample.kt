// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
  specimenInteractionSource: MutableInteractionSource? = null,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val defaultSpecimenInteractionSource = remember { MutableInteractionSource() }
  val resolvedSpecimenInteractionSource = specimenInteractionSource ?: defaultSpecimenInteractionSource
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val landscape = maxWidth > maxHeight
    if (landscape) {
      Row(Modifier.fillMaxSize()) {
        LabSpecimen(
          hazeState = hazeState,
          state = state,
          recordingMode = recordingMode,
          onRevealChrome = { onRecordingModeChanged(false) },
          interactionSource = resolvedSpecimenInteractionSource,
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
          interactionSource = resolvedSpecimenInteractionSource,
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
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  var dragOffset by remember { mutableStateOf(Offset.Zero) }
  var viewportSize by remember { mutableStateOf(IntSize.Zero) }
  val returnJob = remember { mutableStateOf<Job?>(null) }
  var pressInteraction by remember { mutableStateOf<PressInteraction.Press?>(null) }
  val returnToCenter = {
    returnJob.value?.cancel()
    returnJob.value = scope.launch {
      Animatable(dragOffset, Offset.VectorConverter).animateTo(
        targetValue = Offset.Zero,
        animationSpec = spring(
          dampingRatio = 0.78f,
          stiffness = Spring.StiffnessMediumLow,
        ),
      ) {
        dragOffset = value.coerceCenterTo(viewportSize)
      }
    }
  }
  LaunchedEffect(viewportSize) {
    if (returnJob.value != null) returnToCenter()
  }
  val finishDrag = { cancelled: Boolean ->
    pressInteraction?.let { press ->
      interactionSource.tryEmit(
        if (cancelled) PressInteraction.Cancel(press) else PressInteraction.Release(press),
      )
    }
    pressInteraction = null
    returnToCenter()
  }
  Box(
    modifier = modifier
      .onSizeChanged {
        viewportSize = it
        dragOffset = dragOffset.coerceCenterTo(it)
      }
      .testTag("glass_lab_specimen_viewport"),
  ) {
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
        .offset {
          IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
        }
        .fillMaxWidth(0.85f)
        .sizeIn(maxWidth = 360.dp, maxHeight = 240.dp)
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val changes = awaitPointerEvent(PointerEventPass.Initial).changes
              if (changes.any { it.changedToDown() }) returnJob.value?.cancel()
              if (changes.any { it.changedToUp() }) returnToCenter()
            }
          }
        }
        .pointerInput(interactionSource) {
          try {
            detectDragGestures(
              onDragStart = { position ->
                returnJob.value?.cancel()
                pressInteraction = PressInteraction.Press(position).also(interactionSource::tryEmit)
              },
              onDragEnd = { finishDrag(false) },
              onDragCancel = { finishDrag(true) },
            ) { change, amount ->
              change.consume()
              dragOffset = (dragOffset + amount).coerceCenterTo(viewportSize)
            }
          } finally {
            if (pressInteraction != null) finishDrag(true)
          }
        }
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

private fun Offset.coerceCenterTo(viewportSize: IntSize): Offset = Offset(
  x = x.coerceIn(-viewportSize.width / 2f, viewportSize.width / 2f),
  y = y.coerceIn(-viewportSize.height / 2f, viewportSize.height / 2f),
)

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
  val fixed = (values.optics as? GlassOptics.Fixed) ?: GlassOptics.Fixed()
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Optics", style = MaterialTheme.typography.titleMedium)
    LabSlider("Refraction", fixed.refractionStrength, 0f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = fixed.copy(refractionStrength = value)) })
    }
    LabSlider("Fold", fixed.refractionFoldStrength, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = fixed.copy(refractionFoldStrength = value)) },
      )
    }
    LabSlider("Depth", fixed.depth, 0f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = fixed.copy(depth = value)) })
    }
    LabSlider("Blur", fixed.blurRadius.value, 0f..32f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = fixed.copy(blurRadius = value.dp)) })
    }
    Text("Lighting", style = MaterialTheme.typography.titleMedium)
    LabSlider("Specular", values.specularIntensity, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = fixed, specularIntensity = value) },
      )
    }
    LabSlider("Ambient", values.ambientResponse, 0f..1f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = fixed, ambientResponse = value) },
      )
    }
    Text("Colour", style = MaterialTheme.typography.titleMedium)
    LabSlider("Contrast", values.contrast, -1f..1f) { value ->
      onStateChanged(state.editStyle { it.copy(optics = fixed, contrast = value) })
    }
    LabSlider("Chroma", values.chromaMultiplier, 0f..2f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = fixed, chromaMultiplier = value) },
      )
    }
    Text("Rendering", style = MaterialTheme.typography.titleMedium)
    LabSlider("Edge softness", values.edgeSoftness.value, 0f..24f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = fixed, edgeSoftness = value.dp) },
      )
    }
    LabSlider("Chromatic", values.chromaticAberrationStrength, 0f..0.4f) { value ->
      onStateChanged(
        state.editStyle { it.copy(optics = fixed, chromaticAberrationStrength = value) },
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
