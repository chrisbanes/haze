// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kashif.cameraK.controller.CameraController

@Composable
internal actual fun KameraPreview(
  controller: CameraController,
  modifier: Modifier,
) {
  val context = LocalContext.current
  val previewView = remember(context) {
    PreviewView(context).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
    }
  }

  DisposableEffect(controller, previewView) {
    controller.bindCamera(previewView)
    onDispose {}
  }

  AndroidView(
    factory = { previewView },
    modifier = modifier,
  )
}
