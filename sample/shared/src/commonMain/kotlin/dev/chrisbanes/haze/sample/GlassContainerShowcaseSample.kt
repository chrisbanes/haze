// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.hazeGlassContainer
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PHOTO_COUNT = 48

// Photos selected by the scripted loop. They sit in the upper rows of a landscape window.
private val ScriptedSelection = setOf(8, 9, 16)

private val BarHeight = 64.dp
private val CompactBarHeight = 56.dp
private val BarGap = 6.dp
private val TabsPillWidth = 360.dp
private val SelectionPillWidth = 208.dp
private val SearchTravel = 120.dp

// Search must move more than half the container spacing to split away from the tabs.
private val MinSearchTravel = 24.dp
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
    private set

  /**
   * Search's distance from the tabs, as a fraction of how far the script moves it. The script and
   * dragging both move it, so Search can be dragged back after the script is interrupted.
   */
  public var searchOffset: Float by mutableFloatStateOf(0f)
    private set
  internal var autoplayGeneration by mutableIntStateOf(0)
    private set

  public fun progress(): Float = currentProgress

  /** Moves the scripted loop to [progress], entering or leaving selection as the script does. */
  public fun seek(progress: Float) {
    require(progress.isFinite() && progress in 0f..1f)
    currentProgress = progress
    val frame = glassContainerShowcaseFrame(progress)
    searchOffset = frame.searchDetachFraction
    if (frame.selecting != selecting) applySelecting(frame.selecting, ScriptedSelection)
  }

  public fun togglePlayback() {
    isPlaying = !isPlaying
  }

  public fun pause() {
    isPlaying = false
  }

  /**
   * Hands the scene to the user, so the script can't undo what they do. Every user action calls
   * this, including keyboard and accessibility actions, which send no pointer press.
   */
  public fun takeOver() {
    pause()
    recordingMode = false
  }

  public fun updateRecordingMode(value: Boolean) {
    recordingMode = value
  }

  public fun updateSelecting(value: Boolean, initialSelection: Set<Int> = emptySet()) {
    takeOver()
    applySelecting(value, initialSelection)
  }

  /** Entering selection replaces the selection. Leaving keeps it, so the count doesn't change while it fades. */
  private fun applySelecting(value: Boolean, initialSelection: Set<Int>) {
    if (value && !selecting) selectedPhotos = initialSelection
    selecting = value
  }

  public fun togglePhoto(index: Int) {
    takeOver()
    selectedPhotos = if (index in selectedPhotos) selectedPhotos - index else selectedPhotos + index
  }

  public fun selectTab(index: Int) {
    takeOver()
    selectedTab = index
  }

  public fun dragSearchBy(delta: Float, maxOffset: Float) {
    takeOver()
    searchOffset = (searchOffset + delta).coerceIn(0f, maxOffset)
  }

  public suspend fun settleSearch() {
    animate(
      initialValue = searchOffset,
      targetValue = 0f,
      animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
    ) { value, _ -> searchOffset = value }
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
    searchOffset = 0f
    recordingMode = false
    isPlaying = true
    autoplayGeneration++
  }

  internal fun disableAutoplay() {
    currentProgress = 0f
    searchOffset = 0f
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
  // Where the title block ends, which grows with the user's font size.
  var titleBottom by remember { mutableStateOf(0.dp) }

  BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
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
                state.takeOver()
              }
            }
          }
        },
    ) {
      PhotoLibrary(
        hazeState = hazeState,
        state = state,
        onTitleBottomChange = { titleBottom = it },
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
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(bottom = 28.dp),
      )
    }

    // On narrow windows, such as phones, the bar spans the bottom, so the controls go below the
    // title instead.
    val controlsBesideBar = maxWidth >= 900.dp
    AnimatedVisibility(
      visible = !state.recordingMode,
      modifier = if (controlsBesideBar) {
        Modifier
          .align(Alignment.BottomStart)
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(24.dp)
      } else {
        Modifier
          .align(Alignment.TopStart)
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
          .padding(start = 24.dp, top = titleBottom + 12.dp)
      },
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
  onTitleBottomChange: (Dp) -> Unit,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
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
    Box(
      Modifier
        .fillMaxWidth()
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
        .padding(bottom = 48.dp),
    ) {
      Column(
        Modifier
          // Measured before the insets and padding, so the size is the title's bottom edge.
          .onSizeChanged { onTitleBottomChange(with(density) { it.height.toDp() }) }
          .windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(start = 32.dp, top = 24.dp, end = 32.dp),
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
  modifier: Modifier = Modifier,
) = BoxWithConstraints(modifier) {
  // Narrow windows, such as phones, shrink the pills so that the bar and Search's split fit.
  val available = maxWidth - 32.dp
  val memberSize = if (available >= TabsPillWidth + BarGap + BarHeight + SearchTravel * 2) BarHeight else CompactBarHeight
  val tabsPillWidth = minOf(TabsPillWidth, available - BarGap - memberSize - MinSearchTravel * 2).coerceAtLeast(160.dp)
  val selectionPillWidth = minOf(SelectionPillWidth, available - (BarGap + memberSize) * 3).coerceAtLeast(120.dp)
  val restWidth = tabsPillWidth + BarGap + memberSize
  // The bar is centred on its wider row, the tabs with Search or the selection with its actions.
  val barWidth = maxOf(restWidth, selectionPillWidth + (BarGap + memberSize) * 3)
  val maxSearchTravel = ((available - barWidth) / 2 + barWidth - restWidth).coerceAtLeast(MinSearchTravel)
  val searchTravel = minOf(SearchTravel, maxSearchTravel)

  val selecting = state.selecting

  // Members join and leave one at a time. The bar has at most four members in a cluster, so
  // Search finishes retracting before the actions grow, and the actions leave before Search returns.
  var compactPill by remember { mutableStateOf(selecting) }
  var searchVisible by remember { mutableStateOf(!selecting) }
  var visibleActions by remember { mutableIntStateOf(if (selecting) 3 else 0) }
  // A new drag, resuming playback or a reset takes Search over from its return spring, so the
  // two don't both move it.
  val scope = rememberCoroutineScope()
  var settleJob by remember { mutableStateOf<Job?>(null) }
  LaunchedEffect(state.isPlaying, state.autoplayGeneration) {
    if (state.isPlaying) settleJob?.cancel()
  }

  LaunchedEffect(selecting) {
    if (selecting) {
      // Search may have been dragged or split away. Send it home as it leaves, so it returns
      // next to the tabs when selection ends.
      settleJob?.cancel()
      settleJob = scope.launch { state.settleSearch() }
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
    targetValue = if (compactPill) selectionPillWidth else tabsPillWidth,
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

  val searchTravelPx = with(LocalDensity.current) { searchTravel.toPx() }
  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    // Anchor the bar's start, so members joining or leaving at the end don't shift the tabs.
    Box(Modifier.width(barWidth), contentAlignment = Alignment.CenterStart) {
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
            .size(width = pillWidth, height = memberSize)
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
                tabWidth = (tabsPillWidth - 12.dp) / LibraryTabLabels.size,
                tabHeight = memberSize - 12.dp,
                selectedTab = state.selectedTab,
                onTabSelected = state::selectTab,
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
          Box(
            Modifier
              .testTag("glass_container_showcase_search")
              // Offset comes before hazeGlass, so the glass moves with the button.
              .offset { IntOffset((state.searchOffset * searchTravelPx).roundToInt(), 0) }
              .draggable(
                state = rememberDraggableState {
                  state.dragSearchBy(it / searchTravelPx, maxOffset = maxSearchTravel / searchTravel)
                },
                orientation = Orientation.Horizontal,
                interactionSource = interactionSource,
                onDragStarted = { settleJob?.cancel() },
                onDragStopped = { settleJob = scope.launch { state.settleSearch() } },
              )
              .size(memberSize)
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
                .size(memberSize)
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

@Poko
private class SelectionAction(val label: String, val icon: ImageVector, val tint: Color? = null)

private val SelectionActions = listOf(
  SelectionAction("Share", Icons.Filled.Share),
  SelectionAction("Favorite", Icons.Filled.Favorite),
  SelectionAction("Delete", Icons.Filled.Delete, tint = Color(0xFFFF453A)),
)

private val LibraryTabLabels = listOf("Library", "Albums", "For You")

@Composable
private fun LibraryTabs(
  tabWidth: Dp,
  tabHeight: Dp,
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
          .size(width = tabWidth, height = tabHeight)
          .clip(CircleShape)
          .background(if (selected) LocalContentColor.current.copy(alpha = 0.12f) else Color.Transparent)
          .clickable { onTabSelected(index) },
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          maxLines = 1,
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
