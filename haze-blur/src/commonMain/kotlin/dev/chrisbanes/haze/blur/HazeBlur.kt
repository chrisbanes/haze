// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.hazeEffect

/**
 * Applies Blur to an explicit [input].
 *
 * [style] is an opaque, shareable sequence of writes. Resolution starts from
 * [HazeBlurDefaults.style], then applies [LocalHazeBlurStyle], then [style]. Later writes win. Each
 * modifier node owns its Blur runtime, delegate, retained output, performance-mode configuration,
 * cache, and lifecycle resources.
 *
 * Snapshot state read by a Style block is observed by this node: a change resolves the Style again
 * and redraws on the next frame without recomposition. Styles created with [HazeBlurStyle] compare
 * by identity, so `remember` or hoist them. Built-in material presets compare by value.
 *
 * @param input Source-backed content or this modifier's own content.
 * @param style Explicit Blur Style applied after [LocalHazeBlurStyle].
 * @param performanceMode Rendering-fidelity policy, or `null` to inherit [LocalHazePerformanceMode].
 * @param expandLayerBounds Whether Blur may expand its capture layer by the resolved radius.
 */
@Stable
public fun Modifier.hazeBlur(
  input: HazeInput,
  style: HazeBlurStyle = HazeBlurStyle,
  performanceMode: HazePerformanceMode? = null,
  expandLayerBounds: Boolean = true,
): Modifier = hazeEffect(
  factory = HazeBlurFactory,
  input = input,
  style = BlurConfiguration(style, performanceMode),
  expandLayerBounds = expandLayerBounds,
)

@Poko
internal class BlurConfiguration(
  val style: HazeBlurStyle,
  val performanceMode: HazePerformanceMode?,
)

internal object HazeBlurFactory : HazeEffectFactory<BlurConfiguration> {
  override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> = BlurVisualEffect()
}
