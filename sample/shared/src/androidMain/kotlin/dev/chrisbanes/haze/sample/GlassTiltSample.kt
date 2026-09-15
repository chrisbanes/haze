// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi

@Composable
internal fun GlassTiltSample(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  gravitySensor: GlassTiltGravitySensor? = null,
  displayRotation: (() -> GlassTiltDisplayRotation)? = null,
) {
  val integration = rememberAndroidGlassTiltIntegration(gravitySensor, displayRotation)

  GlassTiltSampleContent(
    lightPosition = integration.lightPositionState,
    isTiltAvailable = !integration.isTiltUnavailable,
    onFixed = { integration.updateTiltEnabled(false) },
    onTilt = { integration.updateTiltEnabled(true) },
    onBack = onBack,
    modifier = modifier,
  )
}
