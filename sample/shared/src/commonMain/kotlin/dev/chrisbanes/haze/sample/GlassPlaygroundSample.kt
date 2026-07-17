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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PLAYGROUND_LOOP_DURATION_MILLIS = 12_000

private class ReturnJob(var value: Job? = null)

internal class PlaygroundDragSession(
  private val onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  private val onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  private val onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
) {
  var activeSurface: GlassPlaygroundSurfaceId? = null
    private set

  fun start(id: GlassPlaygroundSurfaceId) {
    activeSurface = id
    onDragStart(id)
  }

  fun dragBy(delta: Offset) {
    activeSurface?.let { onDrag(it, delta) }
  }

  fun end() {
    activeSurface?.let { id ->
      activeSurface = null
      onDragEnd(id)
    }
  }
}

@Stable
internal class GlassPlaygroundState {
  private val progressAnimation = Animatable(0f)
  private val dragOffsets = mutableStateMapOf<GlassPlaygroundSurfaceId, Offset>()

  var isPlaying by mutableStateOf(true)
    private set
  var recordingMode by mutableStateOf(false)
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
    dragOffsets.putIfAbsent(id, Offset.Zero)
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
    }
  }

  suspend fun reset() {
    activeSurface = null
    dragOffsets.clear()
    progressAnimation.snapTo(0f)
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
    onPlayPause = state::togglePlayback,
    onReset = { scope.launch { state.reset() } },
    onRecordingModeChanged = state::updateRecordingMode,
    onBack = navController::navigateUp,
    onDragStart = state::beginDrag,
    onDrag = state::dragBy,
    onDragEnd = { id -> scope.launch { state.endDrag(id) } },
  )
}

@Composable
public fun GlassPlaygroundSampleContent(
  progressProvider: () -> Float,
  dragOffsetProvider: (GlassPlaygroundSurfaceId) -> Offset,
  isPlaying: Boolean,
  recordingMode: Boolean,
  onPlayPause: () -> Unit,
  onReset: () -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    GalleryBackdrop(
      hazeState = hazeState,
      artworkIndex = 0,
      backdrop = GlassGalleryBackdropId.Gallery,
      offsetProvider = { glassPlaygroundFrame(progressProvider()).backdropOffset },
      modifier = Modifier.fillMaxSize(),
    )

    PlaygroundSurfaceScene(
      hazeState = hazeState,
      progressProvider = progressProvider,
      dragOffsetProvider = dragOffsetProvider,
      sceneSizeProvider = { IntSize(constraints.maxWidth, constraints.maxHeight) },
      recordingMode = recordingMode,
      onRecordingModeChanged = onRecordingModeChanged,
      onDragStart = onDragStart,
      onDrag = onDrag,
      onDragEnd = onDragEnd,
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
  recordingMode: Boolean,
  onRecordingModeChanged: (Boolean) -> Unit,
  onDragStart: (GlassPlaygroundSurfaceId) -> Unit,
  onDrag: (GlassPlaygroundSurfaceId, Offset) -> Unit,
  onDragEnd: (GlassPlaygroundSurfaceId) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val returnJobs = remember {
    GlassPlaygroundSurfaceId.entries.associateWith { ReturnJob() }
  }
  val hitTest: (Offset) -> GlassPlaygroundSurfaceId? = { pointerPosition ->
    hitTestPlaygroundSurface(
      pointerPosition = pointerPosition,
      progress = progressProvider(),
      sceneSize = sceneSizeProvider(),
      density = density,
      dragOffsetProvider = dragOffsetProvider,
    )
  }
  val latestHitTest by rememberUpdatedState(hitTest)
  val latestRecordingMode by rememberUpdatedState(recordingMode)
  val latestOnRecordingModeChanged by rememberUpdatedState(onRecordingModeChanged)
  val latestOnDragStart by rememberUpdatedState(onDragStart)
  val latestOnDrag by rememberUpdatedState(onDrag)
  val latestOnDragEnd by rememberUpdatedState(onDragEnd)

  Layout(
    modifier = modifier
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val id = latestHitTest(down.position)
          if (id != null) {
            var overSlop = Offset.Zero
            val postSlop = awaitTouchSlopOrCancellation(down.id) { change, amount ->
              change.consume()
              overSlop = amount
            }
            if (postSlop != null) {
              returnJobs.getValue(id).value?.cancel()
              val session = PlaygroundDragSession(
                onDragStart = { latestOnDragStart(it) },
                onDrag = { surface, amount -> latestOnDrag(surface, amount) },
                onDragEnd = { surface ->
                  returnJobs.getValue(surface).value = scope.launch {
                    latestOnDragEnd(surface)
                  }
                },
              )
              session.start(id)
              try {
                session.dragBy(overSlop)
                drag(postSlop.id) { change ->
                  val amount = change.positionChange()
                  if (amount != Offset.Zero) {
                    change.consume()
                    session.dragBy(amount)
                  }
                }
              } finally {
                session.end()
              }
            }
          } else {
            val movement = awaitTouchSlopOrCancellation(down.id) { change, _ ->
              change.consume()
            }
            if (movement == null && latestRecordingMode) {
              latestOnRecordingModeChanged(false)
            }
          }
        }
      },
    content = {
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        PlaygroundSurface(
          id = id,
          hazeState = hazeState,
          progressProvider = progressProvider,
          sceneSizeProvider = sceneSizeProvider,
        )
      }
    },
  ) { measurables, constraints ->
    val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
    layout(constraints.maxWidth, constraints.maxHeight) {
      val frame = glassPlaygroundFrame(progressProvider())
      GlassPlaygroundSurfaceId.entries.zip(placeables).forEach { (id, placeable) ->
        val position = frame.position(id)
        val dragOffset = dragOffsetProvider(id)
        placeable.place(
          x = (position.x * constraints.maxWidth - placeable.width / 2f + dragOffset.x).roundToInt(),
          y = (position.y * constraints.maxHeight - placeable.height / 2f + dragOffset.y).roundToInt(),
        )
      }
    }
  }
}

internal fun hitTestPlaygroundSurface(
  pointerPosition: Offset,
  progress: Float,
  sceneSize: IntSize,
  density: Density,
  dragOffsetProvider: (GlassPlaygroundSurfaceId) -> Offset,
): GlassPlaygroundSurfaceId? {
  val frame = glassPlaygroundFrame(progress)
  return GlassPlaygroundSurfaceId.entries.reversed().firstOrNull { id ->
    val surfaceSize = playgroundSurfaceSize(id)
    val surfaceWidth = with(density) { surfaceSize.width.toPx() }
    val surfaceHeight = with(density) { surfaceSize.height.toPx() }
    val framePosition = frame.position(id)
    val dragOffset = dragOffsetProvider(id)
    val center = Offset(
      x = framePosition.x * sceneSize.width + dragOffset.x,
      y = framePosition.y * sceneSize.height + dragOffset.y,
    )
    pointerPosition.x in (center.x - surfaceWidth / 2f)..(center.x + surfaceWidth / 2f) &&
      pointerPosition.y in (center.y - surfaceHeight / 2f)..(center.y + surfaceHeight / 2f)
  }
}

@Composable
private fun PlaygroundSurface(
  id: GlassPlaygroundSurfaceId,
  hazeState: HazeState,
  progressProvider: () -> Float,
  sceneSizeProvider: () -> IntSize,
) {
  val size = playgroundSurfaceSize(id)
  val effect = remember(id) {
    GlassVisualEffect().apply {
      style = glassPlaygroundStyle(id)
      shape = glassPlaygroundShape(id)
    }
  }
  val latestProgressProvider by rememberUpdatedState(progressProvider)
  val latestSceneSizeProvider by rememberUpdatedState(sceneSizeProvider)

  LaunchedEffect(effect, id) {
    snapshotFlow { glassPlaygroundFrame(latestProgressProvider()).lightPosition }
      .distinctUntilChanged()
      .collect { normalized ->
        effect.lightPosition = Offset(
          x = normalized.x * latestSceneSizeProvider().width,
          y = normalized.y * latestSceneSizeProvider().height,
        )
      }
  }

  Box(
    modifier = Modifier
      .size(size)
      .hazeEffect(state = hazeState) { visualEffect = effect }
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

private fun playgroundSurfaceSize(id: GlassPlaygroundSurfaceId): DpSize = when (id) {
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
