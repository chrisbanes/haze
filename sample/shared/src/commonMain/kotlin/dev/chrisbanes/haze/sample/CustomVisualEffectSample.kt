// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.VisualEffect
import dev.chrisbanes.haze.VisualEffectContext
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.PI
import kotlin.math.sin

private data class SparkOption(
  val label: String,
  val color: Color,
)

private val SparkOptions = listOf(
  SparkOption(label = "Ocean", color = Color(0xFF0B6DFF)),
  SparkOption(label = "Sunset", color = Color(0xFFE86A33)),
  SparkOption(label = "Mint", color = Color(0xFF00A887)),
)

private val LocalSparkColor = compositionLocalOf { SparkOptions.first().color }
private val LocalSparkAlpha = compositionLocalOf { 0.3f }
private val LocalSparkEnabled = compositionLocalOf { true }
private val LocalSparkleEnabled = compositionLocalOf { true }
private val LocalSparklePhase = compositionLocalOf { 0f }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
fun CustomVisualEffectSample(
  navController: NavHostController,
  blurEnabled: Boolean,
) {
  val hazeState = rememberHazeState()
  var selectedSparkIndex by remember { mutableIntStateOf(0) }
  var drawContentBehind by remember { mutableStateOf(false) }
  var sparkAlpha by remember { mutableFloatStateOf(0.30f) }
  var animateBackgroundLayers by remember { mutableStateOf(true) }
  var showSecondarySource by remember { mutableStateOf(true) }
  var sparkleEnabled by remember { mutableStateOf(true) }

  val transition = rememberInfiniteTransition(label = "custom_spark")
  val animatedPhase by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 7_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "spark_phase",
  )
  val layerPhase = if (animateBackgroundLayers) animatedPhase else 0f
  val theta = (layerPhase * (2f * PI)).toFloat()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Custom VisualEffect") },
        navigationIcon = {
          IconButton(
            onClick = navController::navigateUp,
            modifier = Modifier.testTag("back"),
          ) {
            Icon(Icons.AutoMirrored.Default.ArrowBack, null)
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
    },
    modifier = Modifier.fillMaxSize(),
  ) { contentPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(contentPadding),
    ) {
      AsyncImage(
        model = rememberRandomSampleImageUrl(index = 7),
        contentScale = ContentScale.Crop,
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            scaleX = 1f + (0.06f * sin(theta))
            scaleY = 1f + (0.05f * sin(theta * 1.5f))
            translationX = 36f * sin(theta * 1.2f)
            translationY = 28f * sin(theta * 1.7f)
          }
          .hazeSource(state = hazeState),
      )

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            brush = Brush.radialGradient(
              colors = listOf(
                Color.White.copy(alpha = 0.18f),
                Color.Transparent,
              ),
            ),
          )
          .hazeSource(state = hazeState, zIndex = 1f),
      )

      if (showSecondarySource) {
        AsyncImage(
          model = rememberRandomSampleImageUrl(index = 19),
          contentScale = ContentScale.Crop,
          contentDescription = null,
          modifier = Modifier
            .align(Alignment.Center)
            .size(width = 320.dp, height = 220.dp)
            .alpha(0.85f)
            .clip(RoundedCornerShape(36.dp))
            .graphicsLayer {
              rotationZ = 14f * sin(theta * 0.9f)
              translationX = 70f * sin(theta * 1.1f)
              translationY = 40f * sin(theta * 1.4f)
              scaleX = 1f + (0.08f * sin(theta * 1.6f))
              scaleY = 1f + (0.06f * sin(theta * 1.3f))
            }
            .hazeSource(state = hazeState, zIndex = 2f),
        )
      }

      CompositionLocalProvider(
        LocalSparkColor provides SparkOptions[selectedSparkIndex].color,
        LocalSparkAlpha provides sparkAlpha,
        LocalSparkEnabled provides blurEnabled,
        LocalSparkleEnabled provides sparkleEnabled,
        LocalSparklePhase provides animatedPhase,
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .size(width = 300.dp, height = 180.dp)
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(state = hazeState) {
              this.drawContentBehind = drawContentBehind
              sparkEffect()
            },
        ) {
          Text(
            text = "Custom effect node",
            modifier = Modifier
              .align(Alignment.Center)
              .testTag("custom_effect_label"),
          )
        }
      }

      Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp),
      ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          SparkOptions.forEachIndexed { index, option ->
            val selected = selectedSparkIndex == index
            if (selected) {
              Button(onClick = { selectedSparkIndex = index }) {
                Text(option.label)
              }
            } else {
              OutlinedButton(onClick = { selectedSparkIndex = index }) {
                Text(option.label)
              }
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Animate background")
          Switch(
            checked = animateBackgroundLayers,
            onCheckedChange = { animateBackgroundLayers = it },
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Show 2nd source")
          Switch(
            checked = showSecondarySource,
            onCheckedChange = { showSecondarySource = it },
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Sparkle")
          Switch(checked = sparkleEnabled, onCheckedChange = { sparkleEnabled = it })
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          // Note: drawContentBehind only applies to foreground mode (hazeEffect without state).
          // This sample uses background mode, so this toggle has no visual effect.
          Text("Draw content behind")
          Switch(checked = drawContentBehind, onCheckedChange = { drawContentBehind = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { sparkAlpha = (sparkAlpha - 0.1f).coerceAtLeast(0.1f) }) {
            Text("- alpha")
          }
          Button(onClick = { sparkAlpha = (sparkAlpha + 0.1f).coerceAtMost(0.9f) }) {
            Text("+ alpha")
          }
          Text("${(sparkAlpha * 100).toInt()}%")
        }
      }
    }
  }
}

@OptIn(ExperimentalHazeApi::class)
private class SparkVisualEffect : VisualEffect {
  private var sparkColor: Color = Color.Transparent
  private var sparkAlpha: Float = 0.3f
  private var sparkEnabled: Boolean = true
  private var sparkleEnabled: Boolean = true
  private var sparklePhase: Float = 0f

  override fun update(context: VisualEffectContext) {
    val currentColor = context.currentValueOf(LocalSparkColor)
    val currentAlpha = context.currentValueOf(LocalSparkAlpha)
    val currentEnabled = context.currentValueOf(LocalSparkEnabled)
    val currentSparkleEnabled = context.currentValueOf(LocalSparkleEnabled)
    val currentSparklePhase = context.currentValueOf(LocalSparklePhase)
    if (
      currentColor != sparkColor ||
      currentAlpha != sparkAlpha ||
      currentEnabled != sparkEnabled ||
      currentSparkleEnabled != sparkleEnabled ||
      currentSparklePhase != sparklePhase
    ) {
      sparkColor = currentColor
      sparkAlpha = currentAlpha
      sparkEnabled = currentEnabled
      sparkleEnabled = currentSparkleEnabled
      sparklePhase = currentSparklePhase
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    if (!sparkEnabled) return

    val tintAlpha = sparkAlpha.coerceIn(0f, 1f)
    drawRect(color = sparkColor.copy(alpha = tintAlpha), size = size)

    if (!sparkleEnabled || tintAlpha <= 0f) return

    val bandCenterX = (-size.width * 0.4f) + ((size.width * 1.8f) * sparklePhase)
    val shimmer = Brush.linearGradient(
      colors = listOf(
        Color.Transparent,
        sparkColor.copy(alpha = tintAlpha * 0.16f),
        Color.White.copy(alpha = tintAlpha * 0.38f),
        sparkColor.copy(alpha = tintAlpha * 0.16f),
        Color.Transparent,
      ),
      start = androidx.compose.ui.geometry.Offset(x = bandCenterX - (size.width * 0.3f), y = 0f),
      end = androidx.compose.ui.geometry.Offset(x = bandCenterX + (size.width * 0.3f), y = size.height),
    )
    val minDimension = minOf(size.width, size.height)
    val corner = minDimension * 0.14f
    val strokeWidth = (minDimension * 0.06f).coerceAtLeast(2f)

    drawRoundRect(
      brush = shimmer,
      cornerRadius = CornerRadius(corner, corner),
      size = size,
      style = Stroke(width = strokeWidth),
    )
  }
}

@OptIn(ExperimentalHazeApi::class)
private fun HazeEffectScope.sparkEffect(block: SparkVisualEffect.() -> Unit = {}) {
  val effect = visualEffect as? SparkVisualEffect ?: SparkVisualEffect()
  visualEffect = effect
  effect.block()
}
