// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalFoundationApi::class, ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
public fun GlassProductSample(navController: NavHostController) {
  var selectedArtworkIndex by rememberSaveable { mutableIntStateOf(0) }
  val favorites = remember { mutableStateMapOf<Int, Boolean>() }
  var recordingMode by rememberSaveable { mutableStateOf(false) }

  GlassProductSampleContent(
    selectedArtworkIndex = selectedArtworkIndex,
    favorite = favorites[selectedArtworkIndex] == true,
    recordingMode = recordingMode,
    onArtworkSelected = { selectedArtworkIndex = it.mod(GalleryArtworks.size) },
    onFavoriteChanged = { favorites[selectedArtworkIndex] = it },
    onRecordingModeChanged = { recordingMode = it },
    onBack = navController::navigateUp,
  )
}

internal fun productGlassStyle(isDark: Boolean): GlassStyle = GlassStyle {
  tint(if (isDark) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.1f))
  optics(GlassOptics.Adaptive)
}

@Composable
public fun GlassProductSampleContent(
  selectedArtworkIndex: Int,
  favorite: Boolean,
  recordingMode: Boolean,
  onArtworkSelected: (Int) -> Unit,
  onFavoriteChanged: (Boolean) -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val hazeState = rememberHazeState()
  val pagerState = rememberPagerState(
    initialPage = selectedArtworkIndex,
    pageCount = { GalleryArtworks.size },
  )
  val isDark = androidx.compose.foundation.isSystemInDarkTheme()
  var informationExpanded by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(selectedArtworkIndex) {
    if (pagerState.currentPage != selectedArtworkIndex) {
      pagerState.animateScrollToPage(selectedArtworkIndex)
    }
  }
  LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
      .distinctUntilChanged()
      .collect(onArtworkSelected)
  }

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .testTag("glass_product_page_${pagerState.settledPage}"),
  ) {
    val landscape = maxWidth > maxHeight
    val galleryHeight = maxHeight
    HorizontalPager(
      state = pagerState,
      modifier = Modifier
        .fillMaxSize()
        .testTag("glass_product_pager"),
    ) { page ->
      Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GalleryBackdrop(
          hazeState = hazeState,
          artworkIndex = page,
          backdrop = GlassGalleryBackdropId.Gallery,
          modifier = Modifier
            .fillMaxWidth()
            .height(galleryHeight)
            .pointerInput(recordingMode) {
              detectTapGestures { onRecordingModeChanged(false) }
            },
        )
        GalleryProductDetails(
          artwork = GalleryArtworks[page],
          modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF101218))
            .padding(32.dp),
        )
      }
    }

    ProductTopBar(
      hazeState = hazeState,
      selectedArtworkIndex = selectedArtworkIndex,
      recordingMode = recordingMode,
      onBack = onBack,
      onRecordingModeChanged = onRecordingModeChanged,
      modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
    )
    ProductMetadataCard(
      hazeState = hazeState,
      artwork = GalleryArtworks[selectedArtworkIndex],
      style = productGlassStyle(isDark),
      informationExpanded = informationExpanded,
      modifier = Modifier
        .align(if (landscape) Alignment.CenterStart else Alignment.Center)
        .padding(24.dp)
        .widthIn(max = 360.dp),
    )
    ProductActionDock(
      hazeState = hazeState,
      favorite = favorite,
      landscape = landscape,
      informationExpanded = informationExpanded,
      onPrevious = { onArtworkSelected((selectedArtworkIndex - 1).mod(GalleryArtworks.size)) },
      onNext = { onArtworkSelected((selectedArtworkIndex + 1).mod(GalleryArtworks.size)) },
      onFavoriteChanged = onFavoriteChanged,
      onInformationChanged = { informationExpanded = it },
      modifier = Modifier
        .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
        .padding(24.dp),
    )
  }
}

@Composable
private fun GalleryProductDetails(
  artwork: GalleryArtwork,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Gallery edition", color = Color.White, style = MaterialTheme.typography.headlineMedium)
    Text(
      "${artwork.subtitle} · Available to view in the Haze collection.",
      color = Color.White.copy(alpha = 0.72f),
      style = MaterialTheme.typography.bodyLarge,
    )
  }
}

@Composable
private fun ProductTopBar(
  hazeState: HazeState,
  selectedArtworkIndex: Int,
  recordingMode: Boolean,
  onBack: () -> Unit,
  onRecordingModeChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  GlassSurface(
    hazeState = hazeState,
    style = productGlassStyle(androidx.compose.foundation.isSystemInDarkTheme()),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    modifier = modifier,
    interactionStyle = GlassStyle {
      hovered {}
      pressed {}
    },
  ) {
    Row(
      modifier = Modifier.padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text("Glass Gallery", color = Color.White, style = MaterialTheme.typography.labelLarge)
        Text(
          text = "${selectedArtworkIndex + 1} / ${GalleryArtworks.size}",
          color = Color.White.copy(alpha = 0.72f),
          style = MaterialTheme.typography.labelMedium,
        )
      }
      if (!recordingMode) {
        IconButton(onClick = { onRecordingModeChanged(true) }) {
          Icon(VisibilityOffIcon, contentDescription = "Enter recording mode")
        }
      }
    }
  }
}

@Composable
private fun ProductMetadataCard(
  hazeState: HazeState,
  artwork: GalleryArtwork,
  style: GlassStyle,
  informationExpanded: Boolean,
  modifier: Modifier = Modifier,
) {
  GlassSurface(
    hazeState = hazeState,
    style = style,
    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    modifier = modifier,
  ) {
    AnimatedContent(targetState = artwork) { displayedArtwork ->
      Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          displayedArtwork.title,
          color = displayedArtwork.foreground,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        Text(
          displayedArtwork.subtitle,
          color = displayedArtwork.foreground.copy(alpha = 0.76f),
          style = MaterialTheme.typography.bodyLarge,
        )
        AnimatedVisibility(visible = informationExpanded) {
          Text(
            displayedArtwork.description,
            color = displayedArtwork.foreground.copy(alpha = 0.84f),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
  }
}

@Composable
private fun ProductActionDock(
  hazeState: HazeState,
  favorite: Boolean,
  landscape: Boolean,
  informationExpanded: Boolean,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onFavoriteChanged: (Boolean) -> Unit,
  onInformationChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  GlassSurface(
    hazeState = hazeState,
    style = productGlassStyle(androidx.compose.foundation.isSystemInDarkTheme()),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    modifier = modifier,
    interactionStyle = GlassStyle {
      hovered {}
      pressed {}
    },
  ) {
    val actions: @Composable () -> Unit = {
      IconButton(onClick = onPrevious) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous artwork")
      }
      IconButton(onClick = { onFavoriteChanged(!favorite) }) {
        Icon(
          imageVector = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
          contentDescription = if (favorite) "Remove from favorites" else "Favorite artwork",
        )
      }
      IconButton(onClick = { onInformationChanged(!informationExpanded) }) {
        Icon(
          imageVector = Icons.Outlined.Info,
          contentDescription = "Artwork information",
        )
      }
      IconButton(onClick = onNext) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next artwork")
      }
    }
    if (landscape) {
      Column(modifier = Modifier.padding(4.dp), content = { actions() })
    } else {
      Row(modifier = Modifier.padding(4.dp), content = { actions() })
    }
  }
}
