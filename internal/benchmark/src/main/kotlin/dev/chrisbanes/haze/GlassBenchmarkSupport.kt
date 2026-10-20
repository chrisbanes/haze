// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.Metric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

internal const val TARGET_PACKAGE = "dev.chrisbanes.haze.sample.android"
internal const val BENCHMARK_ITERATIONS = 8
internal const val GLASS_RUNTIME_DRAW_SECTION = "HazeGlass.runtimeDraw"
internal const val GLASS_CREATE_RENDER_EFFECT_SECTION = "HazeGlass.createRenderEffect"
internal const val GLASS_PREPARE_EFFECTS_SECTION = "HazeGlass.prepareEffects"
internal const val GLASS_PREPARE_LAYERS_SECTION = "HazeGlass.prepareLayers"
internal const val HAZE_BACKDROP_DRAW_SECTION = "HazeBackdrop.draw"
internal const val HAZE_SOURCE_RECORD_SECTION = "HazeSource.record"
internal const val HWUI_GRAPHICS_LAYER_DRAW_SECTION = "drawLayer [graphicsLayer]%"

internal fun requireGlassBenchmarkDevice() {
  assumeTrue(
    "Glass profiling requires API 33 or newer",
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
  )
  if (!isBenchmarkDryRun()) {
    assumeFalse(
      "Glass profiling requires a physical device",
      isProbablyEmulator(),
    )
  }
}

internal fun requireBackdropBenchmarkDevice() {
  requireGlassBenchmarkDevice()
  assumeTrue(
    "Backdrop profiling requires Android 37.2",
    isBackdropSdkSupported(),
  )
}

internal fun isBackdropSdkSupported(): Boolean {
  val fullSdkInt = if (Build.VERSION.SDK_INT < 36) {
    Build.VERSION.SDK_INT * 100_000
  } else {
    Build.VERSION.SDK_INT_FULL
  }
  return fullSdkInt >= Build.VERSION_CODES_FULL.CINNAMON_BUN + 2 ||
    (
      fullSdkInt == Build.VERSION_CODES_FULL.CINNAMON_BUN + 1 &&
        Build.VERSION.PREVIEW_SDK_INT == 3_723
      )
}

@OptIn(ExperimentalMetricApi::class)
internal fun backdropComparisonMetrics(
  requireBackdropDraw: Boolean,
): List<Metric> = listOf<Metric>(
  FrameTimingMetric(),
  glassMemoryMetric(),
) + backdropTraceMetrics(requireBackdropDraw)

// The heap counter appears only after a GC, and AndroidX drops every single-value metric for an
// iteration that lacks one of them, so only the counters present in every trace are requested.
@OptIn(ExperimentalMetricApi::class)
private fun glassMemoryMetric() = MemoryUsageMetric(
  mode = MemoryUsageMetric.Mode.Max,
  subMetrics = listOf(
    MemoryUsageMetric.SubMetric.Gpu,
    MemoryUsageMetric.SubMetric.RssAnon,
    MemoryUsageMetric.SubMetric.RssFile,
  ),
)

@OptIn(ExperimentalMetricApi::class)
private fun backdropTraceMetrics(
  requireBackdropDraw: Boolean,
): List<Metric> = listOf(
  TraceSectionMetric(
    sectionName = HAZE_BACKDROP_DRAW_SECTION,
    mode = TraceSectionMetric.Mode.Count,
    label = if (requireBackdropDraw) "requiredHazeBackdropDraw" else "hazeBackdropDraw",
  ),
  TraceSectionMetric(
    sectionName = HAZE_SOURCE_RECORD_SECTION,
    mode = TraceSectionMetric.Mode.Count,
    label = "hazeSourceRecord",
  ),
)

@OptIn(ExperimentalMetricApi::class)
internal fun glassMetrics(
  includeMemory: Boolean,
  requireRuntimeMarker: Boolean = true,
  includePreparationMetrics: Boolean = false,
  includeBackdropComparisonMetrics: Boolean = false,
  requireBackdropDraw: Boolean = false,
  includeRedrawMetrics: Boolean = false,
): List<Metric> = buildList {
  // Both metrics would otherwise add a hazeSourceRecord measurement, which AndroidX rejects.
  require(!(includeRedrawMetrics && includeBackdropComparisonMetrics)) {
    "includeRedrawMetrics already records HazeSource.record; do not combine it with backdrop comparison metrics"
  }
  add(FrameTimingMetric())
  if (requireRuntimeMarker) {
    add(
      TraceSectionMetric(
        sectionName = GLASS_RUNTIME_DRAW_SECTION,
        mode = TraceSectionMetric.Mode.Count,
        label = "hazeGlassRuntimeDraw",
      ),
    )
  }
  if (includeMemory) {
    add(glassMemoryMetric())
  }
  if (includeRedrawMetrics) {
    add(
      TraceSectionMetric(
        sectionName = HWUI_GRAPHICS_LAYER_DRAW_SECTION,
        mode = TraceSectionMetric.Mode.Count,
        label = "graphicsLayerDraw",
      ),
    )
    add(
      TraceSectionMetric(
        sectionName = HAZE_SOURCE_RECORD_SECTION,
        mode = TraceSectionMetric.Mode.Count,
        label = "hazeSourceRecord",
      ),
    )
  }
  if (includeBackdropComparisonMetrics) {
    addAll(backdropTraceMetrics(requireBackdropDraw))
  }
  if (includePreparationMetrics) {
    add(
      TraceSectionMetric(
        sectionName = GLASS_CREATE_RENDER_EFFECT_SECTION,
        mode = TraceSectionMetric.Mode.Sum,
        label = "hazeGlassCreateRenderEffect",
      ),
    )
    add(
      TraceSectionMetric(
        sectionName = GLASS_PREPARE_EFFECTS_SECTION,
        mode = TraceSectionMetric.Mode.Sum,
        label = "hazeGlassPrepareEffects",
      ),
    )
    add(
      TraceSectionMetric(
        sectionName = GLASS_PREPARE_LAYERS_SECTION,
        mode = TraceSectionMetric.Mode.Sum,
        label = "hazeGlassPrepareLayers",
      ),
    )
  }
}

private fun isProbablyEmulator(): Boolean =
  Build.FINGERPRINT.startsWith("generic") ||
    Build.FINGERPRINT.startsWith("unknown") ||
    Build.MODEL.contains("google_sdk", ignoreCase = true) ||
    Build.MODEL.contains("Emulator", ignoreCase = true) ||
    Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
    Build.HARDWARE.contains("goldfish", ignoreCase = true) ||
    Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
    Build.PRODUCT.contains("sdk", ignoreCase = true)

internal fun isBenchmarkDryRun(): Boolean =
  InstrumentationRegistry.getArguments()
    .getString("androidx.benchmark.dryRunMode.enable")
    .toBoolean()
