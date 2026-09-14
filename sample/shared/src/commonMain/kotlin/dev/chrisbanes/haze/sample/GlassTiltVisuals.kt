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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

internal val GLASS_TILT_FIXED_LIGHT_POSITION = Offset(0.50f, 0.50f)
internal const val GLASS_TILT_MAX_DISPLACEMENT = 0.18f

// These are deliberately conservative experiment values; device tuning remains pending.
private const val GLASS_TILT_GAIN = 0.03f
private const val GLASS_TILT_SMOOTHING = 0.18f

public enum class GlassTiltDisplayRotation {
  Rotation0,
  Rotation90,
  Rotation180,
  Rotation270,
}

public class GlassTiltState {
  private var tiltEnabled by mutableStateOf(false)
  var isTiltAvailable by mutableStateOf(true)
    private set
  var lightPosition by mutableStateOf(GLASS_TILT_FIXED_LIGHT_POSITION)
    private set

  val isTiltEnabled: Boolean
    get() = tiltEnabled

  public fun updateTiltEnabled(enabled: Boolean) {
    tiltEnabled = enabled && isTiltAvailable
    restartFromFixedPosition()
  }

  public fun setTiltUnavailable() {
    isTiltAvailable = false
    tiltEnabled = false
    restartFromFixedPosition()
  }

  public fun restartFromFixedPosition() {
    lightPosition = GLASS_TILT_FIXED_LIGHT_POSITION
  }

  public fun onGravity(gravity: Offset, displayRotation: GlassTiltDisplayRotation) {
    if (!isTiltEnabled || !gravity.isFinite()) return
    val mappedGravity = mapGlassTiltGravity(gravity, displayRotation)
    if (!mappedGravity.isFinite()) return

    val target = boundedGlassTiltPosition(
      GLASS_TILT_FIXED_LIGHT_POSITION + mappedGravity * GLASS_TILT_GAIN,
    )
    lightPosition = boundedGlassTiltPosition(
      lightPosition + (target - lightPosition) * GLASS_TILT_SMOOTHING,
    )
  }
}

internal fun mapGlassTiltGravity(
  gravity: Offset,
  displayRotation: GlassTiltDisplayRotation,
): Offset = when (displayRotation) {
  GlassTiltDisplayRotation.Rotation0 -> gravity
  GlassTiltDisplayRotation.Rotation90 -> Offset(-gravity.y, gravity.x)
  GlassTiltDisplayRotation.Rotation180 -> -gravity
  GlassTiltDisplayRotation.Rotation270 -> Offset(gravity.y, -gravity.x)
}

private fun boundedGlassTiltPosition(position: Offset): Offset = Offset(
  x = position.x.coerceIn(
    GLASS_TILT_FIXED_LIGHT_POSITION.x - GLASS_TILT_MAX_DISPLACEMENT,
    GLASS_TILT_FIXED_LIGHT_POSITION.x + GLASS_TILT_MAX_DISPLACEMENT,
  ),
  y = position.y.coerceIn(
    GLASS_TILT_FIXED_LIGHT_POSITION.y - GLASS_TILT_MAX_DISPLACEMENT,
    GLASS_TILT_FIXED_LIGHT_POSITION.y + GLASS_TILT_MAX_DISPLACEMENT,
  ),
)

internal interface GlassTiltGravitySensor {
  fun start(onGravity: (Offset) -> Unit): Boolean

  fun stop()
}

@Composable
public fun GlassTiltSampleContent(
  state: GlassTiltState,
  onFixed: () -> Unit,
  onTilt: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val style = remember(state.lightPosition) { glassTiltStyle(state.lightPosition) }
  Box(modifier = modifier.fillMaxSize().background(Color(0xFF10131A))) {
    GalleryBackdrop(
      hazeState = hazeState,
      artworkIndex = 0,
      backdrop = GlassGalleryBackdropId.Gallery,
      modifier = Modifier.fillMaxSize(),
    )
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onBack) { Text("Back") }
        Button(onClick = onFixed, modifier = Modifier.testTag("glass_tilt_fixed")) { Text("Fixed") }
        Button(
          onClick = onTilt,
          enabled = state.isTiltAvailable,
          modifier = Modifier.testTag("glass_tilt_enable"),
        ) { Text("Tilt") }
      }
      Text(
        text = "Light position: ${(state.lightPosition.x * 100).roundToInt()}%, " +
          "${(state.lightPosition.y * 100).roundToInt()}%",
        color = Color.White,
        modifier = Modifier.testTag("glass_tilt_position"),
      )
      if (!state.isTiltAvailable) {
        Text(
          text = "Tilt unavailable — gravity sensor not available.",
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

private fun glassTiltStyle(lightPosition: Offset): GlassStyle = GlassStyle.regular.then {
  lightPosition(
    BiasAbsoluteAlignment(
      horizontalBias = lightPosition.x * 2f - 1f,
      verticalBias = lightPosition.y * 2f - 1f,
    ),
  )
  shape(RoundedCornerShape(24.dp))
}

private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()
