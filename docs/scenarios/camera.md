# Camera

Haze can apply Blur and Glass to a live camera preview when the preview pixels are drawn into the
same Compose graphics layer that contains `hazeSource`.

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

The important part is how `CameraPreview` renders. A platform interop composable such as
`AndroidView` does not, by itself, guarantee that its pixels can be captured.

## CameraX on Android

CameraX's `PreviewView` uses `ImplementationMode.PERFORMANCE` by default. When possible, that mode
renders through a `SurfaceView`. A `SurfaceView` owns a separate Android surface, so its pixels are
not present when Haze captures the Compose source layer.

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
| Kamera on iOS | No | Its preview uses `UIKitViewController`, which is outside Haze's captured Compose layer. |

The same constraint applies to video players and other platform views. Choose a rendering path that
draws into the Compose layer—for example, ExoPlayer's `TextureView` mode—before marking it with
`hazeSource`.
