// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

@Composable
public fun GlassTiltSampleContent(
  lightPosition: Offset,
  isTiltAvailable: Boolean,
  onFixed: () -> Unit,
  onTilt: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  GlassTiltSampleContent(
    lightPosition = rememberUpdatedState(lightPosition),
    isTiltAvailable = isTiltAvailable,
    onFixed = onFixed,
    onTilt = onTilt,
    onBack = onBack,
    modifier = modifier,
  )
}

@Composable
internal fun GlassTiltSampleContent(
  lightPosition: State<Offset>,
  isTiltAvailable: Boolean,
  onFixed: () -> Unit,
  onTilt: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val navigationEnabled = LocalSampleNavigationEnabled.current
  val hazeState = rememberHazeState()
  val style = rememberGlassTiltStyle(lightPosition)
  Box(modifier = modifier.fillMaxSize().background(Color(0xFF10131A))) {
    GalleryBackdrop(
      hazeState = hazeState,
      artworkIndex = 0,
      modifier = Modifier.fillMaxSize(),
    )
    Column(
      modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (navigationEnabled) Button(onClick = onBack) { Text("Back") }
        Button(onClick = onFixed, modifier = Modifier.testTag("glass_tilt_fixed")) { Text("Fixed") }
        Button(
          onClick = onTilt,
          enabled = isTiltAvailable,
          modifier = Modifier.testTag("glass_tilt_enable"),
        ) { Text("Tilt") }
      }
      GlassTiltLightPositionLabel(lightPosition)
      if (!isTiltAvailable) {
        Text(
          text = "Tilt unavailable.",
          color = Color.White,
          modifier = Modifier.testTag("glass_tilt_unavailable"),
        )
      }
      GlassTiltSurface(hazeState, style, Modifier.fillMaxWidth().height(112.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        GlassTiltSurface(hazeState, style, Modifier.weight(1f).height(168.dp))
        GlassTiltSurface(hazeState, style, Modifier.weight(1f).size(168.dp))
      }
    }
  }
}

@Composable
private fun GlassTiltSurface(
  hazeState: HazeState,
  style: GlassStyle,
  modifier: Modifier,
) {
  Box(
    modifier = modifier
      .hazeGlass(input = HazeInput.Sources(hazeState), style = style)
      .padding(16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text("Glass", color = MaterialTheme.colorScheme.onSurface)
  }
}

@Composable
internal fun rememberGlassTiltStyle(lightPosition: State<Offset>): GlassStyle = remember {
  glassTiltStyle(GlassTiltLightAlignment(lightPosition))
}

@Composable
internal fun GlassTiltLightPositionLabel(lightPosition: State<Offset>) {
  val position = lightPosition.value
  Text(
    text = "Light position: ${(position.x * 100).roundToInt()}%, ${(position.y * 100).roundToInt()}%",
    color = Color.White,
    modifier = Modifier.testTag("glass_tilt_position"),
  )
}

internal class GlassTiltLightAlignment(
  private val lightPosition: State<Offset>,
) : Alignment {
  override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
    val position = lightPosition.value
    return IntOffset(
      x = (space.width * position.x).roundToInt(),
      y = (space.height * position.y).roundToInt(),
    )
  }
}

private fun glassTiltStyle(lightPosition: Alignment): GlassStyle = GlassStyle.regular.then {
  lightPosition(lightPosition)
  shape(RoundedCornerShape(24.dp))
}
