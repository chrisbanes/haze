// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PHOTO_COUNT = 48

// Photos selected by the scripted loop. They sit in the upper rows of a landscape window.
private val ScriptedSelection = setOf(8, 9, 16)

private val BarHeight = 64.dp
private val BarGap = 6.dp
private val TabsPillWidth = 360.dp
private val SelectionPillWidth = 208.dp
private val SearchTravel = 120.dp
private val GridDriftDistance = 160.dp

@Stable
public class GlassContainerShowcaseState {
  private var currentProgress by mutableFloatStateOf(0f)

  public var isPlaying: Boolean by mutableStateOf(true)
    private set
  public var recordingMode: Boolean by mutableStateOf(false)
    private set
  public var selecting: Boolean by mutableStateOf(false)
    private set
  public var selectedPhotos: Set<Int> by mutableStateOf(emptySet())
    private set
  public var selectedTab: Int by mutableIntStateOf(0)
  public var searchDragOffset: Float by mutableFloatStateOf(0f)
    private set
  internal var autoplayGeneration by mutableIntStateOf(0)
    private set

  public fun progress(): Float = currentProgress

  /** Moves the scripted loop to [progress], entering or leaving selection as the script does. */
  public fun seek(progress: Float) {
    require(progress.isFinite() && progress in 0f..1f)
    currentProgress = progress
    val frameSelecting = glassContainerShowcaseFrame(progress).selecting
    if (frameSelecting != selecting) updateSelecting(frameSelecting, ScriptedSelection)
  }

  public fun togglePlayback() {
    isPlaying = !isPlaying
  }

  public fun pause() {
    isPlaying = false
  }

  public fun updateRecordingMode(value: Boolean) {
    recordingMode = value
  }

  /** Entering selection replaces the selection. Leaving keeps it, so the count doesn't change while it fades. */
  public fun updateSelecting(value: Boolean, initialSelection: Set<Int> = emptySet()) {
    if (value && !selecting) selectedPhotos = initialSelection
    selecting = value
  }

  public fun togglePhoto(index: Int) {
    selectedPhotos = if (index in selectedPhotos) selectedPhotos - index else selectedPhotos + index
  }

  public fun dragSearchBy(delta: Float, maxOffset: Float) {
    searchDragOffset = (searchDragOffset + delta).coerceIn(0f, maxOffset)
  }

  public suspend fun settleSearch() {
    animate(
      initialValue = searchDragOffset,
      targetValue = 0f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
    ) { value, _ -> searchDragOffset = value }
  }

  internal suspend fun runAutoplayLoop() {
    while (isPlaying) {
      animate(
        initialValue = currentProgress,
        targetValue = 1f,
        animationSpec = tween(
          durationMillis = (SHOWCASE_LOOP_DURATION_MILLIS * (1f - currentProgress)).roundToInt(),
          easing = LinearEasing,
        ),
      ) { value, _ -> seek(value) }
      seek(0f)
    }
  }

  public fun reset() {
    currentProgress = 0f
    selecting = false
    selectedPhotos = emptySet()
    selectedTab = 0
    searchDragOffset = 0f
    recordingMode = false
    isPlaying = true
    autoplayGeneration++
  }

  internal fun disableAutoplay() {
    currentProgress = 0f
    isPlaying = false
  }
}

@Composable
public fun GlassContainerShowcaseSample(navController: NavHostController) {
  val state = remember { GlassContainerShowcaseState() }

  SampleChromeVisibilityEffect(visible = !state.recordingMode)

  LaunchedEffect(state.isPlaying, state.autoplayGeneration) {
    val animationsEnabled = (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
    if (!animationsEnabled) {
      state.disableAutoplay()
    } else if (state.isPlaying) {
      state.runAutoplayLoop()
    }
  }

  GlassContainerShowcaseSampleContent(state = state, onBack = navController::navigateUp)
}

@Composable
public fun GlassContainerShowcaseSampleContent(
  state: GlassContainerShowcaseState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val scope = rememberCoroutineScope()

  Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
    // Any press in the scene hands control to the user. The demo controls sit outside this box,
    // so pressing them doesn't pause the loop.
    Box(
      Modifier
        .fillMaxSize()
        .pointerInput(state) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent(PointerEventPass.Initial)
              if (event.type == PointerEventType.Press) {
                state.pause()
                state.updateRecordingMode(false)
              }
            }
          }
        },
    ) {
      PhotoLibrary(
        hazeState = hazeState,
        state = state,
        modifier = Modifier.fillMaxSize(),
      )

      SelectButton(
        hazeState = hazeState,
        selecting = state.selecting,
        onClick = { state.updateSelecting(!state.selecting) },
        modifier = Modifier
          .align(Alignment.TopEnd)
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(top = 32.dp, end = 72.dp),
      )

      LibraryBar(
        hazeState = hazeState,
        state = state,
        onSearchDragStopped = { scope.launch { state.settleSearch() } },
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(bottom = 28.dp),
      )
    }

    AnimatedVisibility(
      visible = !state.recordingMode,
      modifier = Modifier
        .align(Alignment.BottomStart)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(24.dp),
    ) {
      DemoChrome(
        hazeState = hazeState,
        onBack = onBack,
        onEnterRecordingMode = { state.updateRecordingMode(true) },
        isPlaying = state.isPlaying,
        onPlayPause = state::togglePlayback,
        onReset = state::reset,
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoLibrary(
  hazeState: HazeState,
  state: GlassContainerShowcaseState,
  modifier: Modifier = Modifier,
) {
  Box(modifier.hazeSource(hazeState)) {
    LazyVerticalGrid(
      columns = GridCells.Adaptive(minSize = 176.dp),
      contentPadding = PaddingValues(bottom = BarHeight + 56.dp),
      horizontalArrangement = Arrangement.spacedBy(3.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp),
      modifier = Modifier
        .testTag("glass_container_showcase_grid")
        .layout { measurable, constraints ->
          // Lay the grid out taller than the window and drift it, so photos keep moving under
          // the glass without leaving a gap at the bottom.
          val overscan = GridDriftDistance.roundToPx()
          val placeable = measurable.measure(
            constraints.copy(
              minHeight = constraints.maxHeight + overscan,
              maxHeight = constraints.maxHeight + overscan,
            ),
          )
          layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeWithLayer(0, 0) {
              translationY = -overscan * glassContainerShowcaseFrame(state.progress()).gridDrift
            }
          }
        },
    ) {
      items((0 until PHOTO_COUNT).toList(), key = { it }) { index ->
        val selected = state.selecting && index in state.selectedPhotos
        Box(
          Modifier
            .aspectRatio(1f)
            .combinedClickable(
              onClick = { if (state.selecting) state.togglePhoto(index) },
              onLongClick = { state.updateSelecting(true, setOf(index)) },
            ),
        ) {
          AsyncImage(
            model = randomSampleImageUrl(seed = 2026 + index, width = 480),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          val dim by animateFloatAsState(if (selected) 0.3f else 0f)
          Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dim)))
          AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
          ) {
            Icon(
              Icons.Filled.CheckCircle,
              contentDescription = "Selected",
              tint = Color(0xFF0A84FF),
              modifier = Modifier
                .size(28.dp)
                .background(Color.White, CircleShape),
            )
          }
        }
      }
    }

    // A scrim keeps the title readable over any photo, in both themes.
    Column(
      Modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(start = 32.dp, top = 24.dp, end = 32.dp, bottom = 48.dp),
    ) {
      Text(
        "Library",
        color = Color.White,
        style = MaterialTheme.typography.displaySmall,
        fontWeight = FontWeight.Bold,
      )
      Text(
        "$PHOTO_COUNT photos",
        color = Color.White.copy(alpha = 0.8f),
        style = MaterialTheme.typography.titleMedium,
      )
    }
  }
}

@Composable
private fun SelectButton(
  hazeState: HazeState,
  selecting: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val style = GlassStyle.regular.material3()
  val shapedStyle = remember(style) { style.then { shape(CircleShape) } }
  Box(
    modifier = modifier
      .height(44.dp)
      .hazeGlass(input = HazeInput.Sources(hazeState), style = shapedStyle)
      .clip(CircleShape)
      .clickable(onClick = onClick)
      .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      if (selecting) "Cancel" else "Select",
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.labelLarge,
    )
  }
}

@Composable
private fun LibraryBar(
  hazeState: HazeState,
  state: GlassContainerShowcaseState,
  onSearchDragStopped: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val selecting = state.selecting

  // Members join and leave one at a time. The bar has at most four members in a cluster, so
  // Search finishes retracting before the actions grow, and the actions leave before Search returns.
  var compactPill by remember { mutableStateOf(selecting) }
  var searchVisible by remember { mutableStateOf(!selecting) }
  var visibleActions by remember { mutableIntStateOf(if (selecting) 3 else 0) }
  LaunchedEffect(selecting) {
    if (selecting) {
      searchVisible = false
      compactPill = true
      delay(550)
      repeat(3) {
        visibleActions = it + 1
        delay(180)
      }
    } else {
      while (visibleActions > 0) {
        visibleActions--
        delay(140)
      }
      delay(150)
      compactPill = false
      delay(350)
      searchVisible = true
    }
  }

  val pillWidth by animateDpAsState(
    targetValue = if (compactPill) SelectionPillWidth else TabsPillWidth,
    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
  )

  // Styles compare by identity, so remember each one. Every member shares one material, so they
  // can merge; only the shape and press response differ.
  val baseStyle = GlassStyle.regular.material3()
  val pillStyle = remember(baseStyle) { baseStyle.then { shape(CircleShape) } }
  val buttonStyle = remember(baseStyle) {
    baseStyle.then {
      shape(CircleShape)
      pressed {
        lightingIntensity(1f)
        scale(0.94f)
      }
    }
  }

  val searchTravelPx = with(LocalDensity.current) { SearchTravel.toPx() }
  val currentOnSearchDragStopped by rememberUpdatedState(onSearchDragStopped)

  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    // Anchor the bar's start, so members joining or leaving at the end don't shift the tabs.
    Box(modifier.width(TabsPillWidth + BarGap + BarHeight), contentAlignment = Alignment.CenterStart) {
      Row(
        modifier = Modifier.hazeGlassContainer(
          spacing = 32.dp,
          transitionSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        ),
        horizontalArrangement = Arrangement.spacedBy(BarGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          Modifier
            .testTag("glass_container_showcase_pill")
            .size(width = pillWidth, height = BarHeight)
            .hazeGlass(input = HazeInput.Sources(hazeState), style = pillStyle)
            // This clip is inside the effect node, so it only constrains the pill's content.
            .clip(CircleShape),
          contentAlignment = Alignment.CenterStart,
        ) {
          Crossfade(targetState = selecting) { showSelection ->
            if (showSelection) {
              SelectionSummary(
                count = state.selectedPhotos.size,
                onClose = { state.updateSelecting(false) },
              )
            } else {
              LibraryTabs(
                selectedTab = state.selectedTab,
                onTabSelected = { state.selectedTab = it },
              )
            }
          }
        }

        AnimatedVisibility(
          visible = searchVisible,
          enter = fadeIn(tween(200)),
          exit = fadeOut(tween(120)),
        ) {
          val interactionSource = remember { MutableInteractionSource() }
          val searchOffset = { state.searchDragOffset + searchTravelPx * glassContainerShowcaseFrame(state.progress()).searchDetachFraction }
          Box(
            Modifier
              .testTag("glass_container_showcase_search")
              // Offset comes before hazeGlass, so the glass moves with the button.
              .offset { IntOffset(searchOffset().roundToInt(), 0) }
              .draggable(
                state = rememberDraggableState { state.dragSearchBy(it, maxOffset = searchTravelPx * 2) },
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                onDragStopped = { currentOnSearchDragStopped() },
              )
              .size(BarHeight)
              .hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = buttonStyle,
                interactionSource = interactionSource,
              )
              .clickable(interactionSource = interactionSource, indication = null) {},
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Filled.Search, contentDescription = "Search")
          }
        }

        SelectionActions.forEachIndexed { index, action ->
          AnimatedVisibility(
            visible = visibleActions > index,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(120)),
          ) {
            val interactionSource = remember { MutableInteractionSource() }
            Box(
              Modifier
                .size(BarHeight)
                .hazeGlass(
                  input = HazeInput.Sources(hazeState),
                  style = buttonStyle,
                  interactionSource = interactionSource,
                )
                .clickable(interactionSource = interactionSource, indication = null) {
                  state.updateSelecting(false)
                },
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                action.icon,
                contentDescription = action.label,
                tint = action.tint ?: LocalContentColor.current,
              )
            }
          }
        }
      }
    }
  }
}

private class SelectionAction(val label: String, val icon: ImageVector, val tint: Color? = null)

private val SelectionActions = listOf(
  SelectionAction("Share", Icons.Filled.Share),
  SelectionAction("Favorite", Icons.Filled.Favorite),
  SelectionAction("Delete", Icons.Filled.Delete, tint = Color(0xFFFF453A)),
)

private val LibraryTabLabels = listOf("Library", "Albums", "For You")

@Composable
private fun LibraryTabs(
  selectedTab: Int,
  onTabSelected: (Int) -> Unit,
) {
  Row(
    // The pill narrows while this fades out, so keep the tabs at full width and let the pill clip them.
    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).padding(6.dp),
  ) {
    LibraryTabLabels.forEachIndexed { index, label ->
      val selected = index == selectedTab
      Box(
        Modifier
          .size(width = 116.dp, height = BarHeight - 12.dp)
          .clip(CircleShape)
          .background(if (selected) LocalContentColor.current.copy(alpha = 0.12f) else Color.Transparent)
          .clickable { onTabSelected(index) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
      }
    }
  }
}

@Composable
private fun SelectionSummary(
  count: Int,
  onClose: () -> Unit,
) {
  Row(
    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).padding(start = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onClose) {
      Icon(Icons.Filled.Close, contentDescription = "End selection")
    }
    Text("$count Selected", style = MaterialTheme.typography.titleSmall)
  }
}
