// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.math.roundToInt

/** Sample-only controlled progress slider with touch, mouse, and keyboard input. */
@OptIn(ExperimentalHazeApi::class)
@Composable
public fun GlassSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  input: HazeInput,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  onValueChangeStarted: (() -> Unit)? = null,
  onValueChangeFinished: (() -> Unit)? = null,
) {
  var width by remember { mutableFloatStateOf(1f) }
  val layoutDirection = LocalLayoutDirection.current
  val thumbDiameter = with(LocalDensity.current) { 32.dp.toPx() }
  val rangeLength = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
  val fraction = (
    (value.coerceIn(valueRange.start, valueRange.endInclusive) - valueRange.start) /
      rangeLength
    ).coerceIn(0f, 1f)
  val interactionSource = remember { MutableInteractionSource() }
  fun updateAt(x: Float) {
    val directionFraction = (x / width).coerceIn(0f, 1f)
    val logicalFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - directionFraction else directionFraction
    val next = valueRange.start + logicalFraction * (valueRange.endInclusive - valueRange.start)
    onValueChange(next)
  }
  Box(
    contentAlignment = Alignment.CenterStart,
    modifier = modifier
      .fillMaxWidth()
      .height(48.dp)
      .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
      .semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, 0)
        setProgress { requested ->
          if (!enabled) return@setProgress false
          onValueChange(requested.coerceIn(valueRange.start, valueRange.endInclusive))
          true
        }
      }
      .onPreviewKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val step = (valueRange.endInclusive - valueRange.start) / 20f
        when (event.key) {
          Key.DirectionLeft, Key.DirectionDown -> onValueChange((value - step).coerceAtLeast(valueRange.start))
          Key.DirectionRight, Key.DirectionUp -> onValueChange((value + step).coerceAtMost(valueRange.endInclusive))
          else -> return@onPreviewKeyEvent false
        }
        true
      }
      .focusable(enabled, interactionSource)
      .pointerInput(enabled, width) {
        if (enabled) {
          var press: PressInteraction.Press? = null
          detectDragGestures(
            onDragStart = {
              press = PressInteraction.Press(it)
              interactionSource.tryEmit(press!!)
              onValueChangeStarted?.invoke()
              updateAt(it.x)
            },
            onDragEnd = {
              press?.let { interactionSource.tryEmit(PressInteraction.Release(it)) }
              press = null
              onValueChangeFinished?.invoke()
            },
            onDragCancel = {
              press?.let { interactionSource.tryEmit(PressInteraction.Cancel(it)) }
              press = null
              onValueChangeFinished?.invoke()
            },
          ) { change, _ ->
            updateAt(change.position.x)
            change.consume()
          }
        }
      }
      .pointerInput(enabled, width) {
        if (enabled) {
          detectTapGestures { offset ->
            val press = PressInteraction.Press(offset)
            interactionSource.tryEmit(press)
            onValueChangeStarted?.invoke()
            updateAt(offset.x)
            interactionSource.tryEmit(PressInteraction.Release(press))
            onValueChangeFinished?.invoke()
          }
        }
      },
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Color.White.copy(alpha = 0.22f)),
    )
    Box(
      modifier = Modifier
        .fillMaxWidth(fraction)
        .height(6.dp)
        .clip(RoundedCornerShape(3.dp))
        .background(Color.White.copy(alpha = 0.75f)),
    )
    Box(
      modifier = Modifier
        .offset { IntOffset((fraction * (width - thumbDiameter)).roundToInt(), 0) }
        .size(32.dp)
        .hazeGlass(
          input = input,
          style = remember { GlassStyle.clear.then { shape(CircleShape) } },
          interactionSource = interactionSource,
          interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
          interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
        )
        .clip(CircleShape)
        .background(Color.White.copy(alpha = 0.32f)),
    )
  }
}
