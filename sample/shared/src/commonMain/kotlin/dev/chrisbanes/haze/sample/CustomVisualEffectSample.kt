// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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

private data class TintOption(
  val label: String,
  val color: Color,
)

private val TintOptions = listOf(
  TintOption(label = "Ocean", color = Color(0xFF0B6DFF)),
  TintOption(label = "Sunset", color = Color(0xFFE86A33)),
  TintOption(label = "Mint", color = Color(0xFF00A887)),
)

private val LocalTintColor = compositionLocalOf { TintOptions.first().color }
private val LocalTintAlpha = compositionLocalOf { 0.3f }
private val LocalTintEnabled = compositionLocalOf { true }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeApi::class)
@Composable
fun CustomVisualEffectSample(
  navController: NavHostController,
  blurEnabled: Boolean,
) {
  val hazeState = rememberHazeState()
  var selectedTintIndex by remember { mutableIntStateOf(0) }
  var drawContentBehind by remember { mutableStateOf(false) }
  var tintAlpha by remember { mutableFloatStateOf(0.30f) }

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
        contentDescription = null,
        modifier = Modifier
          .fillMaxSize()
          .hazeSource(state = hazeState),
      )

      CompositionLocalProvider(
        LocalTintColor provides TintOptions[selectedTintIndex].color,
        LocalTintAlpha provides tintAlpha,
        LocalTintEnabled provides blurEnabled,
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.Center)
            .size(width = 300.dp, height = 180.dp)
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(state = hazeState) {
              this.drawContentBehind = drawContentBehind
              tintEffect()
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
          TintOptions.forEachIndexed { index, option ->
            val selected = selectedTintIndex == index
            if (selected) {
              Button(onClick = { selectedTintIndex = index }) {
                Text(option.label)
              }
            } else {
              OutlinedButton(onClick = { selectedTintIndex = index }) {
                Text(option.label)
              }
            }
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("Draw content behind")
          Switch(checked = drawContentBehind, onCheckedChange = { drawContentBehind = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = { tintAlpha = (tintAlpha - 0.1f).coerceAtLeast(0.1f) }) {
            Text("- alpha")
          }
          Button(onClick = { tintAlpha = (tintAlpha + 0.1f).coerceAtMost(0.9f) }) {
            Text("+ alpha")
          }
          Text("${(tintAlpha * 100).toInt()}%")
        }
      }
    }
  }
}

@OptIn(ExperimentalHazeApi::class)
private class TintVisualEffect : VisualEffect {
  private var tintColor: Color = Color.Transparent
  private var tintAlpha: Float = 0.3f
  private var tintEnabled: Boolean = true

  override fun update(context: VisualEffectContext) {
    val currentTint = context.currentValueOf(LocalTintColor)
    val currentAlpha = context.currentValueOf(LocalTintAlpha)
    val currentEnabled = context.currentValueOf(LocalTintEnabled)
    if (currentTint != tintColor || currentAlpha != tintAlpha || currentEnabled != tintEnabled) {
      tintColor = currentTint
      tintAlpha = currentAlpha
      tintEnabled = currentEnabled
      context.invalidateDraw()
    }
  }

  override fun DrawScope.draw(context: VisualEffectContext) {
    if (!tintEnabled) return
    drawRect(color = tintColor.copy(alpha = tintAlpha), size = context.size)
  }
}

@OptIn(ExperimentalHazeApi::class)
private fun HazeEffectScope.tintEffect(block: TintVisualEffect.() -> Unit = {}) {
  val effect = visualEffect as? TintVisualEffect ?: TintVisualEffect()
  visualEffect = effect
  effect.block()
}
