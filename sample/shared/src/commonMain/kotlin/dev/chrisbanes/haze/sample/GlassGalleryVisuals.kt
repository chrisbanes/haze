// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource

@Composable
internal fun GalleryBackdrop(
  hazeState: HazeState,
  artworkIndex: Int,
  backdrop: GlassGalleryBackdropId,
  modifier: Modifier = Modifier,
  offsetProvider: () -> Float = { 0f },
  horizontalOverscanFraction: Float = 0f,
) {
  val artwork = GalleryArtworks[artworkIndex.mod(GalleryArtworks.size)]
  Box(
    modifier = modifier
      .clipToBounds()
      .hazeSource(hazeState),
  ) {
    Canvas(
      modifier = Modifier
        .matchParentSize()
        .graphicsLayer {
          val offset = offsetProvider()
          require(offset.isFinite())
          require(horizontalOverscanFraction.isFinite() && horizontalOverscanFraction >= 0f)
          scaleX = 1f + horizontalOverscanFraction * 2f
          translationX = size.width * if (horizontalOverscanFraction > 0f) {
            offset.coerceIn(-horizontalOverscanFraction, horizontalOverscanFraction)
          } else {
            offset
          }
        },
    ) {
      when (backdrop) {
        GlassGalleryBackdropId.Gallery -> {
          drawRect(Brush.linearGradient(artwork.colors))
          repeat(12) { index ->
            val x = size.width * index / 11f
            drawLine(
              color = artwork.foreground.copy(alpha = 0.16f),
              start = Offset(x, 0f),
              end = Offset(size.width - x, size.height),
              strokeWidth = 1.dp.toPx(),
            )
          }
          drawCircle(
            color = artwork.accent.copy(alpha = 0.72f),
            radius = size.minDimension * 0.18f,
            center = Offset(size.width * 0.72f, size.height * 0.28f),
          )
        }

        GlassGalleryBackdropId.Grid -> {
          drawRect(Color(0xFF10131A))
          val spacing = 24.dp.toPx()
          var x = 0f
          while (x <= size.width) {
            drawLine(Color.White.copy(alpha = 0.24f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            x += spacing
          }
          var y = 0f
          while (y <= size.height) {
            drawLine(Color.White.copy(alpha = 0.24f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            y += spacing
          }
        }

        GlassGalleryBackdropId.Typography -> drawRect(Color(0xFF0057FF))
        GlassGalleryBackdropId.Bands -> {
          val bandHeight = size.height / artwork.colors.size
          artwork.colors.forEachIndexed { index, color ->
            drawRect(color, topLeft = Offset(0f, bandHeight * index), size = Size(size.width, bandHeight))
          }
        }

        GlassGalleryBackdropId.Uniform -> drawRect(artwork.colors.first())
      }
    }

    if (backdrop == GlassGalleryBackdropId.Gallery || backdrop == GlassGalleryBackdropId.Typography) {
      Column(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(32.dp),
      ) {
        Text(
          text = artwork.title.uppercase(),
          color = artwork.foreground,
          style = MaterialTheme.typography.displayMedium,
          fontWeight = FontWeight.Black,
        )
        Text(
          text = artwork.subtitle,
          color = artwork.foreground.copy(alpha = 0.76f),
          style = MaterialTheme.typography.titleMedium,
        )
      }
    }
  }
}

@Composable
internal fun GlassSurface(
  hazeState: HazeState,
  style: GlassStyle,
  shape: RoundedCornerShape,
  modifier: Modifier = Modifier,
  interactionSource: InteractionSource? = null,
  interactionStyle: GlassStyle = GlassStyle,
  content: @Composable BoxScope.() -> Unit,
) {
  val backgroundColor = MaterialTheme.colorScheme.surface
  Box(
    modifier = modifier
      // Let Glass own the material silhouette. An outer Compose clip creates a second,
      // independently-rasterized rounded boundary and exposes isolated carrier pixels on Skiko.
      .hazeGlass(
        input = HazeInput.Sources(hazeState),
        style = GlassStyle {
          backgroundColor(backgroundColor)
        }.then(style).then {
          this.shape(shape)
        }.then(interactionStyle),
        interactionSource = interactionSource,
        interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
        interactionTransformPivot = GlassTransformPivot.Pointer,
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
      )
      // This clip is inside the effect node, so it only constrains foreground content.
      .clip(shape),
    content = content,
  )
}

@Composable
internal fun DemoChrome(
  hazeState: HazeState,
  onBack: () -> Unit,
  onEnterRecordingMode: () -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier,
  isPlaying: Boolean? = null,
  onPlayPause: (() -> Unit)? = null,
) {
  val shape = RoundedCornerShape(24.dp)
  GlassSurface(
    hazeState = hazeState,
    style = GlassDefaults.style.then {
      tint(Color.Black.copy(alpha = 0.08f))
    },
    shape = shape,
    modifier = modifier,
    interactionStyle = GlassStyle {
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
  ) {
    Row(
      modifier = Modifier.padding(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
      }
      if (isPlaying != null && onPlayPause != null) {
        IconButton(onClick = onPlayPause) {
          Icon(
            imageVector = if (isPlaying) PauseIcon else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause animation" else "Play animation",
          )
        }
      }
      IconButton(onClick = onReset) {
        Icon(ReplayIcon, contentDescription = "Reset demo")
      }
      IconButton(onClick = onEnterRecordingMode) {
        Icon(VisibilityOffIcon, contentDescription = "Enter recording mode")
      }
    }
  }
}

private val PauseIcon = materialIcon("Pause") {
  path(fill = SolidColor(Color.Black)) {
    moveTo(6f, 19f)
    horizontalLineTo(10f)
    verticalLineTo(5f)
    horizontalLineTo(6f)
    close()
    moveTo(14f, 5f)
    verticalLineTo(19f)
    horizontalLineTo(18f)
    verticalLineTo(5f)
    close()
  }
}

private val ReplayIcon = materialIcon("Replay") {
  path(fill = SolidColor(Color.Black)) {
    moveTo(12f, 5f)
    verticalLineTo(1f)
    lineTo(7f, 6f)
    lineTo(12f, 11f)
    verticalLineTo(7f)
    curveTo(15.31f, 7f, 18f, 9.69f, 18f, 13f)
    curveTo(18f, 16.31f, 15.31f, 19f, 12f, 19f)
    curveTo(8.69f, 19f, 6f, 16.31f, 6f, 13f)
    horizontalLineTo(4f)
    curveTo(4f, 17.42f, 7.58f, 21f, 12f, 21f)
    curveTo(16.42f, 21f, 20f, 17.42f, 20f, 13f)
    curveTo(20f, 8.58f, 16.42f, 5f, 12f, 5f)
    close()
  }
}

internal val VisibilityOffIcon = materialIcon("VisibilityOff") {
  path(fill = SolidColor(Color.Black)) {
    moveTo(2f, 4.27f)
    lineTo(3.28f, 3f)
    lineTo(21f, 20.72f)
    lineTo(19.73f, 22f)
    lineTo(16.72f, 18.99f)
    curveTo(15.27f, 19.63f, 13.67f, 20f, 12f, 20f)
    curveTo(7f, 20f, 2.73f, 16.89f, 1f, 12.5f)
    curveTo(1.8f, 10.47f, 3.15f, 8.74f, 4.85f, 7.47f)
    close()
    moveTo(7.32f, 9.94f)
    curveTo(7.11f, 10.44f, 7f, 10.97f, 7f, 11.5f)
    curveTo(7f, 14.26f, 9.24f, 16.5f, 12f, 16.5f)
    curveTo(12.53f, 16.5f, 13.06f, 16.39f, 13.56f, 16.18f)
    close()
    moveTo(9.92f, 7.54f)
    lineTo(8.37f, 5.99f)
    curveTo(9.53f, 5.35f, 10.74f, 5f, 12f, 5f)
    curveTo(17f, 5f, 21.27f, 8.11f, 23f, 12.5f)
    curveTo(22.35f, 14.15f, 21.34f, 15.59f, 20.08f, 16.77f)
    lineTo(16.63f, 13.32f)
    curveTo(16.87f, 12.74f, 17f, 12.13f, 17f, 11.5f)
    curveTo(17f, 8.74f, 14.76f, 6.5f, 12f, 6.5f)
    curveTo(11.26f, 6.5f, 10.56f, 6.66f, 9.92f, 6.94f)
    close()
  }
}

private fun materialIcon(
  name: String,
  block: ImageVector.Builder.() -> Unit,
) = ImageVector.Builder(
  name = name,
  defaultWidth = 24.dp,
  defaultHeight = 24.dp,
  viewportWidth = 24f,
  viewportHeight = 24f,
).apply(block).build()
