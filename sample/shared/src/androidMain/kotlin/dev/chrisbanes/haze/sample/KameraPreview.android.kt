// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.kashif.cameraK.controller.CameraController

@Composable
internal actual fun KameraPreview(
  controller: CameraController,
  modifier: Modifier,
) {
  AndroidView(
    factory = { context ->
      PreviewView(context).also { previewView ->
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        controller.bindCamera(previewView)
      }
    },
    modifier = modifier,
  )
}
