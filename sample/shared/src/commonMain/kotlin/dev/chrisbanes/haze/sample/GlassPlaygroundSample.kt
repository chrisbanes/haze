// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.then
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val PLAYGROUND_LOOP_DURATION_MILLIS = 12_000

@Stable
internal class GlassPlaygroundState(
  private val loopDurationMillis: Int = PLAYGROUND_LOOP_DURATION_MILLIS,
) {
  private val progressAnimation = Animatable(0f)
  private val dragOffsets = mutableStateMapOf<GlassPlaygroundSurfaceId, Offset>()
  private val frozenProgress = mutableStateMapOf<GlassPlaygroundSurfaceId, Float>()
  private val returnFractions = mutableStateMapOf<GlassPlaygroundSurfaceId, Float>()

  var isPlaying by mutableStateOf(true)
    private set
  var recordingMode by mutableStateOf(false)
    private set
  var completedLoopCount by mutableIntStateOf(0)
    private set
  var autoplayGeneration by mutableIntStateOf(0)
    private set
  var activeSurface by mutableStateOf<GlassPlaygroundSurfaceId?>(null)
    private set

  fun progress(): Float = progressAnimation.value

  fun surfaceProgress(id: GlassPlaygroundSurfaceId): Float = frozenProgress[id] ?: progress()

  fun dragOffset(id: GlassPlaygroundSurfaceId): Offset = dragOffsets[id] ?: Offset.Zero

  fun returnFraction(id: GlassPlaygroundSurfaceId): Float =
    returnFractions[id] ?: if (id in frozenProgress) 1f else 0f

  fun togglePlayback() {
    isPlaying = !isPlaying
  }

  fun updateRecordingMode(value: Boolean) {
    recordingMode = value
  }

  fun beginDrag(id: GlassPlaygroundSurfaceId) {
    activeSurface = id
    frozenProgress[id] = progress()
    returnFractions.remove(id)
    dragOffsets.getOrPut(id) { Offset.Zero }
  }

  fun dragBy(id: GlassPlaygroundSurfaceId, delta: Offset) {
    if (activeSurface == id) dragOffsets[id] = dragOffset(id) + delta
  }

  suspend fun endDrag(id: GlassPlaygroundSurfaceId) {
    Animatable(1f).animateTo(
      targetValue = 0f,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
    ) {
      returnFractions[id] = value
    }
    dragOffsets.remove(id)
    frozenProgress.remove(id)
    returnFractions.remove(id)
    if (activeSurface == id) activeSurface = null
  }

  suspend fun runAutoplayLoop(loopLimit: Int = Int.MAX_VALUE) {
    var loops = 0
    while (isPlaying && loops < loopLimit) {
      val remaining = 1f - progressAnimation.value
      progressAnimation.animateTo(
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = (loopDurationMillis * remaining).roundToInt(),
          easing = LinearEasing,
        ),
      )
      progressAnimation.snapTo(0f)
      completedLoopCount++
      loops++
    }
  }

  suspend fun reset() {
    activeSurface = null
    dragOffsets.clear()
    frozenProgress.clear()
    returnFractions.clear()
    progressAnimation.snapTo(0f)
    completedLoopCount = 0
    isPlaying = true
    recordingMode = false
    autoplayGeneration++
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
  GlassPlaygroundSample(
    navController = navController,
    state = rememberGlassPlaygroundState(),
  )
}

@Composable
internal fun GlassPlaygroundSample(
  navController: NavHostController,
  state: GlassPlaygroundState,
  runAutoplay: suspend GlassPlaygroundState.() -> Unit = { runAutoplayLoop() },
) {
  val scope = rememberCoroutineScope()
  val returnJobs = remember { mutableMapOf<GlassPlaygroundSurfaceId, Job>() }

  LaunchedEffect(state.isPlaying, state.autoplayGeneration) {
    val animationsEnabled = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!animationsEnabled) {
      state.disableAutoplay()
    } else if (state.isPlaying) {
      runAutoplay(state)
    }
  }

  GlassPlaygroundSampleContent(
    progressProvider = state::progress,
    dragOffsetProvider = state::dragOffset,
    surfaceProgressProvider = state::surfaceProgress,
    returnFractionProvider = state::returnFraction,
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
  surfaceProgressProvider: (GlassPlaygroundSurfaceId) -> Float = { progressProvider() },
  returnFractionProvider: (GlassPlaygroundSurfaceId) -> Float = { 1f },
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
      surfaceProgressProvider = surfaceProgressProvider,
      returnFractionProvider = returnFractionProvider,
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
  surfaceProgressProvider: (GlassPlaygroundSurfaceId) -> Float,
  returnFractionProvider: (GlassPlaygroundSurfaceId) -> Float,
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
          surfaceProgressProvider = surfaceProgressProvider,
          returnFractionProvider = returnFractionProvider,
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
          frozenNormalizedCenter = glassPlaygroundFrame(surfaceProgressProvider(id)).position(id),
          returnFraction = returnFractionProvider(id),
          sceneSize = IntSize(constraints.maxWidth, constraints.maxHeight),
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
  frozenNormalizedCenter: Offset = normalizedCenter,
  returnFraction: Float = 1f,
  sceneSize: IntSize,
  dragOffset: Offset,
): Offset {
  val effectiveCenter = when (returnFraction) {
    0f -> normalizedCenter
    1f -> frozenNormalizedCenter
    else -> normalizedCenter + (frozenNormalizedCenter - normalizedCenter) * returnFraction
  }
  return Offset(
    x = (effectiveCenter.x * sceneSize.width) + dragOffset.x * returnFraction,
    y = (effectiveCenter.y * sceneSize.height) + dragOffset.y * returnFraction,
  )
}

internal fun resolvePlaygroundSurfaceLightPosition(
  normalizedLight: Offset,
  normalizedCenter: Offset,
  frozenNormalizedCenter: Offset = normalizedCenter,
  returnFraction: Float = 1f,
  sceneSize: IntSize,
  surfaceSize: IntSize,
  dragOffset: Offset,
): Offset {
  val center = resolvedPlaygroundSurfaceCenter(
    normalizedCenter = normalizedCenter,
    frozenNormalizedCenter = frozenNormalizedCenter,
    returnFraction = returnFraction,
    sceneSize = sceneSize,
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
  surfaceProgressProvider: (GlassPlaygroundSurfaceId) -> Float,
  returnFractionProvider: (GlassPlaygroundSurfaceId) -> Float,
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
  var lightPosition by remember(id) { mutableStateOf(Offset.Unspecified) }
  val style = glassPlaygroundStyle(id).then {
    shape(glassPlaygroundShape(id))
    lightPosition(lightPosition)
  }.then(playgroundInteractionStyle())
  val latestProgressProvider by rememberUpdatedState(progressProvider)
  val latestSurfaceProgressProvider by rememberUpdatedState(surfaceProgressProvider)
  val latestReturnFractionProvider by rememberUpdatedState(returnFractionProvider)
  val latestSceneSizeProvider by rememberUpdatedState(sceneSizeProvider)
  val latestDragOffsetProvider by rememberUpdatedState(dragOffsetProvider)
  val latestOnDragStart by rememberUpdatedState(onDragStart)
  val latestOnDrag by rememberUpdatedState(onDrag)
  val latestOnDragEnd by rememberUpdatedState(onDragEnd)
  val zIndex = playgroundSurfaceZIndex(id, returnFractionProvider(id))

  LaunchedEffect(id, surfaceSize) {
    snapshotFlow {
      val frame = glassPlaygroundFrame(latestProgressProvider())
      val frozenFrame = glassPlaygroundFrame(latestSurfaceProgressProvider(id))
      resolvePlaygroundSurfaceLightPosition(
        normalizedLight = frame.lightPosition,
        normalizedCenter = frame.position(id),
        frozenNormalizedCenter = frozenFrame.position(id),
        returnFraction = latestReturnFractionProvider(id),
        sceneSize = latestSceneSizeProvider(),
        surfaceSize = surfaceSize,
        dragOffset = latestDragOffsetProvider(id),
      )
    }
      .distinctUntilChanged()
      .collect { lightPosition = it }
  }

  Box(
    modifier = Modifier
      .size(size)
      .hazeSource(hazeState, zIndex = zIndex)
      .zIndex(zIndex)
      .hazeGlass(
        input = HazeInput.Sources(hazeState),
        style = style,
        interactionSource = interactionSource,
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionTransformPivot = GlassTransformPivot.Pointer,
        interactionPositionAnimationSpec = GlassDefaults.positionAnimationSpec,
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
      )
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

internal fun playgroundInteractionStyle() = dev.chrisbanes.haze.glass.GlassStyle {
  hovered {}
  pressed {
    animate(toSpec = DefaultGlassPressAnimationSpec, fromSpec = DefaultGlassReleaseAnimationSpec) {
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

internal fun playgroundSurfaceZIndex(
  id: GlassPlaygroundSurfaceId,
  dragReturnFraction: Float,
): Float {
  val restingZIndex = 1f + id.ordinal
  val draggedZIndex = 1f + GlassPlaygroundSurfaceId.entries.size
  return lerp(restingZIndex, draggedZIndex, dragReturnFraction.coerceIn(0f, 1f))
}

@Composable
private fun SurfaceLabel(text: String) {
  Text(
    text = text,
    color = Color.White,
    modifier = Modifier.padding(20.dp),
  )
}
