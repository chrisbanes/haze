// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kashif.cameraK.compose.CameraPreviewView
import com.kashif.cameraK.controller.CameraController

@Composable
internal actual fun KameraPreview(
  controller: CameraController,
  modifier: Modifier,
) {
  CameraPreviewView(
    controller = controller,
    modifier = modifier,
  )
}
