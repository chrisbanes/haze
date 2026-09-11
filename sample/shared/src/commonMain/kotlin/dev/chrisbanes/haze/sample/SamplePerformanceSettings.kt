// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazePerformanceMode
import kotlin.math.roundToInt

internal val LocalSampleChromeController = compositionLocalOf { SampleChromeController() }

@Stable
internal class SampleChromeController {
  var isVisible by mutableStateOf(true)
    private set

  fun updateVisibility(isVisible: Boolean) {
    this.isVisible = isVisible
  }
}

@Composable
internal fun SampleChromeVisibilityEffect(visible: Boolean) {
  val controller = LocalSampleChromeController.current
  DisposableEffect(visible, controller) {
    controller.updateVisibility(visible)
    onDispose { controller.updateVisibility(true) }
  }
}

private val performanceModes = listOf(
  HazePerformanceMode.Adaptive,
  HazePerformanceMode.Performance,
  HazePerformanceMode.Balanced,
  HazePerformanceMode.Quality,
  null,
)

internal val HazePerformanceMode?.sampleLabel: String
  get() = when (this) {
    HazePerformanceMode.Adaptive -> "Adaptive"
    HazePerformanceMode.Performance -> "Performance"
    HazePerformanceMode.Balanced -> "Balanced"
    HazePerformanceMode.Quality -> "Quality"
    else -> "Custom"
  }

@Stable
internal class SamplePerformanceSettingsState {
  var selectedMode by mutableStateOf<HazePerformanceMode?>(HazePerformanceMode.Adaptive)
    private set
  var customQuality by mutableStateOf(0.5f)
    private set
  var metricsEnabled by mutableStateOf(false)
    private set

  val performanceMode: HazePerformanceMode
    get() = selectedMode ?: HazePerformanceMode.Fixed(customQuality)

  fun selectMode(mode: HazePerformanceMode?) {
    selectedMode = mode
  }

  fun updateCustomQuality(value: Float) {
    customQuality = value.coerceIn(0f, 1f)
    selectedMode = null
  }

  fun updateMetricsEnabled(enabled: Boolean) {
    metricsEnabled = enabled
  }
}

@Composable
internal fun rememberSamplePerformanceSettingsState(): SamplePerformanceSettingsState = remember {
  SamplePerformanceSettingsState()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun SamplePerformanceSettingsSheet(
  state: SamplePerformanceSettingsState,
  onDismissRequest: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismissRequest) {
    Column(
      modifier = Modifier
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("Performance controls", style = MaterialTheme.typography.titleLarge)
      Text(
        "Quality fraction selects built-in rendering fidelity; it is not a GPU-work or " +
          "resolution percentage.",
        style = MaterialTheme.typography.bodyMedium,
      )
      performanceModes.forEach { mode ->
        ListItem(
          headlineContent = { Text(mode.sampleLabel) },
          leadingContent = {
            RadioButton(
              selected = state.selectedMode == mode,
              onClick = null,
            )
          },
          modifier = Modifier
            .selectable(
              selected = state.selectedMode == mode,
              role = Role.RadioButton,
              onClick = { state.selectMode(mode) },
            )
            .testTag("sample_performance_${mode.sampleLabel.lowercase()}")
            .semantics { contentDescription = "${mode.sampleLabel} performance mode" },
        )
      }
      Text("Custom quality: ${state.customQuality.formatFraction()}")
      Slider(
        value = state.customQuality,
        onValueChange = state::updateCustomQuality,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("sample_performance_custom_slider")
          .semantics { contentDescription = "Custom performance quality fraction" },
      )
      ListItem(
        headlineContent = { Text("Live frame metrics") },
        supportingContent = { Text("Diagnostic readings affected by this observer.") },
        trailingContent = {
          Switch(
            checked = state.metricsEnabled,
            onCheckedChange = state::updateMetricsEnabled,
            modifier = Modifier.testTag("sample_metrics_enabled"),
          )
        },
      )
      Button(
        onClick = onDismissRequest,
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 16.dp)
          .testTag("sample_performance_done"),
      ) { Text("Done") }
    }
  }
}

internal fun Float.formatFraction(): String = "${(this * 100).roundToInt()}%"
