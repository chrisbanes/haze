// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.components

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass

/** Sample-only controlled switch, designed to be copied with public Compose and Haze APIs. */
@OptIn(ExperimentalHazeApi::class)
@Composable
public fun GlassToggle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  input: HazeInput,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val shape = RoundedCornerShape(24.dp)
  val interactionSource = remember { MutableInteractionSource() }
  Box(
    modifier = modifier
      .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
      .hazeGlass(
        input = input,
        style = GlassStyle.regular.then {
          this.shape(shape)
          tint(if (checked) Color(0xff82d7ff).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.14f))
        },
        interactionSource = interactionSource,
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
      )
      .pointerInput(enabled, checked) {
        if (enabled) {
          var totalDrag = 0f
          detectDragGestures(
            onDragStart = { totalDrag = 0f },
            onDragEnd = {
              if (totalDrag > 8f && !checked) onCheckedChange(true)
              if (totalDrag < -8f && checked) onCheckedChange(false)
            },
          ) { change, amount ->
            totalDrag += amount.x
            change.consume()
          }
        }
      }
      .clip(shape)
      .semantics { contentDescription = "Glass toggle" }
      .toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        interactionSource = interactionSource,
        indication = null,
        onValueChange = onCheckedChange,
      )
      .padding(4.dp),
  ) {
    val thumbOffset by animateDpAsState(if (checked) 16.dp else 0.dp, label = "Glass toggle thumb")
    Box(
      modifier = Modifier
        .align(Alignment.CenterStart)
        .padding(start = thumbOffset)
        .size(40.dp)
        .clip(CircleShape)
        .background(Color.White.copy(alpha = if (enabled) 0.9f else 0.35f)),
    )
  }
}
