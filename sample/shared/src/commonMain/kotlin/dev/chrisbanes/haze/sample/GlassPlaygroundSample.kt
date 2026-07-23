// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PLAYGROUND_LOOP_DURATION_MILLIS = 12_000

@Stable
internal class GlassPlaygroundState {
  private val progressAnimation = Animatable(0f)
  private val dragOffsets = mutableStateMapOf<GlassPlaygroundSurfaceId, Offset>()

  var isPlaying by mutableStateOf(true)
    private set
  var recordingMode by mutableStateOf(false)
    private set
  var completedLoopCount by mutableIntStateOf(0)
    private set
  var activeSurface by mutableStateOf<GlassPlaygroundSurfaceId?>(null)
    private set

  fun progress(): Float = progressAnimation.value

  fun dragOffset(id: GlassPlaygroundSurfaceId): Offset = dragOffsets[id] ?: Offset.Zero

  fun togglePlayback() {
    isPlaying = !isPlaying
  }

  fun updateRecordingMode(value: Boolean) {
    recordingMode = value
  }

  fun beginDrag(id: GlassPlaygroundSurfaceId) {
    activeSurface = id
    dragOffsets.getOrPut(id) { Offset.Zero }
  }

  fun dragBy(id: GlassPlaygroundSurfaceId, delta: Offset) {
    if (activeSurface == id) dragOffsets[id] = dragOffset(id) + delta
  }

  suspend fun endDrag(id: GlassPlaygroundSurfaceId) {
    val start = dragOffset(id)
    Animatable(start, Offset.VectorConverter).animateTo(
      targetValue = Offset.Zero,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
    ) {
      dragOffsets[id] = value
    }
    dragOffsets.remove(id)
    if (activeSurface == id) activeSurface = null
  }

  suspend fun runAutoplayLoop() {
    while (isPlaying && activeSurface == null) {
      val remaining = 1f - progressAnimation.value
      progressAnimation.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = (PLAYGROUND_LOOP_DURATION_MILLIS * remaining).roundToInt(),
          easing = LinearEasing,
        ),
      )
      progressAnimation.snapTo(0f)
      completedLoopCount++
    }
  }

  suspend fun reset() {
    activeSurface = null
    dragOffsets.clear()
    progressAnimation.snapTo(0f)
    completedLoopCount = 0
    isPlaying = true
    recordingMode = false
  }

  suspend fun disableAutoplay() {
    progressAnimation.snapTo(0f)
    isPlaying = false
  }
}

@Composable
internal fun rememberGlassPlaygroundState(): GlassPlaygroundState = remember { GlassPlaygroundState() }

@Composable
public fun GlassPlaygroundSample(navController: NavHostController) {
  val state = rememberGlassPlaygroundState()
  val scope = rememberCoroutineScope()
  val returnJobs = remember { mutableMapOf<GlassPlaygroundSurfaceId, Job>() }

  LaunchedEffect(state.isPlaying, state.activeSurface) {
    val animationsEnabled = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!animationsEnabled) {
      state.disableAutoplay()
    } else if (state.isPlaying && state.activeSurface == null) {
      state.runAutoplayLoop()
    }
  }

  GlassPlaygroundSampleContent(
    progressProvider = state::progress,
    dragOffsetProvider = state::dragOffset,
    isPlaying = state.isPlaying,
    recordingMode = state.recordingMode,
    completedLoopCount = state.completedLoopCount,
    onPlayPause = state::togglePlayback,
    onReset = {
      returnJobs.values.forEach(Job::cancel)
      returnJobs.clear()
      scope.launch { state.reset() }
    },
    onRecordingModeChanged = state::updateRecordingMode,
    onBack = navController::navigateUp,
    onDragStart = { id ->
      returnJobs.remove(id)?.cancel()
      state.beginDrag(id)
    },
    onDrag = state::dragBy,
    onDragEnd = { id ->
      returnJobs.remove(id)?.cancel()
      returnJobs[id] = scope.launch { state.endDrag(id) }
    },
  )
}

@Composable
public fun GlassPlaygroundSampleContent(
  progressProvider: () -> Float,
  dragOffsetProvider: (GlassPlaygroundSurfaceId) -> Offset,
  isPlaying: Boolean,
  recordingMode: Boolean,
  completedLoopCount: Int = 0,
  onPlayPause: () -> Unit,
  onReset: () -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
  interactionSourceProvider: (GlassPlaygroundSurfaceId) -> InteractionSource? = { null },
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .testTag("glass_playground_loop_$completedLoopCount"),
  ) {
    GalleryBackdrop(
      hazeState = hazeState,
      artworkIndex = 0,
      backdrop = GlassGalleryBackdropId.Gallery,
      offsetProvider = { glassPlaygroundFrame(progressProvider()).backdropOffset },
      horizontalOverscanFraction = 0.08f,
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(recordingMode) {
          detectTapGestures {
            if (recordingMode) onRecordingModeChanged(false)
          }
        },
    )

    PlaygroundSurfaceScene(
      hazeState = hazeState,
      progressProvider = progressProvider,
      dragOffsetProvider = dragOffsetProvider,
      sceneSizeProvider = { IntSize(constraints.maxWidth, constraints.maxHeight) },
      onDragStart = onDragStart,
      onDrag = onDrag,
      onDragEnd = onDragEnd,
      interactionSourceProvider = interactionSourceProvider,
      modifier = Modifier.fillMaxSize(),
    )

    AnimatedVisibility(
      visible = !recordingMode,
      modifier = Modifier.align(Alignment.TopStart).padding(24.dp),
    ) {
      DemoChrome(
        hazeState = hazeState,
        onBack = onBack,
        onEnterRecordingMode = { onRecordingModeChanged(true) },
        isPlaying = isPlaying,
        onPlayPause = onPlayPause,
        onReset = onReset,
      )
    }
  }
}

@Composable
private fun PlaygroundSurfaceScene(
  hazeState: HazeState,
  progressProvider: () -> Float,
  dragOffsetProvider: (GlassPlaygroundSurfaceId) -> Offset,
  sceneSizeProvider: () -> IntSize,
  onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
  interactionSourceProvider: (GlassPlaygroundSurfaceId) -> InteractionSource?,
  modifier: Modifier = Modifier,
) {
  Layout(
    modifier = modifier,
    content = {
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        PlaygroundSurface(
          id = id,
          hazeState = hazeState,
          progressProvider = progressProvider,
          sceneSizeProvider = sceneSizeProvider,
          dragOffsetProvider = dragOffsetProvider,
          onDragStart = onDragStart,
          onDrag = onDrag,
          onDragEnd = onDragEnd,
          interactionSourceProvider = interactionSourceProvider,
        )
      }
    },
  ) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      val frame = glassPlaygroundFrame(progressProvider())
      GlassPlaygroundSurfaceId.entries.zip(placeables).forEach { (id, placeable) ->
        val center = resolvedPlaygroundSurfaceCenter(
          normalizedCenter = frame.position(id),
          sceneSize = IntSize(constraints.maxWidth, constraints.maxHeight),
          surfaceSize = IntSize(placeable.width, placeable.height),
          dragOffset = dragOffsetProvider(id),
        )
        placeable.place(
          x = (center.x - placeable.width / 2f).roundToInt(),
          y = (center.y - placeable.height / 2f).roundToInt(),
        )
      }
    }
  }
}

internal fun resolvedPlaygroundSurfaceCenter(
  normalizedCenter: Offset,
  sceneSize: IntSize,
  surfaceSize: IntSize,
  dragOffset: Offset,
): Offset {
  val halfWidth = surfaceSize.width / 2f
  val halfHeight = surfaceSize.height / 2f
  val minCenterX = minOf(halfWidth, sceneSize.width / 2f)
  val maxCenterX = maxOf(sceneSize.width - halfWidth, sceneSize.width / 2f)
  val minCenterY = minOf(halfHeight, sceneSize.height / 2f)
  val maxCenterY = maxOf(sceneSize.height - halfHeight, sceneSize.height / 2f)
  return Offset(
    x = (normalizedCenter.x * sceneSize.width).coerceIn(minCenterX, maxCenterX) + dragOffset.x,
    y = (normalizedCenter.y * sceneSize.height).coerceIn(minCenterY, maxCenterY) + dragOffset.y,
  )
}

internal fun resolvePlaygroundSurfaceLightPosition(
  normalizedLight: Offset,
  normalizedCenter: Offset,
  sceneSize: IntSize,
  surfaceSize: IntSize,
  dragOffset: Offset,
): Offset {
  val center = resolvedPlaygroundSurfaceCenter(
    normalizedCenter = normalizedCenter,
    sceneSize = sceneSize,
    surfaceSize = surfaceSize,
    dragOffset = dragOffset,
  )
  val surfaceOrigin = center - Offset(surfaceSize.width / 2f, surfaceSize.height / 2f)
  return Offset(
    x = normalizedLight.x * sceneSize.width,
    y = normalizedLight.y * sceneSize.height,
  ) - surfaceOrigin
}

@Composable
private fun PlaygroundSurface(
  id: GlassPlaygroundSurfaceId,
  hazeState: HazeState,
  progressProvider: () -> Float,
  sceneSizeProvider: () -> IntSize,
  dragOffsetProvider: (GlassPlaygroundSurfaceId) -> Offset,
  onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
  interactionSourceProvider: (GlassPlaygroundSurfaceId) -> InteractionSource?,
) {
  val size = playgroundSurfaceSize(id)
  val density = LocalDensity.current
  val surfaceSize = with(density) {
    IntSize(size.width.roundToPx(), size.height.roundToPx())
  }
  val interactionSource = interactionSourceProvider(id)
  val effect = remember(id, interactionSource) {
    GlassVisualEffect().apply {
      style = glassPlaygroundStyle(id)
      shape = glassPlaygroundShape(id)
      configurePlaygroundInteraction(interactionSource)
    }
  }
  val latestProgressProvider by rememberUpdatedState(progressProvider)
  val latestSceneSizeProvider by rememberUpdatedState(sceneSizeProvider)
  val latestDragOffsetProvider by rememberUpdatedState(dragOffsetProvider)
  val latestOnDragStart by rememberUpdatedState(onDragStart)
  val latestOnDrag by rememberUpdatedState(onDrag)
  val latestOnDragEnd by rememberUpdatedState(onDragEnd)

  LaunchedEffect(effect, id, surfaceSize) {
    snapshotFlow {
      val frame = glassPlaygroundFrame(latestProgressProvider())
      resolvePlaygroundSurfaceLightPosition(
        normalizedLight = frame.lightPosition,
        normalizedCenter = frame.position(id),
        sceneSize = latestSceneSizeProvider(),
        surfaceSize = surfaceSize,
        dragOffset = latestDragOffsetProvider(id),
      )
    }
      .distinctUntilChanged()
      .collect { effect.lightPosition = it }
  }

  Box(
    modifier = Modifier
      .size(size)
      .hazeEffect(state = hazeState) { visualEffect = effect }
      .pointerInput(id) {
        detectDragGestures(
          onDragStart = { latestOnDragStart(id) },
          onDragEnd = { latestOnDragEnd(id) },
          onDragCancel = { latestOnDragEnd(id) },
        ) { change, amount ->
          change.consume()
          latestOnDrag(id, amount)
        }
      }
      .testTag("glass_playground_${id.name.lowercase()}")
      .semantics { contentDescription = "${id.name} draggable glass surface" },
  ) {
    when (id) {
      GlassPlaygroundSurfaceId.Card -> SurfaceLabel("DEPTH")
      GlassPlaygroundSurfaceId.Prism -> SurfaceLabel("PRISM")
      GlassPlaygroundSurfaceId.Lens,
      GlassPlaygroundSurfaceId.Pill,
      -> Unit
    }
  }
}

internal fun GlassVisualEffect.configurePlaygroundInteraction(source: InteractionSource?) {
  interactionSource = source
  interactionTransformTarget = GlassTransformTarget.MaterialAndContent
  interactionTransformPivot = GlassTransformPivot.Pointer
  interactionPositionAnimationSpec = GlassDefaults.positionAnimationSpec
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.System
  hovered()
  pressed {
    animate(
      toSpec = GlassDefaults.pressAnimationSpec,
      fromSpec = GlassDefaults.releaseAnimationSpec,
    ) {
      lightingIntensity(1f)
      refractionMultiplier(1.08f)
      scale(0.98f)
    }
  }
}

internal fun playgroundSurfaceSize(id: GlassPlaygroundSurfaceId): DpSize = when (id) {
  GlassPlaygroundSurfaceId.Lens -> DpSize(128.dp, 128.dp)
  GlassPlaygroundSurfaceId.Pill -> DpSize(220.dp, 88.dp)
  GlassPlaygroundSurfaceId.Card -> DpSize(280.dp, 180.dp)
  GlassPlaygroundSurfaceId.Prism -> DpSize(180.dp, 112.dp)
}

@Composable
private fun SurfaceLabel(text: String) {
  androidx.compose.material3.Text(
    text = text,
    color = Color.White,
    modifier = Modifier.padding(20.dp),
  )
}
