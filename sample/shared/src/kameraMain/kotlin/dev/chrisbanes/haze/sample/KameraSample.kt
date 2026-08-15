// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.kashif.cameraK.compose.rememberCameraKState
import com.kashif.cameraK.controller.CameraController
import com.kashif.cameraK.enums.AspectRatio
import com.kashif.cameraK.enums.CameraLens
import com.kashif.cameraK.permissions.providePermissions
import com.kashif.cameraK.state.CameraConfiguration
import com.kashif.cameraK.state.CameraKState
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

internal val Kamera = Sample(
  route = "kamera",
  title = "Kamera",
  effects = listOf(SampleEffect.Blur, SampleEffect.Glass),
) { navController, effect ->
  KameraSample(
    effect = effect,
    onBack = navController::navigateUp,
  )
}

@Composable
internal fun KameraSample(
  effect: SampleEffect,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val permissions = providePermissions()
  var permissionGranted by remember(permissions) {
    mutableStateOf(permissions.hasCameraPermission())
  }
  var permissionDenied by remember { mutableStateOf(false) }

  LifecycleResumeEffect(permissions) {
    permissionGranted = permissions.hasCameraPermission()
    if (permissionGranted) permissionDenied = false
    onPauseOrDispose { }
  }

  if (!permissionGranted) {
    if (!permissionDenied) {
      permissions.RequestCameraPermission(
        onGranted = { permissionGranted = true },
        onDenied = { permissionDenied = true },
      )
    }
    KameraMessage(
      message = if (permissionDenied) {
        "Camera permission is required to run this sample."
      } else {
        "Requesting camera permission…"
      },
      onBack = onBack,
      modifier = modifier,
    )
    return
  }

  val configuration = remember {
    CameraConfiguration(
      cameraLens = CameraLens.BACK,
      aspectRatio = AspectRatio.RATIO_16_9,
    )
  }
  val cameraState by rememberCameraKState(config = configuration)

  when (val state = cameraState) {
    CameraKState.Initializing -> KameraMessage(
      message = "Starting camera…",
      onBack = onBack,
      showProgress = true,
      modifier = modifier,
    )

    is CameraKState.Ready -> KameraCamera(
      controller = state.controller,
      effect = effect,
      onBack = onBack,
      modifier = modifier,
    )

    is CameraKState.Error -> KameraMessage(
      message = state.message,
      onBack = onBack,
      modifier = modifier,
    )
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun KameraCamera(
  controller: CameraController,
  effect: SampleEffect,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val shape = RoundedCornerShape(24.dp)
  val glassBackgroundColor = MaterialTheme.colorScheme.surface
  val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.14f)

  Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
    KameraPreview(
      controller = controller,
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
                  style = GlassStyle {
                    backgroundColor(glassBackgroundColor)
                    tint(glassTint)
                    shape(shape)
                    optics(GlassOptics.Adaptive)
                  },
                )
                .clip(shape)
          },
        ),
    )

    CameraBackButton(onBack = onBack)
  }
}

@Composable
private fun KameraMessage(
  message: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  showProgress: Boolean = false,
) {
  Box(
    modifier = modifier.fillMaxSize().background(Color.Black),
    contentAlignment = Alignment.Center,
  ) {
    if (showProgress) {
      CircularProgressIndicator(modifier = Modifier.padding(bottom = 80.dp))
    }
    Text(
      text = message,
      color = Color.White,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(32.dp),
    )
    CameraBackButton(onBack = onBack)
  }
}

@Composable
private fun BoxScope.CameraBackButton(onBack: () -> Unit) {
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

@Composable
internal expect fun KameraPreview(
  controller: CameraController,
  modifier: Modifier = Modifier,
)
