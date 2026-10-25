// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val InkColors = listOf(Color(0xFF587BDB), Color(0xFFE0806D), Color(0xFF5B9B8C), Color(0xFF383E4B))
private val InkNames = listOf("Blue", "Coral", "Sage", "Graphite")

@Stable
public class GlassContainerShowcaseState {
  private var currentProgress by mutableFloatStateOf(0f)
  public var expanded: Boolean by mutableStateOf(false)
    private set
  public var colorOffset: Float by mutableFloatStateOf(0f)
    private set
  public var colorIndex: Int by mutableIntStateOf(0)
    private set
  public var strokeIndex: Int by mutableIntStateOf(1)
    private set
  public var isPlaying: Boolean by mutableStateOf(true)
    private set
  public var isSlowMotion: Boolean by mutableStateOf(false)
    private set
  public var recordingMode: Boolean by mutableStateOf(false)
    private set
  internal var autoplayGeneration by mutableIntStateOf(0)
    private set
  internal val animationDurationScale: Float get() = if (isSlowMotion) 2f else 1f

  public fun seek(progress: Float) {
    val frame = glassContainerShowcaseFrame(progress)
    currentProgress = progress
    expanded = frame.expanded
    colorOffset = frame.colorDetachFraction
  }

  public fun pause() {
    isPlaying = false
  }
  public fun togglePlayback() {
    isPlaying = !isPlaying
  }
  public fun toggleSlowMotion() {
    isSlowMotion = !isSlowMotion
  }
  public fun updateRecordingMode(value: Boolean) {
    recordingMode = value
  }

  public fun takeOver() {
    pause()
    recordingMode = false
  }

  public fun toggleExpanded() {
    takeOver()
    expanded = !expanded
    colorOffset = 0f
  }

  public fun selectColor(index: Int) {
    require(index in InkColors.indices)
    takeOver()
    colorIndex = index
  }

  public fun cycleStroke() {
    takeOver()
    strokeIndex = (strokeIndex + 1) % 3
  }

  public fun dragColorBy(delta: Float) {
    takeOver()
    colorOffset = (colorOffset + delta).coerceIn(0f, 1f)
  }

  public suspend fun settleColor() {
    var animation = AnimationState(colorOffset)
    var previousScale = animationDurationScale
    do {
      val durationScale = animationDurationScale
      animation = animation.copy(velocity = animation.velocity * previousScale / durationScale)
      previousScale = durationScale
      animation.animateTo(
        targetValue = 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium / (durationScale * durationScale)),
        sequentialAnimation = true,
      ) {
        colorOffset = value
        if (durationScale != animationDurationScale) cancelAnimation()
      }
    } while (previousScale != animationDurationScale)
  }

  internal suspend fun runAutoplayLoop() {
    while (isPlaying) {
      animate(
        currentProgress,
        1f,
        animationSpec = tween(
          durationMillis = (SHOWCASE_LOOP_DURATION_MILLIS * animationDurationScale * (1f - currentProgress)).roundToInt(),
          easing = LinearEasing,
        ),
      ) { value, _ -> seek(value) }
      seek(0f)
    }
  }

  public fun reset() {
    seek(0f)
    colorIndex = 0
    strokeIndex = 1
    recordingMode = false
    isPlaying = true
    isSlowMotion = false
    autoplayGeneration++
  }

  internal fun disableAutoplay() {
    seek(0f)
    expanded = true
    isPlaying = false
  }
}

@Composable
public fun GlassContainerShowcaseSample(navController: NavHostController) {
  val state = remember { GlassContainerShowcaseState() }
  SampleChromeVisibilityEffect(visible = !state.recordingMode)
  LaunchedEffect(state.isPlaying, state.autoplayGeneration, state.isSlowMotion) {
    if (state.isPlaying && (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) <= 0f) {
      state.disableAutoplay()
    } else if (state.isPlaying) {
      state.runAutoplayLoop()
    }
  }
  GlassContainerShowcaseSampleContent(state, onBack = navController::navigateUp)
}

@Composable
public fun GlassContainerShowcaseSampleContent(
  state: GlassContainerShowcaseState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  Box(modifier.fillMaxSize().background(Color(0xFFF4F1E9))) {
    Box(
      Modifier.fillMaxSize().pointerInput(state) {
        awaitPointerEventScope {
          while (true) {
            if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) state.takeOver()
          }
        }
      },
    ) {
      Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
        drawRect(Color(0xFFF4F1E9))
        val secondStroke = Path().apply {
          moveTo(size.width * 0.10f, size.height * 0.24f)
          cubicTo(
            size.width * 0.34f,
            size.height * 0.92f,
            size.width * 0.66f,
            size.height * 0.10f,
            size.width * 0.90f,
            size.height * 0.72f,
          )
        }
        drawPath(secondStroke, Color(0xFFE2AB83), style = Stroke((24 + state.strokeIndex * 12).dp.toPx(), cap = StrokeCap.Round))
        val stroke = Path().apply {
          moveTo(size.width * 0.08f, size.height * 0.76f)
          cubicTo(
            size.width * 0.32f,
            size.height * 0.08f,
            size.width * 0.61f,
            size.height * 0.91f,
            size.width * 0.92f,
            size.height * 0.26f,
          )
        }
        drawPath(stroke, InkColors[state.colorIndex], style = Stroke((48 + state.strokeIndex * 24).dp.toPx(), cap = StrokeCap.Round))
      }
      DrawingPalette(state, hazeState, Modifier.align(Alignment.Center).safeDrawingPadding().padding(24.dp))
    }
    AnimatedVisibility(
      visible = !state.recordingMode,
      modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(24.dp),
    ) {
      DemoChrome(
        hazeState = hazeState,
        onBack = onBack,
        onEnterRecordingMode = { state.updateRecordingMode(true) },
        onReset = state::reset,
        isPlaying = state.isPlaying,
        onPlayPause = state::togglePlayback,
        playbackControls = {
          IconToggleButton(
            checked = state.isSlowMotion,
            onCheckedChange = { state.toggleSlowMotion() },
            modifier = Modifier.width(64.dp).semantics {
              contentDescription = "Slow motion"
              stateDescription = if (state.isSlowMotion) "Half speed" else "Normal speed"
            },
          ) {
            Text(if (state.isSlowMotion) "0.5×" else "1×", maxLines = 1)
          }
        },
      )
    }
  }
}

@Composable
private fun DrawingPalette(state: GlassContainerShowcaseState, hazeState: HazeState, modifier: Modifier) {
  val style = remember { GlassStyle.regular.then { shape(CircleShape) } }
  val foreground = Color(0xFF252B3A)
  val scope = rememberCoroutineScope()
  var settleJob by remember { mutableStateOf<Job?>(null) }
  LaunchedEffect(state.isPlaying, state.autoplayGeneration, state.expanded) { settleJob?.cancel() }
  val durationScale = state.animationDurationScale
  val fadeSpec = remember(durationScale) { spring<Float>(stiffness = Spring.StiffnessMediumLow / (durationScale * durationScale)) }
  val sizeSpec = remember(durationScale) {
    spring<IntSize>(stiffness = Spring.StiffnessMediumLow / (durationScale * durationScale), visibilityThreshold = IntSize.VisibilityThreshold)
  }
  val memberSpec = remember(durationScale) { spring<Float>(stiffness = Spring.StiffnessMedium / (durationScale * durationScale)) }

  BoxWithConstraints(modifier) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val regularPlaybackSpace = if (state.recordingMode) 0.dp else 96.dp
    val verticalHeight = (maxHeight - 64.dp - regularPlaybackSpace - 24.dp) / 3
    // Both Brush labels need two text lines; use the available width before clipping them.
    val minimumHeight = 48.dp * fontScale + 8.dp
    val vertical = maxWidth < 900.dp && (maxWidth < 600.dp || verticalHeight >= minimumHeight)
    val compactHorizontal = !vertical && maxWidth < 900.dp
    val compactVertical = vertical && verticalHeight < minimumHeight
    val playbackSpace = if (state.recordingMode) 0.dp else if (compactVertical) 64.dp else 96.dp
    val travel = if (compactVertical) 32.dp else if (vertical) 64.dp else if (compactHorizontal) 56.dp else 88.dp
    val bottomSpace = travel + playbackSpace
    val preferredHeight = (if (vertical || compactHorizontal) 88.dp else 104.dp) * fontScale
    // Keep room for all three controls, both gaps, detachment travel and the playback controls.
    val availableHeight = (maxHeight - bottomSpace - 24.dp) / 3
    val height = if (vertical) minOf(preferredHeight, availableHeight.coerceAtLeast(24.dp * fontScale + 16.dp)) else preferredHeight
    val compactWidth = maxWidth - travel - 24.dp
    val penWidth = if (vertical) minOf(248.dp, maxWidth) else if (compactHorizontal) compactWidth * 0.36f else 280.dp
    val strokeWidth = if (vertical) minOf(248.dp, maxWidth) else if (compactHorizontal) compactWidth * 0.32f else 232.dp
    val colorWidth = if (vertical) minOf(248.dp, maxWidth) else if (compactHorizontal) compactWidth * 0.32f else 256.dp
    val travelPx = with(LocalDensity.current) { travel.toPx() }
    val containerModifier = Modifier.hazeGlassContainer(mergeDistance = 40.dp, memberTransitionSpec = memberSpec)

    @Composable
    fun Members() {
      Row(
        Modifier.width(penWidth).height(height)
          .hazeGlass(input = HazeInput.Sources(hazeState), style = style)
          .clip(CircleShape)
          .clickable(role = Role.Button, onClickLabel = if (state.expanded) "Close tools" else "Open tools") { state.toggleExpanded() }
          .padding(horizontal = if (compactHorizontal) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compactHorizontal) 8.dp else 16.dp),
      ) {
        if (!vertical && !compactHorizontal) {
          Icon(Icons.Default.Edit, contentDescription = null, tint = foreground, modifier = Modifier.size(32.dp))
        }
        Column(Modifier.weight(1f)) {
          if (!compactHorizontal && !compactVertical) Text("INK", color = foreground.copy(alpha = 0.65f), fontSize = 11.sp, letterSpacing = 2.sp)
          Text("Brush", color = foreground, fontSize = if (vertical || compactHorizontal) 22.sp else 26.sp, fontWeight = FontWeight.Medium)
        }
        if (!compactHorizontal) {
          Icon(if (state.expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null, tint = foreground)
        }
      }
      AnimatedVisibility(
        state.expanded,
        enter = fadeIn(fadeSpec) + if (vertical) expandVertically(sizeSpec, clip = false) else expandHorizontally(sizeSpec, clip = false),
        exit = fadeOut(fadeSpec) + if (vertical) shrinkVertically(sizeSpec, clip = false) else shrinkHorizontally(sizeSpec, clip = false),
      ) {
        Column(
          Modifier.padding(start = if (vertical) 0.dp else 12.dp, top = if (vertical) 12.dp else 0.dp)
            .width(strokeWidth).height(height)
            .hazeGlass(input = HazeInput.Sources(hazeState), style = style)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Change stroke width") { state.cycleStroke() }
            .padding(horizontal = 28.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          Text("Stroke", color = foreground, fontSize = 16.sp)
          if (!compactVertical) {
            Canvas(Modifier.width(strokeWidth - 56.dp).height(24.dp)) {
              drawLine(foreground, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), (3 + state.strokeIndex * 3).dp.toPx(), StrokeCap.Round)
            }
          }
        }
      }
      AnimatedVisibility(
        state.expanded,
        enter = fadeIn(fadeSpec) + if (vertical) expandVertically(sizeSpec, clip = false) else expandHorizontally(sizeSpec, clip = false),
        exit = fadeOut(fadeSpec) + if (vertical) shrinkVertically(sizeSpec, clip = false) else shrinkHorizontally(sizeSpec, clip = false),
      ) {
        Row(
          Modifier.padding(start = if (vertical) 0.dp else 12.dp, top = if (vertical) 12.dp else 0.dp).offset {
            val offset = (state.colorOffset * travelPx).roundToInt()
            if (vertical) IntOffset(0, offset) else IntOffset(offset, 0)
          }
            .width(colorWidth).height(height)
            .draggable(
              state = rememberDraggableState { state.dragColorBy(it / travelPx) },
              orientation = if (vertical) Orientation.Vertical else Orientation.Horizontal,
              onDragStarted = { settleJob?.cancel() },
              onDragStopped = { settleJob = scope.launch { state.settleColor() } },
            )
            .hazeGlass(input = HazeInput.Sources(hazeState), style = style)
            .semantics {
              contentDescription = "Ink colors"
              customActions = listOf(
                CustomAccessibilityAction(if (state.colorOffset > 0f) "Rejoin palette" else "Detach palette") {
                  settleJob?.cancel()
                  state.dragColorBy(if (state.colorOffset > 0f) -1f else 1f)
                  true
                },
              )
            }
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          InkColors.forEachIndexed { index, color ->
            Canvas(
              Modifier.size(minOf(44.dp, (colorWidth - 32.dp) / 4)).clip(CircleShape).selectable(selected = state.colorIndex == index, role = Role.RadioButton, onClick = { state.selectColor(index) })
                .semantics { contentDescription = "${InkNames[index]} ink" },
            ) {
              val scale = size.minDimension / 44.dp.toPx()
              drawCircle(color, radius = 14.dp.toPx() * scale)
              if (state.colorIndex == index) drawCircle(foreground, radius = 19.dp.toPx() * scale, style = Stroke(1.5.dp.toPx() * scale))
            }
          }
        }
      }
    }

    if (vertical) {
      Column(containerModifier.padding(bottom = bottomSpace), horizontalAlignment = Alignment.CenterHorizontally) { Members() }
    } else {
      Row(
        containerModifier.padding(end = travel, bottom = if (compactHorizontal) playbackSpace else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) { Members() }
    }
  }
}
