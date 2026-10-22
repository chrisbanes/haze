// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeFeatureFlags
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.sin

/**
 * Four separate header controls over one busy feed source (issue #1611). Every row animates its
 * video cards for the whole measured window; scroll rows also scroll the feed at a fixed speed.
 */
internal enum class GlassFeedProfilingScenario(
  val id: String,
  val controls: FeedControls,
  val scrolls: Boolean,
) {
  VideoGlass("feed_video_glass", FeedControls.Glass, scrolls = false),
  VideoGlassOffscreen("feed_video_glass_offscreen", FeedControls.GlassOffscreenSource, scrolls = false),
  VideoPlain("feed_video_plain", FeedControls.Plain, scrolls = false),
  ScrollGlass("feed_scroll_glass", FeedControls.Glass, scrolls = true),
  ScrollGlassOffscreen("feed_scroll_glass_offscreen", FeedControls.GlassOffscreenSource, scrolls = true),
  ScrollPlain("feed_scroll_plain", FeedControls.Plain, scrolls = true),
}

internal enum class FeedControls { Glass, GlassOffscreenSource, Plain }

internal class GlassFeedProfilingState {
  var scenario: GlassFeedProfilingScenario? by mutableStateOf(null)
    private set
  var phase: GlassProfilingPhase by mutableStateOf(GlassProfilingPhase.Selecting)
    private set

  fun select(value: GlassFeedProfilingScenario) {
    check(phase != GlassProfilingPhase.Running)
    scenario = value
    phase = GlassProfilingPhase.Settling
  }

  fun markReady() {
    check(phase == GlassProfilingPhase.Settling)
    phase = GlassProfilingPhase.Ready
  }

  fun start() {
    if (phase == GlassProfilingPhase.Ready) phase = GlassProfilingPhase.Running
  }

  fun complete() {
    check(phase == GlassProfilingPhase.Running)
    phase = GlassProfilingPhase.Complete
  }
}

@Composable
internal fun GlassFeedProfilingSampleContent(
  state: GlassFeedProfilingState,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scenario = state.scenario
  if (scenario == null) {
    Column(
      modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Button(onClick = onBack) { Text("Back") }
      GlassFeedProfilingScenario.entries.forEach { entry ->
        Button(onClick = { state.select(entry) }) { Text(entry.id) }
      }
    }
  } else {
    GlassFeedProfilingScene(state = state, scenario = scenario, modifier = modifier)
  }
}

@Composable
private fun GlassFeedProfilingScene(
  state: GlassFeedProfilingState,
  scenario: GlassFeedProfilingScenario,
  modifier: Modifier = Modifier,
) {
  // Applied before the source first draws, and restored when the scene leaves.
  remember(scenario) {
    HazeFeatureFlags.isOffscreenSourceEnabled =
      scenario.controls == FeedControls.GlassOffscreenSource
  }
  DisposableEffect(Unit) {
    onDispose { HazeFeatureFlags.isOffscreenSourceEnabled = false }
  }

  val hazeState = rememberHazeState()
  val listState = rememberLazyListState()
  val density = LocalDensity.current
  var videoNanos by remember { mutableLongStateOf(0L) }

  LaunchedEffect(state.phase) {
    when (state.phase) {
      GlassProfilingPhase.Settling -> {
        repeat(GLASS_PROFILING_SETTLING_FRAMES) { withFrameNanos {} }
        state.markReady()
      }
      GlassProfilingPhase.Running -> {
        val distancePx = if (scenario.scrolls) with(density) { FeedScrollDistance.toPx() } else 0f
        var scrolledPx = 0f
        // Scroll inside scroll {} so isScrollInProgress is true, as it is for a real fling.
        listState.scroll {
          Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = tween(GLASS_PROFILING_DURATION_MILLIS, easing = LinearEasing),
          ) {
            // A draw-phase read: each frame redraws the video cards, not the whole feed.
            videoNanos = (value * GLASS_PROFILING_DURATION_MILLIS * 1_000_000L).toLong()
            scrollBy(distancePx * value - scrolledPx)
            scrolledPx = distancePx * value
          }
        }
        state.complete()
      }
      else -> Unit
    }
  }

  Box(modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
    LazyColumn(
      state = listState,
      contentPadding = PaddingValues(horizontal = 4.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      modifier = Modifier.fillMaxSize().hazeSource(hazeState).testTag("glass_feed_source"),
    ) {
      items(FEED_ITEM_COUNT) { index ->
        FeedCard(index = index, videoNanos = { videoNanos })
      }
    }

    Row(
      modifier = Modifier.statusBarsPadding().padding(16.dp).align(Alignment.TopStart),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      repeat(4) { index ->
        val controlModifier = Modifier
          .size(width = if (index == 3) 44.dp else 40.dp, height = 40.dp)
          .testTag("glass_feed_control_$index")
        when (scenario.controls) {
          FeedControls.Glass,
          FeedControls.GlassOffscreenSource,
          -> Box(
            controlModifier.hazeGlass(
              input = HazeInput.Sources(hazeState),
              style = FeedControlStyle,
            ),
          )
          FeedControls.Plain -> Box(
            controlModifier.clip(FeedControlShape).background(Color.White.copy(alpha = 0.18f)),
          )
        }
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .padding(16.dp)
        .testTag("glass_profiling_selected_${scenario.id}"),
    ) {
      Text(scenario.id, Modifier.testTag("glass_profiling_phase_${state.phase.id}"), color = Color.White)
      if (state.phase == GlassProfilingPhase.Ready) {
        Button(onClick = state::start, modifier = Modifier.testTag("glass_profiling_start")) {
          Text("Start")
        }
      }
    }
  }
}

@Composable
private fun FeedCard(index: Int, videoNanos: () -> Long) {
  val isVideo = index % 3 == 0
  val isFaded = index % 4 == 1
  // Like a real media card: artwork, text, and translucent chips, so each replay is not trivial.
  Box(
    Modifier
      .fillMaxWidth()
      .aspectRatio(if (index % 2 == 0) 9f / 16f else 16f / 9f)
      .then(if (isFaded) Modifier.graphicsLayer { alpha = 0.92f } else Modifier)
      .clip(RoundedCornerShape(16.dp)),
  ) {
    val artwork = remember { feedArtwork() }
    Canvas(Modifier.fillMaxSize()) {
      val hue = (index * 37f) % 360f
      val phase = if (isVideo) videoNanos() / 1_000_000_000f else 0f
      drawImage(
        image = artwork,
        dstSize = IntSize(size.width.toInt(), size.height.toInt()),
        alpha = 0.6f,
      )
      drawRect(
        Brush.linearGradient(
          listOf(Color.hsv(hue, 0.6f, 0.5f, 0.6f), Color.hsv((hue + 60f) % 360f, 0.7f, 0.3f, 0.9f)),
          start = Offset(size.width * (0.5f + 0.5f * sin(phase * 2f)), 0f),
          end = Offset(0f, size.height),
        ),
      )
      repeat(FEED_CARD_OPS) { op ->
        drawCircle(
          Color.White.copy(alpha = 0.05f),
          radius = 18f,
          center = Offset(size.width * (0.1f + 0.8f * op / FEED_CARD_OPS), size.height * 0.1f + 30f * sin(phase + op)),
        )
      }
    }
    Column(
      modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("Creator $index · ${index * 7 % 59 + 1}m", color = Color.White, style = MaterialTheme.typography.titleMedium)
      Text(
        "Caption for item $index. A couple of lines of copy that wrap like a real post, with tags #haze #glass #compose and a link.",
        color = Color.White.copy(alpha = 0.9f),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { chip ->
          Text(
            text = listOf("Like ${index * 13}", "Reply", "Share", "Save")[chip],
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
              .graphicsLayer { alpha = 0.85f }
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black.copy(alpha = 0.35f))
              .padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }
      }
      repeat(FEED_CARD_COMMENTS) { line ->
        Text(
          "user${index + line}: comment number $line on this item",
          color = Color.White.copy(alpha = 0.75f),
          style = MaterialTheme.typography.bodySmall,
          maxLines = 1,
        )
      }
    }
  }
}

private fun feedArtwork(): ImageBitmap {
  val bitmap = ImageBitmap(FEED_ARTWORK_SIZE, FEED_ARTWORK_SIZE)
  val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
  val paint = Paint()
  val cell = FEED_ARTWORK_SIZE / 16f
  for (x in 0 until 16) {
    for (y in 0 until 16) {
      paint.color = Color.hsv((x * 22f + y * 9f) % 360f, 0.5f, 0.4f + 0.03f * ((x + y) % 10))
      canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
    }
  }
  return bitmap
}

private val FeedControlShape = RoundedCornerShape(20.dp)
private val FeedControlStyle = GlassStyle.regular.then {
  shape(FeedControlShape)
  backgroundColor(Color(0xFF0B0D12))
  tint(Color.White.copy(alpha = 0.08f))
  edgeShadow(Color.Transparent)
}
private val FeedScrollDistance = 6_000.dp
private const val FEED_ITEM_COUNT = 60
private const val FEED_CARD_OPS = 24
private const val FEED_CARD_COMMENTS = 6
private const val FEED_ARTWORK_SIZE = 512
