// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

@Suppress("ConstPropertyName", "ktlint:standard:property-naming")
internal object GlassTraceSection {
  const val Prepare = "HazeGlass.prepare"
  const val PrepareBudget = "HazeGlass.prepareBudget"
  const val SelectDelegate = "HazeGlass.selectDelegate"
  const val DelegatePrepare = "HazeGlass.delegatePrepare"
  const val PrepareEffects = "HazeGlass.prepareEffects"
  const val PrepareLayers = "HazeGlass.prepareLayers"
  const val CreateRenderEffect = "HazeGlass.createRenderEffect"
  const val RuntimeDraw = "HazeGlass.runtimeDraw"
  const val Source = "HazeGlass.source"
  const val Blur = "HazeGlass.blur"
  const val Depth = "HazeGlass.depth"
  const val Optical = "HazeGlass.optical"
  const val Detail = "HazeGlass.detail"
  const val Rim = "HazeGlass.rim"
  const val InteractionOptical = "HazeGlass.interactionOptical"
  const val InteractionDetail = "HazeGlass.interactionDetail"
  const val InteractionLighting = "HazeGlass.interactionLighting"
  const val GroupAlpha = "HazeGlass.groupAlpha"
  const val Compose = "HazeGlass.compose"

  val all = listOf(
    Prepare,
    PrepareBudget,
    SelectDelegate,
    DelegatePrepare,
    PrepareEffects,
    PrepareLayers,
    CreateRenderEffect,
    RuntimeDraw,
    Source,
    Blur,
    Depth,
    Optical,
    Detail,
    Rim,
    InteractionOptical,
    InteractionDetail,
    InteractionLighting,
    GroupAlpha,
    Compose,
  )
}
