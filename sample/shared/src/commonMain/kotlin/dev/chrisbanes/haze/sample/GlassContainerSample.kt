// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
public fun GlassContainerSample(navController: NavHostController) {
  var mergeDistance by remember { mutableStateOf(24.dp) }
  var dragOffsets by remember { mutableStateOf(listOf(Offset.Zero, Offset.Zero)) }
  var extraMemberVisible by remember { mutableStateOf(false) }

  GlassContainerSampleContent(
    mergeDistance = mergeDistance,
    dragOffsets = dragOffsets,
    extraMemberVisible = extraMemberVisible,
    onMergeDistanceChange = { mergeDistance = it },
    onDrag = { index, delta ->
      dragOffsets = dragOffsets.mapIndexed { i, offset -> if (i == index) offset + delta else offset }
    },
    onResetDrag = { dragOffsets = listOf(Offset.Zero, Offset.Zero) },
    onExtraMemberVisibleChange = { extraMemberVisible = it },
    onBack = navController::navigateUp,
  )
}

@Composable
public fun GlassContainerSampleContent(
  mergeDistance: Dp,
  dragOffsets: List<Offset>,
  extraMemberVisible: Boolean,
  onMergeDistanceChange: (Dp) -> Unit,
  onDrag: (index: Int, delta: Offset) -> Unit,
  onResetDrag: () -> Unit,
  onExtraMemberVisibleChange: (Boolean) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Every member samples the same source, so every member can merge with its neighbours.
  val hazeState = rememberHazeState()

  // The pointer input below keeps running across recompositions, so it reads the latest callback.
  val currentOnDrag by rememberUpdatedState(onDrag)

  // Styles compare by identity, so remember each one. Tint, shape and interaction responses
  // are per member and do not stop members from merging.
  val memberStyle = remember { GlassStyle.regular.then { shape(CircleShape) } }
  val buttonStyle = remember {
    GlassStyle.regular.then {
      shape(CircleShape)
      pressed {
        lightingIntensity(1f)
        scale(0.94f)
      }
    }
  }
  val redStyle = remember {
    GlassStyle.regular.then {
      shape(CircleShape)
      tint(Color(0xFFEF4444).copy(alpha = 0.35f))
    }
  }
  val greenStyle = remember {
    GlassStyle.regular.then {
      shape(CircleShape)
      tint(Color(0xFF22C55E).copy(alpha = 0.35f))
    }
  }
  val blueStyle = remember {
    GlassStyle.regular.then {
      shape(CircleShape)
      tint(Color(0xFF3B82F6).copy(alpha = 0.35f))
    }
  }

  Box(modifier.fillMaxSize()) {
    Box(
      Modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .background(
          Brush.linearGradient(
            listOf(Color(0xFF1E3A8A), Color(0xFF9333EA), Color(0xFFF97316)),
          ),
        ),
    ) {
      // High-contrast shapes behind each section, so the refraction is easy to see.
      Box(
        Modifier
          .align(Alignment.TopStart)
          .offset(x = 116.dp, y = 80.dp)
          .size(72.dp)
          .background(Color(0xFFFACC15), CircleShape),
      )
      Box(
        Modifier
          .align(Alignment.TopStart)
          .offset(x = 64.dp, y = 240.dp)
          .size(width = 200.dp, height = 48.dp)
          .background(Color(0xFF14B8A6), RoundedCornerShape(24.dp)),
      )
      Box(
        Modifier
          .align(Alignment.TopStart)
          .offset(x = 56.dp, y = 456.dp)
          .size(width = 120.dp, height = 64.dp)
          .background(Color(0xFFEC4899), RoundedCornerShape(16.dp)),
      )
      Box(
        Modifier
          .align(Alignment.TopStart)
          .offset(x = 130.dp, y = 590.dp)
          .size(100.dp)
          .background(Color(0xFF0F172A), CircleShape),
      )
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .testTag("glass_container_scroll")
        .padding(WindowInsets.safeDrawing.asPaddingValues())
        .padding(start = 24.dp, top = 56.dp, end = 24.dp, bottom = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // 1. Toolbar. Members start stretching within the merge distance and bridge below half of it:
      // the 4 dp gaps here are well inside the 8 dp bridge threshold.
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Toolbar", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.hazeGlassContainer(mergeDistance = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          val toolbarIcons = listOf(
            Icons.Filled.Search to "Search",
            Icons.Filled.Favorite to "Favorite",
            Icons.Filled.Settings to "Settings",
            Icons.Filled.Refresh to "Refresh",
          )
          toolbarIcons.forEach { (icon, description) ->
            // Share one interaction source between the button and its glass, so the press
            // light follows the touch on that button's part of the merged shape.
            val interactionSource = remember { MutableInteractionSource() }
            IconButton(
              onClick = {},
              modifier = Modifier
                .size(48.dp)
                .hazeGlass(
                  input = HazeInput.Sources(hazeState),
                  style = buttonStyle,
                  interactionSource = interactionSource,
                ),
              interactionSource = interactionSource,
            ) {
              Icon(icon, contentDescription = description, tint = Color.White)
            }
          }
        }
      }

      // 2. Draggable members. Drag one circle towards the other, or change the merge distance. The
      // circles merge while their gap is less than half of the merge distance. A merge distance of
      // zero disables merging.
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            "Draggable members",
            modifier = Modifier.weight(1f),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
          )
          // Dragging can move a circle out of reach, so offer a way back.
          TextButton(onClick = onResetDrag) { Text("Reset", color = Color.White) }
        }
        Box(
          Modifier
            .fillMaxWidth()
            .height(112.dp)
            .hazeGlassContainer(mergeDistance = mergeDistance),
        ) {
          // Offset comes before hazeGlass, so the glass moves with the circle.
          Box(
            Modifier
              .align(Alignment.CenterStart)
              .offset { dragOffsets[0].round() }
              .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                  change.consume()
                  currentOnDrag(0, delta)
                }
              }
              .size(72.dp)
              .hazeGlass(input = HazeInput.Sources(hazeState), style = memberStyle),
          )
          Box(
            Modifier
              .align(Alignment.CenterStart)
              .padding(start = 80.dp)
              .offset { dragOffsets[1].round() }
              .pointerInput(Unit) {
                detectDragGestures { change, delta ->
                  change.consume()
                  currentOnDrag(1, delta)
                }
              }
              .size(72.dp)
              .hazeGlass(input = HazeInput.Sources(hazeState), style = memberStyle),
          )
        }
        Text(
          "Spacing: ${mergeDistance.value.toInt()} dp",
          color = Color.White,
          style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
          value = mergeDistance.value,
          onValueChange = { onMergeDistanceChange(it.dp) },
          valueRange = 0f..64f,
        )
      }

      // 3. Adding and removing a member. Haze animates only the glass: it grows from the base
      // pill and retracts into it. Animate the content yourself. Use fades here, because a
      // scale animation makes the member non-translation-only, so it would stop merging and
      // matched-geometry transitions.
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Add and remove", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.hazeGlassContainer(mergeDistance = 24.dp)) {
          Box(
            Modifier
              .height(48.dp)
              .hazeGlass(input = HazeInput.Sources(hazeState), style = memberStyle)
              .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text("Inbox", color = Color.White)
          }
          AnimatedVisibility(
            visible = extraMemberVisible,
            enter = fadeIn(),
            exit = fadeOut(),
          ) {
            Box(
              Modifier
                .padding(start = 8.dp)
                .height(48.dp)
                .testTag("glass_container_extra_member")
                .hazeGlass(input = HazeInput.Sources(hazeState), style = memberStyle)
                .padding(horizontal = 20.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text("Archive", color = Color.White)
            }
          }
        }
        Button(
          onClick = { onExtraMemberVisibleChange(!extraMemberVisible) },
          modifier = Modifier.testTag("glass_container_toggle"),
        ) {
          Text(if (extraMemberVisible) "Remove member" else "Add member")
        }
      }

      // 4. Mixed tint. Tint is per member, so members with different tints still merge, and
      // their colours blend across the bridges.
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Mixed tint", color = Color.White, style = MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.hazeGlassContainer(mergeDistance = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          Box(
            Modifier
              .size(width = 96.dp, height = 48.dp)
              .hazeGlass(input = HazeInput.Sources(hazeState), style = redStyle),
          )
          Box(
            Modifier
              .size(width = 96.dp, height = 48.dp)
              .hazeGlass(input = HazeInput.Sources(hazeState), style = greenStyle),
          )
          Box(
            Modifier
              .size(width = 96.dp, height = 48.dp)
              .hazeGlass(input = HazeInput.Sources(hazeState), style = blueStyle),
          )
        }
      }
    }

    if (LocalSampleNavigationEnabled.current) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.padding(WindowInsets.safeDrawing.asPaddingValues()).padding(8.dp),
      ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
      }
    }
  }
}
