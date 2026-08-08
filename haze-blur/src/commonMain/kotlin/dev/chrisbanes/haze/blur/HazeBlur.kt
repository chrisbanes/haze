// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.hazeEffect

/**
 * Applies Blur to an explicit [input].
 *
 * [style] is an opaque, shareable program. Resolution starts from [HazeBlurDefaults.style], then
 * replays [LocalHazeBlurStyle], then [style]. Later writes win. Each modifier node owns its Blur
 * runtime, delegate, retained output, input-scale history, cache, and lifecycle resources.
 *
 * @param input Source-backed content or this modifier's own content.
 * @param style Explicit Blur Style replayed after [LocalHazeBlurStyle].
 * @param performanceMode Rendering-fidelity policy for the Blur runtime.
 * @param expandLayerBounds Whether Blur may expand its capture layer by the resolved radius.
 */
@Stable
public fun Modifier.hazeBlur(
  input: HazeInput,
  style: HazeBlurStyle = HazeBlurStyle,
  performanceMode: HazePerformanceMode = HazePerformanceMode.Default,
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
  val performanceMode: HazePerformanceMode,
)

internal object HazeBlurFactory : HazeEffectFactory<BlurConfiguration> {
  override fun createRenderer(): HazeEffectRenderer<BlurConfiguration> = BlurVisualEffect()
}
