// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.chrisbanes.haze.ExperimentalHazeApi

private class AndroidGlassTiltGravitySensor(context: Context) : GlassTiltGravitySensor, SensorEventListener {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
  private var onGravity: ((Offset) -> Unit)? = null

  override val isAvailable: Boolean
    get() = sensorManager != null && gravitySensor != null

  override fun start(onGravity: (Offset) -> Unit): Boolean {
    val manager = sensorManager ?: return false
    val sensor = gravitySensor ?: return false
    this.onGravity = onGravity
    return manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME).also { registered ->
      if (!registered) this.onGravity = null
    }
  }

  override fun stop() {
    sensorManager?.unregisterListener(this)
    onGravity = null
  }

  override fun onSensorChanged(event: SensorEvent) {
    if (event.values.size >= 2) onGravity?.invoke(Offset(event.values[0], event.values[1]))
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

@Composable
internal fun GlassTiltSample(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  gravitySensor: GlassTiltGravitySensor? = null,
  displayRotation: (() -> GlassTiltDisplayRotation)? = null,
) {
  val state = rememberGlassTiltState(gravitySensor, displayRotation)

  GlassTiltSampleContent(
    lightPosition = state.lightPosition,
    isTiltAvailable = state.isTiltAvailable,
    onFixed = { state.updateTiltEnabled(false) },
    onTilt = { state.updateTiltEnabled(true) },
    onBack = onBack,
    modifier = modifier,
  )
}

@Composable
internal fun rememberGlassTiltState(
  gravitySensor: GlassTiltGravitySensor? = null,
  displayRotation: (() -> GlassTiltDisplayRotation)? = null,
): GlassTiltState {
  val context = LocalContext.current
  val state = remember { GlassTiltState() }
  val sensor = gravitySensor ?: remember(context) { AndroidGlassTiltGravitySensor(context) }
  val currentDisplayRotation = displayRotation ?: { currentGlassTiltDisplayRotation(context) }

  LifecycleResumeEffect(state.isTiltEnabled, sensor) {
    var registered = false
    if (state.isTiltEnabled) {
      state.restartFromFixedPosition()
      if (!sensor.isAvailable) {
        state.setTiltUnavailable()
      } else {
        registered = sensor.start { gravity -> state.onGravity(gravity, currentDisplayRotation()) }
      }
    }
    onPauseOrDispose {
      if (registered) sensor.stop()
    }
  }
  return state
}

private fun currentGlassTiltDisplayRotation(context: Context): GlassTiltDisplayRotation = when (displayRotation(context)) {
  Surface.ROTATION_90 -> GlassTiltDisplayRotation.Rotation90
  Surface.ROTATION_180 -> GlassTiltDisplayRotation.Rotation180
  Surface.ROTATION_270 -> GlassTiltDisplayRotation.Rotation270
  else -> GlassTiltDisplayRotation.Rotation0
}

@Suppress("DEPRECATION")
private fun displayRotation(context: Context): Int? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
  context.display.rotation
} else {
  (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation
}

internal val GLASS_TILT_FIXED_LIGHT_POSITION = Offset(0.50f, 0.50f)
internal const val GLASS_TILT_MAX_DISPLACEMENT = 0.18f

private const val GLASS_TILT_GAIN = 0.03f
private const val GLASS_TILT_SMOOTHING = 0.18f

internal enum class GlassTiltDisplayRotation {
  Rotation0,
  Rotation90,
  Rotation180,
  Rotation270,
}

internal class GlassTiltState {
  private var tiltEnabled by mutableStateOf(false)
  var isTiltAvailable by mutableStateOf(true)
    private set
  var lightPosition by mutableStateOf(GLASS_TILT_FIXED_LIGHT_POSITION)
    private set

  val isTiltEnabled: Boolean
    get() = tiltEnabled

  fun updateTiltEnabled(enabled: Boolean) {
    tiltEnabled = enabled && isTiltAvailable
    restartFromFixedPosition()
  }

  fun setTiltUnavailable() {
    isTiltAvailable = false
    tiltEnabled = false
    restartFromFixedPosition()
  }

  fun restartFromFixedPosition() {
    lightPosition = GLASS_TILT_FIXED_LIGHT_POSITION
  }

  fun onGravity(gravity: Offset, displayRotation: GlassTiltDisplayRotation) {
    if (!isTiltEnabled || !gravity.isFinite()) {
      restartFromFixedPosition()
      return
    }
    val mappedGravity = mapGlassTiltGravity(gravity, displayRotation)
    if (!mappedGravity.isFinite()) {
      restartFromFixedPosition()
      return
    }
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
  val isAvailable: Boolean

  fun start(onGravity: (Offset) -> Unit): Boolean

  fun stop()
}

private fun Offset.isFinite(): Boolean = x.isFinite() && y.isFinite()
