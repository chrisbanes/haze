# Camera

Haze can apply Blur and Glass to a live camera preview when the preview draws into Compose, so
[`hazeSource`][hazeSource] can capture it.

This example uses [`HazeInput.Sources`][HazeInput.Sources], which captures exactly the content marked with
`hazeSource`. [`HazeInput.Backdrop`][HazeInput.Backdrop] reads only pixels drawn earlier in the same window, so it cannot
include a preview on a separate surface or window.

```kotlin
val hazeState = rememberHazeState()

Box {
  CameraPreview(
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(hazeState),
  )

  Box(
    modifier = Modifier
      .align(Alignment.Center)
      .size(200.dp)
      .hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeMaterials.ultraThin(),
      ),
  )
}
```

What matters is how `CameraPreview` renders. Wrapping a platform view in `AndroidView` does not by
itself make its pixels capturable.

## CameraX on Android

CameraX's `PreviewView` uses `ImplementationMode.PERFORMANCE` by default, which renders through a
`SurfaceView` when possible. A `SurfaceView` draws on its own surface, so Haze cannot capture it.

Use `ImplementationMode.COMPATIBLE`, which makes `PreviewView` use a `TextureView`:

```kotlin
val cameraController = remember(context) {
  LifecycleCameraController(context).apply {
    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
  }
}

DisposableEffect(cameraController, lifecycleOwner) {
  cameraController.bindToLifecycle(lifecycleOwner)
  onDispose { cameraController.unbind() }
}

AndroidView(
  factory = { previewContext ->
    PreviewView(previewContext).apply {
      implementationMode = PreviewView.ImplementationMode.COMPATIBLE
      scaleType = PreviewView.ScaleType.FILL_CENTER
      controller = cameraController
    }
  },
  modifier = Modifier
    .fillMaxSize()
    .hazeSource(hazeState),
)
```

Set `implementationMode` before attaching the controller. See the complete
[CameraX sample](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/CameraXSample.kt).

## Kamera

[Kamera](https://github.com/Kashif-E/Kamera) provides shared camera state for Android and Desktop.
The state and Haze layout can live in a source set shared by those targets:

```kotlin
val cameraState by rememberCameraKState(
  config = CameraConfiguration(
    cameraLens = CameraLens.BACK,
    aspectRatio = AspectRatio.RATIO_16_9,
  ),
)

when (val state = cameraState) {
  CameraKState.Initializing -> LoadingCamera()
  is CameraKState.Ready -> KameraPreview(
    controller = state.controller,
    modifier = Modifier
      .fillMaxSize()
      .hazeSource(hazeState),
  )
  is CameraKState.Error -> CameraError(state.message)
}
```

Kamera's Android preview also creates a default `PreviewView`, so use a small Android adapter that
selects the compatible implementation and binds Kamera's controller:

```kotlin
@Composable
actual fun KameraPreview(
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
```

On Desktop, Kamera's standard `CameraPreviewView` renders frames as a Compose `Image`, so it can be
used directly. The complete sample keeps the permission and camera-state handling alongside the
shared Haze layout:

- [Shared Kamera sample](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/kameraMain/kotlin/dev/chrisbanes/haze/sample/KameraSample.kt)
- [Android preview adapter](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/androidMain/kotlin/dev/chrisbanes/haze/sample/KameraPreview.android.kt)
- [Desktop preview adapter](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/jvmMain/kotlin/dev/chrisbanes/haze/sample/KameraPreview.jvm.kt)

## Platform support

| Integration | Haze support | Requirement |
| --- | --- | --- |
| CameraX on Android | Yes | Use `PreviewView.ImplementationMode.COMPATIBLE`. |
| Kamera on Android | Yes | Bind the controller to a compatible `PreviewView`. |
| Kamera on Desktop | Yes | Use Kamera's Compose-rendered preview. |
| Kamera on iOS | No | Its preview uses `UIKitViewController`, which Haze cannot capture. |

The same constraint applies to video players and other platform views. Choose a rendering path that
draws into Compose, such as ExoPlayer's `TextureView` mode, before marking it with `hazeSource`.

For iOS, see [UIKit interop on iOS](uikit-interop.md).

[HazeInput.Backdrop]: ../api/haze/dev.chrisbanes.haze/-haze-input/-backdrop/index.html
[HazeInput.Sources]: ../api/haze/dev.chrisbanes.haze/-haze-input/-sources/index.html
[hazeSource]: ../api/haze/dev.chrisbanes.haze/haze-source.html
