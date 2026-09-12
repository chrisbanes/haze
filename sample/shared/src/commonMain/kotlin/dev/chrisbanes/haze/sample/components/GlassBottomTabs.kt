// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
  tabContent: @Composable (label: String, selected: Boolean) -> Unit = { label, _ ->
    androidx.compose.material3.Text(label)
  },
) {
  val colors = MaterialTheme.colorScheme
  val shape = RoundedCornerShape(28.dp)
  BoxWithConstraints(
    modifier = modifier
      .hazeGlass(
        input = input,
        style = remember(colors) {
          GlassStyle.regular then GlassStyle {
            this.shape(shape)
            optics(depth = 1f, blurRadius = 16.dp, refractionDisplacement = 4.dp)
            tint(colors.surface.copy(alpha = 0.8f))
          }
        },
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
      )
      .clip(shape)
      .padding(4.dp)
      .selectableGroup(),
  ) {
    val tabWidth = maxWidth / tabs.size.coerceAtLeast(1)
    val indicatorOffset by animateDpAsState(tabWidth * selectedIndex.coerceIn(0, tabs.lastIndex), label = "Glass tab indicator")
    Box(
      modifier = Modifier
        .offset(x = indicatorOffset)
        .width(tabWidth)
        .height(48.dp)
        .clip(RoundedCornerShape(24.dp))
        .background(colors.onSurface.copy(alpha = 0.08f)),
    )
    Row(Modifier.fillMaxWidth()) {
      tabs.forEachIndexed { index, label ->
        val selected = selectedIndex == index
        val interactionSource = remember { MutableInteractionSource() }
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 48.dp)
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
          tabContent(label, selected)
        }
      }
    }
  }
}
