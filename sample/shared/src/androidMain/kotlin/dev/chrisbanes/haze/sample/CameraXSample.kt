// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
internal fun CameraXSample(
  effect: SampleEffect,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var permissionGranted by remember(context) {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var permissionDenied by remember { mutableStateOf(false) }
  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    permissionGranted = granted
    permissionDenied = !granted
  }

  LifecycleResumeEffect(context) {
    permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
      PackageManager.PERMISSION_GRANTED
    if (permissionGranted) permissionDenied = false
    onPauseOrDispose { }
  }

  LaunchedEffect(permissionGranted, permissionDenied) {
    if (!permissionGranted && !permissionDenied) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  if (permissionGranted) {
    CameraXCamera(
      effect = effect,
      onBack = onBack,
      modifier = modifier,
    )
  } else {
    CameraXMessage(
      message = if (permissionDenied) {
        "Camera permission is required to run this sample."
      } else {
        "Requesting camera permission…"
      },
      onBack = onBack,
      modifier = modifier,
    )
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun CameraXCamera(
  effect: SampleEffect,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val hazeState = rememberHazeState()
  val cameraController = remember(context) {
    LifecycleCameraController(context).apply {
      cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }
  }
  var cameraError by remember { mutableStateOf<String?>(null) }

  DisposableEffect(cameraController, lifecycleOwner) {
    try {
      cameraController.bindToLifecycle(lifecycleOwner)
    } catch (error: RuntimeException) {
      cameraError = error.message ?: "The camera could not be started."
    }
    onDispose { cameraController.unbind() }
  }

  if (cameraError != null) {
    CameraXMessage(
      message = cameraError.orEmpty(),
      onBack = onBack,
      modifier = modifier,
    )
    return
  }

  val shape = RoundedCornerShape(24.dp)
  val glassBackgroundColor = MaterialTheme.colorScheme.surface
  val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)
  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    AndroidView(
      factory = { previewContext ->
        PreviewView(previewContext).apply {
          implementationMode = PreviewView.ImplementationMode.COMPATIBLE
          scaleType = PreviewView.ScaleType.FILL_CENTER
          controller = cameraController
        }
      },
      modifier = Modifier.fillMaxSize().hazeSource(hazeState),
    )

    Spacer(
      modifier = Modifier
        .size(200.dp)
        .align(Alignment.Center)
        .then(
          when (effect) {
            SampleEffect.Blur ->
              Modifier
                .clip(shape)
                .hazeBlur(
                  input = HazeInput.Sources(hazeState),
                  style = HazeMaterials.ultraThin(),
                )

            SampleEffect.Glass ->
              Modifier
                .hazeGlass(
                  input = HazeInput.Sources(hazeState),
                  style = GlassStyle.regular.then {
                    backgroundColor(glassBackgroundColor)
                    tint(glassTint)
                    shape(shape)
                  },
                )
                .clip(shape)
          },
        ),
    )

    CameraXBackButton(onBack = onBack)
  }
}

@Composable
private fun CameraXMessage(
  message: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = message,
      color = Color.White,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(32.dp),
    )
    CameraXBackButton(onBack = onBack)
  }
}

@Composable
private fun BoxScope.CameraXBackButton(onBack: () -> Unit) {
  Surface(
    modifier = Modifier
      .align(Alignment.TopStart)
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(16.dp),
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
    shadowElevation = 6.dp,
  ) {
    IconButton(
      onClick = onBack,
      modifier = Modifier.size(48.dp),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
      )
    }
  }
}
