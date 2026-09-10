// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

/** Sample-only glass tab bar. Labels stay owned by the caller for copyable sample usage. */
@OptIn(ExperimentalHazeApi::class)
@Composable
public fun GlassBottomTabs(
  selectedIndex: Int,
  tabs: List<String>,
  onSelected: (Int) -> Unit,
  input: HazeInput,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(28.dp)
  Row(
    modifier = modifier
      .hazeGlass(
        input = input,
        style = GlassStyle.regular.then { this.shape(shape); tint(Color.White.copy(alpha = 0.12f)) },
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
      )
      .clip(shape)
      .padding(4.dp)
      .selectableGroup(),
  ) {
    tabs.forEachIndexed { index, label ->
      val selected = selectedIndex == index
      val interactionSource = remember { MutableInteractionSource() }
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .weight(1f)
          .defaultMinSize(minHeight = 48.dp)
          .hazeGlass(
            input = input,
            style = GlassStyle.clear.then {
              this.shape(RoundedCornerShape(24.dp))
              tint(if (selected) Color.White.copy(alpha = 0.26f) else Color.Transparent)
            },
            interactionSource = interactionSource,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
          )
          .clip(RoundedCornerShape(24.dp))
          .selectable(
            selected = selected,
            onClick = { onSelected(index) },
            role = Role.Tab,
            interactionSource = interactionSource,
            indication = null,
          )
          .padding(horizontal = 12.dp),
      ) {
        androidx.compose.material3.Text(label)
      }
    }
  }
}
