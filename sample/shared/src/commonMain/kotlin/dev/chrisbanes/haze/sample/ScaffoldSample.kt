// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

enum class ScaffoldSampleMode {
  Default,
  Progressive,
  Mask,
  StyleChurn,
}

@OptIn(
  ExperimentalMaterial3Api::class,
  ExperimentalHazeApi::class,
)
@Composable
fun ScaffoldSample(
  navController: NavHostController,
  effect: SampleEffect = SampleEffect.Blur,
  mode: ScaffoldSampleMode = ScaffoldSampleMode.Default,
  performanceMode: HazePerformanceMode = HazePerformanceMode.Default,
  sourceOffset: (() -> Float)? = null,
  sourceDrawProgress: (() -> Float)? = null,
  profilingDrawProgress: (() -> Float)? = null,
  useBackdrop: Boolean = false,
) {
  val hazeState = rememberHazeState()
  val hazeInput = if (useBackdrop) {
    HazeInput.Backdrop(HazeInput.Sources(hazeState))
  } else {
    HazeInput.Sources(hazeState)
  }
  val gridState = rememberLazyGridState()
  val showNavigationBar by remember(gridState) {
    derivedStateOf { gridState.firstVisibleItemIndex == 0 }
  }
  // The benchmark reads a changing value while resolving to the same Style on every frame.
  val styleChurnPhase = if (mode == ScaffoldSampleMode.StyleChurn) {
    rememberInfiniteTransition(label = "Blur style churn").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1_000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
      ),
      label = "Blur style churn phase",
    )
  } else {
    null
  }

  val style = if (mode == ScaffoldSampleMode.StyleChurn) {
    null
  } else {
    HazeMaterials.regular(MaterialTheme.colorScheme.surface)
  }
  val scaffoldModifier = Modifier.fillMaxSize().let { modifier ->
    styleChurnPhase?.let { phase ->
      modifier.graphicsLayer {
        // Keep a constant RenderThread workload so frame metrics remain comparable when the
        // equivalent style update is suppressed.
        translationX = phase.value
      }
    } ?: modifier
  }
  val glassTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.16f)
  val glassBackgroundColor = MaterialTheme.colorScheme.surface
  val progressive = HazeProgressive.verticalGradient(
    startIntensity = 1f,
    endIntensity = 0f,
  )

  Scaffold(
    topBar = {
      val currentBlurStyle = style ?: HazeMaterials.regular(MaterialTheme.colorScheme.surface)
      when (effect) {
        SampleEffect.Blur -> LargeTopAppBar(
          title = {},
          navigationIcon = {
            IconButton(
              onClick = navController::navigateUp,
              modifier = Modifier.testTag("back"),
            ) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, null)
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
          ),
          modifier = Modifier
            .drawWithContent {
              profilingDrawProgress?.invoke()
              drawContent()
            }
            .hazeBlur(
              input = hazeInput,
              performanceMode = performanceMode,
              style = currentBlurStyle.then {
                when (mode) {
                  ScaffoldSampleMode.Default -> Unit
                  ScaffoldSampleMode.Progressive -> progressive(progressive)
                  ScaffoldSampleMode.Mask -> mask(Brush.easedVerticalGradient(EaseIn))
                  ScaffoldSampleMode.StyleChurn -> {
                    alpha(1f + checkNotNull(styleChurnPhase).value * 0f)
                  }
                }
              },
            )
            .fillMaxWidth(),
        )

        SampleEffect.Glass -> GlassScaffoldTopBar(
          hazeState = hazeState,
          backgroundColor = glassBackgroundColor,
          tint = glassTint,
          performanceMode = performanceMode,
          optics = when (mode) {
            ScaffoldSampleMode.Progressive -> GlassOptics(progressive = progressive)
            else -> GlassDefaults.optics
          },
          title = "Glass shaped boundary".takeIf { mode == ScaffoldSampleMode.Mask },
          onBack = navController::navigateUp,
          modifier = Modifier
            .drawWithContent {
              profilingDrawProgress?.invoke()
              drawContent()
            }
            .fillMaxWidth(),
        )
      }
    },
    bottomBar = {
      val currentBlurStyle = style ?: HazeMaterials.regular(MaterialTheme.colorScheme.surface)
      var selectedIndex by remember { mutableIntStateOf(0) }
      AnimatedVisibility(
        visible = showNavigationBar,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
      ) {
        when (effect) {
          SampleEffect.Blur -> SampleNavigationBar(
            selectedIndex = selectedIndex,
            onItemClicked = { selectedIndex = it },
            modifier = Modifier
              .drawWithContent {
                profilingDrawProgress?.invoke()
                drawContent()
              }
              .hazeBlur(
                input = hazeInput,
                performanceMode = performanceMode,
                style = currentBlurStyle.then {
                  if (mode == ScaffoldSampleMode.StyleChurn) {
                    alpha(1f + checkNotNull(styleChurnPhase).value * 0f)
                  }
                },
              )
              .fillMaxWidth(),
          )

          SampleEffect.Glass -> GlassScaffoldNavigationBar(
            hazeState = hazeState,
            backgroundColor = glassBackgroundColor,
            tint = glassTint,
            performanceMode = performanceMode,
            selectedIndex = selectedIndex,
            onItemClicked = { selectedIndex = it },
            modifier = Modifier
              .drawWithContent {
                profilingDrawProgress?.invoke()
                drawContent()
              }
              .fillMaxWidth(),
          )
        }
      }
    },
    modifier = scaffoldModifier,
  ) { contentPadding ->
    LazyVerticalGrid(
      state = gridState,
      columns = GridCells.Adaptive(128.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      contentPadding = contentPadding,
      modifier = Modifier
        .fillMaxSize()
        .testTag("lazy_grid")
        .hazeSource(state = hazeState)
        .drawWithContent {
          sourceDrawProgress?.invoke()
          drawContent()
        }
        .graphicsLayer { translationY = sourceOffset?.invoke() ?: 0f },
    ) {
      items(50) { index ->
        ImageItem(
          text = "${index + 1}",
          index = index,
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3 / 4f),
        )
      }
    }
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun GlassScaffoldTopBar(
  hazeState: HazeState,
  backgroundColor: Color,
  tint: Color,
  performanceMode: HazePerformanceMode,
  optics: GlassOptics,
  title: String?,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    GlassScaffoldSurface(
      hazeState = hazeState,
      backgroundColor = backgroundColor,
      tint = tint,
      performanceMode = performanceMode,
      optics = optics,
      shape = RoundedCornerShape(50),
      modifier = Modifier.size(56.dp).testTag("glass_scaffold_back"),
    ) {
      IconButton(onClick = onBack) {
        Icon(
          imageVector = Icons.AutoMirrored.Default.ArrowBack,
          contentDescription = "Back",
        )
      }
    }

    if (title != null) {
      GlassScaffoldSurface(
        hazeState = hazeState,
        backgroundColor = backgroundColor,
        tint = tint,
        performanceMode = performanceMode,
        optics = optics,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.height(56.dp),
      ) {
        Text(
          text = title,
          modifier = Modifier.padding(horizontal = 20.dp),
        )
      }
    }
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun GlassScaffoldNavigationBar(
  hazeState: HazeState,
  backgroundColor: Color,
  tint: Color,
  performanceMode: HazePerformanceMode,
  selectedIndex: Int,
  onItemClicked: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  GlassScaffoldSurface(
    hazeState = hazeState,
    backgroundColor = backgroundColor,
    tint = tint,
    performanceMode = performanceMode,
    optics = GlassDefaults.optics,
    shape = RoundedCornerShape(32.dp),
    modifier = modifier
      .windowInsetsPadding(WindowInsets.navigationBars)
      .padding(16.dp)
      .testTag("glass_scaffold_navigation"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(64.dp)
        .padding(horizontal = 4.dp)
        .selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      for (i in 0 until 3) {
        NavigationBarItem(
          selected = selectedIndex == i,
          onClick = { onItemClicked(i) },
          icon = { SampleNavigationIcon(i) },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
          ),
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
@OptIn(ExperimentalHazeApi::class)
private fun GlassScaffoldSurface(
  hazeState: HazeState,
  backgroundColor: Color,
  tint: Color,
  performanceMode: HazePerformanceMode,
  optics: GlassOptics,
  shape: RoundedCornerShape,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .hazeGlass(
        input = HazeInput.Sources(hazeState),
        performanceMode = performanceMode,
        style = GlassStyle {
          backgroundColor(backgroundColor)
          tint(tint)
          shape(shape)
          optics(optics)
          hovered {
            animate(DefaultGlassHoverAnimationSpec, DefaultGlassReleaseAnimationSpec) {
              lightingIntensity(0.35f)
              refractionMultiplier(1.02f)
              whitePointDelta(0.01f)
            }
          }
          pressed {
            animate(DefaultGlassPressAnimationSpec, DefaultGlassReleaseAnimationSpec) {
              lightingIntensity(1f)
              refractionMultiplier(1.08f)
              whitePointDelta(0.04f)
              scale(0.98f)
            }
          }
        },
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionTransformPivot = GlassTransformPivot.Pointer,
      )
      .clip(shape),
  ) {
    content()
  }
}

@Composable
private fun SampleNavigationBar(
  selectedIndex: Int,
  onItemClicked: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  NavigationBar(
    containerColor = Color.Transparent,
    modifier = modifier,
  ) {
    for (i in (0 until 3)) {
      NavigationBarItem(
        selected = selectedIndex == i,
        onClick = { onItemClicked(i) },
        icon = { SampleNavigationIcon(i) },
      )
    }
  }
}

@Composable
private fun SampleNavigationIcon(index: Int) {
  Icon(
    imageVector = when (index) {
      0 -> Icons.Default.Call
      1 -> Icons.Default.Lock
      else -> Icons.Default.Search
    },
    contentDescription = when (index) {
      0 -> "Calls"
      1 -> "Security"
      else -> "Search"
    },
  )
}
