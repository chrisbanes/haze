// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Sample-only Glass button. A copied version needs a caller-owned [input] backed by
 * `rememberHazeState()` and an earlier `hazeSource` in the same window.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
public fun GlassButton(
  input: HazeInput,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val shape = RoundedCornerShape(24.dp)
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .defaultMinSize(minHeight = 48.dp)
      .hazeGlass(
        input = input,
        style = GlassStyle.regular.then {
          this.shape(shape)
          tint(Color.White.copy(alpha = if (enabled) 0.16f else 0.06f))
          hovered { lightingIntensity(0.35f); refractionMultiplier(1.02f) }
          focused { lightingIntensity(0.5f); whitePointDelta(0.03f) }
          pressed { lightingIntensity(0.9f); refractionMultiplier(1.08f); scale(0.98f) }
        },
        interactionSource = interactionSource,
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
      )
      .clip(shape)
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
      )
      .semantics(mergeDescendants = true) { role = Role.Button }
      .padding(horizontal = 18.dp, vertical = 10.dp),
  ) {
    content()
  }
}
