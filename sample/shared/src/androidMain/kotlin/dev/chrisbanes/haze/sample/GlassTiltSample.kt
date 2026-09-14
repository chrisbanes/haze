// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.chrisbanes.haze.ExperimentalHazeApi

private class AndroidGlassTiltGravitySensor(context: Context) : GlassTiltGravitySensor, SensorEventListener {
  private val sensorManager = context.getSystemService(SensorManager::class.java)
  private val gravitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
  private var onGravity: ((Offset) -> Unit)? = null

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
  val context = LocalContext.current
  val state = remember { GlassTiltState() }
  val sensor = gravitySensor ?: remember(context) { AndroidGlassTiltGravitySensor(context) }
  val currentDisplayRotation = displayRotation ?: { currentGlassTiltDisplayRotation(context) }

  LifecycleResumeEffect(state.isTiltEnabled, sensor) {
    var registered = false
    if (state.isTiltEnabled) {
      state.restartFromFixedPosition()
      registered = sensor.start { gravity -> state.onGravity(gravity, currentDisplayRotation()) }
      if (!registered) {
        state.setTiltUnavailable()
      }
    }
    onPauseOrDispose {
      if (registered) sensor.stop()
    }
  }

  GlassTiltSampleContent(
    state = state,
    onFixed = { state.updateTiltEnabled(false) },
    onTilt = { state.updateTiltEnabled(true) },
    onBack = onBack,
    modifier = modifier,
  )
}

private fun currentGlassTiltDisplayRotation(context: Context): GlassTiltDisplayRotation = when (context.display.rotation) {
  Surface.ROTATION_90 -> GlassTiltDisplayRotation.Rotation90
  Surface.ROTATION_180 -> GlassTiltDisplayRotation.Rotation180
  Surface.ROTATION_270 -> GlassTiltDisplayRotation.Rotation270
  else -> GlassTiltDisplayRotation.Rotation0
}
