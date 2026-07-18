// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.glassEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private val ControlShape = RoundedCornerShape(24.dp)

@Composable
fun InteractiveGlassSample(navController: NavHostController) {
  val hazeState = rememberHazeState()
  val pressedOnlySource = remember { MutableInteractionSource() }
  val allPresetsSource = remember { MutableInteractionSource() }
  val customSource = remember { MutableInteractionSource() }

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .background(
          Brush.linearGradient(
            colors = listOf(Color(0xFF0B3154), Color(0xFF4B1769), Color(0xFFB35E3E)),
          ),
        ),
    ) {
      Text(
        text = "Move, focus, and press the glass controls",
        color = Color.White.copy(alpha = 0.35f),
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(24.dp),
      )
    }

    Column(
      modifier = Modifier
        .widthIn(max = 360.dp)
        .fillMaxWidth()
        .align(Alignment.Center)
        .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      GlassControl(
        label = "Pressed only",
        interactionSource = pressedOnlySource,
        modifier = Modifier.hazeEffect(hazeState) {
          // Pressed only: the smallest opt-in.
          glassEffect {
            pressed()
            shape = ControlShape
          }
        },
      )
      GlassControl(
        label = "All presets",
        interactionSource = allPresetsSource,
        modifier = Modifier.hazeEffect(hazeState) {
          // Every preset.
          glassEffect {
            interactionSource = allPresetsSource
            interactable()
            shape = ControlShape
          }
        },
      )
      GlassControl(
        label = "Custom press",
        interactionSource = customSource,
        modifier = Modifier.hazeEffect(hazeState) {
          // Scale-only custom press with content transform and explicit motion policy.
          glassEffect {
            interactionSource = customSource
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent
            interactionTransformPivot = GlassTransformPivot.Pointer
            interactionPositionAnimationSpec = GlassDefaults.positionAnimationSpec
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.System
            hovered()
            focused()
            pressed {
              animate(
                toSpec = GlassDefaults.pressAnimationSpec,
                fromSpec = GlassDefaults.releaseAnimationSpec,
              ) {
                scale(0.98f)
              }
            }
            shape = ControlShape
          }
        },
      )
    }
  }
}

@Composable
private fun GlassControl(
  label: String,
  interactionSource: MutableInteractionSource,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(88.dp)
      .clickable(interactionSource = interactionSource, indication = null, onClick = {})
      .focusable(interactionSource = interactionSource),
    contentAlignment = Alignment.Center,
  ) {
    Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
  }
}
