// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassMergePrototype
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.ceil
import kotlinx.coroutines.launch

private val PillSize = DpSize(140.dp, 64.dp)
private val PillShape = RoundedCornerShape(32.dp)

/**
 * Prototype only (#1438): two draggable pills that Glass merges into one surface, or draws as two
 * independent surfaces for comparison. Pill positions are in dp, relative to the stage that holds
 * the backdrop and the pills.
 */
@Composable
public fun GlassMergePrototypeSample(navController: NavHostController) {
  val hazeState = rememberHazeState()
  val scope = rememberCoroutineScope()

  var merged by remember { mutableStateOf(true) }
  var overlapped by remember { mutableStateOf(false) }
  val spacing = remember { mutableFloatStateOf(40f) }

  // Pill A is dragged directly. Pill B is an Animatable so the toggle can spring it to or from A.
  // The initial 16 dp gap is less than the spacing, so the bridge is formed.
  val offsetA = remember { mutableStateOf(DpOffset(24.dp, 160.dp)) }
  val offsetB = remember { Animatable(DpOffset(24.dp + PillSize.width + 16.dp, 160.dp), DpOffset.VectorConverter) }

  // Styles compare by identity, so remember the style once. Its block reads the offsets and the
  // spacing, so dragging or animating redraws the Glass surface without recomposing it.
  val mergedStyle = remember {
    GlassStyle.regular.then {
      val a = offsetA.value
      val b = offsetB.value
      val bounds = mergedBounds(a, b, spacing.floatValue.dp)
      // The merge geometry is relative to the container, so subtract the container's origin
      // from the stage-relative pill positions.
      mergePrototype(
        GlassMergePrototype(
          rectA = DpRect(DpOffset(a.x - bounds.left, a.y - bounds.top), PillSize),
          shapeA = PillShape,
          rectB = DpRect(DpOffset(b.x - bounds.left, b.y - bounds.top), PillSize),
          shapeB = PillShape,
          spacing = spacing.floatValue.dp,
        ),
      )
    }
  }
  val independentStyle = remember { GlassStyle.regular.then { shape(PillShape) } }

  Box(Modifier.fillMaxSize()) {
    // A busy, high-contrast backdrop makes the refraction at the neck easy to see.
    Column(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .drawWithCache {
          val cell = 24.dp.toPx()
          onDrawBehind {
            drawRect(Color(0xFF111827))
            for (row in 0 until ceil(size.height / cell).toInt()) {
              for (column in 0 until ceil(size.width / cell).toInt()) {
                if ((row + column) % 2 == 0) {
                  drawRect(
                    Color(0xFFE5E7EB),
                    topLeft = Offset(column * cell, row * cell),
                    size = Size(cell, cell),
                  )
                }
              }
            }
            var x = -size.height
            while (x < size.width) {
              drawLine(
                Color(0xFFF97316),
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = cell / 3,
              )
              x += cell * 2
            }
          }
        },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceEvenly,
    ) {
      repeat(8) {
        Text(
          "HAZE GLASS MERGE",
          color = Color(0xFF38BDF8),
          fontSize = 32.sp,
          fontWeight = FontWeight.Black,
        )
      }
    }

    if (merged) {
      // One Glass surface for both pills. Its bounds are the union of the pills inflated by the
      // spacing, recomputed in layout from the same state the style reads.
      Box(
        Modifier
          .layout { measurable, constraints ->
            val bounds = mergedBounds(offsetA.value, offsetB.value, spacing.floatValue.dp)
            val placeable = measurable.measure(
              Constraints.fixed(bounds.width.roundToPx(), bounds.height.roundToPx()),
            )
            layout(constraints.maxWidth, constraints.maxHeight) {
              placeable.place(bounds.left.roundToPx(), bounds.top.roundToPx())
            }
          }
          .testTag("merge_container")
          .hazeGlass(input = HazeInput.Backdrop(hazeState), style = mergedStyle),
      )

      // The pill content is drawn above the container, at the same stage-relative offsets.
      MergePill(
        label = "A",
        offset = { offsetA.value },
        surface = Modifier.testTag("merge_pill_a"),
        onDrag = { offsetA.value += it },
      )
      MergePill(
        label = "B",
        offset = { offsetB.value },
        surface = Modifier.testTag("merge_pill_b"),
        onDrag = { delta -> scope.launch { offsetB.snapTo(offsetB.value + delta) } },
      )
    } else {
      MergePill(
        label = "A",
        offset = { offsetA.value },
        surface = Modifier
          .testTag("merge_independent_surface_a")
          .hazeGlass(input = HazeInput.Backdrop(hazeState), style = independentStyle),
        onDrag = { offsetA.value += it },
      )
      MergePill(
        label = "B",
        offset = { offsetB.value },
        surface = Modifier
          .testTag("merge_independent_surface_b")
          .hazeGlass(input = HazeInput.Backdrop(hazeState), style = independentStyle),
        onDrag = { delta -> scope.launch { offsetB.snapTo(offsetB.value + delta) } },
      )
    }

    if (LocalSampleNavigationEnabled.current) {
      IconButton(
        onClick = navController::navigateUp,
        modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).padding(8.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .padding(WindowInsets.safeDrawing.asPaddingValues())
        .padding(16.dp)
        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = merged,
          onClick = { merged = true },
          label = { Text("Merged") },
          modifier = Modifier.testTag("merge_mode_merged"),
        )
        FilterChip(
          selected = !merged,
          onClick = { merged = false },
          label = { Text("Independent") },
          modifier = Modifier.testTag("merge_mode_independent"),
        )
        Button(
          onClick = {
            overlapped = !overlapped
            // Fully overlap pill A, or separate vertically by more than the largest spacing.
            val target = if (overlapped) {
              offsetA.value
            } else {
              offsetA.value + DpOffset(0.dp, PillSize.height + 80.dp)
            }
            scope.launch {
              offsetB.animateTo(
                target,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
              )
            }
          },
          modifier = Modifier.testTag("merge_split_toggle"),
        ) {
          Text(if (overlapped) "Separate" else "Overlap")
        }
      }
      Text("Spacing: ${spacing.floatValue.toInt()} dp", color = Color.White)
      Slider(
        value = spacing.floatValue,
        onValueChange = { spacing.floatValue = it },
        valueRange = 0f..64f,
        modifier = Modifier.testTag("merge_spacing"),
      )
    }
  }
}

/** A draggable pill. [surface] is applied once the pill is sized, so Glass sees the pill bounds. */
@Composable
private fun MergePill(
  label: String,
  offset: () -> DpOffset,
  surface: Modifier,
  onDrag: (DpOffset) -> Unit,
) {
  Box(
    modifier = Modifier
      .offset { offset().let { IntOffset(it.x.roundToPx(), it.y.roundToPx()) } }
      .size(PillSize)
      .then(surface)
      .pointerInput(Unit) {
        detectDragGestures { change, amount ->
          change.consume()
          onDrag(DpOffset(amount.x.toDp(), amount.y.toDp()))
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
  }
}

/** The union of both pills inflated by [spacing], so the bulge at the neck is never clipped. */
private fun mergedBounds(a: DpOffset, b: DpOffset, spacing: Dp) = DpRect(
  left = minOf(a.x, b.x) - spacing,
  top = minOf(a.y, b.y) - spacing,
  right = maxOf(a.x, b.x) + PillSize.width + spacing,
  bottom = maxOf(a.y, b.y) + PillSize.height + spacing,
)
